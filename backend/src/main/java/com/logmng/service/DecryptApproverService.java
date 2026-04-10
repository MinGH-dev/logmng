package com.logmng.service;

import com.logmng.constants.ActivityActionType;
import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.UserListItemResponse;
import com.logmng.exception.CustomException;
import com.logmng.util.ChangeReasonValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 복호화 승인 자격: 권한 그룹 화면의 명시 {@code approve=true} + {@code is_system_admin} 상쇄 + 동일 부서(P2-2).
 * 레거시 {@code decrypt_approver} 는 승인 자격 판단에 사용하지 않음 (req 20260323, docs/contract.md).
 * {@link #isAdmin(boolean)} 는 시스템 관리자 UI 게이트용. {@link DepartmentService} 는 빈 호환용으로 유지.
 */
@Service
public class DecryptApproverService {

    private static final Logger log = LoggerFactory.getLogger(DecryptApproverService.class);

    private final DataSource dataSource;
    @SuppressWarnings("unused")
    private final DepartmentService departmentService;
    private final UserActivityLogService userActivityLogService;
    private final PermissionGroupService permissionGroupService;
    private final AppUserResolver appUserResolver;

    public DecryptApproverService(DataSource dataSource, DepartmentService departmentService,
                                  @Autowired(required = false) UserActivityLogService userActivityLogService,
                                  PermissionGroupService permissionGroupService,
                                  AppUserResolver appUserResolver) {
        this.dataSource = dataSource;
        this.departmentService = departmentService;
        this.userActivityLogService = userActivityLogService;
        this.permissionGroupService = permissionGroupService;
        this.appUserResolver = appUserResolver;
    }

    /** @deprecated Use {@link #isAdmin(boolean)} with isSystemAdmin from session. */
    @Deprecated
    public boolean isAdmin(String role) {
        return role != null && "ADMIN".equals(role);
    }

    /** Admin check by is_system_admin (req 20250303). */
    public boolean isAdmin(boolean isSystemAdmin) {
        return isSystemAdmin;
    }

    /**
     * Effective decrypt-approver eligibility (numeric app_user.id): group {@code approve=true} on
     * {@code search-history} or {@code pending-approvals}, then {@code is_system_admin} overrides to false.
     */
    public boolean effectiveDecryptApprove(Long appUserId) {
        if (appUserId == null || permissionGroupService == null || appUserResolver == null) {
            return false;
        }
        try {
            Boolean sys = loadIsSystemAdminById(appUserId);
            if (sys == null || Boolean.TRUE.equals(sys)) {
                return false;
            }
        } catch (SQLException e) {
            log.error("effectiveDecryptApprove: is_system_admin 조회 실패 appUserId={}", appUserId, e);
            return false;
        }
        String username = appUserResolver.getUsernameById(appUserId);
        if (username == null || username.isBlank()) {
            return false;
        }
        return rawGroupDecryptApproveOnEitherScreen(username);
    }

    /**
     * Legacy name: decrypt 승인 <em>자격</em> (권한 그룹 + 시스템관리자 상쇄). {@code decrypt_approver} 미사용.
     */
    public boolean isApprover(Long appUserId) {
        return effectiveDecryptApprove(appUserId);
    }

    /**
     * approverUserId가 requesterUserId 요청에 대해 승인/반려 가능한지: effective decrypt approve + 동일 {@code department_code} (P2-2).
     * Non-SQL throws are caught and result in false so approval path never surfaces 500.
     */
    public boolean canApproveForRequester(Long approverUserId, Long requesterUserId) {
        if (approverUserId == null || requesterUserId == null) {
            return false;
        }
        if (!effectiveDecryptApprove(approverUserId)) {
            return false;
        }
        try {
            String approverDept = loadDepartmentCodeByUserId(approverUserId);
            String requesterDept = loadDepartmentCodeByUserId(requesterUserId);
            if (approverDept == null || approverDept.isBlank() || requesterDept == null || requesterDept.isBlank()) {
                return false;
            }
            return approverDept.equals(requesterDept);
        } catch (SQLException e) {
            log.error("승인 권한 판단 실패: approver={}, requester={}", approverUserId, requesterUserId, e);
            return false;
        } catch (Throwable t) {
            log.warn("승인 권한 판단 중 예외(비-SQL): approver={}, requester={}, type={}", approverUserId, requesterUserId, t.getClass().getName(), t);
            return false;
        }
    }

    private Boolean loadIsSystemAdminById(Long appUserId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT is_system_admin FROM app_user WHERE id = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, appUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return rs.getObject("is_system_admin", Boolean.class);
                }
            }
        }
    }

    private String loadDepartmentCodeByUserId(Long appUserId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT department_code FROM app_user WHERE id = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, appUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString("department_code") : null;
                }
            }
        }
    }

    private boolean rawGroupDecryptApproveOnEitherScreen(String username) {
        Map<String, PermissionGroupService.ScreenFunctionFromDb> pgs = permissionGroupService.getScreenFunctionsForUser(username);
        return screenHasExplicitApprove(pgs, ScreenConstants.SEARCH_HISTORY)
                || screenHasExplicitApprove(pgs, ScreenConstants.PENDING_APPROVALS);
    }

    /** Explicit {@code approve=true} from merged permission_group_screen; null/false → false (§1.1.1 default). */
    private static boolean screenHasExplicitApprove(Map<String, PermissionGroupService.ScreenFunctionFromDb> pgs, String screenId) {
        if (pgs == null) {
            return false;
        }
        PermissionGroupService.ScreenFunctionFromDb row = pgs.get(screenId);
        return row != null && Boolean.TRUE.equals(row.approve);
    }

    /**
     * app_user 목록 + position, rank, isSystemAdmin. §7.1. userId = numeric app_user.id (req 20260316). Approver capability not listed (req 20260323).
     */
    public List<UserListItemResponse> listUsers() {
        return listUsers(null);
    }

    /**
     * Same as {@link #listUsers()} with optional id allowlist for User Management v2 read scope (req 20260409).
     * {@code allowedNumericUserIds} null = no filter; empty = no rows.
     */
    public List<UserListItemResponse> listUsers(java.util.List<Long> allowedNumericUserIds) {
        List<UserListItemResponse> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            StringBuilder sql = new StringBuilder(
                    "SELECT id, username, role, department_code, position, rank, is_system_admin, employee_number "
                            + "FROM app_user WHERE deleted_at IS NULL ");
            if (allowedNumericUserIds != null) {
                if (allowedNumericUserIds.isEmpty()) {
                    sql.append("AND 1=0 ");
                } else {
                    sql.append("AND id IN (");
                    for (int i = 0; i < allowedNumericUserIds.size(); i++) {
                        if (i > 0) sql.append(',');
                        sql.append('?');
                    }
                    sql.append(") ");
                }
            }
            sql.append("ORDER BY username");
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                if (allowedNumericUserIds != null && !allowedNumericUserIds.isEmpty()) {
                    int idx = 1;
                    for (Long id : allowedNumericUserIds) {
                        ps.setLong(idx++, id);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Long id = rs.getObject("id", Long.class);
                        String username = rs.getString("username");
                        String role = rs.getString("role");
                        String departmentCode = rs.getString("department_code");
                        String position = rs.getString("position");
                        String rank = rs.getString("rank");
                        boolean isSystemAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
                        String employeeNumber = rs.getString("employee_number");
                        list.add(new UserListItemResponse(id, username, role, departmentCode, position, rank, isSystemAdmin, employeeNumber));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("사용자 목록 조회 실패", e);
            throw new RuntimeException("사용자 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Soft-delete {@code app_user} (sets {@code deleted_at}), removes external-identity row so HR key can be re-provisioned.
     * Emits {@link ActivityActionType#USER_DELETE} on success. Req 20260407-user-management-consistency-delete-reason-activity-audit.
     *
     * @param actorUsername session username for activity log {@code user_id}; never null for normal calls
     */
    public void softDeleteUserById(long targetUserId, String changeReason, String actorUsername, String clientIp) {
        String reason = ChangeReasonValidator.requireValidChangeReason(changeReason);
        if (actorUsername == null || actorUsername.isBlank()) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Long id;
                String username;
                boolean targetSysAdmin;
                String employeeNumber;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, username, is_system_admin, employee_number FROM app_user "
                                + "WHERE id = ? AND deleted_at IS NULL LIMIT 1")) {
                    ps.setLong(1, targetUserId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            throw CustomException.notFound("해당 사용자를 찾을 수 없습니다.", "USER_NOT_FOUND");
                        }
                        id = rs.getLong("id");
                        username = rs.getString("username");
                        targetSysAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
                        employeeNumber = rs.getString("employee_number");
                    }
                }
                if (targetSysAdmin) {
                    int sysAdminCount = countActiveSystemAdmins(conn);
                    if (sysAdminCount <= 1) {
                        conn.rollback();
                        throw CustomException.badRequest(
                                "마지막 시스템 관리자는 삭제할 수 없습니다.",
                                "LAST_SYSTEM_ADMIN_BLOCKED");
                    }
                    conn.rollback();
                    throw CustomException.badRequest("시스템 관리자는 삭제할 수 없습니다.", "SYSTEM_ADMIN_IMMUTABLE");
                }
                try (PreparedStatement delMap = conn.prepareStatement(
                        "DELETE FROM app_user_external_identity WHERE app_user_id = ?")) {
                    delMap.setLong(1, id);
                    delMap.executeUpdate();
                }
                int updated;
                try (PreparedStatement up = conn.prepareStatement(
                        "UPDATE app_user SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL")) {
                    up.setLong(1, id);
                    updated = up.executeUpdate();
                }
                if (updated == 0) {
                    conn.rollback();
                    throw CustomException.notFound("해당 사용자를 찾을 수 없습니다.", "USER_NOT_FOUND");
                }
                conn.commit();
                log.info("사용자 소프트 삭제: targetUserId={}, actor={}", id, actorUsername);
                emitUserDeleteIfConfigured(actorUsername, reason, id, employeeNumber, username, clientIp);
            } catch (CustomException e) {
                conn.rollback();
                throw e;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (CustomException e) {
            throw e;
        } catch (SQLException e) {
            log.error("사용자 삭제 실패: targetUserId={}", targetUserId, e);
            throw new RuntimeException("사용자 삭제 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private void emitUserDeleteIfConfigured(String actorUsername, String changeReason,
                                            long targetUserId, String employeeNumber, String username, String clientIp) {
        if (userActivityLogService == null) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("changeReason", changeReason);
        detail.put("targetUserId", targetUserId);
        if (employeeNumber != null && !employeeNumber.isBlank()) {
            detail.put("employeeNumber", employeeNumber.trim());
        }
        if (username != null && !username.isBlank()) {
            detail.put("username", username.trim());
        }
        userActivityLogService.saveActivityLog(
                actorUsername,
                actorUsername,
                ActivityActionType.USER_DELETE.getCode(),
                detail,
                clientIp,
                null,
                "DELETE",
                "/api/users/" + targetUserId,
                null,
                200,
                null,
                true,
                null);
    }

    private static int countActiveSystemAdmins(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM app_user WHERE is_system_admin = TRUE AND deleted_at IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * 사용자 역할 변경. §7.4. 관리자 전용.
     * callerUserId: 호출자(세션), targetUserId: 대상 사용자, role: ADMIN | USER
     * - 자기 자신: targetUserId == callerUserId → 400 SELF_DEMOTION_BLOCKED
     * - 시스템 관리자: target has is_system_admin=true → 400 SYSTEM_ADMIN_IMMUTABLE
     * - 마지막 시스템 관리자: demotion would leave zero system admins → 400 LAST_SYSTEM_ADMIN_BLOCKED
     * - 마지막 관리자: ADMIN→USER로 변경 시 count(ADMIN)==1이면 → 400 LAST_ADMIN_BLOCKED
     */
    public UserListItemResponse updateUserRole(String callerUserId, String targetUserId, String role) {
        if (role == null || role.isBlank()) {
            throw CustomException.badRequest("role은 필수이며 ADMIN 또는 USER여야 합니다.", "INVALID_INPUT");
        }
        String roleUpper = role.trim().toUpperCase();
        if (!"ADMIN".equals(roleUpper) && !"USER".equals(roleUpper)) {
            throw CustomException.badRequest("role은 ADMIN 또는 USER여야 합니다.", "INVALID_INPUT");
        }
        ensureUserExists(targetUserId);
        if (targetUserId != null && targetUserId.equals(callerUserId)) {
            throw CustomException.badRequest("자기 자신의 권한은 변경할 수 없습니다.", "SELF_DEMOTION_BLOCKED");
        }
        try (Connection conn = dataSource.getConnection()) {
            boolean targetIsSystemAdmin = isSystemAdmin(conn, targetUserId);
            if (targetIsSystemAdmin) {
                log.warn("시스템 관리자 역할 변경 시도 차단: caller={}, target={}, requestedRole={}", callerUserId, targetUserId, roleUpper);
                throw CustomException.badRequest("시스템 관리자는 수정할 수 없습니다.", "SYSTEM_ADMIN_IMMUTABLE");
            }
            String currentRole = getCurrentRole(conn, targetUserId);
            if ("ADMIN".equals(currentRole) && "USER".equals(roleUpper)) {
                int adminCount = countAdmins(conn);
                if (adminCount <= 1) {
                    throw CustomException.badRequest("마지막 관리자 권한은 변경할 수 없습니다.", "LAST_ADMIN_BLOCKED");
                }
            }
            String sql = "UPDATE app_user SET role = ? WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, roleUpper);
                ps.setString(2, targetUserId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw CustomException.notFound("해당 사용자를 찾을 수 없습니다: " + targetUserId, "USER_NOT_FOUND");
                }
            }
        } catch (CustomException e) {
            throw e;
        } catch (SQLException e) {
            log.error("사용자 역할 변경 실패: targetUserId={}, role={}", targetUserId, role, e);
            throw new RuntimeException("역할 변경 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        log.info("사용자 역할 변경: targetUserId={}, role={}, caller={}", targetUserId, roleUpper, callerUserId);
        return getUserSummary(targetUserId);
    }

    private boolean isSystemAdmin(Connection conn, String userId) throws SQLException {
        String sql = "SELECT is_system_admin FROM app_user WHERE username = ? AND deleted_at IS NULL LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
            }
        }
    }

    private String getCurrentRole(Connection conn, String userId) throws SQLException {
        String sql = "SELECT role FROM app_user WHERE username = ? AND deleted_at IS NULL LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("role") : null;
            }
        }
    }

    private int countAdmins(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN' AND deleted_at IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private UserListItemResponse getUserSummary(String username) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, username, role, department_code, position, rank, is_system_admin, employee_number "
                    + "FROM app_user WHERE username = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Long id = rs.getObject("id", Long.class);
                        String uname = rs.getString("username");
                        String role = rs.getString("role");
                        String departmentCode = rs.getString("department_code");
                        String position = rs.getString("position");
                        String rank = rs.getString("rank");
                        boolean isSystemAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
                        String employeeNumber = rs.getString("employee_number");
                        return new UserListItemResponse(id, uname, role, departmentCode, position, rank, isSystemAdmin, employeeNumber);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("사용자 요약 조회 실패: username={}", username, e);
            throw new RuntimeException("사용자 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        throw CustomException.notFound("해당 사용자를 찾을 수 없습니다: " + username, "USER_NOT_FOUND");
    }

    private void ensureUserExists(String userId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM app_user WHERE username = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw CustomException.notFound("해당 사용자를 찾을 수 없습니다: " + userId, "USER_NOT_FOUND");
                    }
                }
            }
        } catch (SQLException e) {
            log.error("사용자 존재 확인 실패: userId={}", userId, e);
            throw new RuntimeException("사용자 확인 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
}
