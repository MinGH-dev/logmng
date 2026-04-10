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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrSyncPocControllerTest {

    private MockMvc mockMvc;
    private HrSyncPocProperties props;
    private StubJdbcTemplateForHrSyncPoc jdbc;
    private HrSyncPocService svc;
    private StubAuthServiceForUserController auth;

    @BeforeEach
    void setUp() {
        props = new HrSyncPocProperties();
        jdbc = new StubJdbcTemplateForHrSyncPoc();
        jdbc.setCountResult(0L);
        svc = new HrSyncPocService(jdbc);
        auth = new StubAuthServiceForUserController();
        HrSyncPocController controller = new HrSyncPocController(props, svc, auth);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.logmng.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void getConfig_whenPocDisabled_returns200WithFlagsFromProperties() throws Exception {
        props.setEnabled(false);
        props.setApplyEnabled(false);
        props.setDefaultMode("PREVIEW_ONLY");
        auth.setCheckAuth(true);
        mockMvc.perform(get("/api/hr-sync/poc/config").sessionAttr("username", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pocEnabled").value(false))
                .andExpect(jsonPath("$.data.defaultMode").value("PREVIEW_ONLY"))
                .andExpect(jsonPath("$.data.applyEnabled").value(false));
    }

    @Test
    void preview_whenPocDisabled_returns403PocDisabled() throws Exception {
        props.setEnabled(false);
        auth.setCheckAuth(true);
        jdbc.resetStats();
        mockMvc.perform(post("/api/hr-sync/poc/preview")
                        .sessionAttr("username", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POC_DISABLED"));
        assertThat(jdbc.getQueryForObjectNoArgCalls()).isZero();
    }

    @Test
    void getConfig_whenPocEnabled_returns200() throws Exception {
        props.setEnabled(true);
        props.setApplyEnabled(true);
        props.setDefaultMode("PREVIEW_ONLY");
        auth.setCheckAuth(true);
        mockMvc.perform(get("/api/hr-sync/poc/config").sessionAttr("username", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pocEnabled").value(true))
                .andExpect(jsonPath("$.data.defaultMode").value("PREVIEW_ONLY"))
                .andExpect(jsonPath("$.data.applyEnabled").value(true));
    }

    @Test
    void preview_whenPocEnabled_returns200Shape() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        jdbc.setCountResult(99L);
        jdbc.setCountForSnapshot("snap-x", 2L);

        mockMvc.perform(post("/api/hr-sync/poc/preview")
                        .sessionAttr("username", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"snapshotId\":\"snap-x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.previewId").exists())
                .andExpect(jsonPath("$.data.snapshotId").value("snap-x"))
                .andExpect(jsonPath("$.data.classificationCounts.TRANSFER").value(0))
                .andExpect(jsonPath("$.data.classificationCounts.UNCHANGED").value(2))
                .andExpect(jsonPath("$.data.riskTier").value("AUTO"))
                .andExpect(jsonPath("$.data.upstreamGateStatus").value("PLACEHOLDER"))
                .andExpect(jsonPath("$.data.messageCode").value("HR_SYNC_POC_PREVIEW_OK"));

        assertThat(jdbc.getQueryForObjectNoArgCalls()).isZero();
        assertThat(jdbc.getQueryForObjectSnapshotCountCalls()).isEqualTo(1);
    }

    @Test
    void preview_whenScopedCountFails_returns503HrSyncPocPreviewFailed() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        jdbc.setCountForSnapshot("snap-x", 1L);
        jdbc.setFailSnapshotCount(true);

        mockMvc.perform(post("/api/hr-sync/poc/preview")
                        .sessionAttr("username", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"snapshotId\":\"snap-x\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("HR_SYNC_POC_PREVIEW_FAILED"));
    }

    @Test
    void preview_snapshotIdNonString_returns400ValidationError() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        jdbc.resetStats();
        mockMvc.perform(post("/api/hr-sync/poc/preview")
                        .sessionAttr("username", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"snapshotId\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.getQueryForObjectNoArgCalls()).isZero();
    }

    @Test
    void preview_invalidJson_returns400ValidationError() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        mockMvc.perform(post("/api/hr-sync/poc/preview")
                        .sessionAttr("username", "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getConfig_unauthorizedWhenNoSessionHandledByController() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(false);
        mockMvc.perform(get("/api/hr-sync/poc/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listSnapshots_whenPocDisabled_returns403() throws Exception {
        props.setEnabled(false);
        auth.setCheckAuth(true);
        mockMvc.perform(get("/api/hr-sync/poc/snapshots").sessionAttr("username", "u1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POC_DISABLED"));
    }

    @Test
    void listSnapshots_whenPocEnabled_returns200WithItems() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        jdbc.setSnapshotRows(List.of(
                new StubJdbcTemplateForHrSyncPoc.SnapshotRow(
                        "poc-snap-20260408-A",
                        3L,
                        Timestamp.from(Instant.parse("2026-04-08T02:00:00Z"))),
                new StubJdbcTemplateForHrSyncPoc.SnapshotRow("poc-snap-20260408-B", 2L, null)));

        mockMvc.perform(get("/api/hr-sync/poc/snapshots").sessionAttr("username", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.snapshots[0].snapshotId").value("poc-snap-20260408-A"))
                .andExpect(jsonPath("$.data.snapshots[0].label").value("PoC sample A"))
                .andExpect(jsonPath("$.data.snapshots[0].employeeCount").value(3))
                .andExpect(jsonPath("$.data.snapshots[0].maxImportedAt").exists())
                .andExpect(jsonPath("$.data.snapshots[1].snapshotId").value("poc-snap-20260408-B"))
                .andExpect(jsonPath("$.data.snapshots[1].label").value("PoC sample B"))
                .andExpect(jsonPath("$.data.snapshots[1].employeeCount").value(2))
                .andExpect(jsonPath("$.data.snapshots[1].maxImportedAt").doesNotExist());
    }

    @Test
    void listEmployees_unknownSnapshot_returns404NotFound() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        jdbc.setCountForSnapshot("poc-snap-20260408-Z", 0L);

        mockMvc.perform(get("/api/hr-sync/poc/snapshots/poc-snap-20260408-Z/employees").sessionAttr("username", "u1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void listEmployees_sizeOverMax_returns400() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        mockMvc.perform(get("/api/hr-sync/poc/snapshots/poc-snap-20260408-A/employees")
                        .param("page", "1")
                        .param("size", "101")
                        .sessionAttr("username", "u1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listEmployees_whenPocEnabled_returnsPageAndFullEmployeeNumber() throws Exception {
        props.setEnabled(true);
        auth.setCheckAuth(true);
        jdbc.setCountForSnapshot("poc-snap-20260408-A", 3L);
        jdbc.setEmployeesForSnapshot(
                "poc-snap-20260408-A",
                List.of(new StubJdbcTemplateForHrSyncPoc.EmployeeRow(
                        "Sample User One",
                        "Analyst",
                        "EXT-DEPT-001",
                        "Sample Dept A",
                        true,
                        "20261001")));

        mockMvc.perform(get("/api/hr-sync/poc/snapshots/poc-snap-20260408-A/employees")
                        .sessionAttr("username", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.snapshotId").value("poc-snap-20260408-A"))
                .andExpect(jsonPath("$.data.employees[0].displayName").value("Sample User One"))
                .andExpect(jsonPath("$.data.employees[0].departmentKey").value("EXT-DEPT-001"))
                .andExpect(jsonPath("$.data.employees[0].employeeNumber").value("20261001"))
                .andExpect(jsonPath("$.data.pagination.currentPage").value(1))
                .andExpect(jsonPath("$.data.pagination.totalPages").value(1))
                .andExpect(jsonPath("$.data.pagination.totalCount").value(3));
    }
}
