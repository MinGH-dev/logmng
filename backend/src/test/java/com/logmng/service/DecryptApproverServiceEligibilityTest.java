package com.logmng.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Req 20260323 / contract 「복호화 승인 자격」: group {@code approve} + {@code is_system_admin} override + P2-2 same department.
 */
class DecryptApproverServiceEligibilityTest {

    private static final String H2_URL = "jdbc:h2:mem:decrypt_eligibility_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
            + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    private DataSource dataSource;
    private DecryptApproverService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createSchema();
        AppUserResolver resolver = new AppUserResolver(dataSource);
        PermissionGroupService pgs = new PermissionGroupService(dataSource, resolver);
        service = new DecryptApproverService(dataSource, new DepartmentService(dataSource), null, pgs, resolver);
    }

    private static DataSource createSchema() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS app_user_permission_group");
            stmt.execute("DROP TABLE IF EXISTS permission_group_screen");
            stmt.execute("DROP TABLE IF EXISTS permission_group");
            stmt.execute("DROP TABLE IF EXISTS app_user");
            stmt.execute("CREATE TABLE app_user ("
                    + "id BIGINT PRIMARY KEY, username VARCHAR(100) NOT NULL UNIQUE, "
                    + "department_code VARCHAR(50), is_system_admin BOOLEAN NOT NULL DEFAULT false, "
                    + "deleted_at TIMESTAMP NULL)");
            stmt.execute("CREATE TABLE permission_group (id BIGINT PRIMARY KEY, code VARCHAR(50), name VARCHAR(200))");
            stmt.execute("CREATE TABLE permission_group_screen ("
                    + "permission_group_id BIGINT NOT NULL, screen_id VARCHAR(100) NOT NULL, "
                    + "read BOOLEAN, write BOOLEAN, approve BOOLEAN, decrypt BOOLEAN)");
            stmt.execute("CREATE TABLE app_user_permission_group ("
                    + "user_id VARCHAR(100) NOT NULL, permission_group_id BIGINT NOT NULL)");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
    }

    private void seedGroupWithApprove(boolean approveFlag) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO permission_group (id, code, name) VALUES (1, 'G1', 'Group1')");
            stmt.execute("INSERT INTO permission_group_screen (permission_group_id, screen_id, read, approve) "
                    + "VALUES (1, 'search-history', true, " + approveFlag + ")");
        }
    }

    @Test
    void effectiveDecryptApprove_whenGroupApproveTrueAndNotSysAdmin_isTrue() throws Exception {
        seedGroupWithApprove(true);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO app_user (id, username, department_code, is_system_admin) VALUES (10, 'u10', 'D1', false)");
            stmt.execute("INSERT INTO app_user_permission_group (user_id, permission_group_id) VALUES ('u10', 1)");
        }
        assertThat(service.effectiveDecryptApprove(10L)).isTrue();
        assertThat(service.isApprover(10L)).isTrue();
    }

    @Test
    void effectiveDecryptApprove_whenSysAdmin_evenIfGroupApprove_isFalse() throws Exception {
        seedGroupWithApprove(true);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO app_user (id, username, department_code, is_system_admin) VALUES (11, 'adminU', 'D1', true)");
            stmt.execute("INSERT INTO app_user_permission_group (user_id, permission_group_id) VALUES ('adminU', 1)");
        }
        assertThat(service.effectiveDecryptApprove(11L)).isFalse();
    }

    @Test
    void canApproveForRequester_whenSameDepartmentAndEligible_isTrue() throws Exception {
        seedGroupWithApprove(true);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO app_user (id, username, department_code, is_system_admin) VALUES (20, 'ap', 'D1', false)");
            stmt.execute("INSERT INTO app_user (id, username, department_code, is_system_admin) VALUES (21, 'rq', 'D1', false)");
            stmt.execute("INSERT INTO app_user_permission_group (user_id, permission_group_id) VALUES ('ap', 1)");
        }
        assertThat(service.canApproveForRequester(20L, 21L)).isTrue();
    }

    @Test
    void canApproveForRequester_whenDifferentDepartment_isFalse() throws Exception {
        seedGroupWithApprove(true);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO app_user (id, username, department_code, is_system_admin) VALUES (30, 'ap2', 'D1', false)");
            stmt.execute("INSERT INTO app_user (id, username, department_code, is_system_admin) VALUES (31, 'rq2', 'D2', false)");
            stmt.execute("INSERT INTO app_user_permission_group (user_id, permission_group_id) VALUES ('ap2', 1)");
        }
        assertThat(service.canApproveForRequester(30L, 31L)).isFalse();
    }

    @Test
    void effectiveDecryptApprove_whenApproveExplicitFalse_isFalse() throws Exception {
        seedGroupWithApprove(false);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO app_user (id, username, department_code, is_system_admin) VALUES (40, 'u40', 'D1', false)");
            stmt.execute("INSERT INTO app_user_permission_group (user_id, permission_group_id) VALUES ('u40', 1)");
        }
        assertThat(service.effectiveDecryptApprove(40L)).isFalse();
    }
}
