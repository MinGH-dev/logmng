package com.logmng.service;

import com.logmng.dto.request.UserActivityLogSearchRequest;
import com.logmng.dto.response.UserActivityLogResponse;
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
        userActivityLogService = new UserActivityLogService(dataSource);

        insertAppUser("currentUser", "D01");
        insertAppUser("teamMate", "D01");
        insertAppUser("outsideUser", "D02");

        insertActivityLog(1L, "currentUser", "Current User", "10.0.0.1");
        insertActivityLog(2L, "teamMate", "Team Mate", "10.0.0.2");
        insertActivityLog(3L, "outsideUser", "Outside User", "10.0.0.3");
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user (" +
                    "username VARCHAR(100) PRIMARY KEY, department_code VARCHAR(50))");
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
        }
    }

    private void insertAppUser(String username, String departmentCode) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO app_user (username, department_code) VALUES (?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, departmentCode);
            ps.executeUpdate();
        }
    }

    private void insertActivityLog(Long id, String userId, String username, String ipAddress) throws Exception {
        Timestamp now = Timestamp.from(Instant.parse("2026-03-13T10:15:30Z"));
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO user_activity_log " +
                             "(id, user_id, username, action_type, action_detail, ip_address, user_agent, request_method, request_path, request_params, response_status, response_time_ms, success, error_message, created_at, updated_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, userId);
            ps.setString(3, username);
            ps.setString(4, "LOGIN");
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
        request.setUserId("outsideUser");
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
        request.setUserId("outsideUser");
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
        request.setUserId("teamMate");

        ScopeHelper.applyActivityLogSearchScope(request, "team", "currentUser", List.of("currentUser", "teamMate"));
        UserActivityLogResponse response = userActivityLogService.searchActivityLogs(request);

        assertThat(extractUserIds(response)).containsExactly("teamMate");
        assertThat(response.getPagination().getTotalCount()).isEqualTo(1L);
    }

    @Test
    void searchActivityLogs_scopeAll_legitimateCrossUserSearchStillWorks() {
        UserActivityLogSearchRequest request = newRequest();
        request.setUserId("outsideUser");
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
}
