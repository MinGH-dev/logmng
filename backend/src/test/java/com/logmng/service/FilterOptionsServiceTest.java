package com.logmng.service;

import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FilterOptionsServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:filter_options_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    private DataSource dataSource;
    private FilterOptionsService filterOptionsService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        clearAllTables();
        filterOptionsService = new FilterOptionsService(new DepartmentService(dataSource));
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS department (" +
                    "code VARCHAR(50) PRIMARY KEY, parent_code VARCHAR(50), name VARCHAR(100), sort_order INT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user (" +
                    "username VARCHAR(100) PRIMARY KEY, department_code VARCHAR(50), deleted_at TIMESTAMP NULL)");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
    }

    private void clearAllTables() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM app_user");
            stmt.execute("DELETE FROM department");
        }
    }

    private void insertDepartment(String code, String name, int sortOrder) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO department (code, parent_code, name, sort_order) VALUES (?, NULL, ?, ?)")) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setInt(3, sortOrder);
            ps.executeUpdate();
        }
    }

    private void insertUser(String username, String departmentCode) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO app_user (username, department_code) VALUES (?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, departmentCode);
            ps.executeUpdate();
        }
    }

    private static LoginResponse newUserInfo(String username, boolean isSystemAdmin, String scope, String screenId) {
        LoginResponse userInfo = new LoginResponse();
        userInfo.setUsername(username);
        userInfo.setIsSystemAdmin(isSystemAdmin);
        userInfo.setScreenScopes(screenId == null ? Collections.emptyMap() : Map.of(screenId, scope));
        return userInfo;
    }

    @Test
    void getDepartmentOptions_scopeAll_returnsSameCurrentDepartmentsForAllSupportedScreens() throws Exception {
        insertDepartment("D02", "지원", 2);
        insertDepartment("D01", "개발", 1);
        insertDepartment("D03", "운영", 3);

        List<String> expected = List.of("개발", "지원", "운영");
        for (String screenId : List.of(
                ScreenConstants.ACTIVITY_LOG,
                ScreenConstants.STATISTICS,
                ScreenConstants.SEARCH_HISTORY)) {
            LoginResponse userInfo = newUserInfo("alice", false, "all", screenId);
            assertThat(filterOptionsService.getDepartmentOptions(screenId, userInfo)).containsExactlyElementsOf(expected);
        }
    }

    @Test
    void getDepartmentOptions_scopeTeam_returnsOnlyCurrentUsersDepartment() throws Exception {
        insertDepartment("D01", "개발", 1);
        insertDepartment("D02", "지원", 2);
        insertUser("alice", "D02");

        LoginResponse userInfo = newUserInfo("alice", false, "team", ScreenConstants.STATISTICS);

        assertThat(filterOptionsService.getDepartmentOptions(ScreenConstants.STATISTICS, userInfo))
                .containsExactly("지원");
    }

    @Test
    void getDepartmentOptions_scopeSelf_returnsEmptyList() throws Exception {
        insertDepartment("D01", "개발", 1);
        insertUser("alice", "D01");

        LoginResponse userInfo = newUserInfo("alice", false, "self", ScreenConstants.SEARCH_HISTORY);

        assertThat(filterOptionsService.getDepartmentOptions(ScreenConstants.SEARCH_HISTORY, userInfo)).isEmpty();
    }

    @Test
    void getDepartmentOptions_systemAdminReturnsAllDepartmentsEvenWithoutScopeEntry() throws Exception {
        insertDepartment("D01", "개발", 1);
        insertDepartment("D02", "지원", 2);

        LoginResponse userInfo = newUserInfo("admin", true, "self", null);

        assertThat(filterOptionsService.getDepartmentOptions(ScreenConstants.ACTIVITY_LOG, userInfo))
                .containsExactly("개발", "지원");
    }
}
