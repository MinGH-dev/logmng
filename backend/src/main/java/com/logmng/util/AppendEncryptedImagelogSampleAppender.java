package com.logmng.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Idempotent INSERT for dev: append encrypted imagelog rows without deleting existing data.
 * Matches uniqueness semantics of {@code uq_imagelog_guid_row_status} via NOT EXISTS on
 * {@code (guid, COALESCE(NULLIF(TRIM(status), ''), ''))}.
 */
public final class AppendEncryptedImagelogSampleAppender {

    private AppendEncryptedImagelogSampleAppender() {
    }

    /**
     * Normalized status key aligned with DB: {@code COALESCE(NULLIF(TRIM(status), ''), '')}.
     */
    public static String normalizedStatusKey(String status) {
        if (status == null) {
            return "";
        }
        String t = status.trim();
        return t.isEmpty() ? "" : t;
    }

    /**
     * Inserts one row if no row exists with the same guid and normalized status.
     *
     * @return 1 if a row was inserted, 0 if skipped (already present)
     */
    public static int insertIfAbsent(Connection conn, GenerateEncryptedSampleData.SampleData row, long insertTimeMs)
            throws SQLException {
        String norm = normalizedStatusKey(row.status);
        String sql = "INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time) "
                + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ? "
                + "WHERE NOT EXISTS ("
                + "  SELECT 1 FROM imagelog i WHERE i.guid = ? "
                + "  AND COALESCE(NULLIF(TRIM(i.status), ''), '') = ?"
                + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, row.application);
            ps.setString(2, row.servicegroup);
            ps.setString(3, row.service);
            ps.setString(4, row.status);
            ps.setString(5, row.data);
            ps.setString(6, row.datastring);
            ps.setString(7, row.guid);
            ps.setString(8, row.header);
            ps.setString(9, row.headerstring);
            ps.setLong(10, insertTimeMs);
            ps.setString(11, row.guid);
            ps.setString(12, norm);
            return ps.executeUpdate();
        }
    }
}
