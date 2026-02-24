package com.logmng.service;

import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.response.LogDbSearchResponse;
import com.logmng.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SearchHistoryService (decryption approval snapshot).
 * Uses H2 in-memory DB to avoid mocking DataSource (Java 17+ Mockito limitations with JDBC interfaces).
 * Ref: docs/requirements/20260224-decryption-snapshot-final-design-en.md §6.1, §6.4
 */
@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:search_history_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    private DataSource dataSource;
    private StubLogDbService stubLogDbService;
    private SearchHistoryService searchHistoryService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);
        stubLogDbService = new StubLogDbService(dataSource, cryptoUtil);
        searchHistoryService = new SearchHistoryService(dataSource, stubLogDbService);
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS search_history_approved_row (" +
                    "search_history_id BIGINT NOT NULL, log_type VARCHAR(50) NOT NULL, row_id VARCHAR(512) NOT NULL, " +
                    "PRIMARY KEY (search_history_id, log_type, row_id))");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
    }

    // ---- isRowInApprovedSnapshot ----

    @Test
    void isRowInApprovedSnapshot_returnsFalseWhenSearchHistoryIdIsNull() {
        assertThat(searchHistoryService.isRowInApprovedSnapshot(null, "java_fw_imglog", "guid-1")).isFalse();
    }

    @Test
    void isRowInApprovedSnapshot_returnsFalseWhenLogTypeIsNull() {
        assertThat(searchHistoryService.isRowInApprovedSnapshot(1L, null, "guid-1")).isFalse();
    }

    @Test
    void isRowInApprovedSnapshot_returnsFalseWhenLogTypeIsBlank() {
        assertThat(searchHistoryService.isRowInApprovedSnapshot(1L, "   ", "guid-1")).isFalse();
    }

    @Test
    void isRowInApprovedSnapshot_returnsFalseWhenRowIdIsNull() {
        assertThat(searchHistoryService.isRowInApprovedSnapshot(1L, "java_fw_imglog", null)).isFalse();
    }

    @Test
    void isRowInApprovedSnapshot_returnsFalseWhenRowIdIsBlank() {
        assertThat(searchHistoryService.isRowInApprovedSnapshot(1L, "java_fw_imglog", "  ")).isFalse();
    }

    @Test
    void isRowInApprovedSnapshot_returnsTrueWhenRowExists() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO search_history_approved_row (search_history_id, log_type, row_id) VALUES (?, ?, ?)")) {
            ps.setLong(1, 1L);
            ps.setString(2, "java_fw_imglog");
            ps.setString(3, "guid-123");
            ps.executeUpdate();
        }

        boolean result = searchHistoryService.isRowInApprovedSnapshot(1L, "java_fw_imglog", "guid-123");

        assertThat(result).isTrue();
    }

    @Test
    void isRowInApprovedSnapshot_returnsFalseWhenRowDoesNotExist() {
        boolean result = searchHistoryService.isRowInApprovedSnapshot(2L, "java_fw_imglog", "unknown-guid");

        assertThat(result).isFalse();
    }

    @Test
    void isRowInApprovedSnapshot_returnsFalseWhenWrongLogType() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO search_history_approved_row (search_history_id, log_type, row_id) VALUES (?, ?, ?)")) {
            ps.setLong(1, 3L);
            ps.setString(2, "java_fw_imglog");
            ps.setString(3, "guid-x");
            ps.executeUpdate();
        }

        boolean result = searchHistoryService.isRowInApprovedSnapshot(3L, "pb_feplog", "guid-x");

        assertThat(result).isFalse();
    }

    // ---- approve: snapshot insert (mocked LogDbService, H2 for persistence) ----

    @Test
    void approve_insertsSnapshotRowsAndSetsApproved() throws Exception {
        createSearchHistoryTableAndInsertPending(dataSource, 10L, "java_fw_imglog", "{}");

        List<Map<String, Object>> searchData = List.of(
                Map.of("guid", "guid-a"),
                Map.of("guid", "guid-b")
        );
        stubLogDbService.setSearchLogsData(searchData);

        Map<String, Object> result = searchHistoryService.approve(10L, "approver1");

        assertThat(result).isNotNull();
        assertThat(result.get("approvalStatus")).isEqualTo("APPROVED");
        assertThat(result.get("id")).isEqualTo(10L);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT row_id FROM search_history_approved_row WHERE search_history_id = ? AND log_type = ? ORDER BY row_id")) {
            ps.setLong(1, 10L);
            ps.setString(2, "java_fw_imglog");
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("row_id")).isEqualTo("guid-a");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("row_id")).isEqualTo("guid-b");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    void approve_withEmptySearchResults_stillSetsApproved() throws Exception {
        createSearchHistoryTableAndInsertPending(dataSource, 20L, "java_fw_imglog", "{}");

        stubLogDbService.setSearchLogsData(Collections.emptyList());

        Map<String, Object> result = searchHistoryService.approve(20L, "approver2");

        assertThat(result).isNotNull();
        assertThat(result.get("approvalStatus")).isEqualTo("APPROVED");
    }

    private static void createSearchHistoryTableAndInsertPending(DataSource ds, long id, String logType, String searchParams) throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS search_history (" +
                    "id BIGINT PRIMARY KEY, log_type VARCHAR(50), search_params CLOB, approval_status VARCHAR(20), " +
                    "approved_by VARCHAR(100), approved_at TIMESTAMP, updated_at TIMESTAMP)");
        }
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO search_history (id, log_type, search_params, approval_status, updated_at) VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)")) {
            ps.setLong(1, id);
            ps.setString(2, logType);
            ps.setString(3, searchParams);
            ps.executeUpdate();
        }
    }

}
