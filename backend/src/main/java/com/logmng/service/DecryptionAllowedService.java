package com.logmng.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Decryption-allowed store: authorization for "who can decrypt which GUID" per user and screen.
 * Req: docs/requirements/20260318-decryption-allowed-store-and-decrypt-ui.md.
 * Table: user_decryption_allowed (user_id, screen, guid, valid_until). search_history_approved_row is audit-only.
 */
@Service
public class DecryptionAllowedService {

    private static final Logger log = LoggerFactory.getLogger(DecryptionAllowedService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);
    /** Validity duration for new allowed set (same as search_history approval validity). */
    private static final int VALIDITY_HOURS = 24;

    private final DataSource dataSource;

    public DecryptionAllowedService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Get allowed GUIDs and valid_until for (user_id, screen). Only rows with valid_until > now are returned.
     * Response: map with "screen", "validUntil" (ISO string), "guids" (list). Empty guids if none.
     */
    public Map<String, Object> getAllowed(Long userId, String screen) {
        if (userId == null || screen == null || screen.isBlank()) {
            return emptyAllowedResponse(screen);
        }
        List<String> guids = new ArrayList<>();
        String validUntilStr = null;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT guid, valid_until FROM user_decryption_allowed WHERE user_id = ? AND screen = ? AND valid_until > CURRENT_TIMESTAMP ORDER BY valid_until DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindUserId(ps, 1, userId);
                ps.setString(2, screen.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String g = rs.getString("guid");
                        if (g != null && !g.isBlank()) {
                            guids.add(g);
                        }
                        if (validUntilStr == null) {
                            Timestamp ts = rs.getTimestamp("valid_until");
                            if (ts != null) {
                                validUntilStr = ISO_FORMATTER.format(ts.toInstant());
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("decryption-allowed get failed: userId={}, screen={}", userId, screen, e);
            return emptyAllowedResponse(screen);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("screen", screen);
        out.put("validUntil", validUntilStr != null ? validUntilStr : null);
        out.put("guids", guids);
        return out;
    }

    /**
     * Check whether the user is allowed to decrypt the given guid on the screen (valid_until > now).
     */
    public boolean isAllowed(Long userId, String screen, String guid) {
        if (userId == null || screen == null || screen.isBlank() || guid == null || guid.isBlank()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM user_decryption_allowed WHERE user_id = ? AND screen = ? AND guid = ? AND valid_until > CURRENT_TIMESTAMP LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindUserId(ps, 1, userId);
                ps.setString(2, screen.trim());
                ps.setString(3, guid.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("decryption-allowed check failed: userId={}, screen={}, guid={}", userId, screen, guid, e);
            return false;
        }
    }

    /**
     * Replace the allowed set for (user_id, screen) with the given guids and set valid_until to now + VALIDITY_HOURS.
     * Called after approve: refresh requester's allowed set with snapshot GUIDs.
     */
    public void addOrReplaceAllowed(Long userId, String screen, List<String> guids) {
        if (userId == null || screen == null || screen.isBlank()) {
            return;
        }
        Instant validUntil = Instant.now().plusSeconds(VALIDITY_HOURS * 3600L);
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String del = "DELETE FROM user_decryption_allowed WHERE user_id = ? AND screen = ?";
                try (PreparedStatement ps = conn.prepareStatement(del)) {
                    bindUserId(ps, 1, userId);
                    ps.setString(2, screen.trim());
                    ps.executeUpdate();
                }
                if (guids != null && !guids.isEmpty()) {
                    String ins = "INSERT INTO user_decryption_allowed (user_id, screen, guid, valid_until) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(ins)) {
                        Timestamp validTs = Timestamp.from(validUntil);
                        for (String guid : guids) {
                            if (guid == null || guid.isBlank()) continue;
                            bindUserId(ps, 1, userId);
                            ps.setString(2, screen.trim());
                            ps.setString(3, guid.trim());
                            ps.setTimestamp(4, validTs);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                conn.commit();
                log.info("decryption-allowed refreshed: userId={}, screen={}, guidsCount={}, validUntil={}", userId, screen, guids != null ? guids.size() : 0, ISO_FORMATTER.format(validUntil));
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("decryption-allowed addOrReplace failed: userId={}, screen={}", userId, screen, e);
            throw new RuntimeException("복호화 허용 목록 갱신 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Delete expired rows for the user (valid_until < now). Called after approve to avoid unbounded growth.
     */
    public int deleteExpiredForUser(Long userId) {
        if (userId == null) return 0;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM user_decryption_allowed WHERE user_id = ? AND valid_until < CURRENT_TIMESTAMP";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindUserId(ps, 1, userId);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    log.debug("decryption-allowed cleanup: userId={}, deleted={}", userId, deleted);
                }
                return deleted;
            }
        } catch (SQLException e) {
            log.error("decryption-allowed deleteExpired failed: userId={}", userId, e);
            return 0;
        }
    }

    private static Map<String, Object> emptyAllowedResponse(String screen) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("screen", screen != null ? screen : null);
        out.put("validUntil", null);
        out.put("guids", Collections.emptyList());
        return out;
    }

    private static void bindUserId(PreparedStatement ps, int index, Long userId) throws SQLException {
        if (userId == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setObject(index, userId);
        }
    }
}
