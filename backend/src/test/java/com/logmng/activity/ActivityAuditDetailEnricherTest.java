package com.logmng.activity;

import com.logmng.controller.PermissionGroupController;
import com.logmng.dto.request.PermissionGroupCreateRequest;
import com.logmng.dto.request.PermissionGroupUpdateRequest;
import com.logmng.dto.response.AllowedScreenItem;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.AssignUserToGroupResponse;
import com.logmng.dto.response.PermissionGroupResponse;
import com.logmng.service.PermissionGroupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TC-01–TC-07: permission-group audit {@code permissionGroupAuditV1} (spec activity-permission-group-audit).
 */
class ActivityAuditDetailEnricherTest {

    /** Anonymous subclass of controller — same pattern as Spring CGLIB proxy declaring type. */
    private static final class PermissionGroupControllerSubclass extends PermissionGroupController {
        PermissionGroupControllerSubclass() {
            super(null, null, null);
        }
    }

    private static final HttpServletRequest DUMMY_REQUEST = mock(HttpServletRequest.class);

    @AfterEach
    void tearDown() {
        PermissionGroupAuditContext.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pgV1(Map<String, Object> detail) {
        return (Map<String, Object>) detail.get("permissionGroupAuditV1");
    }

    private static void assertNoDenylistKeys(Object node) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = e.getKey() != null ? e.getKey().toString() : "";
                assertThat(ActivityAuditDetailEnricher.isDenylistedKey(key))
                        .as("denylist key must not appear: %s", key)
                        .isFalse();
                assertNoDenylistKeys(e.getValue());
            }
        } else if (node instanceof List<?> list) {
            for (Object o : list) {
                assertNoDenylistKeys(o);
            }
        }
    }

    @Test
    void enrichCreate_putsPermissionGroupAuditV1WithAfterSnapshot() {
        PermissionGroupCreateRequest req = new PermissionGroupCreateRequest();
        req.setCode("PG01");
        req.setName("n");
        req.setAllowedScreens(List.of(new AllowedScreenItem("main", null)));

        PermissionGroupResponse pr = new PermissionGroupResponse(99L, "PG01", "n", null, 0);
        AllowedScreenItem screen = new AllowedScreenItem("main", "team");
        screen.setRead(true);
        pr.setAllowedScreens(List.of(screen));

        ResponseEntity<ApiResponse<PermissionGroupResponse>> res =
                ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(pr));

        Object[] args = new Object[] { req, DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "create", args, res, detail);

        assertThat(detail.get("permissionGroupId")).isEqualTo(99L);
        assertThat(detail.get("permissionGroupCode")).isEqualTo("PG01");
        assertThat(detail.get("allowedScreenCount")).isEqualTo(1);

        Map<String, Object> v1 = pgV1(detail);
        assertThat(v1.get("schemaVersion")).isEqualTo("1");
        assertThat(v1.get("operation")).isEqualTo("CREATE");
        assertThat(v1.get("before")).isNull();
        assertThat(v1.get("permissionGroupId")).isEqualTo(99L);
        Map<String, Object> after = (Map<String, Object>) v1.get("after");
        assertThat(after.get("code")).isEqualTo("PG01");
        assertThat(after.get("name")).isEqualTo("n");
        assertThat(after.get("sortOrder")).isEqualTo(0);
        List<Map<String, Object>> screens = (List<Map<String, Object>>) after.get("allowedScreens");
        assertThat(screens).hasSize(1);
        assertThat(screens.get(0).get("screenId")).isEqualTo("main");
        assertNoDenylistKeys(detail);
    }

    @Test
    void enrichUpdate_putsBeforeAndAfterSnapshots() {
        PermissionGroupResponse before = new PermissionGroupResponse(5L, "CODE5", "old", "d0", 0);
        before.setAllowedScreens(List.of(new AllowedScreenItem("activity-log", "team")));
        PermissionGroupAuditContext.setBeforeState(before);

        PermissionGroupUpdateRequest body = new PermissionGroupUpdateRequest();
        body.setAllowedScreens(List.of(new AllowedScreenItem("activity-log", "all")));
        body.setChangeReason("reason");

        PermissionGroupResponse after = new PermissionGroupResponse(5L, "CODE5", "new", "d1", 1);
        after.setAllowedScreens(List.of(new AllowedScreenItem("activity-log", "all")));

        ResponseEntity<ApiResponse<PermissionGroupResponse>> res =
                ResponseEntity.ok(ApiResponse.success(after));

        Object[] args = new Object[] { 5L, body, DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "update", args, res, detail);

        assertThat(detail.get("permissionGroupId")).isEqualTo(5L);
        Map<String, Object> v1 = pgV1(detail);
        assertThat(v1.get("operation")).isEqualTo("UPDATE");
        assertThat(v1.get("changeReason")).isEqualTo("reason");
        Map<String, Object> b = (Map<String, Object>) v1.get("before");
        assertThat(b.get("name")).isEqualTo("old");
        Map<String, Object> a = (Map<String, Object>) v1.get("after");
        assertThat(a.get("name")).isEqualTo("new");
        assertThat(a.get("sortOrder")).isEqualTo(1);
        assertNoDenylistKeys(detail);
    }

    @Test
    void enrichDelete_putsBeforeAndNullAfter() {
        PermissionGroupResponse before = new PermissionGroupResponse(42L, "DEL", "n", null, 0);
        PermissionGroupAuditContext.setBeforeState(before);

        Object[] args = new Object[] { 42L, DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "delete", args, ResponseEntity.ok(ApiResponse.success(null)), detail);

        assertThat(detail.get("permissionGroupId")).isEqualTo(42L);
        Map<String, Object> v1 = pgV1(detail);
        assertThat(v1.get("operation")).isEqualTo("DELETE");
        assertThat(v1.get("after")).isNull();
        assertThat(((Map<?, ?>) v1.get("before")).get("code")).isEqualTo("DEL");
        assertNoDenylistKeys(detail);
    }

    @Test
    void enrichAssign_tc01_putsBeforeAndAfterSnapshotsWhenContextSet() {
        PermissionGroupResponse prevA = new PermissionGroupResponse(1L, "GA", "Group A", null, 1);
        prevA.setAllowedScreens(List.of(new AllowedScreenItem("activity-log", "team")));
        PermissionGroupAuditContext.setAssignPreviousState(prevA);

        PermissionGroupResponse afterB = new PermissionGroupResponse(7L, "G7", "Group B", "d", 2);
        afterB.setAllowedScreens(List.of(new AllowedScreenItem("main", "team")));
        PermissionGroupAuditContext.setAssignAfterState(afterB);

        AssignUserToGroupResponse assign = new AssignUserToGroupResponse(20260001L, 7L, "G7");
        ResponseEntity<ApiResponse<AssignUserToGroupResponse>> res =
                ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(assign));

        Object[] args = new Object[] { 7L, Map.of("userId", 20260001L), DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "assignUser", args, res, detail);

        assertThat(detail.get("targetUserId")).isEqualTo(20260001L);
        assertThat(detail.get("permissionGroupId")).isEqualTo(7L);
        assertThat(detail.get("permissionGroupCode")).isEqualTo("G7");
        Map<String, Object> v1 = pgV1(detail);
        assertThat(v1.get("operation")).isEqualTo("ASSIGN_USER");
        assertThat(v1.get("targetUserId")).isEqualTo(20260001L);
        Map<String, Object> before = (Map<String, Object>) v1.get("before");
        assertThat(before.get("code")).isEqualTo("GA");
        assertThat(before.get("name")).isEqualTo("Group A");
        Map<String, Object> after = (Map<String, Object>) v1.get("after");
        assertThat(after.get("code")).isEqualTo("G7");
        assertThat(after.get("name")).isEqualTo("Group B");
        assertThat(after.get("sortOrder")).isEqualTo(2);
        assertNoDenylistKeys(detail);
    }

    @Test
    void enrichAssign_tc02_beforeNullWhenNoPriorGroup() {
        PermissionGroupAuditContext.setAssignPreviousState(null);
        PermissionGroupResponse afterB = new PermissionGroupResponse(7L, "G7", "New", null, 0);
        PermissionGroupAuditContext.setAssignAfterState(afterB);

        AssignUserToGroupResponse assign = new AssignUserToGroupResponse(20260001L, 7L, "G7");
        ResponseEntity<ApiResponse<AssignUserToGroupResponse>> res =
                ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(assign));
        Object[] args = new Object[] { 7L, Map.of("userId", 20260001L), DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "assignUser", args, res, detail);

        Map<String, Object> v1 = pgV1(detail);
        assertThat(v1.get("before")).isNull();
        assertThat(((Map<?, ?>) v1.get("after")).get("code")).isEqualTo("G7");
        assertNoDenylistKeys(detail);
    }

    @Test
    void enrichAssign_loadsAfterViaPermissionGroupServiceWhenThreadLocalAfterMissing() {
        PermissionGroupResponse prevA = new PermissionGroupResponse(2L, "AUDIT", "감사", null, 1);
        PermissionGroupAuditContext.setAssignPreviousState(prevA);
        // Intentionally no setAssignAfterState — simulates lost ThreadLocal; enricher should call service

        PermissionGroupResponse general = new PermissionGroupResponse(5L, "GENERAL_USER", "일반 사용자 그룹", null, 0);
        general.setAllowedScreens(List.of(new AllowedScreenItem("activity-log", "team")));
        PermissionGroupService pgs = new PermissionGroupService(null, null) {
            @Override
            public PermissionGroupResponse findById(Long id) {
                if (id != null && id.equals(5L)) {
                    return general;
                }
                return null;
            }
        };

        AssignUserToGroupResponse assign = new AssignUserToGroupResponse(20260004L, 5L, "GENERAL_USER");
        ResponseEntity<ApiResponse<AssignUserToGroupResponse>> res =
                ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(assign));
        Object[] args = new Object[] { 5L, Map.of("userId", 20260004L), DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "assignUser", args, res, detail, pgs);

        Map<String, Object> v1 = pgV1(detail);
        assertThat(((Map<?, ?>) v1.get("before")).get("code")).isEqualTo("AUDIT");
        assertThat(((Map<?, ?>) v1.get("after")).get("code")).isEqualTo("GENERAL_USER");
        assertThat(((Map<?, ?>) v1.get("after")).get("name")).isEqualTo("일반 사용자 그룹");
        assertNoDenylistKeys(detail);
    }

    @Test
    void enrichUnassign_tc03_putsBeforeSnapshotAndNullAfter() {
        PermissionGroupResponse groupC = new PermissionGroupResponse(3L, "GRP3", "Group C", null, 0);
        groupC.setAllowedScreens(List.of(new AllowedScreenItem("statistics", "all")));
        PermissionGroupAuditContext.setUnassignBeforeState(groupC);
        PermissionGroupAuditContext.setUnassignGroupCode("GRP3");

        Object[] args = new Object[] { 3L, 20260002L, DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "unassignUser", args, ResponseEntity.ok(ApiResponse.success(null)), detail);

        assertThat(detail.get("permissionGroupId")).isEqualTo(3L);
        assertThat(detail.get("targetUserId")).isEqualTo(20260002L);
        Map<String, Object> v1 = pgV1(detail);
        assertThat(v1.get("operation")).isEqualTo("UNASSIGN_USER");
        assertThat(v1.get("permissionGroupCode")).isEqualTo("GRP3");
        assertThat(v1.get("targetUserId")).isEqualTo(20260002L);
        Map<String, Object> before = (Map<String, Object>) v1.get("before");
        assertThat(before.get("code")).isEqualTo("GRP3");
        assertThat(before.get("name")).isEqualTo("Group C");
        assertThat(v1.get("after")).isNull();
        List<Map<String, Object>> screens = (List<Map<String, Object>>) before.get("allowedScreens");
        assertThat(screens).hasSize(1);
        assertThat(screens.get(0).get("screenId")).isEqualTo("statistics");
        assertNoDenylistKeys(detail);
    }

    @Test
    void toSnapshotMap_tc04_setsAllowedScreensTruncatedWhenOverLimit() {
        PermissionGroupResponse r = new PermissionGroupResponse(1L, "C", "N", null, 0);
        List<AllowedScreenItem> screens = new ArrayList<>();
        int n = ActivityAuditDetailEnricher.MAX_ALLOWED_SCREEN_ITEMS_IN_AUDIT_SNAPSHOT + 1;
        for (int i = 0; i < n; i++) {
            screens.add(new AllowedScreenItem("screen-" + i, "team"));
        }
        r.setAllowedScreens(screens);
        Map<String, Object> snap = ActivityAuditDetailEnricher.toSnapshotMap(r);
        assertThat(snap.get("allowedScreensTruncated")).isEqualTo(true);
        assertThat(((List<?>) snap.get("allowedScreens")).size())
                .isEqualTo(ActivityAuditDetailEnricher.MAX_ALLOWED_SCREEN_ITEMS_IN_AUDIT_SNAPSHOT);
        assertNoDenylistKeys(snap);
    }

    @Test
    void enrichUpdate_truncatesChangeReasonTo500() {
        PermissionGroupResponse before = new PermissionGroupResponse(1L, "C", "o", null, 0);
        PermissionGroupAuditContext.setBeforeState(before);

        PermissionGroupUpdateRequest body = new PermissionGroupUpdateRequest();
        body.setChangeReason("x".repeat(600));

        PermissionGroupResponse after = new PermissionGroupResponse(1L, "C", "n", null, 0);
        ResponseEntity<ApiResponse<PermissionGroupResponse>> res =
                ResponseEntity.ok(ApiResponse.success(after));

        Object[] args = new Object[] { 1L, body, DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "update", args, res, detail);

        String cr = (String) pgV1(detail).get("changeReason");
        assertThat(cr).hasSize(ActivityAuditDetailEnricher.MAX_CHANGE_REASON_AUDIT_CHARS);
        assertThat(cr).isEqualTo("x".repeat(ActivityAuditDetailEnricher.MAX_CHANGE_REASON_AUDIT_CHARS));
    }

    @Test
    void enrichUpdate_omitsChangeReasonWhenBlank() {
        PermissionGroupResponse before = new PermissionGroupResponse(1L, "C", "o", null, 0);
        PermissionGroupAuditContext.setBeforeState(before);

        PermissionGroupUpdateRequest body = new PermissionGroupUpdateRequest();
        body.setChangeReason("   ");

        PermissionGroupResponse after = new PermissionGroupResponse(1L, "C", "n", null, 0);
        Object[] args = new Object[] { 1L, body, DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "update", args,
                ResponseEntity.ok(ApiResponse.success(after)), detail);

        assertThat(pgV1(detail)).doesNotContainKey("changeReason");
    }

    @Test
    void enrichIgnoresNonPermissionGroupController() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("x", 1);
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                Object.class, "create", new Object[0], null, detail);
        assertThat(detail).containsOnlyKeys("x");
    }

    /**
     * Mirrors Spring AOP {@code signature.getDeclaringType()} for a CGLIB proxy: declaring type is a subclass of the controller.
     */
    @Test
    void enrichAssign_runsWhenControllerClassIsCglibLikeSubclass() {
        PermissionGroupResponse prevA = new PermissionGroupResponse(1L, "GA", "Group A", null, 1);
        prevA.setAllowedScreens(List.of(new AllowedScreenItem("activity-log", "team")));
        PermissionGroupAuditContext.setAssignPreviousState(prevA);

        PermissionGroupResponse afterB = new PermissionGroupResponse(7L, "G7", "Group B", "d", 2);
        afterB.setAllowedScreens(List.of(new AllowedScreenItem("main", "team")));
        PermissionGroupAuditContext.setAssignAfterState(afterB);

        AssignUserToGroupResponse assign = new AssignUserToGroupResponse(20260001L, 7L, "G7");
        ResponseEntity<ApiResponse<AssignUserToGroupResponse>> res =
                ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(assign));

        Object[] args = new Object[] { 7L, Map.of("userId", 20260001L), DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();

        Class<?> subclassDeclaringType = new PermissionGroupControllerSubclass().getClass();
        assertThat(PermissionGroupController.class.isAssignableFrom(subclassDeclaringType)).isTrue();
        assertThat(subclassDeclaringType).isNotEqualTo(PermissionGroupController.class);

        ActivityAuditDetailEnricher.enrichPermissionGroup(
                subclassDeclaringType, "assignUser", args, res, detail);

        Map<String, Object> v1 = pgV1(detail);
        assertThat(v1.get("operation")).isEqualTo("ASSIGN_USER");
        assertThat(((Map<?, ?>) v1.get("before")).get("code")).isEqualTo("GA");
        assertThat(((Map<?, ?>) v1.get("after")).get("code")).isEqualTo("G7");
        assertNoDenylistKeys(detail);
    }
}
