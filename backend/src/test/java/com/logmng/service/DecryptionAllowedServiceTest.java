package com.logmng.service;

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
 * Unit tests for DecryptionAllowedService (req 20260318).
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
                    "user_id BIGINT NOT NULL, screen VARCHAR(50) NOT NULL, guid VARCHAR(512) NOT NULL, valid_until TIMESTAMP NOT NULL, " +
                    "PRIMARY KEY (user_id, screen, guid))");
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
    }

    @Test
    @DisplayName("getAllowed returns guids and validUntil when rows exist and not expired")
    void getAllowed_returnsGuidsWhenNotExpired() throws Exception {
        Instant future = Instant.now().plusSeconds(3600);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO user_decryption_allowed (user_id, screen, guid, valid_until) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, 10L);
            ps.setString(2, "main");
            ps.setString(3, "guid-1");
            ps.setTimestamp(4, Timestamp.from(future));
            ps.executeUpdate();
            ps.setLong(1, 10L);
            ps.setString(2, "main");
            ps.setString(3, "guid-2");
            ps.setTimestamp(4, Timestamp.from(future));
            ps.executeUpdate();
        }

        Map<String, Object> out = service.getAllowed(10L, "main");
        assertThat(out.get("screen")).isEqualTo("main");
        assertThat(out.get("validUntil")).isNotNull();
        assertThat((List<String>) out.get("guids")).containsExactlyInAnyOrder("guid-1", "guid-2");
    }

    @Test
    @DisplayName("isAllowed returns true when row exists and valid_until in future")
    void isAllowed_returnsTrueWhenInStoreAndNotExpired() throws Exception {
        Instant future = Instant.now().plusSeconds(3600);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO user_decryption_allowed (user_id, screen, guid, valid_until) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, 20L);
            ps.setString(2, "main");
            ps.setString(3, "guid-ok");
            ps.setTimestamp(4, Timestamp.from(future));
            ps.executeUpdate();
        }

        assertThat(service.isAllowed(20L, "main", "guid-ok")).isTrue();
    }

    @Test
    @DisplayName("isAllowed returns false when guid not in store")
    void isAllowed_returnsFalseWhenNotInStore() throws Exception {
        Instant future = Instant.now().plusSeconds(3600);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO user_decryption_allowed (user_id, screen, guid, valid_until) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, 30L);
            ps.setString(2, "main");
            ps.setString(3, "guid-only");
            ps.setTimestamp(4, Timestamp.from(future));
            ps.executeUpdate();
        }

        assertThat(service.isAllowed(30L, "main", "other-guid")).isFalse();
    }

    @Test
    @DisplayName("addOrReplaceAllowed inserts guids and replaces existing for user/screen")
    void addOrReplaceAllowed_replacesAndInserts() {
        service.addOrReplaceAllowed(40L, "main", List.of("g1", "g2"));
        Map<String, Object> out = service.getAllowed(40L, "main");
        assertThat((List<String>) out.get("guids")).containsExactlyInAnyOrder("g1", "g2");

        service.addOrReplaceAllowed(40L, "main", List.of("g3"));
        out = service.getAllowed(40L, "main");
        assertThat((List<String>) out.get("guids")).containsExactly("g3");
    }

    @Test
    @DisplayName("deleteExpiredForUser removes only expired rows for that user")
    void deleteExpiredForUser_removesOnlyExpired() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        Instant future = Instant.now().plusSeconds(3600);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO user_decryption_allowed (user_id, screen, guid, valid_until) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, 50L);
            ps.setString(2, "main");
            ps.setString(3, "expired");
            ps.setTimestamp(4, Timestamp.from(past));
            ps.executeUpdate();
            ps.setLong(1, 50L);
            ps.setString(2, "main");
            ps.setString(3, "valid");
            ps.setTimestamp(4, Timestamp.from(future));
            ps.executeUpdate();
        }

        int deleted = service.deleteExpiredForUser(50L);
        assertThat(deleted).isEqualTo(1);
        assertThat(service.isAllowed(50L, "main", "valid")).isTrue();
        assertThat(service.isAllowed(50L, "main", "expired")).isFalse();
    }
}
