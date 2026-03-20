package com.logmng.service;

import com.logmng.diagnostic.ApprovalFlowDiagnosticLog;
import com.logmng.dto.DecryptionRowKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Decryption-allowed store: (user_id, screen, guid, row_status) composite for java_fw_imglog.
 * Req: 20260318 store + 20260320 composite (guid, status).
 */
@Service
public class DecryptionAllowedService {

    private static final Logger log = LoggerFactory.getLogger(DecryptionAllowedService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final int VALIDITY_HOURS = 24;

    private final DataSource dataSource;
    private final boolean diagnosticApprovalFlow;

    public DecryptionAllowedService(DataSource dataSource) {
        this(dataSource, false);
    }

    @Autowired
    public DecryptionAllowedService(DataSource dataSource, @Value("${app.diagnostic.approval-flow:false}") boolean diagnosticApprovalFlow) {
        this.dataSource = dataSource;
        this.diagnosticApprovalFlow = diagnosticApprovalFlow;
    }

    /**
     * Allowed rows for UI: {@code allowedRows: [{guid, status}, ...]}, plus legacy {@code guids} (distinct guid only, for backward compat).
     */
    public Map<String, Object> getAllowed(Long userId, String screen) {
        if (userId == null || screen == null || screen.isBlank()) {
            return emptyAllowedResponse(screen);
        }
        List<Map<String, String>> allowedRows = new ArrayList<>();
        LinkedHashSet<String> distinctGuids = new LinkedHashSet<>();
        String validUntilStr = null;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT guid, row_status, valid_until FROM user_decryption_allowed WHERE user_id = ? AND screen = ? AND valid_until > CURRENT_TIMESTAMP ORDER BY valid_until DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindUserId(ps, 1, userId);
                ps.setString(2, screen.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String g = rs.getString("guid");
                        String st = rs.getString("row_status");
                        if (g != null && !g.isBlank()) {
                            Map<String, String> pair = new LinkedHashMap<>();
                            pair.put("guid", g.trim());
                            pair.put("status", st != null ? st : "");
                            allowedRows.add(pair);
                            distinctGuids.add(g.trim());
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
        out.put("allowedRows", allowedRows);
        out.put("guids", new ArrayList<>(distinctGuids));
        return out;
    }

    /**
     * Authorization: exact match on (guid, row_status); row_status normalized same as DB column.
     */
    public boolean isAllowed(Long userId, String screen, String guid, String status) {
        if (userId == null || screen == null || screen.isBlank() || guid == null || guid.isBlank()) {
            return false;
        }
        String rowStatus = DecryptionRowKey.normalizeStatus(status);
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM user_decryption_allowed WHERE user_id = ? AND screen = ? AND guid = ? AND row_status = ? AND valid_until > CURRENT_TIMESTAMP LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindUserId(ps, 1, userId);
                ps.setString(2, screen.trim());
                ps.setString(3, guid.trim());
                ps.setString(4, rowStatus);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("decryption-allowed check failed: userId={}, screen={}, guid={}, rowStatus={}", userId, screen, guid, rowStatus, e);
            return false;
        }
    }

    /**
     * Replace allowed set for (user_id, screen) with composite keys.
     */
    public void addOrReplaceAllowed(Long userId, String screen, List<DecryptionRowKey> keys) {
        addOrReplaceAllowed(userId, screen, keys, null);
    }

    /**
     * @param searchHistoryIdForDiagnostics optional; when set and {@code app.diagnostic.approval-flow} is true, SQL errors include correlation.
     */
    public void addOrReplaceAllowed(Long userId, String screen, List<DecryptionRowKey> keys, Long searchHistoryIdForDiagnostics) {
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
                if (keys != null && !keys.isEmpty()) {
                    LinkedHashMap<String, DecryptionRowKey> uniqueKeys = new LinkedHashMap<>();
                    for (DecryptionRowKey k : keys) {
                        if (k == null || k.getGuid().isEmpty()) {
                            continue;
                        }
                        uniqueKeys.putIfAbsent(k.compositeMapKey(), k);
                    }
                    String ins = "INSERT INTO user_decryption_allowed (user_id, screen, guid, row_status, valid_until) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(ins)) {
                        Timestamp validTs = Timestamp.from(validUntil);
                        for (DecryptionRowKey k : uniqueKeys.values()) {
                            bindUserId(ps, 1, userId);
                            ps.setString(2, screen.trim());
                            ps.setString(3, k.getGuid());
                            ps.setString(4, k.getStatus());
                            ps.setTimestamp(5, validTs);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                conn.commit();
                log.info("decryption-allowed refreshed: userId={}, screen={}, keys={}, validUntil={}", userId, screen, keys != null ? keys.size() : 0, ISO_FORMATTER.format(validUntil));
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            ApprovalFlowDiagnosticLog.logSqlException(diagnosticApprovalFlow, searchHistoryIdForDiagnostics, userId, screen, "DECRYPTION_ALLOWED_ADD_OR_REPLACE", e);
            log.error("decryption-allowed addOrReplace failed: userId={}, screen={}", userId, screen, e);
            throw new RuntimeException("복호화 허용 목록 갱신 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    public int deleteExpiredForUser(Long userId) {
        if (userId == null) {
            return 0;
        }
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
        out.put("allowedRows", Collections.emptyList());
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
