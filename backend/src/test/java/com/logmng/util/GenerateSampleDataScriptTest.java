package com.logmng.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-style tests for GenerateSampleDataScript (TC-02, TC-06: preserve on restart).
 * Uses H2 in-memory and real JdbcTemplate because JdbcTemplate is not mockable on newer JVMs.
 * Requirement: docs/requirements/20260318-image-log-sample-data-preserve.md
 */
class GenerateSampleDataScriptTest {

    private static final String H2_URL = "jdbc:h2:mem:imagelog_script_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";

    private JdbcTemplate jdbcTemplate;
    private CryptoUtil cryptoUtil;
    private GenerateSampleDataScript script;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = createH2DataSource();
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("TRUNCATE TABLE imagelog");
        cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);
        script = new GenerateSampleDataScript();
        ReflectionTestUtils.setField(script, "cryptoUtil", cryptoUtil);
        ReflectionTestUtils.setField(script, "jdbcTemplate", jdbcTemplate);
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS imagelog (" +
                    "application VARCHAR(256), servicegroup VARCHAR(256), service VARCHAR(256), status VARCHAR(256)," +
                    "data TEXT, datastring TEXT, guid VARCHAR(256), header TEXT, headerstring TEXT, insert_time BIGINT)");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
    }

    private long imagelogCount() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM imagelog", Long.class);
        return c != null ? c : 0;
    }

    @Test
    void run_doesNotInsertWhenImagelogAlreadyHasRows() {
        jdbcTemplate.update("INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "A", "B", "C", "input", "{}", "{}", "guid-1", "{}", "{}", System.currentTimeMillis());
        assertThat(imagelogCount()).isEqualTo(1);

        script.run();

        assertThat(imagelogCount()).isEqualTo(1);
    }

    @Test
    void run_insertsSampleDataWhenImagelogIsEmpty() {
        assertThat(imagelogCount()).isEqualTo(0);

        script.run();

        assertThat(imagelogCount()).isEqualTo(GenerateEncryptedSampleData.TARGET_TOTAL);
    }

    @Test
    void run_secondRestartPreservesRows() {
        script.run();
        assertThat(imagelogCount()).isEqualTo(GenerateEncryptedSampleData.TARGET_TOTAL);

        script.run();

        assertThat(imagelogCount()).isEqualTo(GenerateEncryptedSampleData.TARGET_TOTAL);
    }
}
