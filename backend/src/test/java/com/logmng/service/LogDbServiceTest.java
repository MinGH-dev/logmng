package com.logmng.service;

import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.request.LogDbSortSpec;
import com.logmng.dto.response.LogDbSearchResponse;
import com.logmng.exception.CustomException;
import com.logmng.testsupport.H2ClasspathSql;
import com.logmng.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
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
 * PB FEP / imagelog DML lives in classpath SQL under {@code sql/logdb-service/} (not in Java literals).
 */
class LogDbServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:logdb_service_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Fixed epoch ms for imagelog filter windows (stable across zones for test SQL + request range). */
    private static final long ILOG_BASE_MS = 1_730_000_000_000L;
    private static final LocalDateTime PB_SMOKE_LDT = LocalDateTime.of(2025, 6, 15, 12, 0);
    private static final LocalDateTime WIRE_LDT = LocalDateTime.of(2025, 6, 15, 15, 0);

    private DataSource dataSource;
    private LogDbService logDbService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        H2ClasspathSql.runScript(dataSource, "/sql/logdb-service/truncate-all.sql");
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);
        logDbService = new LogDbService(dataSource, dataSource, dataSource, cryptoUtil);
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL)) {
            H2ClasspathSql.runScript(conn, "/sql/logdb-service/h2-schema.sql");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
    }

    private void insertImageLog(String application, String servicegroup, String service, String status,
                                String datastring, String headerstring, long insertTime) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = H2ClasspathSql.prepareFromResource(conn, "/sql/logdb-service/insert-imagelog.sql")) {
            ps.setString(1, application);
            ps.setString(2, servicegroup);
            ps.setString(3, service);
            ps.setString(4, status);
            ps.setString(5, datastring != null ? datastring : "");
            ps.setString(6, "guid-" + insertTime);
            ps.setString(7, headerstring != null ? headerstring : "");
            ps.setLong(8, insertTime);
            ps.executeUpdate();
        }
    }

    private void insertPbSend(long id, Timestamp logTimestamp) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = H2ClasspathSql.prepareFromResource(conn, "/sql/logdb-service/insert-pb-send-minimal.sql")) {
            ps.setLong(1, id);
            ps.setTimestamp(2, logTimestamp);
            ps.executeUpdate();
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

    private LogDbSearchRequest imageLogRequestAroundBase() {
        return imageLogRequest(ILOG_BASE_MS - 86_400_000L, ILOG_BASE_MS + 86_400_000L);
    }

    /** TC-01: datastring-only search returns only rows where datastring contains the term; total count matches filtered count. */
    @Test
    void searchJavaFwImglog_datastringOnly_returnsMatchingRowsAndCorrectCount() throws Exception {
        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", "plain needle1 here", "h1", ts);
        insertImageLog("A", "B", "C", "ok", "other text", "h2", ts + 1);

        LogDbSearchRequest req = imageLogRequestAroundBase();
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
        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", "d1", "header needle2 value", ts);
        insertImageLog("A", "B", "C", "ok", "d2", "other header", ts + 1);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setHeaderstring("needle2");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertThat((String) res.getData().get(0).get("headerstring")).contains("needle2");
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
    }

    /** TC-03: keywords (OR) search returns rows matching at least one keyword in datastring or headerstring. */
    @Test
    void searchJavaFwImglog_keywordsOnly_returnsRowsMatchingAnyKeyword() throws Exception {
        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", "data with kw1 inside", "h1", ts);
        insertImageLog("A", "B", "C", "ok", "no match", "header has kw2", ts + 1);
        insertImageLog("A", "B", "C", "ok", "x", "y", ts + 2);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setKeywords(List.of("kw1", "kw2"));

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(2);
        long total = res.getPagination() != null ? res.getPagination().getTotalCount() : 0;
        assertThat(total).isEqualTo(2L);
    }

    /** TC-04: empty/null datastring, headerstring, keywords — no in-memory filter applied; no NPE. */
    @Test
    void searchJavaFwImglog_emptyOrNullFilters_noNpeAndReturnsAllRowsInRange() throws Exception {
        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", "d1", "h1", ts);
        insertImageLog("A", "B", "C", "ok", "d2", "h2", ts + 1);

        LogDbSearchRequest req = imageLogRequestAroundBase();
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
        Timestamp logTs = Timestamp.valueOf(PB_SMOKE_LDT);
        insertPbSend(1L, logTs);

        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("pb_feplog");
        req.setStartDate(PB_SMOKE_LDT.toLocalDate().atStartOfDay().format(FMT));
        req.setEndDate(PB_SMOKE_LDT.toLocalDate().plusDays(1).atStartOfDay().minusSeconds(1).format(FMT));
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
        Timestamp logTs = Timestamp.valueOf(WIRE_LDT);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = H2ClasspathSql.prepareFromResource(conn, "/sql/logdb-service/insert-pb-send-wireframe.sql")) {
            ps.setLong(1, 101);
            ps.setTimestamp(2, logTs);
            ps.executeUpdate();
        }

        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setStartDate(WIRE_LDT.toLocalDate().atStartOfDay().format(FMT));
        req.setEndDate(WIRE_LDT.toLocalDate().plusDays(1).atStartOfDay().minusSeconds(1).format(FMT));
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
        Timestamp logTs = Timestamp.valueOf(WIRE_LDT);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = H2ClasspathSql.prepareFromResource(conn, "/sql/logdb-service/insert-pb-recv-wireframe.sql")) {
            ps.setLong(1, 202);
            ps.setTimestamp(2, logTs);
            ps.executeUpdate();
        }

        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setStartDate(WIRE_LDT.toLocalDate().atStartOfDay().format(FMT));
        req.setEndDate(WIRE_LDT.toLocalDate().plusDays(1).atStartOfDay().minusSeconds(1).format(FMT));
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
        logDbService = new LogDbService(dataSource, dataSource, dataSource, wrongKey);

        String out = (String) ReflectionTestUtils.invokeMethod(logDbService, "decryptJsonStringValues", json);
        assertThat(out).doesNotContain("E002");
        assertThat(out).contains("복호화에 실패했습니다");
    }
}
