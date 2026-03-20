package com.logmng.service;

import com.logmng.dto.DecryptionRowKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DecryptionAllowedService (req 20260318 + 20260320 composite key).
 */
class DecryptionAllowedServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:decryption_allowed_test;DB_CLOSE_DELAY=-1";

    private DataSource dataSource;
    private DecryptionAllowedService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        service = new DecryptionAllowedService(dataSource);
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user (id BIGINT PRIMARY KEY, username VARCHAR(100), name VARCHAR(200))");
            stmt.execute("CREATE TABLE IF NOT EXISTS user_decryption_allowed (" +
                    "user_id BIGINT NOT NULL, screen VARCHAR(50) NOT NULL, guid VARCHAR(512) NOT NULL, " +
                    "row_status VARCHAR(256) NOT NULL DEFAULT '', valid_until TIMESTAMP NOT NULL, " +
                    "PRIMARY KEY (user_id, screen, guid, row_status))");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
    }

    @Test
    @DisplayName("getAllowed returns empty when no rows")
    void getAllowed_emptyWhenNoRows() {
        Map<String, Object> out = service.getAllowed(1L, "main");
        assertThat(out.get("screen")).isEqualTo("main");
        assertThat(out.get("validUntil")).isNull();
        assertThat((List<?>) out.get("guids")).isEmpty();
        assertThat((List<?>) out.get("allowedRows")).isEmpty();
    }

    @Test
    @DisplayName("getAllowed returns allowedRows and guids when rows exist")
    void getAllowed_returnsAllowedRowsWhenNotExpired() throws Exception {
        Instant future = Instant.now().plusSeconds(3600);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO user_decryption_allowed (user_id, screen, guid, row_status, valid_until) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, 10L);
            ps.setString(2, "main");
            ps.setString(3, "guid-1");
            ps.setString(4, "input");
            ps.setTimestamp(5, Timestamp.from(future));
            ps.executeUpdate();
            ps.setLong(1, 10L);
            ps.setString(2, "main");
            ps.setString(3, "guid-1");
            ps.setString(4, "output");
            ps.setTimestamp(5, Timestamp.from(future));
            ps.executeUpdate();
        }

        Map<String, Object> out = service.getAllowed(10L, "main");
        assertThat(out.get("screen")).isEqualTo("main");
        assertThat(out.get("validUntil")).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rows = (List<Map<String, String>>) out.get("allowedRows");
        assertThat(rows).hasSize(2);
        assertThat(service.isAllowed(10L, "main", "guid-1", "input")).isTrue();
        assertThat(service.isAllowed(10L, "main", "guid-1", "output")).isTrue();
        assertThat(service.isAllowed(10L, "main", "guid-1", "other")).isFalse();
    }

    @Test
    @DisplayName("isAllowed returns false when guid not in store")
    void isAllowed_returnsFalseWhenNotInStore() throws Exception {
        Instant future = Instant.now().plusSeconds(3600);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO user_decryption_allowed (user_id, screen, guid, row_status, valid_until) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, 30L);
            ps.setString(2, "main");
            ps.setString(3, "guid-only");
            ps.setString(4, "s1");
            ps.setTimestamp(5, Timestamp.from(future));
            ps.executeUpdate();
        }

        assertThat(service.isAllowed(30L, "main", "other-guid", "s1")).isFalse();
    }

    @Test
    @DisplayName("addOrReplaceAllowed inserts composite keys and replaces existing for user/screen")
    void addOrReplaceAllowed_replacesAndInserts() {
        service.addOrReplaceAllowed(40L, "main", List.of(
                new DecryptionRowKey("g1", "a"),
                new DecryptionRowKey("g2", "b")));
        Map<String, Object> out = service.getAllowed(40L, "main");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rows = (List<Map<String, String>>) out.get("allowedRows");
        assertThat(rows).hasSize(2);

        service.addOrReplaceAllowed(40L, "main", List.of(new DecryptionRowKey("g3", "x")));
        out = service.getAllowed(40L, "main");
        rows = (List<Map<String, String>>) out.get("allowedRows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("guid")).isEqualTo("g3");
        assertThat(rows.get(0).get("status")).isEqualTo("x");
    }

    @Test
    @DisplayName("deleteExpiredForUser removes only expired rows for that user")
    void deleteExpiredForUser_removesOnlyExpired() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        Instant future = Instant.now().plusSeconds(3600);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO user_decryption_allowed (user_id, screen, guid, row_status, valid_until) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, 50L);
            ps.setString(2, "main");
            ps.setString(3, "expired");
            ps.setString(4, "");
            ps.setTimestamp(5, Timestamp.from(past));
            ps.executeUpdate();
            ps.setLong(1, 50L);
            ps.setString(2, "main");
            ps.setString(3, "valid");
            ps.setString(4, "ok");
            ps.setTimestamp(5, Timestamp.from(future));
            ps.executeUpdate();
        }

        int deleted = service.deleteExpiredForUser(50L);
        assertThat(deleted).isEqualTo(1);
        assertThat(service.isAllowed(50L, "main", "valid", "ok")).isTrue();
        assertThat(service.isAllowed(50L, "main", "expired", "")).isFalse();
    }
}
