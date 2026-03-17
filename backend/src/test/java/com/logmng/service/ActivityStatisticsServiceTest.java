package com.logmng.service;

import com.logmng.util.StubDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Activity statistics service tests (scope=team empty allowlist, userId/userName mapping).
 * Req: 20260317-activity-statistics-department-approver-error.
 */
class ActivityStatisticsServiceTest {

    private ActivityStatisticsService serviceWithStub;

    @BeforeEach
    void setUp() {
        serviceWithStub = new ActivityStatisticsService(new StubDataSource());
    }

    // TC-01/05: scope=team with empty allowedUserIds → getUsers returns empty (no 500, no leak)
    @Test
    void getUsers_emptyAllowedUserIds_returnsEmpty() {
        List<Map<String, Object>> result = serviceWithStub.getUsers(null, Collections.emptyList());
        assertThat(result).isEmpty();
    }

    // TC-02/05: scope=team with empty allowedUserIds → getIps returns empty
    @Test
    void getIps_emptyAllowedUserIds_returnsEmpty() {
        List<String> result = serviceWithStub.getIps(null, Collections.emptyList());
        assertThat(result).isEmpty();
    }

    // TC-03/05: scope=team with empty allowedUserIds → getDailyStatistics returns empty dailyStats and zero summary.
    // (Covered by controller scope=team tests; service SQL uses PostgreSQL DATE() so not run against H2 here.)
    // TC-04: scope=team getMonthly with empty allowedUserIds — same as above, covered by controller.

    // TC-06/TC-08: userId in response is numeric, userName is display name (service uses JOIN app_user; schema has a.id)
    @Test
    void getUsers_withOneUser_returnsUserIdNumericAndUserName() throws Exception {
        DataSource ds = createH2WithAppUserAndLog();
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        List<Map<String, Object>> result = svc.getUsers(null, List.of("u1"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("userId")).isInstanceOf(Long.class);
        assertThat(result.get(0).get("userId")).isEqualTo(100L);
        assertThat(result.get(0).get("userName")).isEqualTo("User One");
    }

    /** getAllUserStatistics with empty logType uses getAllUserStatisticsAsSumOfLogTypes; response must have numeric userId (no ClassCastException). */
    @Test
    void getAllUserStatistics_emptyLogType_returnsNumericUserId() throws Exception {
        DataSource ds = createH2WithAppUserAndLog();
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        List<Map<String, Object>> result = svc.getAllUserStatistics(
                null, null, "", null, null, null, null, null);
        assertThat(result).isNotEmpty();
        Map<String, Object> first = result.get(0);
        assertThat(first.get("userId")).isInstanceOf(Long.class);
        assertThat(first.get("userId")).isEqualTo(100L);
        assertThat(first.get("userName")).isEqualTo("User One");
    }

    private static DataSource createH2WithAppUserAndLog() throws Exception {
        Class.forName("org.h2.Driver");
        String dbName = "stat_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        try (Connection conn = java.sql.DriverManager.getConnection(url);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS app_user (id BIGINT PRIMARY KEY, username VARCHAR(100), name VARCHAR(200))");
            st.execute("CREATE TABLE IF NOT EXISTS user_activity_log (id BIGSERIAL PRIMARY KEY, user_id VARCHAR(100), username VARCHAR(100), action_type VARCHAR(50), action_detail TEXT, created_at TIMESTAMP)");
            st.execute("INSERT INTO app_user (id, username, name) VALUES (100, 'u1', 'User One')");
            st.execute("INSERT INTO user_activity_log (user_id, username, action_type, created_at) VALUES ('u1', 'u1', 'LOGIN', CURRENT_TIMESTAMP)");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(url);
        return ds;
    }
}
