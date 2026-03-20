package com.logmng.service;

import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.response.LogDbSearchResponse;
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
}
