package com.logmng.service.statistics;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityDecryptDedupKeyParserTest {

    @Test
    void postDecrypt_auditShape_returnsKey() {
        String json = "{\"requestParams\":{\"logType\":\"java_fw_imglog\","
                + "\"request\":{\"guid\":\"  g1  \",\"status\":\"  active  \"}}}";
        Optional<String> k = ActivityDecryptDedupKeyParser.tryDedupKey("java_fw_imglog", json);
        assertThat(k).contains("java_fw_imglog\u001fg1\u001factive");
    }

    @Test
    void getDecrypt_flatParams_usesIdentifierAndStatus() {
        String json = "{\"requestParams\":{\"logType\":\"java_fw_imglog\",\"type\":\"guid\","
                + "\"identifier\":\"enc-99\",\"status\":\"  st  \"}}";
        Optional<String> k = ActivityDecryptDedupKeyParser.tryDedupKey("java_fw_imglog", json);
        assertThat(k).contains("java_fw_imglog\u001fenc-99\u001fst");
    }

    @Test
    void missingGuid_excluded() {
        String json = "{\"requestParams\":{\"logType\":\"java_fw_imglog\",\"status\":\"x\"}}";
        assertThat(ActivityDecryptDedupKeyParser.tryDedupKey("java_fw_imglog", json)).isEmpty();
    }

    @Test
    void missingStatus_excluded() {
        String json = "{\"requestParams\":{\"logType\":\"java_fw_imglog\",\"request\":{\"guid\":\"g\"}}}";
        assertThat(ActivityDecryptDedupKeyParser.tryDedupKey("java_fw_imglog", json)).isEmpty();
    }

    @Test
    void malformedJson_excluded() {
        assertThat(ActivityDecryptDedupKeyParser.tryDedupKey("java_fw_imglog", "{not json")).isEmpty();
    }

    @Test
    void innerLogTypeMismatch_excluded() {
        String json = "{\"requestParams\":{\"logType\":\"pb_feplog\",\"identifier\":\"1\",\"status\":\"s\"}}";
        assertThat(ActivityDecryptDedupKeyParser.tryDedupKey("java_fw_imglog", json)).isEmpty();
    }

    /**
     * Legacy {@code action_detail}: {@code logType} scalar was JSON-escaped so the Java string includes
     * surrounding quote characters (fails plain {@code equals} to {@code java_fw_imglog}).
     */
    @Test
    void legacy_logTypeJsonEscapedQuotes_returnsKey() {
        String json = "{\"requestParams\":{\"logType\":\"\\\"java_fw_imglog\\\"\","
                + "\"request\":{\"guid\":\"g1\",\"status\":\"active\"}}}";
        Optional<String> k = ActivityDecryptDedupKeyParser.tryDedupKey("java_fw_imglog", json);
        assertThat(k).contains("java_fw_imglog\u001fg1\u001factive");
    }

    @Test
    void legacy_logTypeDoubleWrappedQuotes_returnsKey() {
        String json = "{\"requestParams\":{\"logType\":\"\\\"\\\"java_fw_imglog\\\"\\\"\","
                + "\"request\":{\"guid\":\"g1\",\"status\":\"active\"}}}";
        Optional<String> k = ActivityDecryptDedupKeyParser.tryDedupKey("java_fw_imglog", json);
        assertThat(k).contains("java_fw_imglog\u001fg1\u001factive");
    }

    @Test
    void legacy_quotedPbFeplog_stillExcluded() {
        String json = "{\"requestParams\":{\"logType\":\"\\\"pb_feplog\\\"\","
                + "\"request\":{\"guid\":\"g1\",\"status\":\"active\"}}}";
        assertThat(ActivityDecryptDedupKeyParser.tryDedupKey("java_fw_imglog", json)).isEmpty();
    }

    @Test
    void pbFeplogFilter_returnsEmpty() {
        String json = "{\"requestParams\":{\"logType\":\"pb_feplog\",\"x\":1}}";
        assertThat(ActivityDecryptDedupKeyParser.tryDedupKey("pb_feplog", json)).isEmpty();
    }
}
