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
 * 복호화 결재자 지정: isAdmin, isApprover, listUsers, addApprover, removeApprover.
 * 부서별 결재자: listApproversByDepartment, addApproverForDepartment, removeApproverForDepartment, canApproveForRequester.
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

    public boolean isAdmin(String role) {
        return role != null && "ADMIN".equals(role);
    }

    /**
     * userId(username)가 decrypt_approver에 한 건이라도 있으면 true (전역 또는 부서별). 관리자는 콜러에서 isAdmin(role) || isApprover(userId)로 판단.
     */
    public boolean isApprover(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM decrypt_approver WHERE user_id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("결재자 여부 조회 실패: userId={}", userId, e);
            return false;
        }
    }

    /**
     * approverUserId가 requesterUserId의 검색 이력에 대해 승인/반려할 수 있는지.
     * 전역 결재자(department_code NULL) 또는 요청자 소속 부서(및 상위 부서)의 결재자이면 true.
     */
    public boolean canApproveForRequester(String approverUserId, String requesterUserId) {
        if (approverUserId == null || approverUserId.isBlank() || requesterUserId == null || requesterUserId.isBlank()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            // 전역 결재자 여부
            String sqlGlobal = "SELECT 1 FROM decrypt_approver WHERE user_id = ? AND department_code IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlGlobal)) {
                ps.setString(1, approverUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return true;
                }
            }
            // 요청자 부서
            String requesterDept = null;
            String sqlUser = "SELECT department_code FROM app_user WHERE username = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlUser)) {
                ps.setString(1, requesterUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) requesterDept = rs.getString("department_code");
                }
            }
            if (requesterDept == null || requesterDept.isBlank()) {
                return false;
            }
            List<String> allowedDepts = departmentService.getAncestorCodesIncludingSelf(requesterDept);
            String sqlDept = "SELECT 1 FROM decrypt_approver WHERE user_id = ? AND department_code = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlDept)) {
                ps.setString(1, approverUserId);
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
        }
        return false;
    }

    /**
     * app_user 목록 + 각 사용자별 isApprover. §7.1
     */
    public List<UserListItemResponse> listUsers() {
        List<UserListItemResponse> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT username, role, department_code FROM app_user ORDER BY username";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String username = rs.getString("username");
                        String role = rs.getString("role");
                        String departmentCode = rs.getString("department_code");
                        boolean isApprover = isApprover(username);
                        list.add(new UserListItemResponse(username, role, departmentCode, isApprover));
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
     * 전역 결재자 추가. app_user에 해당 사용자가 없으면 404. 관리자 전용. §7.2
     */
    public UserListItemResponse addApprover(String userId) {
        ensureUserExists(userId);
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO decrypt_approver (user_id, department_code) VALUES (?, NULL)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate")) {
                throw CustomException.badRequest("이미 전역 결재자로 지정된 사용자입니다.", "ALREADY_APPROVER");
            }
            if (e.getSQLState() != null && "23505".equals(e.getSQLState())) {
                throw CustomException.badRequest("이미 전역 결재자로 지정된 사용자입니다.", "ALREADY_APPROVER");
            }
            log.error("전역 결재자 추가 실패: userId={}", userId, e);
            throw new RuntimeException("결재자 추가 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        log.info("전역 결재자 추가: userId={}", userId);
        return new UserListItemResponse(userId, null, null, true);
    }

    /**
     * 전역 결재자 해제만. 부서별 결재자는 건드리지 않음. 관리자 전용. §7.3
     */
    public UserListItemResponse removeApprover(String userId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM decrypt_approver WHERE user_id = ? AND department_code IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    ensureUserExists(userId);
                }
            }
        } catch (SQLException e) {
            log.error("전역 결재자 제거 실패: userId={}", userId, e);
            throw new RuntimeException("결재자 제거 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        log.info("전역 결재자 제거: userId={}", userId);
        return new UserListItemResponse(userId, null, null, false);
    }

    /**
     * 해당 부서에 지정된 결재자 목록. §12.2
     */
    public List<UserListItemResponse> listApproversByDepartment(String departmentCode) {
        departmentService.requireExists(departmentCode);
        List<UserListItemResponse> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT a.user_id, u.role, u.department_code FROM decrypt_approver a JOIN app_user u ON u.username = a.user_id WHERE a.department_code = ? ORDER BY a.user_id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, departmentCode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new UserListItemResponse(
                                rs.getString("user_id"),
                                rs.getString("role"),
                                rs.getString("department_code"),
                                true));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("부서 결재자 목록 조회 실패: departmentCode={}", departmentCode, e);
            throw new RuntimeException("부서 결재자 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * 부서별 결재자 추가. §12.3
     */
    public UserListItemResponse addApproverForDepartment(String departmentCode, String userId) {
        departmentService.requireExists(departmentCode);
        ensureUserExists(userId);
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO decrypt_approver (user_id, department_code) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                ps.setString(2, departmentCode);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            if (e.getSQLState() != null && "23505".equals(e.getSQLState())) {
                throw CustomException.badRequest("이미 해당 부서 결재자로 지정된 사용자입니다.", "ALREADY_APPROVER");
            }
            log.error("부서 결재자 추가 실패: departmentCode={}, userId={}", departmentCode, userId, e);
            throw new RuntimeException("부서 결재자 추가 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        log.info("부서 결재자 추가: departmentCode={}, userId={}", departmentCode, userId);
        return new UserListItemResponse(userId, null, departmentCode, true);
    }

    /**
     * 부서별 결재자 제거. §12.4
     */
    public UserListItemResponse removeApproverForDepartment(String departmentCode, String userId) {
        departmentService.requireExists(departmentCode);
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM decrypt_approver WHERE user_id = ? AND department_code = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                ps.setString(2, departmentCode);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    ensureUserExists(userId);
                }
            }
        } catch (SQLException e) {
            log.error("부서 결재자 제거 실패: departmentCode={}, userId={}", departmentCode, userId, e);
            throw new RuntimeException("부서 결재자 제거 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        log.info("부서 결재자 제거: departmentCode={}, userId={}", departmentCode, userId);
        return new UserListItemResponse(userId, null, departmentCode, false);
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
