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
import java.nio.charset.StandardCharsets;
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
 * Per req 20260318-image-log-search-data-header-keyword-fix: TC-01–TC-04, TC-07; java_fw_imglog field terms AND, keyword terms OR.
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
        insertImageLogWithGuid(application, servicegroup, service, status, datastring, "guid-" + insertTime,
                headerstring, insertTime);
    }

    /** Same as {@link #insertImageLog} but sets {@code guid} explicitly (e.g. seed-aligned regression rows). */
    private void insertImageLogWithGuid(String application, String servicegroup, String service, String status,
                                        String datastring, String guid, String headerstring, long insertTime) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = H2ClasspathSql.prepareFromResource(conn, "/sql/logdb-service/insert-imagelog.sql")) {
            ps.setString(1, application);
            ps.setString(2, servicegroup);
            ps.setString(3, service);
            ps.setString(4, status);
            ps.setString(5, datastring != null ? datastring : "");
            ps.setString(6, guid != null ? guid : "guid-" + insertTime);
            ps.setString(7, headerstring != null ? headerstring : "");
            ps.setLong(8, insertTime);
            ps.executeUpdate();
        }
    }

    /** Sets {@code data} and {@code header} columns (not only {@code datastring}/{@code headerstring}). */
    private void insertImageLogFull(String application, String servicegroup, String service, String status,
            String dataCol, String datastring, String guid, String headerCol, String headerstring, long insertTime)
            throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = H2ClasspathSql.prepareFromResource(conn, "/sql/logdb-service/insert-imagelog-full.sql")) {
            ps.setString(1, application);
            ps.setString(2, servicegroup);
            ps.setString(3, service);
            ps.setString(4, status);
            ps.setString(5, dataCol != null ? dataCol : "");
            ps.setString(6, datastring != null ? datastring : "");
            ps.setString(7, guid != null ? guid : "guid-" + insertTime);
            ps.setString(8, headerCol != null ? headerCol : "");
            ps.setString(9, headerstring != null ? headerstring : "");
            ps.setLong(10, insertTime);
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

    /**
     * TC-03: multiple keywords are OR — row matches if any keyword matches (data/header plaintext or bracket decrypt).
     * Row with only t1 matches keyword t1; row with neither is excluded.
     */
    @Test
    void searchJavaFwImglog_keywordsMultipleTerms_orSemanticsAnyKeyword() throws Exception {
        long ts = ILOG_BASE_MS;
        // Distinct tokens (avoid accidental substring match, e.g. "no kw2" contains "kw2").
        String t1 = "TERM_K1X";
        String t2 = "TERM_K2Y";
        insertImageLog("A", "B", "C", "ok", "both " + t1 + " and " + t2 + " here", "h1", ts);
        insertImageLog("A", "B", "C", "ok", "has " + t1 + " in data", "header carries " + t2 + " value", ts + 1);
        insertImageLog("A", "B", "C", "ok", "only " + t1 + " alone", "no second token", ts + 2);
        insertImageLog("A", "B", "C", "ok", "x", "y", ts + 3);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setKeywords(List.of(t1, t2));

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(3);
        long total = res.getPagination() != null ? res.getPagination().getTotalCount() : 0;
        assertThat(total).isEqualTo(3L);
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

    /**
     * TC-01 (req 20260413 §3): datastring matches only after bracket JSON decrypt; search still returns row with no
     * {@code decrypted_*} or {@code _*} keys; {@code hasEncryptedMatchDatastring} is true for UI bracket highlight.
     */
    @Test
    void searchJavaFwImglog_bracketDecryptMatch_hasNoDecryptedOrInternalKeys() throws Exception {
        CryptoUtil enc = new CryptoUtil();
        ReflectionTestUtils.setField(enc, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(enc, "decryptionEnabled", true);
        String secretPlain = "MATCH_BRACKET_" + ILOG_BASE_MS;
        String encPayload = enc.encryptImageLogPayload(secretPlain);
        String datastring = String.format("{\"field\":\"[%s]\"}", encPayload);

        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", datastring, "h1", ts);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring(secretPlain);

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        Map<String, Object> row = res.getData().get(0);
        assertJavaFwImglogSearchRowHasNoForbiddenKeys(row);
        assertThat(row).containsEntry("hasEncryptedMatchDatastring", true);
    }

    /**
     * Regression (seed LOCAL-DECRYPT-TST-IM-0001 / {@link com.logmng.util.LocalDecryptSampleSeedGenerator}):
     * keyword "LOCAL" matches plaintext {@code guid} in {@code headerstring}; {@code datastring} mirrors seed (LD1, Korean name, bracket {@code p}) with no substring "LOCAL" in stored JSON.
     */
    @Test
    void searchJavaFwImglog_keywordLocal_matchesHeaderGuidLikeSeedIm0001() throws Exception {
        insertLocalDecryptRegressionRowIm0001Like();

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setKeywords(List.of("LOCAL"));
        req.setDatastring("");
        req.setHeaderstring("");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
        assertThat(res.getData().get(0).get("guid")).isEqualTo("LOCAL-DECRYPT-TST-IM-0001");
    }

    /**
     * Unified text filter: {@code datastring} "LOCAL" alone becomes one term; it matches via {@code headerstring}
     * plaintext (guid substring) like the keyword path — seed row must not be dropped.
     */
    @Test
    void searchJavaFwImglog_datastringLocal_matchesSeedIm0001ViaHeaderPlaintext() throws Exception {
        insertLocalDecryptRegressionRowIm0001Like();

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring("LOCAL");
        req.setKeywords(List.of());
        req.setHeaderstring("");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
        assertThat(res.getData().get(0).get("guid")).isEqualTo("LOCAL-DECRYPT-TST-IM-0001");
    }

    /** datastring term LOCAL matches header; second term OTHER is absent everywhere → row excluded. */
    @Test
    void searchJavaFwImglog_datastringLocalPlusKeywordOther_noMatchWhenOtherAbsent() throws Exception {
        insertLocalDecryptRegressionRowIm0001Like();

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring("LOCAL");
        req.setKeywords(List.of("OTHER"));
        req.setHeaderstring("");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).isEmpty();
        assertThat(res.getPagination().getTotalCount()).isEqualTo(0L);
    }

    /** Duplicate LOCAL from datastring + headerstring dedupes to one term; still matches seed row once. */
    @Test
    void searchJavaFwImglog_datastringAndHeaderstringLocal_deduped_matchesSeedIm0001() throws Exception {
        insertLocalDecryptRegressionRowIm0001Like();

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring("LOCAL");
        req.setHeaderstring("LOCAL");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
    }

    /** Two field terms both required (datastring + headerstring): termA in data, termB in header on the same row. */
    @Test
    void searchJavaFwImglog_twoFieldTermsAnd_oneInDataOneInHeader_matches() throws Exception {
        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", "termA in datastring", "header has termB value", ts);
        insertImageLog("A", "B", "C", "ok", "only termA", "no b", ts + 1);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring("termA");
        req.setHeaderstring("termB");
        req.setKeywords(List.of());

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
        assertThat((String) res.getData().get(0).get("datastring")).contains("termA");
    }

    /** Field term AND keyword OR: datastring must match "needX"; keywords OR — row has needX and kwAlpha only. */
    @Test
    void searchJavaFwImglog_fieldTermAndKeywordOr_rowMustSatisfyBothGroups() throws Exception {
        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", "prefix needX suffix", "h has kwAlpha", ts);
        insertImageLog("A", "B", "C", "ok", "needX only", "no keyword here", ts + 1);
        // Avoid substring "needX" in datastring (e.g. "no needX" would false-match the field term).
        insertImageLog("A", "B", "C", "ok", "payload without token", "kwAlpha", ts + 2);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring("needX");
        req.setKeywords(List.of("kwBeta", "kwAlpha"));

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
        assertThat((String) res.getData().get(0).get("datastring")).contains("needX");
    }

    private void insertLocalDecryptRegressionRowIm0001Like() throws Exception {
        CryptoUtil enc = new CryptoUtil();
        ReflectionTestUtils.setField(enc, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(enc, "decryptionEnabled", true);
        String datastring = buildImagelogDatastringLikeSeedIm0001Input(enc);
        assertThat(datastring).doesNotContain("LOCAL");
        String headerstring = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"LOCAL-DECRYPT-TST-IM-0001\"}";
        long ts = ILOG_BASE_MS;
        insertImageLogWithGuid("LDP", "EduSG", "SE10002_select", "input", datastring,
                "LOCAL-DECRYPT-TST-IM-0001", headerstring, ts);
    }

    /**
     * Same shape as {@code init-data-local-decrypt-test-imagelog.sql} IM-0001 input row; retry encrypt if ciphertext makes full JSON contain "LOCAL" (would false-trigger datastring-only filter).
     */
    private static String buildImagelogDatastringLikeSeedIm0001Input(CryptoUtil enc) {
        String pPlainBase = "한글복호화검증-필드p-0001";
        for (int attempt = 0; attempt < 200; attempt++) {
            String pPlain = attempt == 0 ? pPlainBase : pPlainBase + "-" + attempt;
            String encPayload = enc.encryptImageLogPayload(pPlain);
            String datastring = String.format("{\"id\":\"LD1\",\"name\":\"시드평문이름\",\"p\":\"[%s]\"}", encPayload);
            if (!datastring.contains("LOCAL")) {
                return datastring;
            }
        }
        throw new IllegalStateException("could not encrypt p without substring LOCAL in JSON");
    }

    /**
     * TC-02 (req 20260413 §3): legacy decryptData + keywords must not attach decrypted_data/decrypted_header on search.
     */
    @Test
    void searchJavaFwImglog_decryptDataTrueWithKeywords_doesNotLeakDecryptedKeys() throws Exception {
        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", "visible kw marker", "h1", ts);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDecryptData(true);
        req.setKeywords(List.of("marker"));

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertJavaFwImglogSearchRowHasNoForbiddenKeys(res.getData().get(0));
    }

    /** Forbids {@code decrypted_*} and keys starting with {@code _}; allows {@code hasEncryptedMatchDatastring} / {@code hasEncryptedMatchHeaderstring}. */
    private static void assertJavaFwImglogSearchRowHasNoForbiddenKeys(Map<String, Object> row) {
        assertThat(row.keySet()).noneMatch(k -> k != null && (k.startsWith("_") || k.startsWith("decrypted_")));
    }

    /**
     * Keyword OR: match via decrypted {@code data} column only — {@code datastring}/{@code headerstring} omit the token;
     * optional {@code hasEncryptedMatchData} for UI parity.
     */
    @Test
    void searchJavaFwImglog_keywordMatchesBinaryDataColumn_plainStringsMiss() throws Exception {
        CryptoUtil enc = new CryptoUtil();
        ReflectionTestUtils.setField(enc, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(enc, "decryptionEnabled", true);
        String token = "BINCOL_KW_" + ILOG_BASE_MS;
        String encPayload = enc.encryptImageLogPayload("prefix " + token + " suffix");
        long ts = ILOG_BASE_MS;
        insertImageLogFull("A", "B", "C", "ok", encPayload, "plain datastring without token", "g-bin-" + ts, "{}", "plain header", ts);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setKeywords(List.of(token));
        req.setDatastring("");
        req.setHeaderstring("");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertJavaFwImglogSearchRowHasNoForbiddenKeys(res.getData().get(0));
        assertThat(res.getData().get(0)).containsEntry("hasEncryptedMatchData", true);
    }

    /**
     * Field clause must not use binary {@code data}/{@code header}: term appears only inside encrypted payload, not in {@code datastring}.
     */
    @Test
    void searchJavaFwImglog_fieldOnly_doesNotMatchKeywordInsideBinaryDataColumn() throws Exception {
        CryptoUtil enc = new CryptoUtil();
        ReflectionTestUtils.setField(enc, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(enc, "decryptionEnabled", true);
        String token = "FIELD_SKIP_BIN_" + ILOG_BASE_MS;
        String encPayload = enc.encryptImageLogPayload("only inside cipher " + token);
        long ts = ILOG_BASE_MS;
        insertImageLogFull("A", "B", "C", "ok", encPayload, "visible plain ds", "g-fskip-" + ts, "{}", "visible plain hs", ts);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring(token);
        req.setHeaderstring("");
        req.setKeywords(List.of());

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).isEmpty();
        assertThat(res.getPagination().getTotalCount()).isEqualTo(0L);
    }

    /** Keyword binary path: decrypt throws → no match from that column (garbage ciphertext). */
    @Test
    void searchJavaFwImglog_keywordBinaryDecryptFailure_noMatch() throws Exception {
        long ts = ILOG_BASE_MS;
        String kw = "KW_FAIL_DECRYPT_" + ts;
        insertImageLogFull("A", "B", "C", "ok", "not-a-valid-image-log-payload-!!!", "plain ds without token", "g-bad-" + ts, "{}", "plain hs", ts);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setKeywords(List.of(kw));
        req.setDatastring("");
        req.setHeaderstring("");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).isEmpty();
    }

    @Test
    void coerceImagelogBinaryColumn_byteArrayUtf8_preservesCiphertextForDecrypt() {
        String cipher = "E002-test-bytes";
        byte[] raw = cipher.getBytes(StandardCharsets.UTF_8);
        String coerced = (String) ReflectionTestUtils.invokeMethod(logDbService, "coerceImagelogBinaryColumnToDecryptString", raw);
        assertThat(coerced).isEqualTo(cipher);
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
