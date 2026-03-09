package com.logmng.service;

import com.logmng.dto.response.SearchHistoryListResponse;
import com.logmng.exception.CustomException;
import com.logmng.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private DecryptApproverService decryptApproverService;
    private SearchHistoryService searchHistoryService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "test-key-32-bytes-long!!!!!!!!!");
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);
        stubLogDbService = new StubLogDbService(dataSource, cryptoUtil);
        decryptApproverService = new StubDecryptApproverService();
        searchHistoryService = new SearchHistoryService(dataSource, stubLogDbService, decryptApproverService);
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS search_history_approved_row (" +
                    "search_history_id BIGINT NOT NULL, log_type VARCHAR(50) NOT NULL, row_id VARCHAR(512) NOT NULL, " +
                    "PRIMARY KEY (search_history_id, log_type, row_id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS search_history (" +
                    "id BIGINT PRIMARY KEY, user_id VARCHAR(100), log_type VARCHAR(50), search_params CLOB, " +
                    "requested_at TIMESTAMP, expires_at TIMESTAMP, approval_status VARCHAR(20), approved_by VARCHAR(100), approved_at TIMESTAMP, " +
                    "rejected_by VARCHAR(100), rejected_at TIMESTAMP, rejection_reason VARCHAR(500), created_at TIMESTAMP, updated_at TIMESTAMP)");
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
        Timestamp requestedAt = Timestamp.from(Instant.now());
        Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(86400));
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO search_history (id, user_id, log_type, search_params, approval_status, requested_at, expires_at, updated_at) VALUES (?, ?, ?, ?, 'PENDING', ?, ?, CURRENT_TIMESTAMP)")) {
            ps.setLong(1, id);
            ps.setString(2, "requester1");
            ps.setString(3, logType);
            ps.setString(4, searchParams);
            ps.setTimestamp(5, requestedAt);
            ps.setTimestamp(6, expiresAt);
            ps.executeUpdate();
        }
    }

    /** Insert a search_history row for list/getDetail/reRequest tests. expired=true sets expires_at in the past and status EXPIRED for reRequest. */
    private static long insertSearchHistoryRow(DataSource ds, long id, String ownerUserId, boolean expired) throws Exception {
        Timestamp requestedAt = Timestamp.from(Instant.now().minusSeconds(3600));
        Timestamp expiresAt = expired ? Timestamp.from(Instant.now().minusSeconds(3600)) : Timestamp.from(Instant.now().plusSeconds(86400));
        String status = expired ? "EXPIRED" : "PENDING";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO search_history (id, user_id, log_type, search_params, requested_at, expires_at, approval_status, updated_at) VALUES (?, ?, ?, '{}', ?, ?, ?, CURRENT_TIMESTAMP)")) {
            ps.setLong(1, id);
            ps.setString(2, ownerUserId);
            ps.setString(3, "java_fw_imglog");
            ps.setTimestamp(4, requestedAt);
            ps.setTimestamp(5, expiresAt);
            ps.setString(6, status);
            ps.executeUpdate();
        }
        return id;
    }

    // ---- Requester-only: list always has userId; getDetail/reRequest only for owner (TC-01–TC-05) ----

    @Test
    void list_alwaysReturnsUserIdInEveryRow() throws Exception {
        insertSearchHistoryRow(dataSource, 100L, "user1", false);

        SearchHistoryListResponse resp = searchHistoryService.list("user1", 1, 20, "requested_at", "desc", false, null);

        assertThat(resp.getData()).isNotEmpty();
        for (Map<String, Object> row : resp.getData()) {
            assertThat(row).containsKey("userId");
            assertThat(row.get("userId")).isEqualTo("user1");
        }
    }

    @Test
    void getDetail_allowsRequesterOwnRow() throws Exception {
        long id = insertSearchHistoryRow(dataSource, 101L, "user1", false);

        Map<String, Object> detail = searchHistoryService.getDetail("user1", id, false, null);

        assertThat(detail).isNotNull();
        assertThat(detail.get("id")).isEqualTo(id);
        assertThat(detail.get("logType")).isEqualTo("java_fw_imglog");
    }

    @Test
    void reRequest_allowsRequesterOwnExpiredRow() throws Exception {
        long id = insertSearchHistoryRow(dataSource, 102L, "user1", true);

        Map<String, Object> result = searchHistoryService.reRequest("user1", id, false, null);

        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(id);
        assertThat(result.get("approvalStatus")).isEqualTo("PENDING");
    }

    @Test
    void getDetail_returns403WhenNotRequester() throws Exception {
        long id = insertSearchHistoryRow(dataSource, 103L, "ownerUser", false);

        assertThatThrownBy(() -> searchHistoryService.getDetail("otherUser", id, true, List.of("ownerUser")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ex = (CustomException) e;
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo("FUNCTION_NOT_ALLOWED");
                });
    }

    @Test
    void reRequest_returns403WhenNotRequester() throws Exception {
        long id = insertSearchHistoryRow(dataSource, 104L, "ownerUser", true);

        assertThatThrownBy(() -> searchHistoryService.reRequest("otherUser", id, true, List.of("ownerUser")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ex = (CustomException) e;
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo("FUNCTION_NOT_ALLOWED");
                });
    }

    // ---- listPending scope (req 20260305-pending-approvals-scope) TC-01, TC-02, TC-03 ----

    /** TC-01: scope self → only rows where requester = current user (approverUserId). */
    @Test
    void listPending_scopeSelf_returnsOnlyRowsWhereRequesterEqualsCurrentUser() throws Exception {
        clearSearchHistory(dataSource);
        insertPendingRow(dataSource, 201L, "approver1");
        insertPendingRow(dataSource, 202L, "otherUser");
        insertPendingRow(dataSource, 203L, "approver1");

        SearchHistoryListResponse resp = searchHistoryService.listPending("approver1", false, 1, 20, false, null);

        assertThat(resp.getData()).hasSize(2);
        assertThat(resp.getData()).allMatch(row -> "approver1".equals(row.get("requester")));
        assertThat(resp.getPagination().getTotalCount()).isEqualTo(2);
    }

    /** TC-02 / TC-06: scope team → only rows where requester in allowedUserIds (same department). */
    @Test
    void listPending_scopeTeam_returnsOnlyRowsWhereRequesterInAllowedUserIds() throws Exception {
        clearSearchHistory(dataSource);
        insertPendingRow(dataSource, 301L, "userA");
        insertPendingRow(dataSource, 302L, "userB");
        insertPendingRow(dataSource, 303L, "userC");

        List<String> teamUserIds = List.of("userA", "userB");
        SearchHistoryListResponse resp = searchHistoryService.listPending("approver1", false, 1, 20, false, teamUserIds);

        assertThat(resp.getData()).hasSize(2);
        assertThat(resp.getData()).extracting(row -> row.get("requester")).containsExactlyInAnyOrder("userA", "userB");
        assertThat(resp.getPagination().getTotalCount()).isEqualTo(2);
    }

    /** TC-03: scope all (or is_system_admin) → all PENDING rows that canApproveForRequester allows. */
    @Test
    void listPending_scopeAll_returnsAllApprovableRows() throws Exception {
        clearSearchHistory(dataSource);
        insertPendingRow(dataSource, 401L, "req1");
        insertPendingRow(dataSource, 402L, "req2");
        insertPendingRow(dataSource, 403L, "req3");

        SearchHistoryListResponse resp = searchHistoryService.listPending("approver1", false, 1, 20, true, null);

        assertThat(resp.getData()).hasSize(3);
        assertThat(resp.getPagination().getTotalCount()).isEqualTo(3);
    }

    @Test
    void listPending_scopeSelf_withNoOwnRequests_returnsEmpty() throws Exception {
        clearSearchHistory(dataSource);
        insertPendingRow(dataSource, 501L, "otherUser");

        SearchHistoryListResponse resp = searchHistoryService.listPending("approver1", false, 1, 20, false, null);

        assertThat(resp.getData()).isEmpty();
        assertThat(resp.getPagination().getTotalCount()).isEqualTo(0);
    }

    private static void clearSearchHistory(DataSource ds) throws Exception {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM search_history_approved_row");
            stmt.execute("DELETE FROM search_history");
        }
    }

    private static void insertPendingRow(DataSource ds, long id, String requesterUserId) throws Exception {
        Timestamp requestedAt = Timestamp.from(Instant.now());
        Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(86400));
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO search_history (id, user_id, log_type, search_params, approval_status, requested_at, expires_at, updated_at) VALUES (?, ?, ?, '{}', 'PENDING', ?, ?, CURRENT_TIMESTAMP)")) {
            ps.setLong(1, id);
            ps.setString(2, requesterUserId);
            ps.setString(3, "java_fw_imglog");
            ps.setTimestamp(4, requestedAt);
            ps.setTimestamp(5, expiresAt);
            ps.executeUpdate();
        }
    }

}
