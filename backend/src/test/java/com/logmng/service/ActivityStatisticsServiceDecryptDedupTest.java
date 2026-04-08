package com.logmng.service;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Req docs/requirements/20260408-activity-statistics-decrypt-unique-rows-per-day.md TC-01–TC-08.
 */
class ActivityStatisticsServiceDecryptDedupTest {

    private static final String DETAIL_POST = "{\"requestParams\":{\"logType\":\"java_fw_imglog\","
            + "\"request\":{\"guid\":\"G1\",\"status\":\"S1\"}}}";

    /** Legacy-shaped {@code logType} scalar (extra quote chars after JSON parse). */
    private static final String DETAIL_LEGACY_QUOTED_LOGTYPE = "{\"requestParams\":{\"logType\":\"\\\"java_fw_imglog\\\"\","
            + "\"request\":{\"guid\":\"G1\",\"status\":\"S1\"}}}";

    private static DataSource createH2() throws Exception {
        Class.forName("org.h2.Driver");
        String dbName = "statdec_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        try (Connection conn = java.sql.DriverManager.getConnection(url);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS app_user (id BIGINT PRIMARY KEY, username VARCHAR(100), name VARCHAR(200))");
            st.execute("CREATE TABLE IF NOT EXISTS user_activity_log (id BIGSERIAL PRIMARY KEY, user_id VARCHAR(100), username VARCHAR(100), "
                    + "action_type VARCHAR(50), action_detail TEXT, created_at TIMESTAMP)");
            st.execute("INSERT INTO app_user (id, username, name) VALUES (1, 'alice', 'Alice'), (2, 'bob', 'Bob')");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(url);
        return ds;
    }

    private static void insertDecrypt(Connection conn, String user, String detail, LocalDate day) throws Exception {
        var ps = conn.prepareStatement(
                "INSERT INTO user_activity_log (user_id, username, action_type, action_detail, created_at) VALUES (?,?,?,?,?)");
        ps.setString(1, user);
        ps.setString(2, user);
        ps.setString(3, "DECRYPT");
        ps.setString(4, detail);
        ps.setTimestamp(5, java.sql.Timestamp.valueOf(day.atStartOfDay()));
        ps.executeUpdate();
        ps.close();
    }

    private static void insertApproval(Connection conn, String user, String actionType, LocalDate day) throws Exception {
        var ps = conn.prepareStatement(
                "INSERT INTO user_activity_log (user_id, username, action_type, action_detail, created_at) VALUES (?,?,?,?,?)");
        ps.setString(1, user);
        ps.setString(2, user);
        ps.setString(3, actionType);
        ps.setString(4, "{\"requestParams\":{\"logType\":\"java_fw_imglog\"}}");
        ps.setTimestamp(5, java.sql.Timestamp.valueOf(day.atStartOfDay()));
        ps.executeUpdate();
        ps.close();
    }

    @Test
    void TC01_threeSameKeySameDay_countsOne() throws Exception {
        DataSource ds = createH2();
        LocalDate d = LocalDate.of(2026, 4, 8);
        try (Connection c = ds.getConnection()) {
            insertDecrypt(c, "alice", DETAIL_POST, d);
            insertDecrypt(c, "alice", DETAIL_POST, d);
            insertDecrypt(c, "alice", DETAIL_POST, d);
        }
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        Map<String, Object> daily = svc.getDailyStatistics("2026-04-08", "2026-04-08", "java_fw_imglog", null, null, null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stats = (List<Map<String, Object>>) daily.get("dailyStats");
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).get("totalDecrypts")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> sum = (Map<String, Object>) daily.get("summary");
        assertThat(sum.get("totalDecrypts")).isEqualTo(1L);
    }

    @Test
    void TC02_sameKeyTwoDays_onePerDay() throws Exception {
        DataSource ds = createH2();
        try (Connection c = ds.getConnection()) {
            insertDecrypt(c, "alice", DETAIL_POST, LocalDate.of(2026, 4, 1));
            insertDecrypt(c, "alice", DETAIL_POST, LocalDate.of(2026, 4, 2));
        }
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        Map<String, Object> daily = svc.getDailyStatistics("2026-04-01", "2026-04-02", "java_fw_imglog", null, null, null, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> sum = (Map<String, Object>) daily.get("summary");
        assertThat(sum.get("totalDecrypts")).isEqualTo(2L);
    }

    @Test
    void TC03_sameGuidDifferentStatus_twoInOneDay() throws Exception {
        String d2 = "{\"requestParams\":{\"logType\":\"java_fw_imglog\",\"request\":{\"guid\":\"G1\",\"status\":\"S2\"}}}";
        DataSource ds = createH2();
        LocalDate d = LocalDate.of(2026, 4, 8);
        try (Connection c = ds.getConnection()) {
            insertDecrypt(c, "alice", DETAIL_POST, d);
            insertDecrypt(c, "alice", d2, d);
        }
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        Map<String, Object> daily = svc.getDailyStatistics("2026-04-08", "2026-04-08", "java_fw_imglog", null, null, null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stats = (List<Map<String, Object>>) daily.get("dailyStats");
        assertThat(stats.get(0).get("totalDecrypts")).isEqualTo(2);
    }

    @Test
    void TC04_missingFields_excluded() throws Exception {
        DataSource ds = createH2();
        LocalDate d = LocalDate.of(2026, 4, 8);
        try (Connection c = ds.getConnection()) {
            insertDecrypt(c, "alice", "{\"requestParams\":{\"logType\":\"java_fw_imglog\"}}", d);
            insertDecrypt(c, "alice", DETAIL_POST, d);
        }
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        Map<String, Object> daily = svc.getDailyStatistics("2026-04-08", "2026-04-08", "java_fw_imglog", null, null, null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stats = (List<Map<String, Object>>) daily.get("dailyStats");
        assertThat(stats.get(0).get("totalDecrypts")).isEqualTo(1);
    }

    @Test
    void TC05_approvalTypes_notInKpi() throws Exception {
        DataSource ds = createH2();
        LocalDate d = LocalDate.of(2026, 4, 8);
        try (Connection c = ds.getConnection()) {
            insertApproval(c, "alice", "DECRYPT_APPROVAL_APPROVE", d);
            insertApproval(c, "alice", "DECRYPT_APPROVAL_REJECT", d);
            insertDecrypt(c, "alice", DETAIL_POST, d);
        }
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        Map<String, Object> daily = svc.getDailyStatistics("2026-04-08", "2026-04-08", "java_fw_imglog", null, null, null, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> sum = (Map<String, Object>) daily.get("summary");
        assertThat(sum.get("totalDecrypts")).isEqualTo(1L);
    }

    @Test
    void TC06_logTypeFilter_onlyMatchingLogType() throws Exception {
        String otherLt = "{\"requestParams\":{\"logType\":\"java_fw_imglog\",\"request\":{\"guid\":\"Gx\",\"status\":\"Sx\"}}}";
        DataSource ds = createH2();
        LocalDate d = LocalDate.of(2026, 4, 8);
        try (Connection c = ds.getConnection()) {
            insertDecrypt(c, "alice", otherLt, d);
            insertDecrypt(c, "alice", "{\"requestParams\":{\"logType\":\"other\",\"request\":{\"guid\":\"X\",\"status\":\"Y\"}}}", d);
        }
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        Map<String, Object> daily = svc.getDailyStatistics("2026-04-08", "2026-04-08", "java_fw_imglog", null, null, null, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> sum = (Map<String, Object>) daily.get("summary");
        assertThat(sum.get("totalDecrypts")).isEqualTo(1L);
    }

    @Test
    void TC07_emptyLogType_sumsPerTypeWithDedup() throws Exception {
        DataSource ds = createH2();
        LocalDate d = LocalDate.of(2026, 4, 8);
        try (Connection c = ds.getConnection()) {
            insertDecrypt(c, "alice", DETAIL_POST, d);
            insertDecrypt(c, "alice", DETAIL_POST, d);
        }
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        Map<String, Object> daily = svc.getDailyStatistics("2026-04-08", "2026-04-08", "", null, null, null, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> sum = (Map<String, Object>) daily.get("summary");
        assertThat(sum.get("totalDecrypts")).isEqualTo(1L);
    }

    @Test
    void TC08_twoUsersSameRowSameDay_dailyOne_eachUserOne() throws Exception {
        DataSource ds = createH2();
        LocalDate d = LocalDate.of(2026, 4, 8);
        try (Connection c = ds.getConnection()) {
            insertDecrypt(c, "alice", DETAIL_POST, d);
            insertDecrypt(c, "bob", DETAIL_POST, d);
        }
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        Map<String, Object> daily = svc.getDailyStatistics("2026-04-08", "2026-04-08", "java_fw_imglog", null, List.of("alice", "bob"), null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stats = (List<Map<String, Object>>) daily.get("dailyStats");
        assertThat(stats.get(0).get("totalDecrypts")).isEqualTo(1);
        List<Map<String, Object>> users = svc.getAllUserStatistics("2026-04-08", "2026-04-08", "java_fw_imglog", null, List.of("alice", "bob"), null, null, null);
        Map<Long, Long> dc = users.stream().collect(java.util.stream.Collectors.toMap(
                r -> (Long) r.get("userId"), r -> (Long) r.get("decryptCount")));
        assertThat(dc.get(1L)).isEqualTo(1L);
        assertThat(dc.get(2L)).isEqualTo(1L);
    }

    @Test
    void getDecryptPath_flatParams_countsInDailyStats() throws Exception {
        String getShape = "{\"requestParams\":{\"logType\":\"java_fw_imglog\",\"type\":\"guid\",\"identifier\":\"ID1\",\"status\":\"ST\"}}";
        DataSource ds = createH2();
        LocalDate d = LocalDate.of(2026, 4, 8);
        try (Connection c = ds.getConnection()) {
            insertDecrypt(c, "alice", getShape, d);
            insertDecrypt(c, "alice", getShape, d);
        }
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        Map<String, Object> daily = svc.getDailyStatistics("2026-04-08", "2026-04-08", "java_fw_imglog", null, null, null, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> sum = (Map<String, Object>) daily.get("summary");
        assertThat(sum.get("totalDecrypts")).isEqualTo(1L);
    }

    @Test
    void legacyQuotedLogType_inActionDetail_countsInDecryptKpi() throws Exception {
        DataSource ds = createH2();
        LocalDate d = LocalDate.of(2026, 4, 8);
        try (Connection c = ds.getConnection()) {
            insertDecrypt(c, "alice", DETAIL_LEGACY_QUOTED_LOGTYPE, d);
            insertDecrypt(c, "alice", DETAIL_LEGACY_QUOTED_LOGTYPE, d);
        }
        ActivityStatisticsService svc = new ActivityStatisticsService(ds);
        Map<String, Object> daily = svc.getDailyStatistics("2026-04-08", "2026-04-08", "java_fw_imglog", null, null, null, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> sum = (Map<String, Object>) daily.get("summary");
        assertThat(sum.get("totalDecrypts")).isEqualTo(1L);
    }
}
