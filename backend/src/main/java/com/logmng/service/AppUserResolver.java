package com.logmng.service;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Resolves app_user.id (numeric) ↔ app_user.username for API/DB mapping.
 * API/UI expose numeric userId; DB FKs use username. Req 20260316-user-id-numeric-userid-naming.
 */
@Service
public class AppUserResolver {

    private final DataSource dataSource;

    public AppUserResolver(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @return username for the given app_user.id, or null if not found or any exception (req 20260316: never throw to callers)
     */
    public String getUsernameById(Long id) {
        if (id == null) {
            return null;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT username FROM app_user WHERE id = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString("username") : null;
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return app_user.id for the given username, or null if not found or any exception (req 20260316: never throw to callers)
     */
    public Long getIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id FROM app_user WHERE username = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong("id") : null;
                }
            }
        } catch (Exception e) {
            return null;
        }
    }
}
