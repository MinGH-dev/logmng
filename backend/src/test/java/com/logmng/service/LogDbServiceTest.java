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
    /** Isolated window for PB FEP keyword tests (req 20260415). */
    private static final LocalDateTime PB_KW_LDT = LocalDateTime.of(2025, 8, 1, 10, 0);

    private DataSource dataSource;
    private LogDbService logDbService;
    private CryptoUtil cryptoUtil;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        H2ClasspathSql.runScript(dataSource, "/sql/logdb-service/truncate-all.sql");
        cryptoUtil = new CryptoUtil();
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

    /** PB FEP wire {@code log_time}: 20-digit {@code yyyyMMddHHmmss} + microsecond suffix (nanoseconds truncated). */
    private static String toPbLogTime(LocalDateTime ldt) {
        int micros = ldt.getNano() / 1_000;
        return ldt.withNano(0).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format(java.util.Locale.ROOT, "%06d", micros);
    }

    private void insertPbSend(long id, String logTime) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = H2ClasspathSql.prepareFromResource(conn, "/sql/logdb-service/insert-pb-send-minimal.sql")) {
            ps.setLong(1, id);
            ps.setString(2, logTime);
            ps.executeUpdate();
        }
    }

    private void insertPbSendPayload(long id, String logTime, String trCode, String brodid,
            String vlen, String data, String bmsg) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = H2ClasspathSql.prepareFromResource(conn, "/sql/logdb-service/insert-pb-send-payload.sql")) {
            ps.setLong(1, id);
            ps.setString(2, logTime);
            ps.setString(3, trCode);
            ps.setString(4, brodid);
            ps.setString(5, vlen != null ? vlen : "");
            ps.setString(6, data != null ? data : "");
            ps.setString(7, bmsg != null ? bmsg : "");
            ps.executeUpdate();
        }
    }

    private LogDbSearchRequest pbFeplogKeywordRequest() {
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("pb_feplog");
        req.setStartDate(PB_KW_LDT.toLocalDate().atStartOfDay().format(FMT));
        req.setEndDate(PB_KW_LDT.toLocalDate().plusDays(1).atStartOfDay().minusSeconds(1).format(FMT));
        req.setTrCode("TRK");
        req.setLoginId("kwuser");
        req.setPage(1);
        req.setPageSize(10);
        return req;
    }

    /** PB FEP wireframe search window + login (same as keyword PB tests). */
    private LogDbSearchRequest pbFepWireframeSearchRequest() {
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setStartDate(PB_KW_LDT.toLocalDate().atStartOfDay().format(FMT));
        req.setEndDate(PB_KW_LDT.toLocalDate().plusDays(1).atStartOfDay().minusSeconds(1).format(FMT));
        req.setTrCode("TRK");
        req.setLoginId("kwuser");
        req.setPage(1);
        req.setPageSize(25);
        return req;
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

    /** TC-01: datastring-only search excludes header-only matches; total count matches filtered count. */
    @Test
    void searchJavaFwImglog_datastringOnly_returnsMatchingRowsAndCorrectCount() throws Exception {
        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", "plain needle1 here", "h1", ts);
        insertImageLog("A", "B", "C", "ok", "other text", "header needle1 only", ts + 1);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring("needle1");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        assertThat((String) res.getData().get(0).get("datastring")).contains("needle1");
        assertThat(res.getPagination()).isNotNull();
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
    }

    /** TC-02: headerstring-only search excludes data-only matches. */
    @Test
    void searchJavaFwImglog_headerstringOnly_returnsMatchingRowsAndCorrectCount() throws Exception {
        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", "d1", "header needle2 value", ts);
        insertImageLog("A", "B", "C", "ok", "datastring needle2 only", "other header", ts + 1);

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
     * TC-04: headerstring matches after bracket JSON decrypt only; response keeps contract-safe keys and sets
     * hasEncryptedMatchHeaderstring.
     */
    @Test
    void searchJavaFwImglog_headerstringBracketDecryptMatch_hasNoDecryptedOrInternalKeys() throws Exception {
        CryptoUtil enc = new CryptoUtil();
        ReflectionTestUtils.setField(enc, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(enc, "decryptionEnabled", true);
        String secretPlain = "HEADER_MATCH_" + ILOG_BASE_MS;
        String encPayload = enc.encryptImageLogPayload(secretPlain);
        String headerstring = String.format("{\"field\":\"[%s]\"}", encPayload);

        long ts = ILOG_BASE_MS;
        insertImageLog("A", "B", "C", "ok", "plain-data", headerstring, ts);

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setHeaderstring(secretPlain);

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(1);
        Map<String, Object> row = res.getData().get(0);
        assertJavaFwImglogSearchRowHasNoForbiddenKeys(row);
        assertThat(row).containsEntry("hasEncryptedMatchHeaderstring", true);
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

    /** Regression: datastring filter must not match via headerstring plaintext (seed IM-0001 LOCAL only in header guid). */
    @Test
    void searchJavaFwImglog_datastringLocal_doesNotMatchSeedIm0001ViaHeaderPlaintext() throws Exception {
        insertLocalDecryptRegressionRowIm0001Like();

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring("LOCAL");
        req.setKeywords(List.of());
        req.setHeaderstring("");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).isEmpty();
        assertThat(res.getPagination().getTotalCount()).isEqualTo(0L);
    }

    /** Field+keyword combination keeps rule (field AND keyword): keyword hit alone cannot bypass datastring miss. */
    @Test
    void searchJavaFwImglog_datastringLocalPlusKeywordLocal_noMatchBecauseDatastringIsolated() throws Exception {
        insertLocalDecryptRegressionRowIm0001Like();

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring("LOCAL");
        req.setKeywords(List.of("LOCAL"));
        req.setHeaderstring("");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).isEmpty();
        assertThat(res.getPagination().getTotalCount()).isEqualTo(0L);
    }

    /** Both datastring and headerstring filters are AND: header hit cannot satisfy datastring condition. */
    @Test
    void searchJavaFwImglog_datastringAndHeaderstringLocal_noMatchWhenDatastringMissing() throws Exception {
        insertLocalDecryptRegressionRowIm0001Like();

        LogDbSearchRequest req = imageLogRequestAroundBase();
        req.setDatastring("LOCAL");
        req.setHeaderstring("LOCAL");

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).isEmpty();
        assertThat(res.getPagination().getTotalCount()).isEqualTo(0L);
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
        insertPbSend(1L, toPbLogTime(PB_SMOKE_LDT));

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
        assertThat(data.get(0)).containsKey("log_time");
    }

    /** Old /search regression: single-day date-only range must include same-day rows (endDate expands to end-of-day). */
    @Test
    void searchPbFeplog_dateOnlySingleDayRange_returnsNonEmpty() throws Exception {
        insertPbSend(11L, toPbLogTime(PB_SMOKE_LDT));

        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("pb_feplog");
        req.setStartDate("2025-06-15");
        req.setEndDate("2025-06-15");
        req.setPage(1);
        req.setPageSize(10);

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).isNotEmpty();
        assertThat(res.getPagination()).isNotNull();
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
    }

    @Test
    void buildPbFeplogOrderBy_multiColumn_usesSortSpecs() {
        LogDbSearchRequest req = new LogDbSearchRequest();
        LogDbSortSpec a = new LogDbSortSpec();
        a.setField("log_time");
        a.setDirection("desc");
        LogDbSortSpec b = new LogDbSortSpec();
        b.setField("tr_code");
        b.setDirection("asc");
        req.setSortSpecs(List.of(a, b));
        assertThat(logDbService.buildPbFeplogOrderBy(req)).isEqualTo("log_time DESC, tr_code ASC");
    }

    @Test
    void buildPbFeplogOrderBy_legacyPrcTimeAlias_mapsToBackwardCompatibleTimestampKey() {
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setSortField("prc_time");
        req.setSortDirection("asc");
        assertThat(logDbService.buildPbFeplogOrderBy(req)).isEqualTo("log_time ASC");
    }

    @Test
    void buildPbFeplogOrderBy_logTimestampSortField_throwsBadRequest() {
        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setSortField("log_timestamp");
        req.setSortDirection("asc");
        assertThatThrownBy(() -> logDbService.buildPbFeplogOrderBy(req))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("log_timestamp");
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = H2ClasspathSql.prepareFromResource(conn, "/sql/logdb-service/insert-pb-send-wireframe.sql")) {
            ps.setLong(1, 101);
            ps.setString(2, toPbLogTime(WIRE_LDT));
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
        assertThat(row).containsKeys("id", "log_type", "log_time", "tr_code", "login_id", "msg_code", "bmsg", "log_ch_cd",
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = H2ClasspathSql.prepareFromResource(conn, "/sql/logdb-service/insert-pb-recv-wireframe.sql")) {
            ps.setLong(1, 202);
            ps.setString(2, toPbLogTime(WIRE_LDT));
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

    @Test
    void searchPbFeplog_sameDayKnownFormatRange_returnsKnownRows() throws Exception {
        insertPbSend(31L, "20260414010101000000");
        insertPbSend(32L, "20260414235959000000");
        insertPbSend(33L, "20260415000000000000");

        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("pb_feplog");
        req.setStartDate("2026-04-14 00:00:00");
        req.setEndDate("2026-04-14 23:59:59");
        req.setTrCode("TR1");
        req.setLoginId("u1");
        req.setPage(1);
        req.setPageSize(10);

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(2);
        assertThat(res.getPagination().getTotalCount()).isEqualTo(2L);
        assertThat(res.getData())
                .extracting(row -> row.get("log_time"))
                .containsExactlyInAnyOrder("20260414010101000000", "20260414235959000000");
    }

    @Test
    void searchPbFeplog_sameDayIsoMidnightEndDate_expandsToEndOfDay() throws Exception {
        insertPbSend(41L, "20260414010101000000");
        insertPbSend(42L, "20260414235959000000");
        insertPbSend(43L, "20260415000000000000");

        LogDbSearchRequest req = new LogDbSearchRequest();
        req.setLogType("pb_feplog");
        req.setStartDate("2026-04-14T00:00:00.000");
        req.setEndDate("2026-04-14T00:00:00.000");
        req.setTrCode("TR1");
        req.setLoginId("u1");
        req.setPage(1);
        req.setPageSize(10);

        LogDbSearchResponse res = logDbService.searchLogs(req);

        assertThat(res.getData()).hasSize(2);
        assertThat(res.getPagination().getTotalCount()).isEqualTo(2L);
        assertThat(res.getData())
                .extracting(row -> row.get("log_time"))
                .containsExactlyInAnyOrder("20260414010101000000", "20260414235959000000");
    }

    /** TC-04 (req 20260415): nanoseconds truncated to six fractional digits for 20-char lexical {@code log_time}. */
    @Test
    void toPbFeplogLogTimeLexical_truncatesNanosToMicroseconds() {
        LocalDateTime t = LocalDateTime.of(2026, 4, 15, 14, 30, 25, 123_456_789);
        String s = (String) ReflectionTestUtils.invokeMethod(logDbService, "toPbFeplogLogTimeLexical", t);
        assertThat(s).isEqualTo("20260415143025123456");
        assertThat(s).hasSize(20);
    }

    /** TC-01: keywords match plaintext request_data / response_data / error_message (bmsg) only. */
    @Test
    void searchPbFeplog_keywords_plaintextSurfaces_tc01() throws Exception {
        String lt = toPbLogTime(PB_KW_LDT);
        insertPbSendPayload(801L, lt, "TRK", "kwuser", "plain-req-TC01", "", "");
        insertPbSendPayload(802L, lt, "TRK", "kwuser", "", "plain-res-TC01", "");
        insertPbSendPayload(803L, lt, "TRK", "kwuser", "", "", "plain-bmsg-TC01");

        LogDbSearchRequest r1 = pbFeplogKeywordRequest();
        r1.setKeywords(List.of("plain-req-TC01"));
        assertThat(logDbService.searchLogs(r1).getData()).hasSize(1);
        assertThat(logDbService.searchLogs(r1).getData().get(0).get("id")).isEqualTo(801L);

        LogDbSearchRequest r2 = pbFeplogKeywordRequest();
        r2.setKeywords(List.of("plain-res-TC01"));
        assertThat(logDbService.searchLogs(r2).getData()).extracting(row -> row.get("id")).containsExactly(802L);

        LogDbSearchRequest r3 = pbFeplogKeywordRequest();
        r3.setKeywords(List.of("plain-bmsg-TC01"));
        assertThat(logDbService.searchLogs(r3).getData()).extracting(row -> row.get("id")).containsExactly(803L);
    }

    /** TC-02: keyword matches only after PB_FEP decrypt on request_data. */
    @Test
    void searchPbFeplog_keywords_encryptedPayloadDecryptForMatch_tc02() throws Exception {
        String token = "PB-FEP-KW-TEST-20260415-ONLY-INSIDE";
        String cipher = cryptoUtil.encryptPbFepPayload("prefix-" + token + "-suffix");
        assertThat(cipher.toLowerCase(java.util.Locale.ROOT)).doesNotContain(token.toLowerCase(java.util.Locale.ROOT));

        String lt = toPbLogTime(PB_KW_LDT);
        insertPbSendPayload(811L, lt, "TRK", "kwuser", cipher, "", "");

        LogDbSearchRequest req = pbFeplogKeywordRequest();
        req.setKeywords(List.of(token));

        LogDbSearchResponse res = logDbService.searchLogs(req);
        assertThat(res.getData()).hasSize(1);
        assertThat(res.getData().get(0).get("id")).isEqualTo(811L);
        assertThat(res.getPagination().getTotalCount()).isEqualTo(1L);
    }

    /**
     * TC-03: decrypt throws on full column value; keyword still matches plaintext portion (case-insensitive).
     */
    @Test
    void searchPbFeplog_keywords_decryptFails_plaintextPortionStillMatches_tc03() throws Exception {
        String lt = toPbLogTime(PB_KW_LDT);
        String vlen = "VISIBLE_PLAIN_TC03___not-valid-pb-fep-ciphertext-###";
        insertPbSendPayload(821L, lt, "TRK", "kwuser", vlen, "", "");

        LogDbSearchRequest req = pbFeplogKeywordRequest();
        req.setKeywords(List.of("visible_plain_tc03"));

        LogDbSearchResponse res = logDbService.searchLogs(req);
        assertThat(res.getData()).hasSize(1);
        assertThat(res.getData().get(0).get("id")).isEqualTo(821L);
    }

    /** TC-04: multiple keywords — OR across tokens. */
    @Test
    void searchPbFeplog_keywords_multipleTerms_orSemantics_tc04() throws Exception {
        String lt = toPbLogTime(PB_KW_LDT);
        String onlyA = "PB_KW_OR_ONLY_A_991";
        String onlyB = "PB_KW_OR_ONLY_B_992";
        insertPbSendPayload(831L, lt, "TRK", "kwuser", onlyA, "", "");
        insertPbSendPayload(832L, lt, "TRK", "kwuser", "", onlyB, "");
        insertPbSendPayload(833L, lt, "TRK", "kwuser", "no-match-here", "", "");

        LogDbSearchRequest req = pbFeplogKeywordRequest();
        req.setKeywords(List.of(onlyA, onlyB));

        LogDbSearchResponse res = logDbService.searchLogs(req);
        assertThat(res.getData()).hasSize(2);
        assertThat(res.getData()).extracting(row -> row.get("id")).containsExactlyInAnyOrder(831L, 832L);
    }

    /** TC-05: case-insensitive keyword match on wire text. */
    @Test
    void searchPbFeplog_keywords_caseInsensitive_tc05() throws Exception {
        String lt = toPbLogTime(PB_KW_LDT);
        insertPbSendPayload(841L, lt, "TRK", "kwuser", "MixedCaseTokenXyZ", "", "");

        LogDbSearchRequest req = pbFeplogKeywordRequest();
        req.setKeywords(List.of("mixedcasetokenxyz"));

        assertThat(logDbService.searchLogs(req).getData()).hasSize(1);
    }

    /**
     * TC-06: keywords non-empty, decryptData false — row from decrypt-for-match; response rows must not carry decrypted_* keys.
     */
    @Test
    void searchPbFeplog_keywords_decryptDataFalse_noDecryptedKeys_tc06() throws Exception {
        String token = "PB-FEP-KW-TC06-SECRET";
        String cipher = cryptoUtil.encryptPbFepPayload("body-" + token);
        String lt = toPbLogTime(PB_KW_LDT);
        insertPbSendPayload(851L, lt, "TRK", "kwuser", "", cipher, "");

        LogDbSearchRequest req = pbFeplogKeywordRequest();
        req.setKeywords(List.of(token));
        req.setDecryptData(false);

        LogDbSearchResponse res = logDbService.searchLogs(req);
        assertThat(res.getData()).hasSize(1);
        Map<String, Object> row = res.getData().get(0);
        assertThat(row).doesNotContainKey("decrypted_request_data");
        assertThat(row).doesNotContainKey("decrypted_response_data");

        LogDbSearchRequest wfReq = pbFeplogKeywordRequest();
        wfReq.setPageSize(25);
        wfReq.setKeywords(List.of(token));
        wfReq.setDecryptData(false);
        LogDbSearchResponse wire = logDbService.searchPbFepLogWireframe(wfReq);
        assertThat(wire.getData()).hasSize(1);
        assertThat(wire.getData().get(0)).doesNotContainKey("decrypted_request_data");
    }

    /** TC-KF-01: decrypt-only match on response_data — response flags true; keyword_match_data true (aggregate OR). */
    @Test
    void searchPbFepLogWireframe_keywordFlags_decryptOnlyResponse_tcKf01() throws Exception {
        String token = "LOCAL-PB-KF01-DECRYPT-ONLY";
        String cipher = cryptoUtil.encryptPbFepPayload("prefix-" + token + "-suffix");
        assertThat(cipher.toLowerCase(java.util.Locale.ROOT)).doesNotContain(token.toLowerCase(java.util.Locale.ROOT));

        String lt = toPbLogTime(PB_KW_LDT);
        insertPbSendPayload(910L, lt, "TRK", "kwuser", "no-match-req-KF01", cipher, "no-bmsg-match");

        LogDbSearchRequest req = pbFepWireframeSearchRequest();
        req.setKeywords(List.of(token));

        Map<String, Object> row = logDbService.searchPbFepLogWireframe(req).getData().get(0);
        assertThat(row.get("keyword_match_request_data")).isEqualTo(false);
        assertThat(row.get("keyword_match_response_data")).isEqualTo(true);
        assertThat(row.get("keyword_match_bmsg")).isEqualTo(false);
        assertThat(row.get("keyword_match_data")).isEqualTo(true);
    }

    /** TC-KF-02: literal match on error_message (bmsg). */
    @Test
    void searchPbFepLogWireframe_keywordFlags_literalBmsg_tcKf02() throws Exception {
        String lt = toPbLogTime(PB_KW_LDT);
        insertPbSendPayload(911L, lt, "TRK", "kwuser", "other-req", "other-res", "ERR-LITERAL-KF02-ONLY");

        LogDbSearchRequest req = pbFepWireframeSearchRequest();
        req.setKeywords(List.of("ERR-LITERAL-KF02-ONLY"));

        Map<String, Object> row = logDbService.searchPbFepLogWireframe(req).getData().get(0);
        assertThat(row.get("keyword_match_request_data")).isEqualTo(false);
        assertThat(row.get("keyword_match_response_data")).isEqualTo(false);
        assertThat(row.get("keyword_match_bmsg")).isEqualTo(true);
        assertThat(row.get("keyword_match_data")).isEqualTo(true);
    }

    /** TC-KF-03: match on request_data only (summary falls back to request when response empty). */
    @Test
    void searchPbFepLogWireframe_keywordFlags_requestOnly_tcKf03() throws Exception {
        String lt = toPbLogTime(PB_KW_LDT);
        insertPbSendPayload(912L, lt, "TRK", "kwuser", "REQ-ONLY-KF03-TOKEN", "", "");

        LogDbSearchRequest req = pbFepWireframeSearchRequest();
        req.setKeywords(List.of("REQ-ONLY-KF03-TOKEN"));

        Map<String, Object> row = logDbService.searchPbFepLogWireframe(req).getData().get(0);
        assertThat(row.get("keyword_match_request_data")).isEqualTo(true);
        assertThat(row.get("keyword_match_response_data")).isEqualTo(false);
        assertThat(row.get("keyword_match_bmsg")).isEqualTo(false);
        assertThat(row.get("keyword_match_data")).isEqualTo(true);
    }

    /** TC-KF-04: no keyword filter — optional flags omitted (backward compatible). */
    @Test
    void searchPbFepLogWireframe_keywordFlags_omittedWhenNoKeywords_tcKf04() throws Exception {
        String lt = toPbLogTime(PB_KW_LDT);
        insertPbSendPayload(913L, lt, "TRK", "kwuser", "plain", "", "");

        LogDbSearchRequest reqNullKw = pbFepWireframeSearchRequest();
        Map<String, Object> row1 = logDbService.searchPbFepLogWireframe(reqNullKw).getData().get(0);
        assertThat(row1).doesNotContainKeys("keyword_match_request_data", "keyword_match_response_data",
                "keyword_match_bmsg", "keyword_match_data");

        LogDbSearchRequest reqEmptyKw = pbFepWireframeSearchRequest();
        reqEmptyKw.setKeywords(List.of());
        Map<String, Object> row2 = logDbService.searchPbFepLogWireframe(reqEmptyKw).getData().get(0);
        assertThat(row2).doesNotContainKeys("keyword_match_request_data", "keyword_match_response_data",
                "keyword_match_bmsg", "keyword_match_data");
    }

    /**
     * TC-KF-05: empty request/response payloads, match only on bmsg — keyword_match_data mirrors aggregate OR (bmsg) for stream/UI parity.
     */
    @Test
    void searchPbFepLogWireframe_keywordFlags_bmsgOnlyEmptyReqRes_tcKf05() throws Exception {
        String lt = toPbLogTime(PB_KW_LDT);
        insertPbSendPayload(914L, lt, "TRK", "kwuser", "", "", "BMSG-ONLY-KF05-TOKEN");

        LogDbSearchRequest req = pbFepWireframeSearchRequest();
        req.setKeywords(List.of("BMSG-ONLY-KF05-TOKEN"));

        Map<String, Object> row = logDbService.searchPbFepLogWireframe(req).getData().get(0);
        assertThat(row.get("keyword_match_request_data")).isEqualTo(false);
        assertThat(row.get("keyword_match_response_data")).isEqualTo(false);
        assertThat(row.get("keyword_match_bmsg")).isEqualTo(true);
        assertThat(row.get("keyword_match_data")).isEqualTo(true);
    }

    /**
     * TC-KF-06: both request and response non-empty; keyword matches only request — keyword_match_data must be true
     * (aggregate OR), not false from the old "summary follows response first" branch alone.
     */
    @Test
    void searchPbFepLogWireframe_keywordFlags_requestMatchWhenResponseNonempty_tcKf06() throws Exception {
        String lt = toPbLogTime(PB_KW_LDT);
        insertPbSendPayload(915L, lt, "TRK", "kwuser", "REQ-ONLY-KF06-UNIQUE-TOKEN", "other-res-no-token-KF06", "");

        LogDbSearchRequest req = pbFepWireframeSearchRequest();
        req.setKeywords(List.of("REQ-ONLY-KF06-UNIQUE-TOKEN"));

        Map<String, Object> row = logDbService.searchPbFepLogWireframe(req).getData().get(0);
        assertThat(row.get("keyword_match_request_data")).isEqualTo(true);
        assertThat(row.get("keyword_match_response_data")).isEqualTo(false);
        assertThat(row.get("keyword_match_bmsg")).isEqualTo(false);
        assertThat(row.get("keyword_match_data")).isEqualTo(true);
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
