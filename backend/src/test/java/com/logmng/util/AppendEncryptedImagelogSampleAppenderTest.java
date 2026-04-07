package com.logmng.util;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotent append insert (NOT EXISTS) — H2 in-memory.
 */
class AppendEncryptedImagelogSampleAppenderTest {

    @Test
    void insertIfAbsent_insertsOnce_secondCallSkips() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:appendImagelog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE imagelog ("
                        + "application VARCHAR(256), servicegroup VARCHAR(256), service VARCHAR(256), status VARCHAR(256),"
                        + "data TEXT, datastring TEXT, guid VARCHAR(256), header TEXT, headerstring TEXT, insert_time BIGINT)");
            }

            CryptoUtil crypto = new CryptoUtil();
            ReflectionTestUtils.setField(crypto, "encryptionKey", "01234567890123456789012345678901");
            ReflectionTestUtils.setField(crypto, "decryptionEnabled", true);

            GenerateEncryptedSampleData gen = new GenerateEncryptedSampleData(crypto);
            GenerateEncryptedSampleData.SampleData row = gen.generateAppendEncryptedSamples().get(0);
            long t = 1_700_000_000_000L;

            assertThat(AppendEncryptedImagelogSampleAppender.insertIfAbsent(conn, row, t)).isEqualTo(1);
            assertThat(AppendEncryptedImagelogSampleAppender.insertIfAbsent(conn, row, t)).isEqualTo(0);
        }
    }

    @Test
    void normalizedStatusKey_matchesCoalesceTrim() {
        assertThat(AppendEncryptedImagelogSampleAppender.normalizedStatusKey(null)).isEqualTo("");
        assertThat(AppendEncryptedImagelogSampleAppender.normalizedStatusKey("")).isEqualTo("");
        assertThat(AppendEncryptedImagelogSampleAppender.normalizedStatusKey("  ")).isEqualTo("");
        assertThat(AppendEncryptedImagelogSampleAppender.normalizedStatusKey(" input ")).isEqualTo("input");
    }
}
