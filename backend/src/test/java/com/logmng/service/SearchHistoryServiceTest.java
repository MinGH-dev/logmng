package com.logmng.service;

import com.logmng.dto.request.SearchHistoryListRequest;
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
        clearAllTables(dataSource);
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
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user (" +
                    "username VARCHAR(100) PRIMARY KEY, department_code VARCHAR(50))");
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
        return insertSearchHistoryRow(ds, id, ownerUserId, expired, Instant.now().minusSeconds(3600));
    }

    private static long insertSearchHistoryRow(DataSource ds, long id, String ownerUserId, boolean expired, Instant requestedAtInstant) throws Exception {
        Timestamp expiresAt = expired ? Timestamp.from(Instant.now().minusSeconds(3600)) : Timestamp.from(Instant.now().plusSeconds(86400));
        String status = expired ? "EXPIRED" : "PENDING";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO search_history (id, user_id, log_type, search_params, requested_at, expires_at, approval_status, updated_at) VALUES (?, ?, ?, '{}', ?, ?, ?, CURRENT_TIMESTAMP)")) {
            ps.setLong(1, id);
            ps.setString(2, ownerUserId);
            ps.setString(3, "java_fw_imglog");
            ps.setTimestamp(4, Timestamp.from(requestedAtInstant));
            ps.setTimestamp(5, expiresAt);
            ps.setString(6, status);
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertAppUser(DataSource ds, String username, String departmentCode) throws Exception {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO app_user (username, department_code) VALUES (?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, departmentCode);
            ps.executeUpdate();
        }
    }

    private static SearchHistoryListRequest newListRequest(String actorUserId) {
        SearchHistoryListRequest request = new SearchHistoryListRequest();
        request.setActorUserId(actorUserId);
        request.setPage(1);
        request.setPageSize(20);
        request.setSortField("requested_at");
        request.setSortDirection("desc");
        return request;
    }

    // ---- Search-history requester filter + paging (req 20260313) ----

    @Test
    void list_scopeAll_filtersByUserId_andCountMatchesRows() throws Exception {
        insertAppUser(dataSource, "alpha-user", "D01");
        insertAppUser(dataSource, "beta-user", "D02");
        insertSearchHistoryRow(dataSource, 100L, "alpha-user", false);
        insertSearchHistoryRow(dataSource, 101L, "alpha-user", false);
        insertSearchHistoryRow(dataSource, 102L, "beta-user", false);

        SearchHistoryListRequest request = newListRequest("adminUser");
        request.setUserId("alpha-user");
        SearchHistoryListResponse resp = searchHistoryService.list(request);

        assertThat(resp.getData()).hasSize(2);
        assertThat(resp.getData()).allMatch(row -> "alpha-user".equals(row.get("userId")));
        assertThat(resp.getPagination().getTotalCount()).isEqualTo(2);
        assertThat(resp.getPagination().getTotalPages()).isEqualTo(1);
    }

    @Test
    void list_scopeAll_filtersByUsernamePartial_andCountMatchesRows() throws Exception {
        insertAppUser(dataSource, "alice-admin", "D01");
        insertAppUser(dataSource, "alice-viewer", "D02");
        insertAppUser(dataSource, "bob-user", "D01");
        insertSearchHistoryRow(dataSource, 110L, "alice-admin", false);
        insertSearchHistoryRow(dataSource, 111L, "alice-viewer", false);
        insertSearchHistoryRow(dataSource, 112L, "bob-user", false);

        SearchHistoryListRequest request = newListRequest("adminUser");
        request.setUsername("alice");
        SearchHistoryListResponse resp = searchHistoryService.list(request);

        assertThat(resp.getData()).hasSize(2);
        assertThat(resp.getData()).extracting(row -> row.get("userId"))
                .containsExactlyInAnyOrder("alice-admin", "alice-viewer");
        assertThat(resp.getPagination().getTotalCount()).isEqualTo(2);
    }

    @Test
    void list_scopeAll_filtersByDepartmentExact() throws Exception {
        insertAppUser(dataSource, "sales-user", "SALES");
        insertAppUser(dataSource, "research-user", "RESEARCH");
        insertSearchHistoryRow(dataSource, 120L, "sales-user", false);
        insertSearchHistoryRow(dataSource, 121L, "research-user", false);

        SearchHistoryListRequest request = newListRequest("adminUser");
        request.setDepartment("SALES");
        SearchHistoryListResponse resp = searchHistoryService.list(request);

        assertThat(resp.getData()).hasSize(1);
        assertThat(resp.getData().get(0).get("userId")).isEqualTo("sales-user");
        assertThat(resp.getPagination().getTotalCount()).isEqualTo(1);
    }

    @Test
    void list_scopeTeam_appliesAllowedUserIds_beforeRequesterFilters() throws Exception {
        insertAppUser(dataSource, "current-user", "D01");
        insertAppUser(dataSource, "team-mate", "D01");
        insertAppUser(dataSource, "outside-user", "D02");
        insertSearchHistoryRow(dataSource, 130L, "current-user", false);
        insertSearchHistoryRow(dataSource, 131L, "team-mate", false);
        insertSearchHistoryRow(dataSource, 132L, "outside-user", false);

        SearchHistoryListRequest request = newListRequest("current-user");
        request.setAllowedUserIds(List.of("current-user", "team-mate"));
        request.setUserId("outside-user");
        SearchHistoryListResponse outsideResp = searchHistoryService.list(request);

        assertThat(outsideResp.getData()).isEmpty();
        assertThat(outsideResp.getPagination().getTotalCount()).isEqualTo(0);

        request.setUserId(null);
        request.setUsername("team");
        SearchHistoryListResponse teamResp = searchHistoryService.list(request);

        assertThat(teamResp.getData()).hasSize(1);
        assertThat(teamResp.getData().get(0).get("userId")).isEqualTo("team-mate");
        assertThat(teamResp.getPagination().getTotalCount()).isEqualTo(1);
    }

    @Test
    void list_usesConsistentFilterSetForCountAndPageRows_andKeepsDefaultSortDesc() throws Exception {
        insertAppUser(dataSource, "alpha-user", "D01");
        insertSearchHistoryRow(dataSource, 140L, "alpha-user", false, Instant.parse("2026-03-13T01:00:00Z"));
        insertSearchHistoryRow(dataSource, 141L, "alpha-user", false, Instant.parse("2026-03-13T02:00:00Z"));
        insertSearchHistoryRow(dataSource, 142L, "alpha-user", false, Instant.parse("2026-03-13T03:00:00Z"));

        SearchHistoryListRequest request = newListRequest("adminUser");
        request.setUserId("alpha-user");
        request.setPageSize(2);
        SearchHistoryListResponse resp = searchHistoryService.list(request);

        assertThat(resp.getPagination().getTotalCount()).isEqualTo(3);
        assertThat(resp.getPagination().getTotalPages()).isEqualTo(2);
        assertThat(resp.getData()).hasSize(2);
        assertThat(resp.getData().get(0).get("id")).isEqualTo(142L);
        assertThat(resp.getData().get(1).get("id")).isEqualTo(141L);
    }

    @Test
    void getDetail_allowsRequesterOwnRow() throws Exception {
        long id = insertSearchHistoryRow(dataSource, 101L, "user1", false);

        Map<String, Object> detail = searchHistoryService.getDetail("user1", id);

        assertThat(detail).isNotNull();
        assertThat(detail.get("id")).isEqualTo(id);
        assertThat(detail.get("logType")).isEqualTo("java_fw_imglog");
    }

    @Test
    void reRequest_allowsRequesterOwnExpiredRow() throws Exception {
        long id = insertSearchHistoryRow(dataSource, 102L, "user1", true);

        Map<String, Object> result = searchHistoryService.reRequest("user1", id);

        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(id);
        assertThat(result.get("approvalStatus")).isEqualTo("PENDING");
    }

    @Test
    void getDetail_returns403WhenNotRequester() throws Exception {
        long id = insertSearchHistoryRow(dataSource, 103L, "ownerUser", false);

        assertThatThrownBy(() -> searchHistoryService.getDetail("otherUser", id))
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

        assertThatThrownBy(() -> searchHistoryService.reRequest("otherUser", id))
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
        clearAllTables(dataSource);
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
        clearAllTables(dataSource);
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
        clearAllTables(dataSource);
        insertPendingRow(dataSource, 401L, "req1");
        insertPendingRow(dataSource, 402L, "req2");
        insertPendingRow(dataSource, 403L, "req3");

        SearchHistoryListResponse resp = searchHistoryService.listPending("approver1", false, 1, 20, true, null);

        assertThat(resp.getData()).hasSize(3);
        assertThat(resp.getPagination().getTotalCount()).isEqualTo(3);
    }

    @Test
    void listPending_scopeSelf_withNoOwnRequests_returnsEmpty() throws Exception {
        clearAllTables(dataSource);
        insertPendingRow(dataSource, 501L, "otherUser");

        SearchHistoryListResponse resp = searchHistoryService.listPending("approver1", false, 1, 20, false, null);

        assertThat(resp.getData()).isEmpty();
        assertThat(resp.getPagination().getTotalCount()).isEqualTo(0);
    }

    private static void clearAllTables(DataSource ds) throws Exception {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM search_history_approved_row");
            stmt.execute("DELETE FROM search_history");
            stmt.execute("DELETE FROM app_user");
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
