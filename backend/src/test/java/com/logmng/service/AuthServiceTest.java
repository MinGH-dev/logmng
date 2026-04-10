package com.logmng.service;

import com.logmng.config.AuthProperties;
import com.logmng.constants.ScreenConstants;
import com.logmng.dto.request.ChangeMyPasswordRequest;
import com.logmng.dto.request.LoginRequest;
import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.response.ScreenFunctionCapability;
import com.logmng.util.IpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {

    private static final String H2_URL = "jdbc:h2:mem:auth_service_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    private DataSource dataSource;
    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createH2DataSource();
        clearAllTables();
        AppUserResolver resolver = new AppUserResolver(dataSource);
        PermissionGroupService permissionGroupService = new PermissionGroupService(dataSource, resolver);
        authService = new AuthService(
                new IpUtil(),
                dataSource,
                permissionGroupService,
                resolver,
                new AuthProperties(),
                new ExternalIdentityService(dataSource, new AuthProperties()),
                null);
    }

    private static DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = java.sql.DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user (" +
                    "id BIGINT, username VARCHAR(100) PRIMARY KEY, password_hash VARCHAR(255), is_system_admin BOOLEAN, department_code VARCHAR(50), name VARCHAR(200), employee_number VARCHAR(100), deleted_at TIMESTAMP NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS permission_group_screen (" +
                    "permission_group_id BIGINT, screen_id VARCHAR(100), scope VARCHAR(20), read BOOLEAN, write BOOLEAN, approve BOOLEAN, decrypt BOOLEAN)");
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user_permission_group (" +
                    "user_id VARCHAR(100), permission_group_id BIGINT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS decrypt_approver (" +
                    "user_id VARCHAR(100), department_code VARCHAR(50))");
            stmt.execute("CREATE TABLE IF NOT EXISTS department (" +
                    "code VARCHAR(50) PRIMARY KEY, parent_code VARCHAR(50), name VARCHAR(100), sort_order INT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS app_user_external_identity (" +
                    "app_user_id BIGINT NOT NULL, source_system VARCHAR(50), external_employee_id VARCHAR(100))");
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
            stmt.execute("DELETE FROM app_user_external_identity");
            stmt.execute("DELETE FROM app_user");
            stmt.execute("DELETE FROM department");
        }
    }

    private void insertUser(String username, String passwordHash, boolean isSystemAdmin, String departmentCode) throws Exception {
        long id = "admin".equalsIgnoreCase(username) ? 20269999L : 20260001L;
        insertUser(id, username, passwordHash, isSystemAdmin, departmentCode, null, null);
    }

    private void insertUser(String username, String passwordHash, boolean isSystemAdmin, String departmentCode, String name) throws Exception {
        long id = "admin".equalsIgnoreCase(username) ? 20269999L : 20260001L;
        insertUser(id, username, passwordHash, isSystemAdmin, departmentCode, name, null);
    }

    private void insertUser(long id, String username, String passwordHash, boolean isSystemAdmin, String departmentCode, String name) throws Exception {
        insertUser(id, username, passwordHash, isSystemAdmin, departmentCode, name, null);
    }

    private void insertUser(long id, String username, String passwordHash, boolean isSystemAdmin, String departmentCode, String name, String employeeNumber) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO app_user (id, username, password_hash, is_system_admin, department_code, name, employee_number) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, username);
            ps.setString(3, passwordHash);
            ps.setBoolean(4, isSystemAdmin);
            ps.setString(5, departmentCode);
            ps.setString(6, name);
            ps.setString(7, employeeNumber);
            ps.executeUpdate();
        }
    }

    private void softDeleteUser(long id) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE app_user SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private void insertExternalIdentity(long appUserId, String sourceSystem, String externalEmployeeId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO app_user_external_identity (app_user_id, source_system, external_employee_id) VALUES (?, ?, ?)")) {
            ps.setLong(1, appUserId);
            ps.setString(2, sourceSystem);
            ps.setString(3, externalEmployeeId);
            ps.executeUpdate();
        }
    }

    private String selectPasswordHash(long userId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM app_user WHERE id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    private AuthService authServiceWithLoginMode(String mode) {
        AuthProperties props = new AuthProperties();
        props.getLogin().setMode(mode);
        AppUserResolver resolver = new AppUserResolver(dataSource);
        return new AuthService(
                new IpUtil(),
                dataSource,
                new PermissionGroupService(dataSource, resolver),
                resolver,
                props,
                new ExternalIdentityService(dataSource, props),
                null);
    }

    private static MockHttpServletRequest requestWithSessionUserId(long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", userId);
        request.setSession(session);
        return request;
    }

    @Test
    void login_populatesAuthoritativeSelfContext() throws Exception {
        insertUser(20260001L, "self-user", "pw", false, "D01", null, "E-0001");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        LoginResponse response = authService.login(new LoginRequest(20260001L, "pw"), request);

        assertThat(response.getSelfContext()).isNotNull();
        assertThat(response.getSelfContext().getDepartment()).isEqualTo("D01");
        assertThat(response.getSelfContext().getUsername()).isEqualTo("self-user");
        assertThat(response.getSelfContext().getUserId()).isEqualTo(20260001L);
        assertThat(response.getSelfContext().getEmployeeNumber()).isEqualTo("E-0001");
    }

    @Test
    void login_whenAppUserNameSet_selfContextUsernameIsDisplayName() throws Exception {
        insertUser(20260002L, "display-user", "pw", false, "D01", "홍길동", "E-0002");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        LoginResponse response = authService.login(new LoginRequest(20260002L, "pw"), request);

        assertThat(response.getSelfContext()).isNotNull();
        assertThat(response.getSelfContext().getUserId()).isEqualTo(20260002L);
        assertThat(response.getSelfContext().getUsername()).isEqualTo("홍길동");
        assertThat(response.getSelfContext().getDepartment()).isEqualTo("D01");
        assertThat(response.getSelfContext().getEmployeeNumber()).isEqualTo("E-0002");
    }

    @Test
    void getCurrentUserInfo_populatesSelfContextFromSessionIdentity() throws Exception {
        insertUser(20260003L, "session-user", "pw", false, "OPS", null, "E-0003");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        session.setAttribute("userId", 20260003L);
        session.setAttribute("isSystemAdmin", false);

        LoginResponse response = authService.getCurrentUserInfo(request);

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("session-user");
        assertThat(response.getSelfContext()).isNotNull();
        assertThat(response.getSelfContext().getDepartment()).isEqualTo("OPS");
        assertThat(response.getSelfContext().getUsername()).isEqualTo("session-user");
        assertThat(response.getSelfContext().getUserId()).isEqualTo(20260003L);
        assertThat(response.getSelfContext().getEmployeeNumber()).isEqualTo("E-0003");
    }

    @Test
    void getCurrentUserInfo_whenAppUserNameSet_returnsDisplayNameInSelfContext() throws Exception {
        insertUser(20260004L, "me-user", "pw", false, "OPS", "Display Name", "E-0004");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        session.setAttribute("userId", 20260004L);
        session.setAttribute("isSystemAdmin", false);

        LoginResponse response = authService.getCurrentUserInfo(request);

        assertThat(response).isNotNull();
        assertThat(response.getSelfContext()).isNotNull();
        assertThat(response.getSelfContext().getUserId()).isEqualTo(20260004L);
        assertThat(response.getSelfContext().getUsername()).isEqualTo("Display Name");
        assertThat(response.getSelfContext().getEmployeeNumber()).isEqualTo("E-0004");
    }

    @Test
    void login_withWrongUserId_throwsInvalidCredentials() throws Exception {
        insertUser(20260001L, "self-user", "pw", false, "D01", null, "E-0001");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        com.logmng.exception.CustomException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.logmng.exception.CustomException.class,
                () -> authService.login(new LoginRequest(999L, "pw"), request));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void login_withWrongPassword_throwsInvalidCredentials() throws Exception {
        insertUser(20260001L, "self-user", "pw", false, "D01", null, "E-0001");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        com.logmng.exception.CustomException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.logmng.exception.CustomException.class,
                () -> authService.login(new LoginRequest(20260001L, "wrong"), request));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void login_whenSoftDeleted_throwsUserAccountDisabled() throws Exception {
        insertUser(20260005L, "deleted-user", "pw", false, "D01", null, "E-0005");
        softDeleteUser(20260005L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        com.logmng.exception.CustomException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.logmng.exception.CustomException.class,
                () -> authService.login(new LoginRequest(20260005L, "pw"), request));
        assertThat(ex.getErrorCode()).isEqualTo("USER_ACCOUNT_DISABLED");
    }

    @Test
    void login_activeUser_succeeds_deletedAtFilterDoesNotExclude() throws Exception {
        insertUser(20260006L, "active-user", "secret", false, "D02", null, "E-0006");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        LoginResponse response = authService.login(new LoginRequest(20260006L, "secret"), request);
        assertThat(response.getUsername()).isEqualTo("active-user");
        assertThat(response.getUserId()).isEqualTo(20260006L);
        assertThat(response.getSelfContext()).isNotNull();
        assertThat(response.getSelfContext().getUserId()).isEqualTo(20260006L);
        assertThat(response.getSelfContext().getEmployeeNumber()).isEqualTo("E-0006");
    }

    @Test
    void login_withEmployeeNumber_primaryPath_succeeds() throws Exception {
        insertUser(20260020L, "emp-user", "pw", false, "D01", null, " EMP-020 ");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        LoginResponse response = authService.login(new LoginRequest("EMP-020", "pw"), request);

        assertThat(response.getUserId()).isEqualTo(20260020L);
        assertThat(response.getUsername()).isEqualTo("emp-user");
        assertThat(response.getSelfContext()).isNotNull();
        assertThat(response.getSelfContext().getEmployeeNumber()).isEqualTo("EMP-020");
    }

    @Test
    void login_withBothEmployeeNumberAndUserId_throwsInvalidInput() throws Exception {
        insertUser(20260021L, "both-user", "pw", false, "D01", null, "EMP-021");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        LoginRequest body = new LoginRequest(20260021L, "pw");
        body.setEmployeeNumber("EMP-021");

        com.logmng.exception.CustomException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.logmng.exception.CustomException.class,
                () -> authService.login(body, request));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_INPUT");
    }

    @Test
    void login_withNeitherEmployeeNumberNorUserId_throwsInvalidInput() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        LoginRequest body = new LoginRequest();
        body.setPassword("pw");

        com.logmng.exception.CustomException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.logmng.exception.CustomException.class,
                () -> authService.login(body, request));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_INPUT");
    }

    @Test
    void login_withDuplicatedActiveEmployeeNumber_throwsDuplicatedCode() throws Exception {
        insertUser(20260022L, "dup-user-1", "pw", false, "D01", null, "EMP-DUP");
        insertUser(20260023L, "dup-user-2", "pw", false, "D01", null, " EMP-DUP ");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        com.logmng.exception.CustomException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.logmng.exception.CustomException.class,
                () -> authService.login(new LoginRequest("EMP-DUP", "pw"), request));
        assertThat(ex.getErrorCode()).isEqualTo("USER_EMPLOYEE_NUMBER_DUPLICATED");
    }

    @Test
    void login_adMode_withEmployeeNumberOrUserId_rejectedAsInvalidInput() throws Exception {
        AuthService adAuth = authServiceWithLoginMode("ad");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        LoginRequest withEmployeeNumber = new LoginRequest("EMP-030", "pw");
        withEmployeeNumber.setPrincipal("principal");
        com.logmng.exception.CustomException ex1 = org.junit.jupiter.api.Assertions.assertThrows(
                com.logmng.exception.CustomException.class,
                () -> adAuth.login(withEmployeeNumber, request));
        assertThat(ex1.getErrorCode()).isEqualTo("INVALID_INPUT");

        LoginRequest withUserId = new LoginRequest(20260030L, "pw");
        withUserId.setPrincipal("principal");
        com.logmng.exception.CustomException ex2 = org.junit.jupiter.api.Assertions.assertThrows(
                com.logmng.exception.CustomException.class,
                () -> adAuth.login(withUserId, request));
        assertThat(ex2.getErrorCode()).isEqualTo("INVALID_INPUT");
    }

    @Test
    void changeOwnPassword_success_updatesStoredHash() throws Exception {
        insertUser(20260010L, "pwd-user", "old-secret", false, "D01", null);
        ChangeMyPasswordRequest req = new ChangeMyPasswordRequest();
        req.setCurrentPassword("old-secret");
        req.setNewPassword("new-secret");
        req.setConfirmNewPassword("new-secret");
        authService.changeOwnPassword(requestWithSessionUserId(20260010L), req);
        assertThat(selectPasswordHash(20260010L)).isEqualTo("new-secret");
    }

    @Test
    void changeOwnPassword_wrongCurrent_throwsWrongPassword() throws Exception {
        insertUser(20260011L, "u-pwd-1", "secret", false, "D01", null);
        ChangeMyPasswordRequest req = new ChangeMyPasswordRequest();
        req.setCurrentPassword("wrong");
        req.setNewPassword("new-secret");
        req.setConfirmNewPassword("new-secret");
        assertThatThrownBy(() -> authService.changeOwnPassword(requestWithSessionUserId(20260011L), req))
                .isInstanceOf(com.logmng.exception.CustomException.class)
                .satisfies(ex -> assertThat(((com.logmng.exception.CustomException) ex).getErrorCode()).isEqualTo("WRONG_PASSWORD"));
    }

    @Test
    void changeOwnPassword_whenUnauthenticated_throwsUnauthorized() {
        ChangeMyPasswordRequest req = new ChangeMyPasswordRequest();
        req.setCurrentPassword("a");
        req.setNewPassword("newpass1");
        req.setConfirmNewPassword("newpass1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> authService.changeOwnPassword(request, req))
                .isInstanceOf(com.logmng.exception.CustomException.class)
                .satisfies(ex -> assertThat(((com.logmng.exception.CustomException) ex).getErrorCode()).isEqualTo("UNAUTHORIZED"));
    }

    @Test
    void changeOwnPassword_confirmMismatch_throwsInvalidInput() throws Exception {
        insertUser(20260012L, "u-pwd-2", "secret", false, "D01", null);
        ChangeMyPasswordRequest req = new ChangeMyPasswordRequest();
        req.setCurrentPassword("secret");
        req.setNewPassword("new-secret");
        req.setConfirmNewPassword("other");
        assertThatThrownBy(() -> authService.changeOwnPassword(requestWithSessionUserId(20260012L), req))
                .isInstanceOf(com.logmng.exception.CustomException.class)
                .satisfies(ex -> assertThat(((com.logmng.exception.CustomException) ex).getErrorCode()).isEqualTo("INVALID_INPUT"));
    }

    @Test
    void changeOwnPassword_sameAsCurrent_throwsInvalidInput() throws Exception {
        insertUser(20260013L, "u-pwd-3", "same", false, "D01", null);
        ChangeMyPasswordRequest req = new ChangeMyPasswordRequest();
        req.setCurrentPassword("same");
        req.setNewPassword("same");
        req.setConfirmNewPassword("same");
        assertThatThrownBy(() -> authService.changeOwnPassword(requestWithSessionUserId(20260013L), req))
                .isInstanceOf(com.logmng.exception.CustomException.class)
                .satisfies(ex -> assertThat(((com.logmng.exception.CustomException) ex).getErrorCode()).isEqualTo("INVALID_INPUT"));
    }

    @Test
    void changeOwnPassword_whenAdMode_throwsForbidden() throws Exception {
        insertUser(20260014L, "u-pwd-4", "secret", false, "D01", null);
        AuthService adAuth = authServiceWithLoginMode("ad");
        ChangeMyPasswordRequest req = new ChangeMyPasswordRequest();
        req.setCurrentPassword("secret");
        req.setNewPassword("new-secret");
        req.setConfirmNewPassword("new-secret");
        assertThatThrownBy(() -> adAuth.changeOwnPassword(requestWithSessionUserId(20260014L), req))
                .isInstanceOf(com.logmng.exception.CustomException.class)
                .satisfies(ex -> assertThat(((com.logmng.exception.CustomException) ex).getErrorCode()).isEqualTo("PASSWORD_CHANGE_NOT_ALLOWED"));
    }

    @Test
    void changeOwnPassword_whenExternalIdentityLinked_throwsForbidden() throws Exception {
        insertUser(20260015L, "u-pwd-5", "secret", false, "D01", null);
        insertExternalIdentity(20260015L, "HR", "EXT-1");
        ChangeMyPasswordRequest req = new ChangeMyPasswordRequest();
        req.setCurrentPassword("secret");
        req.setNewPassword("new-secret");
        req.setConfirmNewPassword("new-secret");
        assertThatThrownBy(() -> authService.changeOwnPassword(requestWithSessionUserId(20260015L), req))
                .isInstanceOf(com.logmng.exception.CustomException.class)
                .satisfies(ex -> assertThat(((com.logmng.exception.CustomException) ex).getErrorCode()).isEqualTo("PASSWORD_CHANGE_NOT_ALLOWED"));
    }

    /** TC-01/02 (AuthService gate mirrors interceptor): permission-group screens satisfy canAccessUserManagementView. */
    @Test
    void canAccessUserManagementView_permissionGroupScreenMatrixOnly_returnsTrue() {
        LoginResponse user = new LoginResponse();
        user.setIsSystemAdmin(false);
        user.setAllowedScreenIds(List.of(ScreenConstants.PERMISSION_GROUP_SCREEN_MATRIX));
        AuthService auth = new FixedLoginUserAuthService(user);
        assertThat(auth.canAccessUserManagementView(new MockHttpServletRequest())).isTrue();
    }

    @Test
    void canAccessUserManagementView_permissionGroupManagementOnly_returnsTrue() {
        LoginResponse user = new LoginResponse();
        user.setIsSystemAdmin(false);
        user.setAllowedScreenIds(List.of(ScreenConstants.PERMISSION_GROUP_MANAGEMENT));
        AuthService auth = new FixedLoginUserAuthService(user);
        assertThat(auth.canAccessUserManagementView(new MockHttpServletRequest())).isTrue();
    }

    @Test
    void canAccessUserManagementView_searchHistoryOnly_returnsFalse() {
        LoginResponse user = new LoginResponse();
        user.setIsSystemAdmin(false);
        user.setAllowedScreenIds(List.of(ScreenConstants.SEARCH_HISTORY));
        AuthService auth = new FixedLoginUserAuthService(user);
        assertThat(auth.canAccessUserManagementView(new MockHttpServletRequest())).isFalse();
    }

    @Test
    void hasWriteForManagementScreens_permissionGroupScreenMatrixWrite_returnsTrue() {
        LoginResponse user = new LoginResponse();
        user.setIsSystemAdmin(false);
        user.setScreenFunctions(Map.of(
                ScreenConstants.PERMISSION_GROUP_SCREEN_MATRIX,
                new ScreenFunctionCapability(true, true, false)));
        AuthService auth = new FixedLoginUserAuthService(user);
        assertThat(auth.hasWriteForManagementScreens(new MockHttpServletRequest())).isTrue();
    }

    @Test
    void hasWriteForManagementScreens_permissionGroupScreenMatrixReadOnly_returnsFalse() {
        LoginResponse user = new LoginResponse();
        user.setIsSystemAdmin(false);
        user.setScreenFunctions(Map.of(
                ScreenConstants.PERMISSION_GROUP_SCREEN_MATRIX,
                new ScreenFunctionCapability(true, false, false)));
        AuthService auth = new FixedLoginUserAuthService(user);
        assertThat(auth.hasWriteForManagementScreens(new MockHttpServletRequest())).isFalse();
    }

    /**
     * Minimal AuthService for policy unit tests: returns a fixed {@link LoginResponse} from {@link #getCurrentUserInfo}.
     */
    private static final class FixedLoginUserAuthService extends AuthService {
        private final LoginResponse loginUser;

        FixedLoginUserAuthService(LoginResponse loginUser) {
            super(null, null, null, null, new AuthProperties(), null, null);
            this.loginUser = loginUser;
        }

        @Override
        public LoginResponse getCurrentUserInfo(jakarta.servlet.http.HttpServletRequest request) {
            return loginUser;
        }
    }
}
