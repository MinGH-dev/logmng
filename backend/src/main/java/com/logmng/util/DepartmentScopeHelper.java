package com.logmng.util;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper to resolve "users in same department" for scope=team filtering.
 * Used by activity-log, statistics, and search-history when effective scope is 'team'.
 * Per req 20250304-team-scope-default-and-approval.
 */
public final class DepartmentScopeHelper {

    private DepartmentScopeHelper() {
    }

    /**
     * Returns usernames of users that share the same department_code as the given user.
     * If the user has no department_code (null/blank), returns only that user.
     *
     * @param dataSource datasource for app_user table
     * @param username  current user's username (app_user.username)
     * @return list of user_ids (usernames) in the same department; never null
     */
    public static List<String> getUserIdsInSameDepartment(DataSource dataSource, String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptyList();
        }
        try (Connection conn = dataSource.getConnection()) {
            String deptSql = "SELECT department_code FROM app_user WHERE username = ? AND deleted_at IS NULL LIMIT 1";
            String departmentCode;
            try (PreparedStatement ps = conn.prepareStatement(deptSql)) {
                ps.setString(1, username.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Collections.singletonList(username.trim());
                    }
                    departmentCode = rs.getString("department_code");
                }
            }
            if (departmentCode == null || departmentCode.isBlank()) {
                return Collections.singletonList(username.trim());
            }
            String listSql = "SELECT username FROM app_user WHERE department_code = ? AND deleted_at IS NULL ORDER BY username";
            List<String> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(listSql)) {
                ps.setString(1, departmentCode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getString("username"));
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            return Collections.singletonList(username.trim());
        }
    }

    /**
     * Returns numeric app_user.id of users in the same department as the given user (by app_user.id).
     * For search-history scope=team: list query filters with sh.user_id IN (...). Req 20260316.
     *
     * @param dataSource datasource for app_user table
     * @param userId    current user's app_user.id (numeric)
     * @return list of app_user.id in the same department; never null
     */
    public static List<Long> getNumericUserIdsInSameDepartment(DataSource dataSource, Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        try (Connection conn = dataSource.getConnection()) {
            String deptSql = "SELECT department_code FROM app_user WHERE id = ? AND deleted_at IS NULL LIMIT 1";
            String departmentCode;
            try (PreparedStatement ps = conn.prepareStatement(deptSql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Collections.singletonList(userId);
                    }
                    departmentCode = rs.getString("department_code");
                }
            }
            if (departmentCode == null || departmentCode.isBlank()) {
                return Collections.singletonList(userId);
            }
            String listSql = "SELECT id FROM app_user WHERE department_code = ? AND deleted_at IS NULL ORDER BY id";
            List<Long> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(listSql)) {
                ps.setString(1, departmentCode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getLong("id"));
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            return Collections.singletonList(userId);
        }
    }
}
