package com.logmng.service;

import com.logmng.dto.request.LoginRequest;
import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.response.ScreenFunctionCapability;
import com.logmng.exception.CustomException;
import com.logmng.util.IpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import jakarta.servlet.http.HttpServletRequest;

import com.logmng.constants.ScreenConstants;
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
    private final DecryptApproverService decryptApproverService;

    @Value("${app.security.authorized-ips:127.0.0.1,localhost,0:0:0:0:0:0:0:1}")
    private String authorizedIPs;

    public AuthService(IpUtil ipUtil, DataSource dataSource, PermissionGroupService permissionGroupService,
                      DecryptApproverService decryptApproverService) {
        this.ipUtil = ipUtil;
        this.dataSource = dataSource;
        this.permissionGroupService = permissionGroupService;
        this.decryptApproverService = decryptApproverService;
    }

    /**
     * 로그인 처리. app_user에서 username/password_hash/role 조회 후 비밀번호 검증.
     * (개발 환경: password_hash에 평문 저장 시 그대로 비교)
     */
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername();
        String password = request.getPassword();

        String clientIP = ipUtil.getClientIP(httpRequest);
        log.info("로그인 시도 - IP: {}, 사용자명: {}", clientIP, username);

        if (!ipUtil.isAuthorizedIP(clientIP, authorizedIPs)) {
            log.warn("인가되지 않은 IP에서 로그인 시도: {}", clientIP);
            throw CustomException.forbidden(
                    "접근이 제한된 IP 주소입니다. 시스템 관리자에게 접근 권한을 요청하세요.",
                    "IP_ACCESS_DENIED"
            );
        }

        String passwordHash = null;
        boolean isSystemAdmin = false;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT username, password_hash, is_system_admin FROM app_user WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        log.warn("로그인 실패: 사용자 없음 ({})", username);
                        throw CustomException.unauthorized(
                                "인증 정보가 올바르지 않습니다. 사용자명과 비밀번호를 다시 확인해주세요.",
                                "INVALID_CREDENTIALS"
                        );
                    }
                    passwordHash = rs.getString("password_hash");
                    isSystemAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
                }
            }
        } catch (SQLException e) {
            log.error("로그인 조회 실패: username={}", username, e);
            throw CustomException.unauthorized(
                    "인증 정보가 올바르지 않습니다. 사용자명과 비밀번호를 다시 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }
        if (passwordHash == null) {
            throw CustomException.unauthorized(
                    "인증 정보가 올바르지 않습니다. 사용자명과 비밀번호를 다시 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }

        // 개발: password_hash에 평문 저장 시 비교. 추후 BCrypt 등으로 교체 가능.
        if (!password.equals(passwordHash)) {
            log.warn("로그인 실패: 비밀번호 불일치 ({})", username);
            throw CustomException.unauthorized(
                    "인증 정보가 올바르지 않습니다. 사용자명과 비밀번호를 다시 확인해주세요.",
                    "INVALID_CREDENTIALS"
            );
        }

        log.info("로그인 성공: {} isSystemAdmin={} (IP: {})", username, isSystemAdmin, clientIP);

        LoginResponse response = new LoginResponse();
        response.setUsername(username);
        response.setLoginTime(LocalDateTime.now());
        response.setClientIP(clientIP);
        response.setIsSystemAdmin(isSystemAdmin);
        response.setAllowedScreenIds(resolveAllowedScreenIds(username, isSystemAdmin));
        response.setScreenScopes(resolveScreenScopes(username, isSystemAdmin));
        response.setScreenFunctions(resolveScreenFunctions(username, isSystemAdmin));
        response.setSelfContext(resolveSelfContext(username));
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
            return all;
        }
        return permissionGroupService.getScreenScopesForUser(username);
    }

    /**
     * Resolves the authoritative current-user self-context for self-scoped filter display.
     * `userId` remains the canonical `app_user.username`, and `username` display reuses the
     * authenticated username until a separate profile/display-name field exists.
     */
    protected LoginResponse.SelfContext resolveSelfContext(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        String normalizedUsername = username.trim();
        String department = null;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT department_code FROM app_user WHERE username = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, normalizedUsername);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        department = rs.getString("department_code");
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("selfContext 조회 실패: username={}", normalizedUsername, e);
        }

        return new LoginResponse.SelfContext(department, normalizedUsername, normalizedUsername);
    }

    /**
     * Returns current user info (username, isSystemAdmin, allowedScreenIds) from session. For GET /api/auth/me.
     */
    public LoginResponse getCurrentUserInfo(HttpServletRequest request) {
        if (!checkAuth(request)) {
            return null;
        }
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object username = session.getAttribute("username");
        Object isSystemAdmin = session.getAttribute("isSystemAdmin");
        if (username == null || username.toString().isBlank()) return null;
        String uname = username.toString();
        boolean sysAdmin = Boolean.TRUE.equals(isSystemAdmin);
        LoginResponse resp = new LoginResponse();
        resp.setUsername(uname);
        resp.setIsSystemAdmin(sysAdmin);
        resp.setAllowedScreenIds(resolveAllowedScreenIds(uname, sysAdmin));
        resp.setScreenScopes(resolveScreenScopes(uname, sysAdmin));
        resp.setScreenFunctions(resolveScreenFunctions(uname, sysAdmin));
        resp.setSelfContext(resolveSelfContext(uname));
        return resp;
    }

    /**
     * Computes screenFunctions from allowedScreenIds, permission_group_screen, decrypt_approver.
     * Per spec §4.4: when pgs.read/write/approve non-null, use them; else use derivation.
     * approve = (pgs.approve OR null) AND (decrypt_approver canApproveForRequester OR is_system_admin).
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
        boolean isApprover = decryptApproverService.isApprover(username);
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
                boolean approverOrAdmin = isSystemAdmin || isApprover;
                if (pgs != null && Boolean.FALSE.equals(pgs.approve)) {
                    approve = false; // explicit deny
                } else {
                    approve = approverOrAdmin; // pgs.approve true or null -> gate by decrypt_approver
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
     * Returns true if the current user can access the user-management view.
     * Per specs/permission-group-hierarchy.spec.yaml §4.3: is_system_admin OR
     * allowedScreenIds contains user-management OR user-permission-hierarchy.
     */
    public boolean canAccessUserManagementView(HttpServletRequest request) {
        LoginResponse user = getCurrentUserInfo(request);
        if (user == null) return false;
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) return true;
        List<String> allowed = user.getAllowedScreenIds();
        return allowed != null && (allowed.contains(ScreenConstants.USER_MANAGEMENT)
                || allowed.contains(ScreenConstants.USER_PERMISSION_HIERARCHY));
    }

    /**
     * Returns true if the current user has write permission for user-management or user-permission-hierarchy.
     * Per spec §4.4: write from group or derived. Return 403 FUNCTION_NOT_ALLOWED when write=false.
     */
    public boolean hasWriteForManagementScreens(HttpServletRequest request) {
        LoginResponse user = getCurrentUserInfo(request);
        if (user == null) return false;
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) return true;
        Map<String, ScreenFunctionCapability> sf = user.getScreenFunctions();
        if (sf == null) return false;
        ScreenFunctionCapability um = sf.get(ScreenConstants.USER_MANAGEMENT);
        ScreenFunctionCapability uph = sf.get(ScreenConstants.USER_PERMISSION_HIERARCHY);
        return (um != null && Boolean.TRUE.equals(um.getWrite()))
                || (uph != null && Boolean.TRUE.equals(uph.getWrite()));
    }

    /**
     * Returns true if the current user has approve for search-history or pending-approvals.
     * Per spec §4.4: approve = (pgs.approve) AND (decrypt_approver or is_system_admin).
     */
    public boolean hasApproveForSearchHistory(HttpServletRequest request) {
        LoginResponse user = getCurrentUserInfo(request);
        if (user == null) return false;
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) return true;
        Map<String, ScreenFunctionCapability> sf = user.getScreenFunctions();
        if (sf == null) return false;
        ScreenFunctionCapability sh = sf.get(ScreenConstants.SEARCH_HISTORY);
        ScreenFunctionCapability pa = sf.get(ScreenConstants.PENDING_APPROVALS);
        return (sh != null && Boolean.TRUE.equals(sh.getApprove()))
                || (pa != null && Boolean.TRUE.equals(pa.getApprove()));
    }

    /**
     * Returns true if the current user may request decryption (decrypt API).
     * Per spec §4.4, req 20260306: is_system_admin OR (main in allowedScreenIds AND screenFunctions.main.decrypt === true).
     */
    public boolean hasDecryptForMain(HttpServletRequest request) {
        LoginResponse user = getCurrentUserInfo(request);
        if (user == null) return false;
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) return true;
        List<String> allowed = user.getAllowedScreenIds();
        if (allowed == null || !allowed.contains(ScreenConstants.MAIN)) return false;
        Map<String, ScreenFunctionCapability> sf = user.getScreenFunctions();
        if (sf == null) return false;
        ScreenFunctionCapability mainCap = sf.get(ScreenConstants.MAIN);
        return mainCap != null && Boolean.TRUE.equals(mainCap.getDecrypt());
    }

    /**
     * 인증 상태 확인 (세션 기반)
     *
     * @param request HTTP 요청 (세션 확인용, null이면 false)
     * @return 인증 여부 (세션에 userId 또는 username이 있으면 true)
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
}





