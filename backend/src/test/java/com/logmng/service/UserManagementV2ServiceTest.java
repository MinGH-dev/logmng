package com.logmng.service;

import com.logmng.dto.request.UserDeleteRequest;
import com.logmng.dto.request.UserManagementV2CreateDepartmentRequest;
import com.logmng.dto.request.UserManagementV2DirectUserCreateRequest;
import com.logmng.exception.CustomException;
import com.logmng.util.LocalUserInitialPassword;
import com.logmng.util.UserManagementReadScopeContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserManagementV2ServiceTest {

    private DataSource dataSource;
    private UserManagementV2Service service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        service = new UserManagementV2Service(dataSource, new DepartmentService(dataSource), null);
    }

    @Test
    void createRootDepartment_andCreateChildDepartment_success() throws Exception {
        UserManagementV2CreateDepartmentRequest rootReq = validDepartmentRequest("본부", "ROOT_V2", 10);
        Map<String, Object> root = service.createRootDepartment(rootReq, "admin1", "127.0.0.1", "junit",
                "/api/user-management-v2/departments/root", UserManagementReadScopeContext.unrestricted());

        assertThat(root.get("departmentId")).isEqualTo("ROOT_V2");
        assertThat(root.get("code")).isEqualTo("ROOT_V2");
        assertThat(root.get("name")).isEqualTo("본부");
        assertThat(root.get("parentDepartmentId")).isNull();
        assertThat(root.get("sortOrder")).isEqualTo(10);

        UserManagementV2CreateDepartmentRequest childReq = validDepartmentRequest("개발팀", "DEV_TEAM", 20);
        Map<String, Object> child = service.createChildDepartment("ROOT_V2", childReq, "admin1", "127.0.0.1", "junit",
                "/api/user-management-v2/departments/ROOT_V2/children", UserManagementReadScopeContext.unrestricted());

        assertThat(child.get("departmentId")).isEqualTo("DEV_TEAM");
        assertThat(child.get("code")).isEqualTo("DEV_TEAM");
        assertThat(child.get("name")).isEqualTo("개발팀");
        assertThat(child.get("parentDepartmentId")).isEqualTo("ROOT_V2");
        assertThat(child.get("sortOrder")).isEqualTo(20);

        assertThat(countRows("SELECT COUNT(*) FROM department WHERE code = 'ROOT_V2' AND parent_code IS NULL")).isEqualTo(1);
        assertThat(countRows("SELECT COUNT(*) FROM department WHERE code = 'DEV_TEAM' AND parent_code = 'ROOT_V2'")).isEqualTo(1);
    }

    @Test
    void createChildDepartment_resolvesParentPathCaseInsensitively() throws Exception {
        UserManagementV2CreateDepartmentRequest rootReq = validDepartmentRequest("본부", "PAR_V2", 1);
        service.createRootDepartment(rootReq, "admin1", "127.0.0.1", "junit", "/api/user-management-v2/departments/root", UserManagementReadScopeContext.unrestricted());

        UserManagementV2CreateDepartmentRequest childReq = validDepartmentRequest("팀", "CHILD_V2", 2);
        Map<String, Object> child = service.createChildDepartment("par_v2", childReq, "admin1", "127.0.0.1", "junit",
                "/api/user-management-v2/departments/par_v2/children", UserManagementReadScopeContext.unrestricted());

        assertThat(child.get("parentDepartmentId")).isEqualTo("PAR_V2");
        assertThat(countRows("SELECT COUNT(*) FROM department WHERE code = 'CHILD_V2' AND parent_code = 'PAR_V2'")).isEqualTo(1);
    }

    @Test
    void createChildDepartment_whenParentCodeContainsSlash_succeeds() throws Exception {
        insertDepartment("ORG/ROOT", null, "루트");
        UserManagementV2CreateDepartmentRequest childReq = validDepartmentRequest("하위", "ORG_SUB", 1);
        Map<String, Object> child = service.createChildDepartment("ORG/ROOT", childReq, "admin1", "127.0.0.1", "junit",
                "/api/user-management-v2/departments/children", UserManagementReadScopeContext.unrestricted());
        assertThat(child.get("parentDepartmentId")).isEqualTo("ORG/ROOT");
        assertThat(countRows("SELECT COUNT(*) FROM department WHERE code = 'ORG_SUB' AND parent_code = 'ORG/ROOT'")).isEqualTo(1);
    }

    @Test
    void createDirectUser_resolvesDepartmentIdCaseInsensitively() throws Exception {
        insertDepartment("ROOT", null, "본부");
        insertPermissionGroup(1L, "PG_USER", "일반 사용자");
        UserManagementV2DirectUserCreateRequest req = validRequest();
        req.setDepartmentId("root");
        req.setEmployeeNumber("20269999");
        Map<String, Object> created = service.createDirectUser(req, "admin1", "127.0.0.1", "junit",
                "/api/user-management-v2/users/direct", UserManagementReadScopeContext.unrestricted());
        assertThat(created.get("departmentId")).isEqualTo("ROOT");
        assertThat(countRows(
                "SELECT COUNT(*) FROM app_user WHERE employee_number = '20269999' AND department_code = 'ROOT' AND deleted_at IS NULL"))
                .isEqualTo(1);
    }

    @Test
    void createDirectUser_success_returnsExpectedShapeCoreFields() throws Exception {
        insertDepartment("ROOT", null, "본부");
        insertPermissionGroup(1L, "PG_USER", "일반 사용자");

        UserManagementV2DirectUserCreateRequest req = validRequest();
        req.setEmployeeNumber("20260001");
        req.setName("홍길동");
        req.setRank("과장");

        Map<String, Object> created = service.createDirectUser(req, "admin1", "127.0.0.1", "junit",
                "/api/user-management-v2/users/direct", UserManagementReadScopeContext.unrestricted());

        assertThat(created.get("userId")).isInstanceOf(Number.class);
        assertThat(created.get("employeeNumber")).isEqualTo("20260001");
        assertThat(created.get("name")).isEqualTo("홍길동");
        assertThat(created.get("rank")).isEqualTo("과장");
        assertThat(created.get("departmentId")).isEqualTo("ROOT");
        assertThat(created.get("permissionGroupId")).isEqualTo(1L);
        assertThat(created.get("createdAt")).isInstanceOf(String.class);

        assertThat(countRows("SELECT COUNT(*) FROM app_user WHERE employee_number = '20260001' AND deleted_at IS NULL")).isEqualTo(1);
        assertThat(countRows(
                "SELECT COUNT(*) FROM app_user_permission_group g JOIN app_user u ON g.user_id = u.username " +
                        "WHERE u.employee_number = '20260001' AND g.permission_group_id = 1")).isEqualTo(1);

        assertThat(selectPasswordHashByEmployeeNumber("20260001")).isEqualTo(LocalUserInitialPassword.PLAINTEXT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getQuickEntryOptions_success_withDefaultAndLimit() throws Exception {
        insertDepartment("ROOT", null, "본부");
        insertPermissionGroup(1L, "PG_USER", "일반 사용자");

        for (int i = 1; i <= 3; i++) {
            UserManagementV2DirectUserCreateRequest req = validRequest();
            req.setEmployeeNumber("2026100" + i);
            req.setName("테스터" + i);
            req.setRank("R" + i);
            service.createDirectUser(req, "admin1", "127.0.0.1", "junit", "/api/user-management-v2/users/direct", UserManagementReadScopeContext.unrestricted());
        }

        Map<String, Object> defaults = service.getQuickEntryOptions("admin1", null, null, UserManagementReadScopeContext.unrestricted());
        assertThat(defaults).containsKeys("employeeNumber", "name", "rank", "permissionGroupId");

        Map<String, Object> defaultEmployee = (Map<String, Object>) defaults.get("employeeNumber");
        assertThat(defaultEmployee.get("previous")).isEqualTo("20261003");
        assertThat((List<String>) defaultEmployee.get("recent"))
                .containsExactly("20261003", "20261002", "20261001");

        Map<String, Object> limited = service.getQuickEntryOptions("admin1", List.of("employeeNumber"), 2, UserManagementReadScopeContext.unrestricted());
        Map<String, Object> limitedEmployee = (Map<String, Object>) limited.get("employeeNumber");
        assertThat((List<String>) limitedEmployee.get("recent")).containsExactly("20261003", "20261002");
    }

    @Test
    void createDirectUser_whenParentDepartmentNotFound_throwsDepartmentNotFound() {
        UserManagementV2DirectUserCreateRequest req = validRequest();
        req.setDepartmentId("MISSING_DEPT");

        assertThatThrownBy(() -> service.createDirectUser(req, "admin1", "127.0.0.1", "junit",
                        "/api/user-management-v2/users/direct", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo("DEPARTMENT_NOT_FOUND");
                });
    }

    @Test
    void createChildDepartment_whenParentIsHierarchyUnassignedBucket_throwsInvalidInput() {
        UserManagementV2CreateDepartmentRequest childReq = validDepartmentRequest("팀", "CHILD1", 10);

        assertThatThrownBy(() -> service.createChildDepartment(
                UserPermissionHierarchyService.UNASSIGNED_DEPARTMENT_CODE, childReq, "admin1", "127.0.0.1", "junit",
                "/api/user-management-v2/departments/children", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo("INVALID_INPUT");
                });
    }

    @Test
    void createDirectUser_whenDepartmentIsHierarchyUnassignedBucket_throwsInvalidInput() {
        UserManagementV2DirectUserCreateRequest req = validRequest();
        req.setDepartmentId(UserPermissionHierarchyService.UNASSIGNED_DEPARTMENT_CODE);

        assertThatThrownBy(() -> service.createDirectUser(req, "admin1", "127.0.0.1", "junit",
                        "/api/user-management-v2/users/direct", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo("INVALID_INPUT");
                });
    }

    @Test
    void createDirectUser_whenPermissionGroupNotFound_throwsPermissionGroupNotFound() throws Exception {
        insertDepartment("ROOT", null, "본부");

        UserManagementV2DirectUserCreateRequest req = validRequest();
        req.setPermissionGroupId(9999L);

        assertThatThrownBy(() -> service.createDirectUser(req, "admin1", "127.0.0.1", "junit",
                        "/api/user-management-v2/users/direct", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo("PERMISSION_GROUP_NOT_FOUND");
                });
    }

    @Test
    void createDirectUser_whenEmployeeNumberDuplicated_throwsDuplicatedCode() throws Exception {
        insertDepartment("ROOT", null, "본부");
        insertPermissionGroup(1L, "PG_USER", "일반 사용자");
        insertUser("existing_user", "20269999");

        UserManagementV2DirectUserCreateRequest req = validRequest();
        req.setEmployeeNumber("20269999");

        assertThatThrownBy(() -> service.createDirectUser(req, "admin1", "127.0.0.1", "junit",
                        "/api/user-management-v2/users/direct", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo("USER_EMPLOYEE_NUMBER_DUPLICATED");
                });
    }

    @Test
    void createDirectUser_whenRequiredInputMissing_throwsInvalidInput() {
        UserManagementV2DirectUserCreateRequest req = validRequest();
        req.setDepartmentId("   ");

        assertThatThrownBy(() -> service.createDirectUser(req, "admin1", "127.0.0.1", "junit",
                        "/api/user-management-v2/users/direct", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo("INVALID_INPUT");
                });
    }

    @Test
    void createDirectUser_whenPermissionGroupIdMissing_throwsInvalidInput() {
        UserManagementV2DirectUserCreateRequest req = validRequest();
        req.setPermissionGroupId(null);

        assertThatThrownBy(() -> service.createDirectUser(req, "admin1", "127.0.0.1", "junit",
                        "/api/user-management-v2/users/direct", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo("INVALID_INPUT");
                });
    }

    @Test
    void deleteDepartment_success_removesRow() throws Exception {
        insertDepartment("LEAF", null, "잎");
        UserDeleteRequest req = new UserDeleteRequest();
        req.setChangeReason("조직 폐지");
        Map<String, Object> out = service.deleteDepartment("LEAF", req, "admin1", "127.0.0.1", "junit",
                "/api/user-management-v2/departments/LEAF", UserManagementReadScopeContext.unrestricted());
        assertThat(out.get("departmentId")).isEqualTo("LEAF");
        assertThat(countRows("SELECT COUNT(*) FROM department WHERE code = 'LEAF'")).isZero();
    }

    @Test
    void deleteDepartment_resolvesCodeCaseInsensitively() throws Exception {
        insertDepartment("PAR_V2", null, "본부");
        UserDeleteRequest req = new UserDeleteRequest();
        req.setChangeReason("정리");
        service.deleteDepartment("par_v2", req, "admin1", "127.0.0.1", "junit", "/api/x", UserManagementReadScopeContext.unrestricted());
        assertThat(countRows("SELECT COUNT(*) FROM department WHERE code = 'PAR_V2'")).isZero();
    }

    @Test
    void deleteDepartment_whenMissing_returns404() {
        UserDeleteRequest req = new UserDeleteRequest();
        req.setChangeReason("정리");
        assertThatThrownBy(() -> service.deleteDepartment("NONE", req, "a", "127.0.0.1", "j", "/x", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void deleteDepartment_whenHasChild_returns409() throws Exception {
        insertDepartment("P", null, "상위");
        insertDepartment("C", "P", "하위");
        UserDeleteRequest req = new UserDeleteRequest();
        req.setChangeReason("정리");
        assertThatThrownBy(() -> service.deleteDepartment("P", req, "a", "127.0.0.1", "j", "/x", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("DEPARTMENT_HAS_CHILDREN"));
    }

    @Test
    void deleteDepartment_whenActiveUser_returns409() throws Exception {
        insertDepartment("D", null, "부서");
        insertUserWithDepartment("u1", "e1", "D");
        UserDeleteRequest req = new UserDeleteRequest();
        req.setChangeReason("정리");
        assertThatThrownBy(() -> service.deleteDepartment("D", req, "a", "127.0.0.1", "j", "/x", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("DEPARTMENT_HAS_ACTIVE_USERS"));
    }

    @Test
    void deleteDepartment_whenOrgLink_returns409() throws Exception {
        insertDepartment("D", null, "부서");
        insertDepartmentOrgLink("HR", "ext-1", "D");
        UserDeleteRequest req = new UserDeleteRequest();
        req.setChangeReason("정리");
        assertThatThrownBy(() -> service.deleteDepartment("D", req, "a", "127.0.0.1", "j", "/x", UserManagementReadScopeContext.unrestricted()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("DEPARTMENT_ORG_LINK_REFERENCES"));
    }

    private UserManagementV2DirectUserCreateRequest validRequest() {
        UserManagementV2DirectUserCreateRequest req = new UserManagementV2DirectUserCreateRequest();
        req.setDepartmentId("ROOT");
        req.setEmployeeNumber("20269999");
        req.setName("테스터");
        req.setRank("대리");
        req.setPermissionGroupId(1L);
        req.setChangeReason("등록");
        return req;
    }

    private UserManagementV2CreateDepartmentRequest validDepartmentRequest(String name, String code, Integer sortOrder) {
        UserManagementV2CreateDepartmentRequest req = new UserManagementV2CreateDepartmentRequest();
        req.setName(name);
        req.setCode(code);
        req.setSortOrder(sortOrder);
        req.setChangeReason("등록");
        return req;
    }

    private DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:mem:umv2_service_test_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (Connection conn = java.sql.DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE department (" +
                    "code VARCHAR(50) PRIMARY KEY, " +
                    "parent_code VARCHAR(50), " +
                    "name VARCHAR(200) NOT NULL, " +
                    "sort_order INT DEFAULT 0)");
            stmt.execute("CREATE TABLE permission_group (" +
                    "id BIGINT PRIMARY KEY, " +
                    "code VARCHAR(50) NOT NULL, " +
                    "name VARCHAR(200) NOT NULL)");
            stmt.execute("CREATE TABLE app_user (" +
                    "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL UNIQUE, " +
                    "employee_number VARCHAR(32), " +
                    "password_hash VARCHAR(255) NOT NULL DEFAULT 'x', " +
                    "role VARCHAR(20) NOT NULL DEFAULT 'USER', " +
                    "department_code VARCHAR(50), " +
                    "name VARCHAR(100), " +
                    "rank VARCHAR(50), " +
                    "is_system_admin BOOLEAN DEFAULT FALSE, " +
                    "deleted_at TIMESTAMP NULL)");
            stmt.execute("CREATE TABLE app_user_permission_group (" +
                    "user_id VARCHAR(100) NOT NULL, " +
                    "permission_group_id BIGINT NOT NULL, " +
                    "PRIMARY KEY (user_id, permission_group_id))");
            stmt.execute("CREATE TABLE department_org_link (" +
                    "source_system VARCHAR(50) NOT NULL, " +
                    "external_department_id VARCHAR(256) NOT NULL, " +
                    "department_code VARCHAR(50) NOT NULL, " +
                    "PRIMARY KEY (source_system, external_department_id))");
        }
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(url);
        return ds;
    }

    private void insertDepartment(String code, String parentCode, String name) throws Exception {
        try (Connection conn = dataSource.getConnection();
             var ps = conn.prepareStatement("INSERT INTO department(code, parent_code, name, sort_order) VALUES (?, ?, ?, 0)")) {
            ps.setString(1, code);
            ps.setString(2, parentCode);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    private void insertPermissionGroup(Long id, String code, String name) throws Exception {
        try (Connection conn = dataSource.getConnection();
             var ps = conn.prepareStatement("INSERT INTO permission_group(id, code, name) VALUES (?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, code);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    private void insertUser(String username, String employeeNumber) throws Exception {
        try (Connection conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO app_user(username, employee_number, password_hash, role, is_system_admin, deleted_at) " +
                             "VALUES (?, ?, 'x', 'USER', false, NULL)")) {
            ps.setString(1, username);
            ps.setString(2, employeeNumber);
            ps.executeUpdate();
        }
    }

    private void insertUserWithDepartment(String username, String employeeNumber, String departmentCode) throws Exception {
        try (Connection conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO app_user(username, employee_number, password_hash, role, department_code, is_system_admin, deleted_at) "
                             + "VALUES (?, ?, 'x', 'USER', ?, false, NULL)")) {
            ps.setString(1, username);
            ps.setString(2, employeeNumber);
            ps.setString(3, departmentCode);
            ps.executeUpdate();
        }
    }

    private void insertDepartmentOrgLink(String sourceSystem, String externalDepartmentId, String departmentCode) throws Exception {
        try (Connection conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO department_org_link(source_system, external_department_id, department_code) VALUES (?, ?, ?)")) {
            ps.setString(1, sourceSystem);
            ps.setString(2, externalDepartmentId);
            ps.setString(3, departmentCode);
            ps.executeUpdate();
        }
    }

    private String selectPasswordHashByEmployeeNumber(String employeeNumber) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT password_hash FROM app_user WHERE employee_number = ? AND deleted_at IS NULL")) {
            ps.setString(1, employeeNumber);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    private int countRows(String sql) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
