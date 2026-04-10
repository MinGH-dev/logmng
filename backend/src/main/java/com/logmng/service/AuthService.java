package com.logmng.service;

import com.logmng.config.AuthProperties;
import com.logmng.dto.request.ChangeMyPasswordRequest;
import com.logmng.dto.request.LoginRequest;
import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.response.ScreenFunctionCapability;
import com.logmng.exception.CustomException;
import com.logmng.util.IpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import jakarta.servlet.http.HttpServletRequest;

import com.logmng.constants.ScreenConstants;

import java.util.Locale;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 인증 서비스. 로그인은 app_user 테이블 기준(DataSource).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final IpUtil ipUtil;
    private final DataSource dataSource;
    private final PermissionGroupService permissionGroupService;
    private final AppUserResolver appUserResolver;
    private final AuthProperties authProperties;
    private final ExternalIdentityService externalIdentityService;
    private final LdapBindAuthenticator ldapBindAuthenticator;

    @Value("${app.security.authorized-ips:127.0.0.1,localhost,0:0:0:0:0:0:0:1}")
    private String authorizedIPs;

    public AuthService(IpUtil ipUtil, DataSource dataSource, PermissionGroupService permissionGroupService,
                      AppUserResolver appUserResolver,
                      AuthProperties authProperties, ExternalIdentityService externalIdentityService,
                      @Autowired(required = false) LdapBindAuthenticator ldapBindAuthenticator) {
        this.ipUtil = ipUtil;
        this.dataSource = dataSource;
        this.permissionGroupService = permissionGroupService;
        this.appUserResolver = appUserResolver;
        this.authProperties = authProperties;
        this.externalIdentityService = externalIdentityService;
        this.ldapBindAuthenticator = ldapBindAuthenticator;
    }

    /**
     * 로그인: {@code auth.login.mode} 가 local 이면 password_hash 검증, ad 이면 디렉터리 바인드 후 외부 매핑으로 app_user 조회.
     */
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String mode = authProperties.getLogin().getMode() != null
                ? authProperties.getLogin().getMode().trim().toLowerCase(Locale.ROOT) : "local";
        if ("local".equals(mode)) {
            return loginLocal(request, httpRequest);
        }
        if ("ad".equals(mode)) {
            return loginAd(request, httpRequest);
        }
        throw CustomException.serviceUnavailable("인증 설정이 올바르지 않습니다.", "AUTH_CONFIGURATION_ERROR");
    }

    private LoginResponse loginLocal(LoginRequest request, HttpServletRequest httpRequest) {
        if (StringUtils.hasText(request.getPrincipal())) {
            throw CustomException.badRequest("로그인 요청 형식이 올바르지 않습니다.", "INVALID_INPUT");
        }
        String employeeNumber = request.getEmployeeNumber() != null ? request.getEmployeeNumber().trim() : null;
        boolean hasEmployeeNumber = StringUtils.hasText(employeeNumber);
        boolean hasLegacyUserId = request.getUserId() != null;
        if (hasEmployeeNumber == hasLegacyUserId) {
            throw CustomException.badRequest("로그인 요청 형식이 올바르지 않습니다.", "INVALID_INPUT");
        }
        Long userId = request.getUserId();
        String password = request.getPassword();

        String clientIP = ipUtil.getClientIP(httpRequest);
        log.info("로그인 시도 (local) - IP: {}, 식별자 타입: {}", clientIP, hasEmployeeNumber ? "employeeNumber" : "userId");

        if (!ipUtil.isAuthorizedIP(clientIP, authorizedIPs)) {
            log.warn("인가되지 않은 IP에서 로그인 시도: {}", clientIP);
            throw CustomException.forbidden(
                    "접근이 제한된 IP 주소입니다. 시스템 관리자에게 접근 권한을 요청하세요.",
                    "IP_ACCESS_DENIED"
            );
        }

        String username = null;
        String passwordHash = null;
        boolean isSystemAdmin = false;
        Long resolvedUserId = null;
        try (Connection conn = dataSource.getConnection()) {
            if (hasEmployeeNumber) {
                String sql = "SELECT id, username, password_hash, is_system_admin " +
                        "FROM app_user WHERE deleted_at IS NULL AND employee_number IS NOT NULL " +
                        "AND TRIM(employee_number) = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, employeeNumber);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            log.warn("로그인 실패: 사용자 없음 (employeeNumber={})", employeeNumber);
                            throw CustomException.unauthorized(
                                    "사용자 ID와 비밀번호를 확인해주세요.",
                                    "INVALID_CREDENTIALS"
                            );
                        }
                        resolvedUserId = rs.getObject("id", Long.class);
                        username = rs.getString("username");
                        passwordHash = rs.getString("password_hash");
                        isSystemAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));

                        if (rs.next()) {
                            log.error("로그인 차단: 활성 사용자 간 employee_number 중복 감지 (employeeNumber={})", employeeNumber);
                            throw CustomException.conflict("이미 등록된 사번입니다.", "USER_EMPLOYEE_NUMBER_DUPLICATED");
                        }
                    }
                }
            } else {
                String sql = "SELECT username, password_hash, is_system_admin, deleted_at FROM app_user WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            log.warn("로그인 실패: 사용자 없음 (id={})", userId);
                            throw CustomException.unauthorized(
                                    "사용자 ID와 비밀번호를 확인해주세요.",
                                    "INVALID_CREDENTIALS"
                            );
                        }
                        if (rs.getTimestamp("deleted_at") != null) {
                            log.warn("로그인 실패: 비활성(소프트 삭제) 계정 (id={})", userId);
                            throw CustomException.unauthorized(
                                    "계정이 비활성화되어 로그인할 수 없습니다. 관리자에게 문의하세요.",
                                    "USER_ACCOUNT_DISABLED"
                            );
                        }
                        resolvedUserId = userId;
                        username = rs.getString("username");
                        passwordHash = rs.getString("password_hash");
                        isSystemAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
                    }
                }
            }
        } catch (CustomException e) {
            throw e;
        } catch (SQLException e) {
            log.error("로그인 조회 실패: userId={}, employeeNumber={}", userId, employeeNumber, e);
            throw CustomException.unauthorized(
                    "사용자 ID와 비밀번호를 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }
        if (passwordHash == null || username == null || resolvedUserId == null) {
            throw CustomException.unauthorized(
                    "사용자 ID와 비밀번호를 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }

        if (!password.equals(passwordHash)) {
            log.warn("로그인 실패: 비밀번호 불일치 (resolvedUserId={})", resolvedUserId);
            throw CustomException.unauthorized(
                    "사용자 ID와 비밀번호를 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }

        log.info("로그인 성공 (local): 사용자 ID: {} isSystemAdmin={} (IP: {})", resolvedUserId, isSystemAdmin, clientIP);
        return buildLoginResponse(username, resolvedUserId, isSystemAdmin, clientIP);
    }

    private LoginResponse loginAd(LoginRequest request, HttpServletRequest httpRequest) {
        if (request.getUserId() != null || StringUtils.hasText(request.getEmployeeNumber())) {
            throw CustomException.badRequest("로그인 요청 형식이 올바르지 않습니다.", "INVALID_INPUT");
        }
        if (!StringUtils.hasText(request.getPrincipal())) {
            throw CustomException.badRequest("로그인 요청 형식이 올바르지 않습니다.", "INVALID_INPUT");
        }
        if (ldapBindAuthenticator == null) {
            throw CustomException.serviceUnavailable("인증 설정이 올바르지 않습니다.", "AUTH_CONFIGURATION_ERROR");
        }

        String principal = request.getPrincipal().trim();
        String password = request.getPassword();
        String clientIP = ipUtil.getClientIP(httpRequest);
        log.info("로그인 시도 (ad) - IP: {}, principal present", clientIP);

        if (!ipUtil.isAuthorizedIP(clientIP, authorizedIPs)) {
            throw CustomException.forbidden(
                    "접근이 제한된 IP 주소입니다. 시스템 관리자에게 접근 권한을 요청하세요.",
                    "IP_ACCESS_DENIED"
            );
        }

        ldapBindAuthenticator.authenticate(principal, password);

        Long userId = externalIdentityService.findAppUserIdForDirectoryPrincipal(principal);
        if (userId == null) {
            throw CustomException.unauthorized(
                    "앱에 등록된 사용자가 아닙니다. 관리자에게 문의하세요.",
                    "APP_USER_NOT_PROVISIONED"
            );
        }

        String username;
        boolean isSystemAdmin;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT username, is_system_admin FROM app_user WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw CustomException.unauthorized(
                                "사용자 ID와 비밀번호를 확인해주세요.",
                                "INVALID_CREDENTIALS"
                        );
                    }
                    username = rs.getString("username");
                    isSystemAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
                }
            }
        } catch (SQLException e) {
            log.error("로그인 조회 실패 (ad): userId={}", userId, e);
            throw CustomException.unauthorized(
                    "사용자 ID와 비밀번호를 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }

        log.info("로그인 성공 (ad): 사용자 ID: {} isSystemAdmin={} (IP: {})", userId, isSystemAdmin, clientIP);
        return buildLoginResponse(username, userId, isSystemAdmin, clientIP);
    }

    private LoginResponse buildLoginResponse(String username, Long userId, boolean isSystemAdmin, String clientIP) {
        LoginResponse response = new LoginResponse();
        response.setUsername(username);
        response.setUserId(userId);
        response.setLoginTime(LocalDateTime.now());
        response.setClientIP(clientIP);
        response.setIsSystemAdmin(isSystemAdmin);
        response.setAllowedScreenIds(resolveAllowedScreenIds(username, isSystemAdmin));
        response.setScreenScopes(resolveScreenScopes(username, isSystemAdmin));
        response.setScreenFunctions(resolveScreenFunctions(username, isSystemAdmin));
        LoginResponse.SelfContext selfContext = resolveSelfContext(username);
        response.setSelfContext(selfContext);
        if (response.getUserId() == null && selfContext != null && selfContext.getUserId() != null) {
            response.setUserId(selfContext.getUserId());
        }
        return response;
    }
    
    /**
     * 로그아웃 처리
     * 
     * @return 성공 여부
     */
    public boolean logout() {
        log.info("✅ 로그아웃 요청");
        return true;
    }
    
    /**
     * Returns allowed screen IDs for the user. System admin gets all; others get union from permission groups.
     */
    protected List<String> resolveAllowedScreenIds(String username, boolean isSystemAdmin) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        if (isSystemAdmin) {
            return new ArrayList<>(ScreenConstants.getAllAllowedScreens());
        }
        return permissionGroupService.getAllowedScreenIdsForUser(username);
    }

    /**
     * Returns screenScopes for activity-log, statistics, search-history.
     * is_system_admin=true → all screens get 'all'. Otherwise from permission groups.
     */
    protected Map<String, String> resolveScreenScopes(String username, boolean isSystemAdmin) {
        if (username == null || username.isBlank()) {
            return new HashMap<>();
        }
        if (isSystemAdmin) {
            Map<String, String> all = new HashMap<>();
            all.put(ScreenConstants.ACTIVITY_LOG, "all");
            all.put(ScreenConstants.STATISTICS, "all");
            all.put(ScreenConstants.SEARCH_HISTORY, "all");
            all.put(ScreenConstants.USER_MANAGEMENT_V2, "all");
            return all;
        }
        return permissionGroupService.getScreenScopesForUser(username);
    }

    /**
     * Resolves the authoritative current-user self-context for self-scoped filter display.
     * Department is the display name from department.name when available, else department_code.
     * userId = numeric app_user.id (req 20260316-user-id-numeric-userid-naming).
     */
    protected LoginResponse.SelfContext resolveSelfContext(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        String normalizedUsername = username.trim();
        String department = null;
        String displayName = normalizedUsername;
        Long userId = null;
        String employeeNumber = null;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT u.id, u.department_code, d.name AS department_name, u.name AS user_name, u.employee_number " +
                    "FROM app_user u LEFT JOIN department d ON u.department_code = d.code " +
                    "WHERE u.username = ? AND u.deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, normalizedUsername);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getObject("id", Long.class);
                        String code = rs.getString("department_code");
                        String deptName = rs.getString("department_name");
                        department = (deptName != null && !deptName.isBlank()) ? deptName : (code != null ? code : "");
                        String appUserName = rs.getString("user_name");
                        displayName = (appUserName != null && !appUserName.isBlank()) ? appUserName : normalizedUsername;
                        String rawEmployeeNumber = rs.getString("employee_number");
                        employeeNumber = StringUtils.hasText(rawEmployeeNumber) ? rawEmployeeNumber.trim() : null;
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("selfContext 조회 실패: username={}", normalizedUsername, e);
        }

        return new LoginResponse.SelfContext(department != null ? department : "", displayName, userId, employeeNumber);
    }

    /**
     * Returns current user info from session. For GET /api/auth/me.
     * Session stores userId (Long); resolves to username via AppUserResolver for permission/selfContext.
     * Never throws: any exception is logged and null is returned to avoid 500 from interceptors/controllers.
     */
    public LoginResponse getCurrentUserInfo(HttpServletRequest request) {
        try {
            return getCurrentUserInfoInternal(request);
        } catch (Exception e) {
            log.warn("getCurrentUserInfo failed, returning null: {}", e.getMessage(), e);
            return null;
        }
    }

    private LoginResponse getCurrentUserInfoInternal(HttpServletRequest request) {
        if (!checkAuth(request)) {
            return null;
        }
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object sid = session.getAttribute("userId");
        Long sessionUserId = null;
        if (sid instanceof Long) {
            sessionUserId = (Long) sid;
        } else if (sid instanceof Number) {
            sessionUserId = ((Number) sid).longValue();
        } else if (sid != null && !sid.toString().trim().isEmpty()) {
            try {
                sessionUserId = Long.parseLong(sid.toString().trim());
            } catch (NumberFormatException e) {
                log.trace("Session userId not numeric: {}", sid);
            }
        }
        if (sessionUserId == null) {
            Object username = session.getAttribute("username");
            if (username != null && !username.toString().isBlank()) {
                String uname = username.toString();
                boolean sysAdmin = Boolean.TRUE.equals(session.getAttribute("isSystemAdmin"));
                LoginResponse resp = new LoginResponse();
                resp.setUsername(uname);
                resp.setIsSystemAdmin(sysAdmin);
                resp.setAllowedScreenIds(resolveAllowedScreenIds(uname, sysAdmin));
                resp.setScreenScopes(resolveScreenScopes(uname, sysAdmin));
                resp.setScreenFunctions(resolveScreenFunctions(uname, sysAdmin));
                LoginResponse.SelfContext selfContext = resolveSelfContext(uname);
                resp.setSelfContext(selfContext);
                Long uid = (selfContext != null && selfContext.getUserId() != null)
                        ? selfContext.getUserId()
                        : appUserResolver.getIdByUsername(uname);
                if (uid != null) {
                    resp.setUserId(uid);
                } else {
                    log.warn("getCurrentUserInfo: session has username but userId resolution returned null (username present); decrypt/ownership checks may require userId");
                }
                return resp;
            }
            return null;
        }
        String uname = appUserResolver.getUsernameById(sessionUserId);
        if (uname == null || uname.isBlank()) return null;
        boolean sysAdmin = Boolean.TRUE.equals(session.getAttribute("isSystemAdmin"));
        LoginResponse resp = new LoginResponse();
        resp.setUserId(sessionUserId);
        resp.setUsername(uname);
        resp.setIsSystemAdmin(sysAdmin);
        resp.setAllowedScreenIds(resolveAllowedScreenIds(uname, sysAdmin));
        resp.setScreenScopes(resolveScreenScopes(uname, sysAdmin));
        resp.setScreenFunctions(resolveScreenFunctions(uname, sysAdmin));
        resp.setSelfContext(resolveSelfContext(uname));
        return resp;
    }

    /**
     * Computes screenFunctions from allowedScreenIds and permission_group_screen.
     * Per spec §4.4 / contract 「복호화 승인 자격」: decrypt screens — explicit {@code approve=true} or derived false when null;
     * then {@code is_system_admin} forces effective approve false. {@code decrypt_approver} is not read.
     */
    protected Map<String, ScreenFunctionCapability> resolveScreenFunctions(String username, boolean isSystemAdmin) {
        Map<String, ScreenFunctionCapability> result = new LinkedHashMap<>();
        if (username == null || username.isBlank()) {
            return result;
        }
        List<String> allowed = resolveAllowedScreenIds(username, isSystemAdmin);
        if (allowed == null || allowed.isEmpty()) {
            return result;
        }
        Map<String, PermissionGroupService.ScreenFunctionFromDb> pgsMap = permissionGroupService.getScreenFunctionsForUser(username);
        for (String screenId : allowed) {
            if (screenId == null || screenId.isBlank()) continue;
            boolean read = true; // user has this screen (allowedScreenIds already filtered read=false)
            Boolean write = null;
            Boolean approve = null;
            Boolean decrypt = null;
            PermissionGroupService.ScreenFunctionFromDb pgs = pgsMap.get(screenId);
            if (ScreenConstants.supportsWrite(screenId)) {
                if (pgs != null && pgs.write != null) {
                    write = pgs.write;
                } else {
                    write = true; // derivation: read implies write for management screens
                }
            }
            if (ScreenConstants.supportsApprove(screenId)) {
                boolean groupApprove = pgs != null && Boolean.TRUE.equals(pgs.approve);
                if (pgs != null && Boolean.FALSE.equals(pgs.approve)) {
                    approve = false;
                } else if (groupApprove) {
                    approve = !isSystemAdmin;
                } else {
                    approve = false; // null or missing → §1.1.1 default false
                }
            }
            if (ScreenConstants.supportsDecrypt(screenId)) {
                // main only: decrypt from pgs; default false when null (req 20260306)
                decrypt = (pgs != null && pgs.decrypt != null) ? pgs.decrypt : false;
            }
            result.put(screenId, new ScreenFunctionCapability(read, write, approve, decrypt));
        }
        return result;
    }

    /**
     * Returns true if the current user can access the department/approvers view.
     * Per spec §4.3: is_system_admin OR department-approvers OR user-permission-hierarchy.
     */
    public boolean canAccessDepartmentView(HttpServletRequest request) {
        LoginResponse user = getCurrentUserInfo(request);
        if (user == null) return false;
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) return true;
        List<String> allowed = user.getAllowedScreenIds();
        return allowed != null && (allowed.contains(ScreenConstants.DEPARTMENT_APPROVERS)
                || allowed.contains(ScreenConstants.USER_PERMISSION_HIERARCHY));
    }

    /**
     * Ensures the current user can access the requested screen.
     * Shared filter-option APIs use query parameters, so they must validate screen access explicitly.
     */
    public LoginResponse requireScreenAccess(HttpServletRequest request, String screenId) {
        LoginResponse user = getCurrentUserInfo(request);
        if (user == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) {
            return user;
        }
        List<String> allowed = user.getAllowedScreenIds();
        if (allowed != null && allowed.contains(screenId)) {
            return user;
        }
        log.info("화면 접근 거부: screenId={} allowedScreenCount={}", screenId,
                allowed != null ? allowed.size() : 0);
        throw CustomException.forbidden("해당 화면에 대한 접근 권한이 없습니다.", "FORBIDDEN");
    }

    /**
     * Returns true if the current user can access the user-management <em>family</em> views and
     * the same controller gate used by {@link com.logmng.controller.PermissionGroupController}
     * (aligned with {@code ScreenAccessInterceptor} for {@code /api/permission-groups.*}).
     * <p>
     * Enumerated {@code allowedScreenIds} (any one is sufficient; system admin bypasses):
     * {@code user-management}, {@code user-permission-hierarchy}, {@code user-management-v2},
     * {@code permission-group-management}, {@code permission-group-screen-matrix}.
     * See {@code docs/requirements/20260410-screen-access-menu-api-consistency.md} and
     * {@code specs/permission-group-hierarchy.spec.yaml} §4.3.
     */
    public boolean canAccessUserManagementView(HttpServletRequest request) {
        LoginResponse user = getCurrentUserInfo(request);
        if (user == null) return false;
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) return true;
        List<String> allowed = user.getAllowedScreenIds();
        return allowed != null && (allowed.contains(ScreenConstants.USER_MANAGEMENT)
                || allowed.contains(ScreenConstants.USER_PERMISSION_HIERARCHY)
                || allowed.contains(ScreenConstants.USER_MANAGEMENT_V2)
                || allowed.contains(ScreenConstants.PERMISSION_GROUP_MANAGEMENT)
                || allowed.contains(ScreenConstants.PERMISSION_GROUP_SCREEN_MATRIX));
    }

    /**
     * Returns true if the current user has write on any management screen that may mutate permission-group
     * admin APIs ({@link com.logmng.controller.PermissionGroupController}), same family as
     * {@link #canAccessUserManagementView(HttpServletRequest)} for {@code permission-group-management} /
     * {@code permission-group-screen-matrix}. Per spec §4.4: write from group or derived.
     * Enumerated keys: {@code user-management}, {@code user-management-v2}, {@code user-permission-hierarchy},
     * {@code permission-group-management}, {@code permission-group-screen-matrix}.
     * Ref: {@code docs/requirements/20260410-screen-access-menu-api-consistency.md}.
     */
    public boolean hasWriteForManagementScreens(HttpServletRequest request) {
        LoginResponse user = getCurrentUserInfo(request);
        if (user == null) return false;
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) return true;
        Map<String, ScreenFunctionCapability> sf = user.getScreenFunctions();
        if (sf == null) return false;
        ScreenFunctionCapability um = sf.get(ScreenConstants.USER_MANAGEMENT);
        ScreenFunctionCapability umv2 = sf.get(ScreenConstants.USER_MANAGEMENT_V2);
        ScreenFunctionCapability uph = sf.get(ScreenConstants.USER_PERMISSION_HIERARCHY);
        ScreenFunctionCapability pgm = sf.get(ScreenConstants.PERMISSION_GROUP_MANAGEMENT);
        ScreenFunctionCapability pgsm = sf.get(ScreenConstants.PERMISSION_GROUP_SCREEN_MATRIX);
        return (um != null && Boolean.TRUE.equals(um.getWrite()))
                || (umv2 != null && Boolean.TRUE.equals(umv2.getWrite()))
                || (uph != null && Boolean.TRUE.equals(uph.getWrite()))
                || (pgm != null && Boolean.TRUE.equals(pgm.getWrite()))
                || (pgsm != null && Boolean.TRUE.equals(pgsm.getWrite()));
    }

    /**
     * Returns true if the current user has effective approve on search-history or pending-approvals
     * (session {@code screenFunctions}; no {@code decrypt_approver}; system admin never true per contract).
     */
    public boolean hasApproveForSearchHistory(HttpServletRequest request) {
        LoginResponse user = getCurrentUserInfo(request);
        if (user == null) return false;
        Map<String, ScreenFunctionCapability> sf = user.getScreenFunctions();
        if (sf == null) return false;
        ScreenFunctionCapability sh = sf.get(ScreenConstants.SEARCH_HISTORY);
        ScreenFunctionCapability pa = sf.get(ScreenConstants.PENDING_APPROVALS);
        return (sh != null && Boolean.TRUE.equals(sh.getApprove()))
                || (pa != null && Boolean.TRUE.equals(pa.getApprove()));
    }

    /**
     * Effective read on 복호화 승인 관리 for GET /api/search-history list/detail (interceptor).
     * Per docs/api-definition.md §6.1.2: screen in allowedScreenIds with read true (explicit false denies).
     */
    public boolean hasEffectiveReadForPendingApprovals(LoginResponse user) {
        if (user == null) {
            return false;
        }
        Map<String, ScreenFunctionCapability> sf = user.getScreenFunctions();
        if (sf == null) {
            return true;
        }
        ScreenFunctionCapability pa = sf.get(ScreenConstants.PENDING_APPROVALS);
        if (pa == null) {
            return true;
        }
        return pa.isRead();
    }

    /**
     * Returns true if the current user may request decryption for the given screen.
     * Per spec §4.4, req 20260318: is_system_admin OR (screenId in allowedScreenIds AND screenFunctions[screenId].decrypt === true).
     * Use for pb-feplog, java-fw-imagelog (and main for backward compat).
     */
    public boolean hasDecryptForScreen(HttpServletRequest request, String screenId) {
        if (screenId == null || screenId.isBlank()) return false;
        String sid = screenId.trim();
        LoginResponse user = getCurrentUserInfo(request);
        if (user == null) return false;
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) return true;
        Map<String, ScreenFunctionCapability> sf = user.getScreenFunctions();
        if (sf == null) return false;
        if (isPbFeplogFamilyScreen(sid)) {
            return hasDecryptOnAnyPbFeplogScreen(user, sf);
        }
        List<String> allowed = user.getAllowedScreenIds();
        if (allowed == null || !allowed.contains(sid)) return false;
        ScreenFunctionCapability cap = sf.get(sid);
        return cap != null && Boolean.TRUE.equals(cap.getDecrypt());
    }

    private static boolean isPbFeplogFamilyScreen(String screenId) {
        return ScreenConstants.PB_FEPLOG.equals(screenId)
                || ScreenConstants.PB_FEP_LOG_SEARCH.equals(screenId)
                || ScreenConstants.MAIN.equals(screenId);
    }

    /** Decrypt for pb_feplog APIs if user has decrypt on pb-feplog, pb-fep-log-search, or legacy main. */
    private static boolean hasDecryptOnAnyPbFeplogScreen(LoginResponse user, Map<String, ScreenFunctionCapability> sf) {
        List<String> allowed = user.getAllowedScreenIds();
        if (allowed == null) return false;
        for (String key : List.of(ScreenConstants.PB_FEPLOG, ScreenConstants.PB_FEP_LOG_SEARCH, ScreenConstants.MAIN)) {
            if (allowed.contains(key)) {
                ScreenFunctionCapability cap = sf.get(key);
                if (cap != null && Boolean.TRUE.equals(cap.getDecrypt())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the current user may request decryption (decrypt API) for the main screen.
     * @deprecated Prefer hasDecryptForScreen(request, screenId) with pb-feplog/java-fw-imagelog. Kept for backward compat.
     */
    public boolean hasDecryptForMain(HttpServletRequest request) {
        return hasDecryptForScreen(request, ScreenConstants.MAIN);
    }

    /**
     * 인증 상태 확인 (세션 기반)
     *
     * @param request HTTP 요청 (세션 확인용, null이면 false)
     * @return 인증 여부 (세션에 userId(Long) 또는 username이 있으면 true)
     */
    public boolean checkAuth(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        try {
            jakarta.servlet.http.HttpSession session = request.getSession(false);
            if (session == null) {
                return false;
            }
            Object userId = session.getAttribute("userId");
            Object username = session.getAttribute("username");
            return (userId != null && !userId.toString().isEmpty())
                    || (username != null && !username.toString().isEmpty());
        } catch (Exception e) {
            log.debug("인증 확인 중 오류: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Authenticated user changes own password (local {@code password_hash} only).
     * AD login mode or directory-linked users cannot use this API (403).
     */
    public void changeOwnPassword(HttpServletRequest request, ChangeMyPasswordRequest body) {
        if (!checkAuth(request)) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        String mode = authProperties.getLogin().getMode() != null
                ? authProperties.getLogin().getMode().trim().toLowerCase(Locale.ROOT) : "local";
        if (!"local".equals(mode)) {
            throw CustomException.forbidden(
                    "디렉터리 로그인 환경에서는 이 기능을 사용할 수 없습니다.",
                    "PASSWORD_CHANGE_NOT_ALLOWED");
        }
        if (body == null) {
            throw CustomException.badRequest("요청 본문이 필요합니다.", "INVALID_INPUT");
        }
        String current = body.getCurrentPassword() != null ? body.getCurrentPassword() : "";
        String next = body.getNewPassword() != null ? body.getNewPassword() : "";
        String confirm = body.getConfirmNewPassword() != null ? body.getConfirmNewPassword() : "";
        if (!next.equals(confirm)) {
            throw CustomException.badRequest("새 비밀번호와 확인 값이 일치하지 않습니다.", "INVALID_INPUT");
        }
        if (next.equals(current)) {
            throw CustomException.badRequest("새 비밀번호는 현재 비밀번호와 달라야 합니다.", "INVALID_INPUT");
        }

        Long userId = resolveSessionNumericUserId(request);
        if (userId == null) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        if (userHasExternalIdentityLink(userId)) {
            throw CustomException.forbidden(
                    "외부 계정과 연결된 사용자는 이 API로 비밀번호를 변경할 수 없습니다.",
                    "PASSWORD_CHANGE_NOT_ALLOWED");
        }

        String storedHash;
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT password_hash FROM app_user WHERE id = ? AND deleted_at IS NULL")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
                    }
                    storedHash = rs.getString("password_hash");
                }
            }
        } catch (SQLException e) {
            log.error("비밀번호 변경 조회 실패: userId={}", userId, e);
            throw CustomException.internalError("비밀번호 변경 처리 중 오류가 발생했습니다.", "INTERNAL_ERROR");
        }
        if (storedHash == null) {
            throw CustomException.unauthorized("사용자 ID와 비밀번호를 확인해주세요.", "INVALID_CREDENTIALS");
        }
        if (!current.equals(storedHash)) {
            log.info("비밀번호 변경 거부: 현재 비밀번호 불일치 (userId={})", userId);
            throw CustomException.badRequest("현재 비밀번호가 올바르지 않습니다.", "WRONG_PASSWORD");
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE app_user SET password_hash = ? WHERE id = ? AND deleted_at IS NULL")) {
            ps.setString(1, next);
            ps.setLong(2, userId);
            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw CustomException.internalError("비밀번호 변경 처리 중 오류가 발생했습니다.", "INTERNAL_ERROR");
            }
        } catch (SQLException e) {
            log.error("비밀번호 변경 저장 실패: userId={}", userId, e);
            throw CustomException.internalError("비밀번호 변경 처리 중 오류가 발생했습니다.", "INTERNAL_ERROR");
        }
        log.info("비밀번호 변경 완료 (local, self-service): userId={}", userId);
    }

    private Long resolveSessionNumericUserId(HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object sid = session.getAttribute("userId");
        if (sid instanceof Long) {
            return (Long) sid;
        }
        if (sid instanceof Number) {
            return ((Number) sid).longValue();
        }
        if (sid != null && !sid.toString().trim().isEmpty()) {
            try {
                return Long.parseLong(sid.toString().trim());
            } catch (NumberFormatException e) {
                log.trace("Session userId not numeric: {}", sid);
            }
        }
        Object username = session.getAttribute("username");
        if (username != null && !username.toString().isBlank()) {
            LoginResponse.SelfContext ctx = resolveSelfContext(username.toString().trim());
            if (ctx != null && ctx.getUserId() != null) {
                return ctx.getUserId();
            }
            return appUserResolver.getIdByUsername(username.toString().trim());
        }
        return null;
    }

    private boolean userHasExternalIdentityLink(long userId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM app_user_external_identity WHERE app_user_id = ? LIMIT 1")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.warn("외부 계정 연결 여부 확인 실패: userId={}", userId, e);
            return true;
        }
    }
}





