package com.logmng.service;

import com.logmng.dto.response.UserListItemResponse;
import com.logmng.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 복호화 결재자: isAdmin, isApprover, listUsers, canApproveForRequester.
 * 결재자는 팀장(position) 자동 지정으로 관리; addApprover/removeApprover 및 부서별 결재자 API 제거됨.
 */
@Service
public class DecryptApproverService {

    private static final Logger log = LoggerFactory.getLogger(DecryptApproverService.class);

    private final DataSource dataSource;
    private final DepartmentService departmentService;

    public DecryptApproverService(DataSource dataSource, DepartmentService departmentService) {
        this.dataSource = dataSource;
        this.departmentService = departmentService;
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
     * appUserId (numeric app_user.id)가 decrypt_approver에 한 건이라도 있으면 true (전역 또는 부서별).
     * Req 20260316: permission checks use app_user_id.
     */
    public boolean isApprover(Long appUserId) {
        if (appUserId == null) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM decrypt_approver WHERE app_user_id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, appUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("결재자 여부 조회 실패: appUserId={}", appUserId, e);
            return false;
        }
    }

    /**
     * approverUserId가 requesterUserId의 검색 이력에 대해 승인/반려할 수 있는지.
     * 전역 결재자(department_code NULL) 또는 요청자 소속 부서(및 상위 부서)의 결재자이면 true.
     * Req 20260316: both params are numeric app_user.id; query decrypt_approver by app_user_id; requester department by app_user.id.
     * Non-SQL throws are caught and result in false so approval path never surfaces 500.
     */
    public boolean canApproveForRequester(Long approverUserId, Long requesterUserId) {
        if (approverUserId == null || requesterUserId == null) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            // 전역 결재자 여부
            String sqlGlobal = "SELECT 1 FROM decrypt_approver WHERE app_user_id = ? AND department_code IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlGlobal)) {
                ps.setLong(1, approverUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return true;
                }
            }
            // 요청자 부서 (by app_user.id)
            String requesterDept = null;
            String sqlUser = "SELECT department_code FROM app_user WHERE id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlUser)) {
                ps.setLong(1, requesterUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) requesterDept = rs.getString("department_code");
                }
            }
            if (requesterDept == null || requesterDept.isBlank()) {
                return false;
            }
            List<String> allowedDepts = departmentService.getAncestorCodesIncludingSelf(requesterDept);
            String sqlDept = "SELECT 1 FROM decrypt_approver WHERE app_user_id = ? AND department_code = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlDept)) {
                ps.setLong(1, approverUserId);
                for (String dept : allowedDepts) {
                    ps.setString(2, dept);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return true;
                    }
                }
            }
        } catch (SQLException e) {
            log.error("승인 권한 판단 실패: approver={}, requester={}", approverUserId, requesterUserId, e);
            return false;
        } catch (Throwable t) {
            log.warn("승인 권한 판단 중 예외(비-SQL): approver={}, requester={}, type={}", approverUserId, requesterUserId, t.getClass().getName(), t);
            return false;
        }
        return false;
    }

    /**
     * app_user 목록 + 각 사용자별 isApprover, position, rank, isSystemAdmin. §7.1. userId = numeric app_user.id (req 20260316).
     */
    public List<UserListItemResponse> listUsers() {
        List<UserListItemResponse> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, username, role, department_code, position, rank, is_system_admin FROM app_user ORDER BY username";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Long id = rs.getObject("id", Long.class);
                        String username = rs.getString("username");
                        String role = rs.getString("role");
                        String departmentCode = rs.getString("department_code");
                        String position = rs.getString("position");
                        String rank = rs.getString("rank");
                        boolean isSystemAdmin = Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
                        boolean isApprover = isApprover(id);
                        list.add(new UserListItemResponse(id, username, role, departmentCode, isApprover, position, rank, isSystemAdmin));
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
        String sql = "SELECT is_system_admin FROM app_user WHERE username = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && Boolean.TRUE.equals(rs.getObject("is_system_admin", Boolean.class));
            }
        }
    }

    private String getCurrentRole(Connection conn, String userId) throws SQLException {
        String sql = "SELECT role FROM app_user WHERE username = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("role") : null;
            }
        }
    }

    private int countAdmins(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private UserListItemResponse getUserSummary(String username) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, username, role, department_code, position, rank, is_system_admin FROM app_user WHERE username = ? LIMIT 1";
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
                        boolean isApprover = isApprover(id);
                        return new UserListItemResponse(id, uname, role, departmentCode, isApprover, position, rank, isSystemAdmin);
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
            String sql = "SELECT 1 FROM app_user WHERE username = ? LIMIT 1";
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
