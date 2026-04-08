package com.logmng.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.constants.ActivityActionType;
import com.logmng.dto.request.UserDeleteRequest;
import com.logmng.dto.request.UserManagementV2CreateDepartmentRequest;
import com.logmng.dto.request.UserManagementV2DirectUserCreateRequest;
import com.logmng.exception.CustomException;
import com.logmng.repository.UserActivityAccessAuditRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Activity audit for User Management v2 mutations (req 20260408-user-management-v2-activity-audit-detail-in-activity-log).
 */
class UserManagementV2ServiceActivityAuditTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private DataSource dataSource;
    private UserManagementV2Service service;
    private AtomicInteger saveCount;
    private AtomicReference<String> lastActionType;
    private AtomicReference<Map<String, Object>> lastDetail;
    private AtomicReference<String> lastRequestParams;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        saveCount = new AtomicInteger();
        lastActionType = new AtomicReference<>();
        lastDetail = new AtomicReference<>();
        lastRequestParams = new AtomicReference<>();

        UserActivityLogService capture = new UserActivityLogService(dataSource, new UserActivityAccessAuditRepository(dataSource)) {
            @Override
            public void saveActivityLog(String userId, String username, String actionType,
                                        Map<String, Object> actionDetail, String ipAddress,
                                        String userAgent, String requestMethod, String requestPath,
                                        String requestParams, Integer responseStatus,
                                        Integer responseTimeMs, Boolean success, String errorMessage) {
                saveCount.incrementAndGet();
                lastActionType.set(actionType);
                lastDetail.set(actionDetail);
                lastRequestParams.set(requestParams);
            }
        };
        service = new UserManagementV2Service(dataSource, capture);
    }

    @Test
    void createRootDepartment_success_emitsDepartmentCreateRootWithDepartmentAdminV1() throws Exception {
        UserManagementV2CreateDepartmentRequest req = new UserManagementV2CreateDepartmentRequest();
        req.setName("본부");
        req.setCode("ROOT_V2");
        req.setSortOrder(10);
        req.setChangeReason("조직 신설");

        String path = "/api/user-management-v2/departments/root";
        service.createRootDepartment(req, "admin1", "127.0.0.1", "junit", path);

        assertThat(saveCount.get()).isEqualTo(1);
        assertThat(lastActionType.get()).isEqualTo(ActivityActionType.DEPARTMENT_CREATE_ROOT.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> v1 = (Map<String, Object>) lastDetail.get().get("departmentAdminV1");
        assertThat(v1).isNotNull();
        assertThat(v1.get("operation")).isEqualTo("CREATE_ROOT");
        assertThat(v1.get("changeReason")).isEqualTo("조직 신설");
        assertThat(v1.get("departmentCode")).isEqualTo("ROOT_V2");
        assertThat(v1).doesNotContainKey("parentDepartmentCode");
        assertThat(v1.get("name")).isEqualTo("본부");
        assertThat(v1.get("sortOrder")).isEqualTo(10);

        Map<String, Object> rp = OM.readValue(lastRequestParams.get(), new TypeReference<>() {});
        assertThat(rp.get("method")).isEqualTo("POST");
        assertThat(rp.get("path")).isEqualTo(path);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) rp.get("body");
        assertThat(body.get("changeReason")).isEqualTo("조직 신설");
        assertThat(body.get("name")).isEqualTo("본부");
    }

    @Test
    void createChildDepartment_success_emitsDepartmentCreateChildAndParentInDetail() throws Exception {
        UserManagementV2CreateDepartmentRequest rootReq = new UserManagementV2CreateDepartmentRequest();
        rootReq.setName("본부");
        rootReq.setCode("P1");
        rootReq.setSortOrder(1);
        rootReq.setChangeReason("루트");
        service.createRootDepartment(rootReq, "admin1", "127.0.0.1", "junit", "/api/user-management-v2/departments/root");
        saveCount.set(0);

        UserManagementV2CreateDepartmentRequest childReq = new UserManagementV2CreateDepartmentRequest();
        childReq.setName("팀");
        childReq.setCode("C1");
        childReq.setSortOrder(2);
        childReq.setChangeReason("하위 추가");
        String childPath = "/api/user-management-v2/departments/children";
        service.createChildDepartment("P1", childReq, "admin1", "127.0.0.1", "junit", childPath);

        assertThat(saveCount.get()).isEqualTo(1);
        assertThat(lastActionType.get()).isEqualTo(ActivityActionType.DEPARTMENT_CREATE_CHILD.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> v1 = (Map<String, Object>) lastDetail.get().get("departmentAdminV1");
        assertThat(v1.get("operation")).isEqualTo("CREATE_CHILD");
        assertThat(v1.get("parentDepartmentCode")).isEqualTo("P1");
        assertThat(v1.get("departmentCode")).isEqualTo("C1");

        Map<String, Object> rp = OM.readValue(lastRequestParams.get(), new TypeReference<>() {});
        assertThat(rp.get("path")).isEqualTo(childPath);
    }

    @Test
    void createDirectUser_success_emitsUserCreateWithRegistrationSourceAndRequestParams() throws Exception {
        try (Connection conn = dataSource.getConnection();
             var ps = conn.prepareStatement("INSERT INTO department(code, parent_code, name, sort_order) VALUES ('ROOT', NULL, '본부', 0)")) {
            ps.executeUpdate();
        }
        try (Connection conn = dataSource.getConnection();
             var ps = conn.prepareStatement("INSERT INTO permission_group(id, code, name) VALUES (1, 'PG', 'g')")) {
            ps.executeUpdate();
        }

        UserManagementV2DirectUserCreateRequest req = new UserManagementV2DirectUserCreateRequest();
        req.setDepartmentId("ROOT");
        req.setEmployeeNumber("20260002");
        req.setName("홍길동");
        req.setRank("대리");
        req.setPermissionGroupId(1L);
        req.setChangeReason("신규 입사");
        String path = "/api/user-management-v2/users/direct";
        service.createDirectUser(req, "admin1", "127.0.0.1", "junit", path);

        assertThat(saveCount.get()).isEqualTo(1);
        assertThat(lastActionType.get()).isEqualTo(ActivityActionType.USER_CREATE.getCode());
        Map<String, Object> d = lastDetail.get();
        assertThat(d.get("registrationSource")).isEqualTo("USER_MANAGEMENT_V2_DIRECT");
        assertThat(d.get("targetUserId")).isInstanceOf(Number.class);
        assertThat(d.get("changeReason")).isEqualTo("신규 입사");
        assertThat(d.get("employeeNumber")).isEqualTo("20260002");
        assertThat(d.get("departmentCode")).isEqualTo("ROOT");
        assertThat(d.get("name")).isEqualTo("홍길동");
        assertThat(d.get("rank")).isEqualTo("대리");

        Map<String, Object> rp = OM.readValue(lastRequestParams.get(), new TypeReference<>() {});
        assertThat(rp.get("path")).isEqualTo(path);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) rp.get("body");
        assertThat(body).doesNotContainKey("password");
        assertThat(body.get("employeeNumber")).isEqualTo("20260002");
    }

    @Test
    void createRootDepartment_whenChangeReasonInvalid_doesNotEmitAudit() {
        UserManagementV2CreateDepartmentRequest req = new UserManagementV2CreateDepartmentRequest();
        req.setName("본부");
        req.setCode("R1");
        req.setChangeReason("   ");

        assertThatThrownBy(() -> service.createRootDepartment(req, "admin1", "127.0.0.1", "junit", "/api/x"))
                .isInstanceOf(CustomException.class);
        assertThat(saveCount.get()).isZero();
    }

    @Test
    void deleteDepartment_success_emitsDepartmentDeleteWithDepartmentAdminV1() throws Exception {
        try (Connection conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO department(code, parent_code, name, sort_order) VALUES ('LEAF', NULL, '잎', 5)")) {
            ps.executeUpdate();
        }
        UserDeleteRequest req = new UserDeleteRequest();
        req.setChangeReason("폐지");
        String path = "/api/user-management-v2/departments/LEAF";
        service.deleteDepartment("LEAF", req, "admin1", "127.0.0.1", "junit", path);

        assertThat(saveCount.get()).isEqualTo(1);
        assertThat(lastActionType.get()).isEqualTo(ActivityActionType.DEPARTMENT_DELETE.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> v1 = (Map<String, Object>) lastDetail.get().get("departmentAdminV1");
        assertThat(v1.get("operation")).isEqualTo("DELETE");
        assertThat(v1.get("departmentCode")).isEqualTo("LEAF");
        assertThat(v1.get("changeReason")).isEqualTo("폐지");
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) v1.get("before");
        assertThat(before.get("name")).isEqualTo("잎");
        assertThat(before.get("sortOrder")).isEqualTo(5);

        Map<String, Object> rp = OM.readValue(lastRequestParams.get(), new TypeReference<>() {});
        assertThat(rp.get("method")).isEqualTo("DELETE");
        assertThat(rp.get("path")).isEqualTo(path);
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:mem:umv2_audit_test_" + UUID.randomUUID()
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
}
