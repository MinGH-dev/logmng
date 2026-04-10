package com.logmng.service;

import com.logmng.dto.response.HrSyncPocEmployeesPageResponse;
import com.logmng.dto.response.HrSyncPocPreviewResponse;
import com.logmng.dto.response.HrSyncPocReplicaUsersPageResponse;
import com.logmng.dto.response.HrSyncPocSnapshotsResponse;
import com.logmng.exception.CustomException;
import com.logmng.testsupport.StubJdbcTemplateForHrSyncPoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HrSyncPocServiceTest {

    private StubJdbcTemplateForHrSyncPoc jdbc;
    private HrSyncPocService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubJdbcTemplateForHrSyncPoc();
        jdbc.setCountResult(0L);
        jdbc.setFailGlobalCount(false);
        jdbc.setFailSnapshotCount(false);
        service = new HrSyncPocService(jdbc);
    }

    /** TC-01: counts scoped to snapshot A, not global A+B. */
    @Test
    void buildPreview_withSnapshotId_countsOnlyThatSnapshotRows() {
        jdbc.setCountResult(999L);
        jdbc.setCountForSnapshot("poc-snap-20260408-A", 3L);
        jdbc.setCountForSnapshot("poc-snap-20260408-B", 5L);

        HrSyncPocPreviewResponse r = service.buildPreview("poc-snap-20260408-A", null);

        assertThat(r.getSnapshotId()).isEqualTo("poc-snap-20260408-A");
        assertThat(r.getClassificationCounts().getUnchanged()).isEqualTo(3);
        assertThat(jdbc.getQueryForObjectSnapshotCountCalls()).isEqualTo(1);
        assertThat(jdbc.getQueryForObjectNoArgCalls()).isZero();
    }

    /** TC-02: different snapshot yields different scoped count. */
    @Test
    void buildPreview_withSnapshotIdB_countsOnlySnapshotB() {
        jdbc.setCountResult(999L);
        jdbc.setCountForSnapshot("poc-snap-20260408-A", 3L);
        jdbc.setCountForSnapshot("poc-snap-20260408-B", 5L);

        HrSyncPocPreviewResponse r = service.buildPreview("poc-snap-20260408-B", null);

        assertThat(r.getClassificationCounts().getUnchanged()).isEqualTo(5);
        assertThat(jdbc.getQueryForObjectSnapshotCountCalls()).isEqualTo(1);
        assertThat(jdbc.getQueryForObjectNoArgCalls()).isZero();
    }

    @Test
    void buildPreview_usesSnapshotId_echoAndReadOnlyCount() {
        jdbc.setCountForSnapshot("snap-1", 4L);

        HrSyncPocPreviewResponse r = service.buildPreview("snap-1", null);

        assertThat(r.getSnapshotId()).isEqualTo("snap-1");
        assertThat(r.getPreviewId()).isNotBlank();
        assertThat(r.getClassificationCounts().getUnchanged()).isEqualTo(4);
        assertThat(r.getMessageCode()).isEqualTo("HR_SYNC_POC_PREVIEW_OK");
        assertThat(jdbc.getQueryForObjectSnapshotCountCalls()).isEqualTo(1);
        assertThat(jdbc.getQueryForObjectNoArgCalls()).isZero();
    }

    @Test
    void buildPreview_fallsBackToIngestRunId_whenSnapshotMissing() {
        jdbc.setCountForSnapshot("run-9", 0L);

        HrSyncPocPreviewResponse r = service.buildPreview(null, "run-9");

        assertThat(r.getSnapshotId()).isEqualTo("run-9");
        assertThat(r.getClassificationCounts().getUnchanged()).isZero();
        assertThat(jdbc.getQueryForObjectSnapshotCountCalls()).isEqualTo(1);
    }

    /** TC-03: snapshot present in request but zero ext_employee rows → legitimate zeros. */
    @Test
    void buildPreview_snapshotWithNoRows_returnsLegitimateZeros() {
        jdbc.setCountForSnapshot("empty-snap", 0L);

        HrSyncPocPreviewResponse r = service.buildPreview("empty-snap", null);

        assertThat(r.getClassificationCounts().getUnchanged()).isZero();
        assertThat(r.getClassificationCounts().getTransfer()).isZero();
    }

    @Test
    void buildPreview_noSnapshotUsesGlobalCount_backwardCompatible() {
        jdbc.setCountResult(7L);

        HrSyncPocPreviewResponse r = service.buildPreview(null, null);

        assertThat(r.getSnapshotId()).isEmpty();
        assertThat(r.getClassificationCounts().getUnchanged()).isEqualTo(7);
        assertThat(jdbc.getQueryForObjectNoArgCalls()).isEqualTo(1);
        assertThat(jdbc.getQueryForObjectSnapshotCountCalls()).isZero();
    }

    /** TC-04: counting failure → non-200 error, not silent zeros. */
    @Test
    void buildPreview_whenScopedCountQueryFails_throwsHrSyncPocPreviewFailed() {
        jdbc.setCountForSnapshot("snap-1", 1L);
        jdbc.setFailSnapshotCount(true);

        assertThatThrownBy(() -> service.buildPreview("snap-1", null))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo("HR_SYNC_POC_PREVIEW_FAILED");
                    assertThat(ce.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void buildPreview_whenGlobalCountQueryFails_throwsHrSyncPocPreviewFailed() {
        jdbc.setFailGlobalCount(true);

        assertThatThrownBy(() -> service.buildPreview(null, null))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo("HR_SYNC_POC_PREVIEW_FAILED"));
    }

    @Test
    void loadSnapshots_whenQueryFails_returnsEmptyList() {
        jdbc.setFailListSnapshots(true);

        HrSyncPocSnapshotsResponse r = service.loadSnapshots();

        assertThat(r.getSnapshots()).isEmpty();
    }

    @Test
    void loadEmployeesPage_whenCountZero_throwsNotFound() {
        jdbc.setCountForSnapshot("missing", 0L);

        assertThatThrownBy(() -> service.loadEmployeesPage("missing", 1, 20))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("NOT_FOUND"));
    }

    /**
     * PoC list must echo {@code ext_employee.employee_number} as plain text (no masking). Demo seed uses 8-digit 2026xxxx.
     */
    @Test
    void loadEmployeesPage_passesThroughFullEightDigitEmployeeNumberUnmasked() {
        jdbc.setCountForSnapshot("poc-snap-20260408-A", 1L);
        jdbc.setEmployeesForSnapshot(
                "poc-snap-20260408-A",
                List.of(
                        new StubJdbcTemplateForHrSyncPoc.EmployeeRow(
                                "Sample Alpha", "Developer", "D-SALES-001", "Sample Sales Division", true, "20261001")));

        HrSyncPocEmployeesPageResponse page = service.loadEmployeesPage("poc-snap-20260408-A", 1, 20);

        assertThat(page.getEmployees()).hasSize(1);
        assertThat(page.getEmployees().get(0).getEmployeeNumber()).isEqualTo("20261001");
    }

    @Test
    void normalizeEmployeeNumber_trimsWhitespaceOnly() {
        assertThat(HrSyncPocService.normalizeEmployeeNumber("  20261001  ")).isEqualTo("20261001");
        assertThat(HrSyncPocService.normalizeEmployeeNumber("20261999")).isEqualTo("20261999");
    }

    @Test
    void loadEmployeesPage_returnsRowsAndPagination() {
        jdbc.setCountForSnapshot("poc-snap-20260408-A", 5L);
        jdbc.setEmployeesForSnapshot(
                "poc-snap-20260408-A",
                List.of(
                        new StubJdbcTemplateForHrSyncPoc.EmployeeRow("a", "j", "d", "n", true, "1111"),
                        new StubJdbcTemplateForHrSyncPoc.EmployeeRow("b", "j", "d", "n", true, "2222"),
                        new StubJdbcTemplateForHrSyncPoc.EmployeeRow(
                                "N", "J", "D1", "Dept", false, "10009999")));

        HrSyncPocEmployeesPageResponse page = service.loadEmployeesPage("poc-snap-20260408-A", 2, 2);

        assertThat(page.getSnapshotId()).isEqualTo("poc-snap-20260408-A");
        assertThat(page.getEmployees()).hasSize(1);
        assertThat(page.getEmployees().get(0).getDisplayName()).isEqualTo("N");
        assertThat(page.getEmployees().get(0).getEmployeeNumber()).isEqualTo("10009999");
        assertThat(page.getEmployees().get(0).isActive()).isFalse();
        assertThat(page.getPagination().getCurrentPage()).isEqualTo(2);
        assertThat(page.getPagination().getTotalCount()).isEqualTo(5L);
        assertThat(page.getPagination().getTotalPages()).isEqualTo(3);
    }

    @Test
    void deriveSnapshotLabel_trailingSingleLetter() {
        assertThat(HrSyncPocService.deriveSnapshotLabel("poc-snap-20260408-A")).isEqualTo("PoC sample A");
    }

    @Test
    void formatImportedAtIso_outputsUtcOffset() {
        java.sql.Timestamp ts = java.sql.Timestamp.from(java.time.Instant.parse("2026-04-08T02:00:05Z"));
        assertThat(HrSyncPocService.formatImportedAtIso(ts)).isEqualTo("2026-04-08T02:00:05Z");
    }

    @Test
    void validateEmployeePageParams_rejectsSizeOver100() {
        assertThatThrownBy(() -> HrSyncPocService.validateEmployeePageParams(1, 101))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void buildReplicaDepartmentTree_ordersRootsAndChildrenByName() {
        List<HrSyncPocService.ExtDeptFlat> rows = List.of(
                new HrSyncPocService.ExtDeptFlat("D-B", null, "Beta Root"),
                new HrSyncPocService.ExtDeptFlat("D-A", null, "Alpha Root"),
                new HrSyncPocService.ExtDeptFlat("D-C", "D-A", "Child Z"),
                new HrSyncPocService.ExtDeptFlat("D-D", "D-A", "Child A"));
        var roots = HrSyncPocService.buildReplicaDepartmentTree(rows);
        assertThat(roots).hasSize(2);
        assertThat(roots.get(0).getDepartmentKey()).isEqualTo("D-A");
        assertThat(roots.get(1).getDepartmentKey()).isEqualTo("D-B");
        assertThat(roots.get(0).getChildren()).hasSize(2);
        assertThat(roots.get(0).getChildren().get(0).getDepartmentKey()).isEqualTo("D-D");
        assertThat(roots.get(0).getChildren().get(1).getDepartmentKey()).isEqualTo("D-C");
    }

    @Test
    void loadReplicaDepartmentTree_returnsRootsFromJdbc() {
        jdbc.setReplicaDeptRows(
                List.of(
                        new StubJdbcTemplateForHrSyncPoc.ReplicaDeptRow("D-A", null, "Alpha"),
                        new StubJdbcTemplateForHrSyncPoc.ReplicaDeptRow("D-B", null, "Beta")));

        var tree = service.loadReplicaDepartmentTree("HR_SAMPLE");

        assertThat(tree.getSourceSystem()).isEqualTo("HR_SAMPLE");
        assertThat(tree.getRoots()).hasSize(2);
        assertThat(tree.getRoots().get(0).getDepartmentKey()).isEqualTo("D-A");
        assertThat(tree.getRoots().get(0).getChildren()).isEmpty();
    }

    @Test
    void loadReplicaUsersPage_whenSnapshotFilterAndNoRows_throwsNotFound() {
        jdbc.setReplicaUsersQueryResults(0L, List.of());
        assertThatThrownBy(() -> service.loadReplicaUsersPage("HR_SAMPLE", "poc-snap-Z", null, 1, 20))
                .isInstanceOf(CustomException.class)
                .satisfies(
                        ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void loadReplicaUsersPage_withoutSnapshot_emptyTotal_ok() {
        jdbc.setReplicaUsersQueryResults(0L, List.of());
        HrSyncPocReplicaUsersPageResponse p = service.loadReplicaUsersPage("HR_SAMPLE", null, null, 1, 20);
        assertThat(p.getSnapshotId()).isNull();
        assertThat(p.getEmployees()).isEmpty();
        assertThat(p.getPagination().getTotalCount()).isZero();
    }

    @Test
    void loadReplicaUsersPage_returnsReplicaUserRowFields() {
        jdbc.setReplicaUsersQueryResults(
                1L,
                List.of(
                        new StubJdbcTemplateForHrSyncPoc.ReplicaUserRow(
                                "E-1",
                                "20261001",
                                "Alice",
                                "Dev",
                                "D-1",
                                "Dept One",
                                true,
                                "poc-snap-A")));
        HrSyncPocReplicaUsersPageResponse p = service.loadReplicaUsersPage("HR_SAMPLE", null, null, 1, 20);
        assertThat(p.getEmployees()).hasSize(1);
        assertThat(p.getEmployees().get(0).getExternalEmployeeId()).isEqualTo("E-1");
        assertThat(p.getEmployees().get(0).isActive()).isTrue();
        assertThat(p.getEmployees().get(0).getSnapshotId()).isEqualTo("poc-snap-A");
    }

    @Test
    void normalizePocSourceSystem_blank_defaultsHrSample() {
        assertThat(HrSyncPocService.normalizePocSourceSystem(null)).isEqualTo("HR_SAMPLE");
        assertThat(HrSyncPocService.normalizePocSourceSystem("  ")).isEqualTo("HR_SAMPLE");
    }
}
