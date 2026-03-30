package com.logmng.activity;

import com.logmng.controller.PermissionGroupController;
import com.logmng.dto.request.PermissionGroupCreateRequest;
import com.logmng.dto.request.PermissionGroupUpdateRequest;
import com.logmng.dto.response.AllowedScreenItem;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.AssignUserToGroupResponse;
import com.logmng.dto.response.PermissionGroupResponse;
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
 * TC-01–TC-04, TC-17: structured permission-group audit detail (no password/token keys).
 */
class ActivityAuditDetailEnricherTest {

    private static final HttpServletRequest DUMMY_REQUEST = mock(HttpServletRequest.class);

    @Test
    void enrichCreate_putsGroupIdsAndCodes() {
        PermissionGroupCreateRequest req = new PermissionGroupCreateRequest();
        req.setCode("PG01");
        req.setName("n");
        req.setAllowedScreens(List.of(new AllowedScreenItem("main", null)));

        PermissionGroupResponse pr = new PermissionGroupResponse(99L, "PG01", "n", null, 0);
        ResponseEntity<ApiResponse<PermissionGroupResponse>> res =
                ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(pr));

        Object[] args = new Object[] { req, DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "create", args, res, detail);

        assertThat(detail.get("permissionGroupId")).isEqualTo(99L);
        assertThat(detail.get("permissionGroupCode")).isEqualTo("PG01");
        assertThat(detail.get("allowedScreenCount")).isEqualTo(1);
        assertThat(detail).doesNotContainKeys("password", "token", "secret", "refreshToken");
    }

    @Test
    void enrichUpdate_putsScreenIdsAndGroupId() {
        PermissionGroupUpdateRequest body = new PermissionGroupUpdateRequest();
        body.setAllowedScreens(List.of(new AllowedScreenItem("activity-log", "team")));

        PermissionGroupResponse pr = new PermissionGroupResponse(5L, "CODE5", "n", null, 0);
        ResponseEntity<ApiResponse<PermissionGroupResponse>> res =
                ResponseEntity.ok(ApiResponse.success(pr));

        Object[] args = new Object[] { 5L, body, DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "update", args, res, detail);

        assertThat(detail.get("permissionGroupId")).isEqualTo(5L);
        assertThat(detail.get("permissionGroupCode")).isEqualTo("CODE5");
        assertThat(detail.get("screenIds")).isEqualTo(List.of("activity-log"));
    }

    @Test
    void enrichDelete_putsGroupId() {
        Object[] args = new Object[] { 42L, DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "delete", args, ResponseEntity.ok().build(), detail);

        assertThat(detail.get("permissionGroupId")).isEqualTo(42L);
    }

    @Test
    void enrichAssign_putsTargetAndGroupFromResponse() {
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
    }

    @Test
    void enrichUnassign_putsTargetAndGroupIds() {
        Object[] args = new Object[] { 3L, 20260002L, DUMMY_REQUEST };
        Map<String, Object> detail = new HashMap<>();
        ActivityAuditDetailEnricher.enrichPermissionGroup(
                PermissionGroupController.class, "unassignUser", args, ResponseEntity.ok(ApiResponse.success(null)), detail);

        assertThat(detail.get("permissionGroupId")).isEqualTo(3L);
        assertThat(detail.get("targetUserId")).isEqualTo(20260002L);
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
