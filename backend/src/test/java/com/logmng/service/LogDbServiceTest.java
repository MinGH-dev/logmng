package com.logmng.service;

import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.request.LogDbSortSpec;
import com.logmng.dto.response.LogDbSearchResponse;
import com.logmng.exception.CustomException;
import com.logmng.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for LogDbService (image log search data/header/keyword filters and pb_feplog smoke).
 * Per req 20260318-image-log-search-data-header-keyword-fix: TC-01–TC-04, TC-07.
 */
class LogDbServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:logdb_service_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DataSource dataSource;
    private LogDbService logDbService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        clearTables();
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);
        logDbService = new LogDbService(dataSource, dataSource, cryptoUtil);
    }

    private void clearTables() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE imagelog");
            stmt.execute("TRUNCATE TABLE pb_send");
            stmt.execute("TRUNCATE TABLE pb_recv");
        }
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS imagelog (" +
                    "application VARCHAR(256), servicegroup VARCHAR(256), service VARCHAR(256), status VARCHAR(256)," +
                    "data TEXT, datastring TEXT, guid VARCHAR(256), header TEXT, headerstring TEXT, insert_time BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS pb_send (" +
                    "id BIGINT PRIMARY KEY, log_timestamp TIMESTAMP, media_code VARCHAR(50), tr_code VARCHAR(50)," +
                    "user_id VARCHAR(100), ip_address VARCHAR(50), user_agent VARCHAR(500), request_data CLOB, response_data CLOB," +
                    "status_code INT, response_time BIGINT, error_message CLOB, session_id VARCHAR(200), device_type VARCHAR(50)," +
                    "created_at TIMESTAMP, updated_at TIMESTAMP)");
            stmt.execute("CREATE TABLE IF NOT EXISTS pb_recv (" +
                    "id BIGINT PRIMARY KEY, log_timestamp TIMESTAMP, media_code VARCHAR(50), tr_code VARCHAR(50)," +
                    "user_id VARCHAR(100), ip_address VARCHAR(50), user_agent VARCHAR(500), request_data CLOB, response_data CLOB," +
                    "status_code INT, response_time BIGINT, error_message CLOB, session_id VARCHAR(200), device_type VARCHAR(50)," +
                    "created_at TIMESTAMP, updated_at TIMESTAMP)");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
    }

    private long insertImageLog(String application, String servicegroup, String service, String status,
                               String datastring, String headerstring, long insertTime) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(
                    "INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time) " +
                            "VALUES ('" + application + "','" + servicegroup + "','" + service + "','" + status + "','{}','" +
                            (datastring != null ? datastring.replace("'", "''") : "") + "','guid-" + insertTime + "','{}','" +
                            (headerstring != null ? headerstring.replace("'", "''") : "") + "'," + insertTime + ")");
        }
    }

    private void insertPbSend(long id, java.sql.Timestamp logTimestamp) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO pb_send (id, log_timestamp, media_code, tr_code, user_id) " +
                    "VALUES (" + id + ",'" + logTimestamp.toString() + "','M1','TR1','u1')");
        }
    }

    private LogDbSearchRequest imageLogRequest(long startTs, long endTs) {
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTs), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTs), ZoneId.systemDefault());
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("java_fw_imglog");
        req.setStartDate(start.format(FMT));
        req.setEndDate(end.format(FMT));
        req.setPage(1);
        req.setPageSize(10);
        return req;
    }

    /** TC-01: datastring-only search returns only rows where datastring contains the term; total count matches filtered count. */
    @Test
    void searchJavaFwImglog_datastringOnly_returnsMatchingRowsAndCorrectCount() throws Exception {
        long ts = System.currentTimeMillis();
        insertImageLog("A", "B", "C", "ok", "plain needle1 here", "h1", ts);
        insertImageLog("A", "B", "C", "ok", "other text", "h2", ts + 1);

        LogDbSearchRequest req = imageLogRequest(ts - 86400000, ts + 86400000);
        req.setDatastring("needle1");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertThat((String) res.getData().get(0).get("datastring")).contains("needle1");
        assertThat(res.getPagination()).isNotNull();
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
    }

    /** TC-02: headerstring-only search returns only rows where headerstring contains the term. */
    @Test
    void searchJavaFwImglog_headerstringOnly_returnsMatchingRowsAndCorrectCount() throws Exception {
        long ts = System.currentTimeMillis();
        insertImageLog("A", "B", "C", "ok", "d1", "header needle2 value", ts);
        insertImageLog("A", "B", "C", "ok", "d2", "other header", ts + 1);

        LogDbSearchRequest req = imageLogRequest(ts - 86400000, ts + 86400000);
        req.setHeaderstring("needle2");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertThat((String) res.getData().get(0).get("headerstring")).contains("needle2");
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
    }

    /** TC-03: keywords (OR) search returns rows matching at least one keyword in datastring or headerstring. */
    @Test
    void searchJavaFwImglog_keywordsOnly_returnsRowsMatchingAnyKeyword() throws Exception {
        long ts = System.currentTimeMillis();
        insertImageLog("A", "B", "C", "ok", "data with kw1 inside", "h1", ts);
        insertImageLog("A", "B", "C", "ok", "no match", "header has kw2", ts + 1);
        insertImageLog("A", "B", "C", "ok", "x", "y", ts + 2);

        LogDbSearchRequest req = imageLogRequest(ts - 86400000, ts + 86400000);
        req.setKeywords(List.of("kw1", "kw2"));

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(2);
        long total = res.getPagination() != null ? res.getPagination().getTotalCount() : 0;
        assertThat(total).isEqualTo(2L);
    }

    /** TC-04: empty/null datastring, headerstring, keywords — no in-memory filter applied; no NPE. */
    @Test
    void searchJavaFwImglog_emptyOrNullFilters_noNpeAndReturnsAllRowsInRange() throws Exception {
        long ts = System.currentTimeMillis();
        insertImageLog("A", "B", "C", "ok", "d1", "h1", ts);
        insertImageLog("A", "B", "C", "ok", "d2", "h2", ts + 1);

        LogDbSearchRequest req = imageLogRequest(ts - 86400000, ts + 86400000);
        req.setDatastring("");
        req.setHeaderstring(null);
        req.setKeywords(List.of());

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(2);
        assertThat(res.getPagination().getTotalCount()).isEqualTo(2L);
    }

    /** TC-07: pb_feplog search unchanged (no regression from image-log fix). */
    @Test
    void searchPbFeplog_returnsResultsUnchanged() throws Exception {
        long now = System.currentTimeMillis();
        java.sql.Timestamp logTs = new java.sql.Timestamp(now);
        insertPbSend(1L, logTs);

        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("pb_feplog");
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(now - 3600000), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(now + 3600000), ZoneId.systemDefault());
        req.setStartDate(start.format(FMT));
        req.setEndDate(end.format(FMT));
        req.setPage(1);
        req.setPageSize(10);

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).isNotEmpty();
        assertThat(res.getPagination()).isNotNull();
        List<Map<String, Object>> data = res.getData();
        assertThat(data.get(0)).containsKey("log_type");
    }

    @Test
    void buildPbFeplogOrderBy_multiColumn_usesSortSpecs() {
        LogDbSearchRequest req = new LogDbSearchRequest();
        LogDbSortSpec a = new LogDbSortSpec();
        a.setField("log_timestamp");
        a.setDirection("desc");
        LogDbSortSpec b = new LogDbSortSpec();
        b.setField("tr_code");
        b.setDirection("asc");
        req.setSortSpecs(List.of(a, b));
        assertThat(logDbService.buildPbFeplogOrderBy(req)).isEqualTo("log_timestamp DESC, tr_code ASC");
    }

    @Test
    void buildPbFeplogOrderBy_skipsUnknownColumns() {
        LogDbSearchRequest req = new LogDbSearchRequest();
        LogDbSortSpec a = new LogDbSortSpec();
        a.setField("drop table");
        a.setDirection("desc");
        LogDbSortSpec b = new LogDbSortSpec();
        b.setField("tr_code");
        b.setDirection("asc");
        req.setSortSpecs(List.of(a, b));
        assertThat(logDbService.buildPbFeplogOrderBy(req)).isEqualTo("tr_code ASC");
    }

    @Test
    void searchPbFepLogWireframe_mapsWireframeKeys_sendBranch() throws Exception {
        long now = System.currentTimeMillis();
        java.sql.Timestamp logTs = new java.sql.Timestamp(now);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO pb_send (id, log_timestamp, tr_code, user_id, ip_address, request_data, response_data, "
                    + "status_code, error_message, session_id, device_type) VALUES (101,'" + logTs + "','TRX','userA','10.0.0.1',"
                    + "'reqBody','resBody',42,'err-hint','sess-1','WEB')");
        }

        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setStartDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(now - 3600000), ZoneId.systemDefault()).format(FMT));
        req.setEndDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(now + 3600000), ZoneId.systemDefault()).format(FMT));
        req.setLoginId("userA");
        req.setPage(1);
        req.setPageSize(25);

        LogDbSearchResponse res = logDbService.searchPbFepLogWireframe(req);

        assertThat(res.getData()).hasSize(1);
        Map<String, Object> row = res.getData().get(0);
        assertThat(row).containsKeys("id", "log_type", "log_timestamp", "tr_code", "login_id", "msg_code", "bmsg", "log_ch_cd",
                "send_recv", "src_ip", "dest_ip", "app_id", "data", "request_data", "response_data");
        assertThat(row.get("login_id")).isEqualTo("userA");
        assertThat(row.get("send_recv")).isEqualTo("SEND");
        assertThat(row.get("dest_ip")).isEqualTo("");
        assertThat(row.get("app_id")).isEqualTo("sess-1");
        assertThat(row.get("msg_code")).isEqualTo("42");
        assertThat(row.get("bmsg")).isEqualTo("err-hint");
        assertThat(row.get("log_ch_cd")).isEqualTo("WEB");
        assertThat(row.get("src_ip")).isEqualTo("10.0.0.1");
        assertThat(row.get("request_data")).isEqualTo("reqBody");
        assertThat(row.get("response_data")).isEqualTo("resBody");
        assertThat(row.get("log_type")).isEqualTo("send");
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
    }

    @Test
    void searchPbFepLogWireframe_mapsRecvBranch() throws Exception {
        long now = System.currentTimeMillis();
        java.sql.Timestamp logTs = new java.sql.Timestamp(now);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO pb_recv (id, log_timestamp, tr_code, user_id) VALUES (202,'" + logTs + "','TRY','userB')");
        }

        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setStartDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(now - 3600000), ZoneId.systemDefault()).format(FMT));
        req.setEndDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(now + 3600000), ZoneId.systemDefault()).format(FMT));
        req.setLoginId("userB");
        req.setPageSize(25);

        Map<String, Object> row = logDbService.searchPbFepLogWireframe(req).getData().get(0);
        assertThat(row.get("send_recv")).isEqualTo("RECV");
        assertThat(row.get("log_type")).isEqualTo("recv");
    }

    @Test
    void searchPbFepLogWireframe_invalidPageSize_throws() {
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setStartDate("2025-01-01 00:00:00");
        req.setEndDate("2025-01-02 00:00:00");
        req.setLoginId("u");
        req.setPageSize(99);
        assertThatThrownBy(() -> logDbService.searchPbFepLogWireframe(req))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void searchPbFepLogWireframe_unknownSortField_throws() {
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setStartDate("2025-01-01 00:00:00");
        req.setEndDate("2025-01-02 00:00:00");
        req.setLoginId("u");
        LogDbSortSpec s = new LogDbSortSpec();
        s.setField("illegal_column");
        s.setDirection("desc");
        req.setSortSpecs(List.of(s));
        assertThatThrownBy(() -> logDbService.searchPbFepLogWireframe(req))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void searchPbFepLogWireframe_sortByLoginIdAlias_buildsOrder() throws Exception {
        LogDbSortSpec s = new LogDbSortSpec();
        s.setField("login_id");
        s.setDirection("asc");
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setSortSpecs(List.of(s));
        assertThat(logDbService.buildPbFeplogOrderBy(req)).isEqualTo("user_id ASC");
    }

    /**
     * Wrong key: bracket-wrapped ImageLog ciphertext must not appear in output as if decrypted (no E002 echo).
     */
    @Test
    void decryptJsonStringValues_wrongKey_doesNotExposeE002Ciphertext() {
        CryptoUtil encryptWithDevDefault = new CryptoUtil();
        ReflectionTestUtils.setField(encryptWithDevDefault, "encryptionKey", "12345678901234567890123456789012");
        ReflectionTestUtils.setField(encryptWithDevDefault, "decryptionEnabled", true);
        ReflectionTestUtils.setField(encryptWithDevDefault, "failureHandling", "fallback");
        String encPayload = encryptWithDevDefault.encryptImageLogPayload("secret-value");
        String json = String.format("{\"p\":\"[%s]\"}", encPayload);

        CryptoUtil wrongKey = new CryptoUtil();
        ReflectionTestUtils.setField(wrongKey, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(wrongKey, "decryptionEnabled", true);
        ReflectionTestUtils.setField(wrongKey, "failureHandling", "fallback");
        logDbService = new LogDbService(dataSource, dataSource, wrongKey);

        String out = (String) ReflectionTestUtils.invokeMethod(logDbService, "decryptJsonStringValues", json);
        assertThat(out).doesNotContain("E002");
        assertThat(out).contains("복호화에 실패했습니다");
    }
}
