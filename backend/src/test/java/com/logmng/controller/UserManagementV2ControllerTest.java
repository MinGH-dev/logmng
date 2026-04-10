package com.logmng.controller;

import com.logmng.dto.response.LoginResponse;
import com.logmng.dto.request.UserManagementV2CreateDepartmentRequest;
import com.logmng.exception.CustomException;
import com.logmng.service.StubAuthServiceForUserController;
import com.logmng.service.DepartmentService;
import com.logmng.service.StubUserManagementV2Service;
import com.logmng.service.UserManagementV2Service;
import com.logmng.util.UserManagementReadScopeContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserManagementV2ControllerTest {

    private MockMvc mockMvc;
    private StubAuthServiceForUserManagementV2 authService;

    @BeforeEach
    void setUp() {
        authService = new StubAuthServiceForUserManagementV2();
        StubUserManagementV2Service service = new StubUserManagementV2Service();
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:umv2_controller_test;DB_CLOSE_DELAY=-1");
        DepartmentService departmentService = new DepartmentService(dataSource);
        UserManagementV2Controller controller = new UserManagementV2Controller(authService, service, dataSource, departmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void createRootDepartment_whenNoSession_returns401() throws Exception {
        authService.setCheckAuth(false);
        mockMvc.perform(post("/api/user-management-v2/departments/root")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"본부\",\"changeReason\":\"등록\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void createRootDepartment_whenNoWrite_returns403FunctionNotAllowed() throws Exception {
        authService.setCheckAuth(true);
        authService.setCanAccessUserManagementView(true);
        authService.setHasWriteForManagementScreens(false);
        mockMvc.perform(post("/api/user-management-v2/departments/root")
                        .sessionAttr("username", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"본부\",\"changeReason\":\"등록\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FUNCTION_NOT_ALLOWED"));
    }

    @Test
    void createRootDepartment_whenAllowed_returns201() throws Exception {
        authService.setCheckAuth(true);
        authService.setCanAccessUserManagementView(true);
        authService.setHasWriteForManagementScreens(true);
        mockMvc.perform(post("/api/user-management-v2/departments/root")
                        .sessionAttr("username", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"본부\",\"changeReason\":\"등록\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.departmentId").value("ROOT"));
    }

    @Test
    void createChildDepartment_pathVariable_returns201() throws Exception {
        authService.setCheckAuth(true);
        authService.setCanAccessUserManagementView(true);
        authService.setHasWriteForManagementScreens(true);
        mockMvc.perform(post("/api/user-management-v2/departments/PAR_V2/children")
                        .sessionAttr("username", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"팀\",\"code\":\"CHILD_V2\",\"changeReason\":\"등록\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.departmentId").value("PAR_V2_CHILD"));
    }

    @Test
    void createChildDepartment_parentInRequestBody_returns201() throws Exception {
        authService.setCheckAuth(true);
        authService.setCanAccessUserManagementView(true);
        authService.setHasWriteForManagementScreens(true);
        // Parent code contains "/" — path-variable style would not match this controller; body field must work.
        mockMvc.perform(post("/api/user-management-v2/departments/children")
                        .sessionAttr("username", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentDepartmentId\":\"PAR/ENT\",\"name\":\"팀\",\"code\":\"CHILD_SLASH\",\"changeReason\":\"등록\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.departmentId").value("PAR/ENT_CHILD"));
    }

    @Test
    void createChildDepartment_bodyMissingParent_returns400() throws Exception {
        authService.setCheckAuth(true);
        authService.setCanAccessUserManagementView(true);
        authService.setHasWriteForManagementScreens(true);
        mockMvc.perform(post("/api/user-management-v2/departments/children")
                        .sessionAttr("username", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"팀\",\"code\":\"C1\",\"changeReason\":\"등록\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void createChildDepartment_whenServiceNotFound_returns404WithErrorCode() throws Exception {
        authService.setCheckAuth(true);
        authService.setCanAccessUserManagementView(true);
        authService.setHasWriteForManagementScreens(true);
        UserManagementV2Service svc = new UserManagementV2Service(null, null, null) {
            @Override
            public Map<String, Object> createChildDepartment(
                    String parentDepartmentId,
                    UserManagementV2CreateDepartmentRequest body,
                    String actorUsername,
                    String clientIp,
                    String userAgent,
                    String requestPath,
                    UserManagementReadScopeContext scopeCtx) {
                throw CustomException.notFound("부서를 찾을 수 없습니다.", "DEPARTMENT_NOT_FOUND");
            }
        };
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:umv2_controller_test_nf;DB_CLOSE_DELAY=-1");
        UserManagementV2Controller controller = new UserManagementV2Controller(authService, svc, ds, new DepartmentService(ds));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
        mvc.perform(post("/api/user-management-v2/departments/MISSING/children")
                        .sessionAttr("username", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"팀\",\"code\":\"C1\",\"changeReason\":\"등록\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void deleteDepartment_whenNoWrite_returns403() throws Exception {
        authService.setCheckAuth(true);
        authService.setCanAccessUserManagementView(true);
        authService.setHasWriteForManagementScreens(false);
        mockMvc.perform(delete("/api/user-management-v2/departments/LEAF")
                        .sessionAttr("username", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeReason\":\"정리\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FUNCTION_NOT_ALLOWED"));
    }

    @Test
    void deleteDepartment_whenAllowed_returns200() throws Exception {
        authService.setCheckAuth(true);
        authService.setCanAccessUserManagementView(true);
        authService.setHasWriteForManagementScreens(true);
        mockMvc.perform(delete("/api/user-management-v2/departments/LEAF")
                        .sessionAttr("username", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeReason\":\"정리\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.departmentId").value("LEAF"));
    }

    @Test
    void createDirectUser_whenAllowed_returns201() throws Exception {
        authService.setCheckAuth(true);
        authService.setCanAccessUserManagementView(true);
        authService.setHasWriteForManagementScreens(true);
        mockMvc.perform(post("/api/user-management-v2/users/direct")
                        .sessionAttr("username", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":\"ROOT\",\"employeeNumber\":\"20269999\",\"name\":\"테스터\",\"rank\":\"대리\",\"permissionGroupId\":1,\"changeReason\":\"등록\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(20269999));
    }

    @Test
    void quickEntryOptions_whenViewOnly_returns200() throws Exception {
        authService.setCheckAuth(true);
        authService.setCanAccessUserManagementView(true);
        authService.setHasWriteForManagementScreens(false);
        mockMvc.perform(get("/api/user-management-v2/quick-entry/options")
                        .sessionAttr("username", "viewer1")
                        .param("fields", "employeeNumber,name")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employeeNumber.previous").value("20269999"));
    }

    static class StubAuthServiceForUserManagementV2 extends StubAuthServiceForUserController {
        private boolean hasWriteForManagementScreens = true;

        public void setHasWriteForManagementScreens(boolean value) {
            this.hasWriteForManagementScreens = value;
        }

        @Override
        public boolean hasWriteForManagementScreens(jakarta.servlet.http.HttpServletRequest request) {
            return hasWriteForManagementScreens;
        }

        @Override
        public LoginResponse getCurrentUserInfo(jakarta.servlet.http.HttpServletRequest request) {
            LoginResponse r = new LoginResponse();
            if (request != null && request.getSession(false) != null) {
                Object username = request.getSession().getAttribute("username");
                if (username != null && !username.toString().isBlank()) {
                    r.setUsername(username.toString().trim());
                    return r;
                }
            }
            r.setUsername("stub-user");
            return r;
        }
    }
}
