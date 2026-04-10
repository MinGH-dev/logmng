package com.logmng.controller;

import com.logmng.config.HrSyncPocProperties;
import com.logmng.service.HrSyncPocService;
import com.logmng.service.StubAuthServiceForUserController;
import com.logmng.testsupport.StubJdbcTemplateForHrSyncPoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrSyncPocUserMgmtControllerTest {

    private MockMvc mockMvc;
    private HrSyncPocProperties props;
    private StubJdbcTemplateForHrSyncPoc jdbc;
    private HrSyncPocService svc;
    private StubAuthServiceForUserController auth;

    @BeforeEach
    void setUp() {
        props = new HrSyncPocProperties();
        jdbc = new StubJdbcTemplateForHrSyncPoc();
        svc = new HrSyncPocService(jdbc);
        auth = new StubAuthServiceForUserController();
        HrSyncPocUserMgmtController controller = new HrSyncPocUserMgmtController(props, svc, auth);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void replicaDepartmentsTree_whenPocDisabled_returns403PocDisabled() throws Exception {
        props.setEnabled(false);
        auth.setCheckAuth(true);
        mockMvc.perform(get("/api/hr-sync/poc/user-mgmt/replica-departments/tree").sessionAttr("username", "u1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POC_DISABLED"));
    }

    @Test
    void replicaDepartmentsTree_unauthorized_returns401() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(false);
        mockMvc.perform(get("/api/hr-sync/poc/user-mgmt/replica-departments/tree"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void replicaDepartmentsTree_whenPocEnabled_returnsNestedTree() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        jdbc.setReplicaDeptRows(
                List.of(
                        new StubJdbcTemplateForHrSyncPoc.ReplicaDeptRow("D-R", null, "Root"),
                        new StubJdbcTemplateForHrSyncPoc.ReplicaDeptRow("D-CH", "D-R", "Child")));

        mockMvc.perform(get("/api/hr-sync/poc/user-mgmt/replica-departments/tree").sessionAttr("username", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceSystem").value("HR_SAMPLE"))
                .andExpect(jsonPath("$.data.roots[0].departmentKey").value("D-R"))
                .andExpect(jsonPath("$.data.roots[0].children[0].departmentKey").value("D-CH"));
    }

    @Test
    void replicaUsers_filtersByDepartmentKey() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        jdbc.setReplicaUsersQueryResults(
                1L,
                List.of(
                        new StubJdbcTemplateForHrSyncPoc.ReplicaUserRow(
                                "E-10001",
                                "20261001",
                                "Sample Alpha",
                                "Developer",
                                "D-SALES-001",
                                "Sample Sales Division",
                                true,
                                "poc-snap-20260408-A")));

        mockMvc.perform(
                        get("/api/hr-sync/poc/user-mgmt/replica-users")
                                .param("departmentKey", "D-SALES-001")
                                .param("snapshotId", "poc-snap-20260408-A")
                                .sessionAttr("username", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employees[0].externalEmployeeId").value("E-10001"))
                .andExpect(jsonPath("$.data.employees[0].departmentKey").value("D-SALES-001"))
                .andExpect(jsonPath("$.data.employees[0].isActive").value(true))
                .andExpect(jsonPath("$.data.snapshotId").value("poc-snap-20260408-A"))
                .andExpect(jsonPath("$.data.departmentKey").value("D-SALES-001"));
    }

    @Test
    void migratePreview_emptyBody_returns200Stub() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        mockMvc.perform(
                        post("/api/hr-sync/poc/user-mgmt/actions/migrate-preview")
                                .sessionAttr("username", "u1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.persisted").value(false))
                .andExpect(jsonPath("$.data.messageCode").value("POC_ACTION_NOT_PERSISTED"));
    }

    @Test
    void migratePreview_whenPocDisabled_returns403() throws Exception {
        props.setEnabled(false);
        auth.setCheckAuth(true);
        mockMvc.perform(
                        post("/api/hr-sync/poc/user-mgmt/actions/migrate-preview")
                                .sessionAttr("username", "u1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POC_DISABLED"));
    }
}
