package com.logmng.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DepartmentScopeHelper (users in same department for scope=team).
 * Req: 20250304-team-scope-default-and-approval.
 */
class DepartmentScopeHelperTest {

    private static final String H2_URL = "jdbc:h2:mem:dept_scope_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user (" +
                    "username VARCHAR(100) PRIMARY KEY, department_code VARCHAR(100), deleted_at TIMESTAMP NULL)");
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(H2_URL);
        return ds;
    }

    private void insertUser(String username, String departmentCode) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO app_user (username, department_code) VALUES (?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, departmentCode);
            ps.executeUpdate();
        }
    }

    @Test
    void getUserIdsInSameDepartment_nullUsername_returnsEmpty() {
        List<String> result = DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, null);
        assertThat(result).isEmpty();
    }

    @Test
    void getUserIdsInSameDepartment_blankUsername_returnsEmpty() {
        List<String> result = DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, "   ");
        assertThat(result).isEmpty();
    }

    @Test
    void getUserIdsInSameDepartment_userNotInDb_returnsSingletonWithUsername() throws Exception {
        insertUser("other", "dept_a");
        List<String> result = DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, "missing");
        assertThat(result).containsExactly("missing");
    }

    @Test
    void getUserIdsInSameDepartment_userHasNoDepartment_returnsSingletonWithUsername() throws Exception {
        insertUser("solo", null);
        List<String> result = DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, "solo");
        assertThat(result).containsExactly("solo");
    }

    @Test
    void getUserIdsInSameDepartment_userHasBlankDepartment_returnsSingletonWithUsername() throws Exception {
        insertUser("solo2", "");
        List<String> result = DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, "solo2");
        assertThat(result).containsExactly("solo2");
    }

    @Test
    void getUserIdsInSameDepartment_sameDepartment_returnsAllInDepartment() throws Exception {
        insertUser("alice", "dept_a");
        insertUser("bob", "dept_a");
        insertUser("carol", "dept_a");
        insertUser("dave", "dept_b");

        List<String> result = DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, "alice");
        assertThat(result).containsExactlyInAnyOrder("alice", "bob", "carol");
    }

    @Test
    void getUserIdsInSameDepartment_trimmedUsername_usesTrimmedForLookup() throws Exception {
        insertUser("trimmed", "dept_x");
        List<String> result = DepartmentScopeHelper.getUserIdsInSameDepartment(dataSource, "  trimmed  ");
        assertThat(result).contains("trimmed");
    }
}
