package com.logmng.service;

import com.logmng.dto.request.UserActivityLogSearchRequest;
import com.logmng.dto.response.UserActivityLogResponse;
import com.logmng.exception.CustomException;
import com.logmng.repository.UserActivityAccessAuditRepository;
import com.logmng.util.ScopeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UserActivityLogServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:user_activity_log_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    private DataSource dataSource;
    private UserActivityLogService userActivityLogService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        clearAllTables();
        userActivityLogService = new UserActivityLogService(dataSource, new UserActivityAccessAuditRepository(dataSource));

        insertDepartment("D01", "영업1팀");
        insertDepartment("D02", "연구2팀");
        insertAppUser("currentUser", "D01");
        insertAppUser("teamMate", "D01");
        insertAppUser("outsideUser", "D02");

        insertActivityLog(1L, "currentUser", "Current User", "10.0.0.1", "LOGIN");
        insertActivityLog(2L, "teamMate", "Team Mate", "10.0.0.2", "LOGIN");
        insertActivityLog(3L, "outsideUser", "Outside User", "10.0.0.3", "LOGIN");
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS department (" +
                    "code VARCHAR(50) PRIMARY KEY, name VARCHAR(100))");
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user (" +
                    "id BIGINT, username VARCHAR(100) PRIMARY KEY, department_code VARCHAR(50))");
            stmt.execute("CREATE TABLE IF NOT EXISTS user_activity_log (" +
                    "id BIGINT PRIMARY KEY, user_id VARCHAR(100), username VARCHAR(100), action_type VARCHAR(50), " +
                    "action_detail CLOB, ip_address VARCHAR(100), user_agent VARCHAR(255), request_method VARCHAR(10), " +
                    "request_path VARCHAR(255), request_params CLOB, response_status INT, response_time_ms INT, " +
                    "success BOOLEAN, error_message VARCHAR(500), created_at TIMESTAMP, updated_at TIMESTAMP)");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
    }

    private void clearAllTables() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM user_activity_log");
            stmt.execute("DELETE FROM app_user");
            stmt.execute("DELETE FROM department");
        }
    }

    private void insertDepartment(String code, String name) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO department (code, name) VALUES (?, ?)")) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private void insertAppUser(String username, String departmentCode) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO app_user (id, username, department_code) VALUES (?, ?, ?)")) {
            ps.setLong(1, 20260001L);
            ps.setString(2, username);
            ps.setString(3, departmentCode);
            ps.executeUpdate();
        }
    }

    private void insertActivityLogWithDetail(Long id, String userId, String username, String actionType, String actionDetailJson,
            String requestParamsJson) throws Exception {
        Timestamp now = Timestamp.from(Instant.parse("2026-03-13T10:15:30Z"));
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO user_activity_log " +
                             "(id, user_id, username, action_type, action_detail, ip_address, user_agent, request_method, request_path, request_params, response_status, response_time_ms, success, error_message, created_at, updated_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, userId);
            ps.setString(3, username);
            ps.setString(4, actionType);
            ps.setString(5, actionDetailJson);
            ps.setString(6, "10.0.0.1");
            ps.setString(7, "JUnit");
            ps.setString(8, "PUT");
            ps.setString(9, "/api/permission-groups/1");
            ps.setString(10, requestParamsJson != null ? requestParamsJson : "{}");
            ps.setInt(11, 200);
            ps.setInt(12, 15);
            ps.setBoolean(13, true);
            ps.setString(14, null);
            ps.setTimestamp(15, now);
            ps.setTimestamp(16, now);
            ps.executeUpdate();
        }
    }

    private void insertActivityLog(Long id, String userId, String username, String ipAddress, String actionType) throws Exception {
        Timestamp now = Timestamp.from(Instant.parse("2026-03-13T10:15:30Z"));
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO user_activity_log " +
                             "(id, user_id, username, action_type, action_detail, ip_address, user_agent, request_method, request_path, request_params, response_status, response_time_ms, success, error_message, created_at, updated_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, userId);
            ps.setString(3, username);
            ps.setString(4, actionType);
            ps.setString(5, "{\"result\":\"ok\"}");
            ps.setString(6, ipAddress);
            ps.setString(7, "JUnit");
            ps.setString(8, "GET");
            ps.setString(9, "/api/test");
            ps.setString(10, "{}");
            ps.setInt(11, 200);
            ps.setInt(12, 15);
            ps.setBoolean(13, true);
            ps.setString(14, null);
            ps.setTimestamp(15, now);
            ps.setTimestamp(16, now);
            ps.executeUpdate();
        }
    }

    private UserActivityLogSearchRequest newRequest() {
        UserActivityLogSearchRequest request = new UserActivityLogSearchRequest();
        request.setPage(1);
        request.setPageSize(20);
        request.setSortField("id");
        request.setSortDirection("asc");
        return request;
    }

    @Test
    void searchActivityLogs_scopeSelf_combinedWideningInputsStillReturnsOnlyCurrentUser() {
        UserActivityLogSearchRequest request = newRequest();
        request.setUserIdForFilter("outsideUser");
        request.setUsername("Outside User");
        request.setDepartment("전체");
        request.setIpAddress("10.0.0.3");
        request.setAllowedUserIds(List.of("outsideUser", "teamMate"));

        ScopeHelper.applyActivityLogSearchScope(request, "self", "currentUser", null);
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);

        assertThat(extractUserIds(response)).containsExactly("currentUser");
        assertThat(response.getPagination().getTotalCount()).isEqualTo(1L);
    }

    @Test
    void searchActivityLogs_scopeTeam_outOfTeamUserFiltersDoNotWidenResults() {
        UserActivityLogSearchRequest request = newRequest();
        request.setUserIdForFilter("outsideUser");
        request.setUsername("Outside User");
        request.setDepartment("all");

        ScopeHelper.applyActivityLogSearchScope(request, "team", "currentUser", List.of("currentUser", "teamMate"));
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);

        assertThat(response.getData()).isEmpty();
        assertThat(response.getPagination().getTotalCount()).isEqualTo(0L);
    }

    @Test
    void searchActivityLogs_scopeTeam_validSameDepartmentUserIdNarrowsWithinAllowedSet() {
        UserActivityLogSearchRequest request = newRequest();
        request.setUserIdForFilter("teamMate");

        ScopeHelper.applyActivityLogSearchScope(request, "team", "currentUser", List.of("currentUser", "teamMate"));
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);

        assertThat(extractUserIds(response)).containsExactly("teamMate");
        assertThat(response.getPagination().getTotalCount()).isEqualTo(1L);
    }

    @Test
    void searchActivityLogs_scopeAll_legitimateCrossUserSearchStillWorks() {
        UserActivityLogSearchRequest request = newRequest();
        request.setUserIdForFilter("outsideUser");
        request.setUsername("Outside User");

        ScopeHelper.applyActivityLogSearchScope(request, "all", "currentUser", null);
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);

        assertThat(extractUserIds(response)).containsExactly("outsideUser");
        assertThat(response.getPagination().getTotalCount()).isEqualTo(1L);
    }

    private List<String> extractUserIds(UserActivityLogResponse response) {
        return response.getData().stream()
                .map(row -> (String) row.get("user_id"))
                .collect(Collectors.toList());
    }

    @Test
    void searchActivityLogs_scopeSelf_actionTypeFilter_onlyMatchingRows() throws Exception {
        insertActivityLog(4L, "currentUser", "Current User", "10.0.0.1", "PERMISSION_GROUP_UPDATE");
        UserActivityLogSearchRequest request = newRequest();
        request.setActionType("PERMISSION_GROUP_UPDATE");
        ScopeHelper.applyActivityLogSearchScope(request, "self", "currentUser", null);
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);

        assertThat(response.getPagination().getTotalCount()).isEqualTo(1L);
        assertThat(extractUserIds(response)).containsExactly("currentUser");
    }

    @Test
    void searchActivityLogs_scopeTeam_actionTypeAndDepartment() throws Exception {
        insertActivityLog(5L, "teamMate", "Team Mate", "10.0.0.2", "PERMISSION_GROUP_DELETE");
        UserActivityLogSearchRequest request = newRequest();
        request.setActionType("PERMISSION_GROUP_DELETE");
        // department filter matches department.name (same strings as filter-options API), not department_code
        request.setDepartment("영업1팀");
        ScopeHelper.applyActivityLogSearchScope(request, "team", "currentUser", List.of("currentUser", "teamMate"));
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);

        assertThat(extractUserIds(response)).containsExactly("teamMate");
    }

    /** Filter by display name: app_user.department_code is a code; request department is department.name. */
    @Test
    void searchActivityLogs_departmentFilter_matchesDepartmentName_notRawCode() throws Exception {
        UserActivityLogSearchRequest request = newRequest();
        request.setDepartment("영업1팀");
        ScopeHelper.applyActivityLogSearchScope(request, "team", "currentUser", List.of("currentUser", "teamMate"));
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);

        assertThat(extractUserIds(response)).containsExactlyInAnyOrder("currentUser", "teamMate");
        assertThat(response.getPagination().getTotalCount()).isEqualTo(2L);
    }

    @Test
    void searchActivityLogs_departmentFilter_rawCodeDoesNotMatchDisplayName() throws Exception {
        UserActivityLogSearchRequest request = newRequest();
        request.setDepartment("D01");
        ScopeHelper.applyActivityLogSearchScope(request, "team", "currentUser", List.of("currentUser", "teamMate"));
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);

        assertThat(response.getData()).isEmpty();
        assertThat(response.getPagination().getTotalCount()).isEqualTo(0L);
    }

    @Test
    void searchActivityLogs_actionTypeTooLong_throws() {
        UserActivityLogSearchRequest request = newRequest();
        request.setActionType("X".repeat(51));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userActivityLogService.searchActivityLogs(request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void saveActivityLog_actionTypeTooLong_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userActivityLogService.saveActivityLog(
                "u", "u", "Y".repeat(51), null, null, null, "GET", "/", null, 200, 1, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** TC-08: detail API returns parsed {@code action_detail} including {@code permissionGroupAuditV1} (MF-02). */
    @Test
    void getActivityLogDetail_parsesNestedPermissionGroupAuditV1() throws Exception {
        String json = "{\"permissionGroupAuditV1\":{\"schemaVersion\":\"1\",\"operation\":\"UPDATE\",\"permissionGroupId\":42,"
                + "\"permissionGroupCode\":\"G\",\"before\":{\"code\":\"G\",\"name\":\"old\",\"sortOrder\":0,\"allowedScreens\":[{\"screenId\":\"activity-log\",\"scope\":\"team\"}]},"
                + "\"after\":{\"code\":\"G\",\"name\":\"new\",\"sortOrder\":0,\"allowedScreens\":[{\"screenId\":\"activity-log\",\"scope\":\"all\"}]}}}";
        insertActivityLogWithDetail(100L, "currentUser", "Current User", "PERMISSION_GROUP_UPDATE", json, "{}");

        Map<String, Object> row = userActivityLogService.getActivityLogDetail(100L, "currentUser", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> ad = (Map<String, Object>) row.get("action_detail");
        assertThat(ad).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> v1 = (Map<String, Object>) ad.get("permissionGroupAuditV1");
        assertThat(v1.get("operation")).isEqualTo("UPDATE");
        assertThat(v1.get("permissionGroupId")).isEqualTo(42);
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) v1.get("before");
        assertThat(before.get("name")).isEqualTo("old");
    }

    /** TC-09: scope=self — cannot load another user's row. */
    @Test
    void getActivityLogDetail_scopeSelf_forbiddenWhenNotOwner() throws Exception {
        String json = "{\"permissionGroupAuditV1\":{\"operation\":\"UPDATE\"}}";
        insertActivityLogWithDetail(101L, "outsideUser", "Outside User", "PERMISSION_GROUP_UPDATE", json, "{}");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        userActivityLogService.getActivityLogDetail(101L, "currentUser", null))
                .isInstanceOf(CustomException.class);
    }

    /** TC-10: scope=team — row visible only when actor's team list includes row owner. */
    @Test
    void getActivityLogDetail_scopeTeam_forbiddenWhenUserNotInAllowedTeamList() throws Exception {
        String json = "{\"permissionGroupAuditV1\":{\"operation\":\"LOGIN\"}}";
        insertActivityLogWithDetail(102L, "outsideUser", "Outside User", "LOGIN", json, "{}");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        userActivityLogService.getActivityLogDetail(102L, null, List.of("currentUser", "teamMate")))
                .isInstanceOf(CustomException.class);

        Map<String, Object> teamRow = userActivityLogService.getActivityLogDetail(2L, null, List.of("currentUser", "teamMate"));
        assertThat(teamRow.get("user_id")).isEqualTo("teamMate");
    }

    /** request_params JSON is parsed in detail API (parity with action_detail). */
    @Test
    void getActivityLogDetail_parsesRequestParams() throws Exception {
        String reqJson = "{\"method\":\"POST\",\"query\":{\"page\":\"1\"},\"password\":\"x\"}";
        insertActivityLogWithDetail(200L, "currentUser", "Current User", "LOGIN", "{}", reqJson);

        Map<String, Object> row = userActivityLogService.getActivityLogDetail(200L, "currentUser", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> rp = (Map<String, Object>) row.get("request_params");
        assertThat(rp.get("method")).isEqualTo("POST");
        @SuppressWarnings("unchecked")
        Map<String, Object> q = (Map<String, Object>) rp.get("query");
        assertThat(q.get("page")).isEqualTo("1");
        assertThat(rp.get("password")).isEqualTo("x");
    }

    /** Search rows parse request_params when selected. */
    @Test
    void searchActivityLogs_parsesRequestParamsInRows() throws Exception {
        String reqJson = "{\"filter\":\"active\"}";
        insertActivityLogWithDetail(201L, "currentUser", "Current User", "LOGOUT", "{}", reqJson);

        UserActivityLogSearchRequest request = newRequest();
        request.setActionType("LOGOUT");
        ScopeHelper.applyActivityLogSearchScope(request, "self", "currentUser", null);
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);

        assertThat(response.getData()).hasSize(1);
        Map<String, Object> row = response.getData().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> rp = (Map<String, Object>) row.get("request_params");
        assertThat(rp.get("filter")).isEqualTo("active");
    }
}
