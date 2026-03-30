package com.logmng.activity;

import com.logmng.controller.PermissionGroupController;
import com.logmng.dto.request.PermissionGroupCreateRequest;
import com.logmng.dto.request.PermissionGroupUpdateRequest;
import com.logmng.dto.response.AllowedScreenItem;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.AssignUserToGroupResponse;
import com.logmng.dto.response.PermissionGroupResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TC-01–TC-07: permission-group audit {@code permissionGroupAuditV1} (spec activity-permission-group-audit).
 */
class ActivityAuditDetailEnricherTest {

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
    void enrichAssign_putsAssignUserOperation() {
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
        assertNoDenylistKeys(detail);
    }

    @Test
    void enrichUnassign_putsUnassignUserAndGroupCodeFromContext() {
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
        assertNoDenylistKeys(detail);
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
}
