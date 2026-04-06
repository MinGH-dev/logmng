package com.logmng.repository;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC access for {@code screen_display_label} (schema_sys.sql).
 */
@Repository
public class JdbcScreenDisplayLabelRepository implements ScreenDisplayLabelDataAccess {

    private final DataSource dataSource;

    public JdbcScreenDisplayLabelRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<ScreenDisplayLabelRow> findAllOrdered() throws SQLException {
        List<ScreenDisplayLabelRow> out = new ArrayList<>();
        String sql = "SELECT screen_id, label_user, label_admin, parent_group_id, sort_order, updated_at, updated_by "
                + "FROM screen_display_label ORDER BY screen_id";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ScreenDisplayLabelRow row = new ScreenDisplayLabelRow();
                row.setScreenId(rs.getString("screen_id"));
                row.setLabelUser(rs.getString("label_user"));
                row.setLabelAdmin(rs.getString("label_admin"));
                row.setParentGroupId(rs.getString("parent_group_id"));
                int so = rs.getInt("sort_order");
                if (rs.wasNull()) {
                    row.setSortOrder(null);
                } else {
                    row.setSortOrder(so);
                }
                row.setUpdatedAt(rs.getTimestamp("updated_at"));
                long ub = rs.getLong("updated_by");
                if (!rs.wasNull()) {
                    row.setUpdatedBy(ub);
                }
                out.add(row);
            }
        }
        return out;
    }

    /**
     * Upsert rows. Uses PostgreSQL ON CONFLICT.
     */
    @Override
    public void upsertAll(List<ScreenDisplayLabelRow> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO screen_display_label (screen_id, label_user, label_admin, parent_group_id, sort_order, updated_by, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (screen_id) DO UPDATE SET "
                + "label_user = EXCLUDED.label_user, "
                + "label_admin = EXCLUDED.label_admin, "
                + "parent_group_id = EXCLUDED.parent_group_id, "
                + "sort_order = EXCLUDED.sort_order, "
                + "updated_by = EXCLUDED.updated_by, "
                + "updated_at = CURRENT_TIMESTAMP";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ScreenDisplayLabelRow row : rows) {
                ps.setString(1, row.getScreenId());
                ps.setString(2, row.getLabelUser());
                if (row.getLabelAdmin() != null) {
                    ps.setString(3, row.getLabelAdmin());
                } else {
                    ps.setNull(3, Types.VARCHAR);
                }
                if (row.getParentGroupId() != null) {
                    ps.setString(4, row.getParentGroupId());
                } else {
                    ps.setNull(4, Types.VARCHAR);
                }
                if (row.getSortOrder() != null) {
                    ps.setInt(5, row.getSortOrder());
                } else {
                    ps.setNull(5, Types.INTEGER);
                }
                if (row.getUpdatedBy() != null) {
                    ps.setLong(6, row.getUpdatedBy());
                } else {
                    ps.setNull(6, Types.BIGINT);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
