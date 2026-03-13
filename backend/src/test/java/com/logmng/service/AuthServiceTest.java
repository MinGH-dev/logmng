package com.logmng.service;

import com.logmng.dto.request.LoginRequest;
import com.logmng.dto.response.LoginResponse;
import com.logmng.util.IpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:auth_service_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    private DataSource dataSource;
    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        clearAllTables();
        authService = new AuthService(
                new IpUtil(),
                dataSource,
                new PermissionGroupService(dataSource),
                new DecryptApproverService(dataSource, new DepartmentService(dataSource)));
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user (" +
                    "username VARCHAR(100) PRIMARY KEY, password_hash VARCHAR(255), is_system_admin BOOLEAN, department_code VARCHAR(50))");
            stmt.execute("CREATE TABLE IF NOT EXISTS permission_group_screen (" +
                    "permission_group_id BIGINT, screen_id VARCHAR(100), scope VARCHAR(20), read BOOLEAN, write BOOLEAN, approve BOOLEAN, decrypt BOOLEAN)");
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user_permission_group (" +
                    "user_id VARCHAR(100), permission_group_id BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS decrypt_approver (" +
                    "user_id VARCHAR(100), department_code VARCHAR(50))");
            stmt.execute("CREATE TABLE IF NOT EXISTS department (" +
                    "code VARCHAR(50) PRIMARY KEY, parent_code VARCHAR(50), name VARCHAR(100), sort_order INT)");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
    }

    private void clearAllTables() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM app_user_permission_group");
            stmt.execute("DELETE FROM permission_group_screen");
            stmt.execute("DELETE FROM decrypt_approver");
            stmt.execute("DELETE FROM app_user");
            stmt.execute("DELETE FROM department");
        }
    }

    private void insertUser(String username, String passwordHash, boolean isSystemAdmin, String departmentCode) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO app_user (username, password_hash, is_system_admin, department_code) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setBoolean(3, isSystemAdmin);
            ps.setString(4, departmentCode);
            ps.executeUpdate();
        }
    }

    @Test
    void login_populatesAuthoritativeSelfContext() throws Exception {
        insertUser("self-user", "pw", false, "D01");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        LoginResponse response = authService.login(new LoginRequest("self-user", "pw"), request);

        assertThat(response.getSelfContext()).isNotNull();
        assertThat(response.getSelfContext().getDepartment()).isEqualTo("D01");
        assertThat(response.getSelfContext().getUsername()).isEqualTo("self-user");
        assertThat(response.getSelfContext().getUserId()).isEqualTo("self-user");
    }

    @Test
    void getCurrentUserInfo_populatesSelfContextFromSessionIdentity() throws Exception {
        insertUser("session-user", "pw", false, "OPS");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        session.setAttribute("userId", "session-user");
        session.setAttribute("username", "session-user");
        session.setAttribute("isSystemAdmin", false);

        LoginResponse response = authService.getCurrentUserInfo(request);

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("session-user");
        assertThat(response.getSelfContext()).isNotNull();
        assertThat(response.getSelfContext().getDepartment()).isEqualTo("OPS");
        assertThat(response.getSelfContext().getUsername()).isEqualTo("session-user");
        assertThat(response.getSelfContext().getUserId()).isEqualTo("session-user");
    }
}
