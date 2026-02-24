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
 */
@Service
public class DecryptApproverService {

    private static final Logger log = LoggerFactory.getLogger(DecryptApproverService.class);

    private final DataSource dataSource;

    public DecryptApproverService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isAdmin(String role) {
        return role != null && "ADMIN".equals(role);
    }

    /**
     * userId(username)가 decrypt_approver에 있으면 true. 관리자는 콜러에서 isAdmin(role) || isApprover(userId)로 판단.
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
     * 결재자 추가. app_user에 해당 사용자가 없으면 404. 관리자 전용.
     */
    public UserListItemResponse addApprover(String userId) {
        ensureUserExists(userId);
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO decrypt_approver (user_id) VALUES (?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate") || e.getSQLState() != null && "23505".equals(e.getSQLState())) {
                throw CustomException.badRequest("이미 결재자로 지정된 사용자입니다.", "ALREADY_APPROVER");
            }
            log.error("결재자 추가 실패: userId={}", userId, e);
            throw new RuntimeException("결재자 추가 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        log.info("결재자 추가: userId={}", userId);
        return new UserListItemResponse(userId, null, null, true);
    }

    /**
     * 결재자 해제. 관리자 전용.
     */
    public UserListItemResponse removeApprover(String userId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM decrypt_approver WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    // 사용자 존재 여부만 확인; 없으면 404
                    ensureUserExists(userId);
                }
            }
        } catch (SQLException e) {
            log.error("결재자 제거 실패: userId={}", userId, e);
            throw new RuntimeException("결재자 제거 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        log.info("결재자 제거: userId={}", userId);
        return new UserListItemResponse(userId, null, null, false);
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
