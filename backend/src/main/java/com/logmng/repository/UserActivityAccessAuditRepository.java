package com.logmng.repository;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only {@code user_activity_access_audit} persistence (JDBC; aligns with UserActivityLogService pattern).
 */
@Repository
public class UserActivityAccessAuditRepository {

    private final DataSource dataSource;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public UserActivityAccessAuditRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(long accessorUserId, Long targetActivityLogId, String accessType,
                       String ipAddress, String userAgent) throws SQLException {
        String sql = "INSERT INTO user_activity_access_audit "
                + "(accessor_user_id, target_activity_log_id, access_type, created_at, ip_address, user_agent) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, accessorUserId);
            if (targetActivityLogId != null) {
                ps.setLong(2, targetActivityLogId);
            } else {
                ps.setNull(2, Types.BIGINT);
            }
            ps.setString(3, accessType);
            ps.setString(4, ipAddress);
            ps.setString(5, userAgent);
            ps.executeUpdate();
        }
    }

    public long countSearch(AccessAuditSearchParams p) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM user_activity_access_audit a ");
        sql.append(buildJoinsAndWhere(p, true));
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bindParams(ps, p, true, 1);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public List<Map<String, Object>> search(AccessAuditSearchParams p) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.accessor_user_id, au.username AS accessor_username, ");
        sql.append("a.target_activity_log_id, a.access_type, a.created_at, a.ip_address ");
        sql.append("FROM user_activity_access_audit a ");
        sql.append("LEFT JOIN app_user au ON a.accessor_user_id = au.id ");
        sql.append(buildJoinsAndWhere(p, false));
        sql.append(" ORDER BY a.created_at ").append(p.sortDesc() ? "DESC" : "ASC");
        sql.append(" LIMIT ? OFFSET ?");

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int idx = bindParams(ps, p, false, 1);
            ps.setInt(idx++, p.pageSize());
            ps.setInt(idx, (p.page() - 1) * p.pageSize());
            List<Map<String, Object>> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        String name = meta.getColumnLabel(i);
                        Object val = rs.getObject(i);
                        if (val instanceof Timestamp ts) {
                            val = ts.toLocalDateTime().format(DT_FMT);
                        }
                        row.put(name, val);
                    }
                    rows.add(row);
                }
            }
            return rows;
        }
    }

    private static String buildJoinsAndWhere(AccessAuditSearchParams p, boolean forCount) {
        StringBuilder w = new StringBuilder();
        w.append("LEFT JOIN user_activity_log u ON a.target_activity_log_id = u.id WHERE 1=1 ");
        if (p.startDate() != null) {
            w.append("AND DATE(a.created_at) >= ? ");
        }
        if (p.endDate() != null) {
            w.append("AND DATE(a.created_at) <= ? ");
        }
        if (p.accessorUserId() != null) {
            w.append("AND a.accessor_user_id = ? ");
        }
        if (p.targetActivityLogId() != null) {
            w.append("AND a.target_activity_log_id = ? ");
        }
        if (p.accessType() != null && !p.accessType().isBlank()) {
            w.append("AND a.access_type = ? ");
        }
        // TC-07: scope — only audit rows whose target log is visible on activity-log search
        switch (p.scopeMode()) {
            case SELF -> w.append("AND u.user_id = ? AND a.target_activity_log_id IS NOT NULL ");
            case TEAM -> {
                if (p.teamUserIds() == null || p.teamUserIds().isEmpty()) {
                    w.append("AND 1 = 0 ");
                } else {
                    w.append("AND u.user_id IN (");
                    w.append(String.join(",", java.util.Collections.nCopies(p.teamUserIds().size(), "?")));
                    w.append(") AND a.target_activity_log_id IS NOT NULL ");
                }
            }
            case ALL -> w.append("AND u.id IS NOT NULL ");
            case ADMIN -> { /* no row visibility filter */ }
        }
        return w.toString();
    }

    private static int bindParams(PreparedStatement ps, AccessAuditSearchParams p, boolean forCount, int start)
            throws SQLException {
        int i = start;
        if (p.startDate() != null) {
            ps.setDate(i++, Date.valueOf(p.startDate()));
        }
        if (p.endDate() != null) {
            ps.setDate(i++, Date.valueOf(p.endDate()));
        }
        if (p.accessorUserId() != null) {
            ps.setLong(i++, p.accessorUserId());
        }
        if (p.targetActivityLogId() != null) {
            ps.setLong(i++, p.targetActivityLogId());
        }
        if (p.accessType() != null && !p.accessType().isBlank()) {
            ps.setString(i++, p.accessType().trim());
        }
        switch (p.scopeMode()) {
            case SELF -> {
                ps.setString(i++, p.currentUsername());
            }
            case TEAM -> {
                if (p.teamUserIds() != null && !p.teamUserIds().isEmpty()) {
                    for (String uid : p.teamUserIds()) {
                        ps.setString(i++, uid);
                    }
                }
            }
            default -> {
            }
        }
        return i;
    }

    public enum ScopeMode {
        ADMIN, SELF, TEAM, ALL
    }

    public record AccessAuditSearchParams(
            LocalDate startDate,
            LocalDate endDate,
            Long accessorUserId,
            Long targetActivityLogId,
            String accessType,
            int page,
            int pageSize,
            boolean sortDesc,
            ScopeMode scopeMode,
            String currentUsername,
            List<String> teamUserIds
    ) {
        public int page() {
            return Math.max(1, page);
        }

        public int pageSize() {
            int ps = pageSize > 0 ? pageSize : 20;
            return Math.min(ps, 200);
        }

        public boolean sortDesc() {
            return sortDesc;
        }
    }
}
