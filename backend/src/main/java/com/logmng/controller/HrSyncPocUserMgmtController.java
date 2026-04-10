package com.logmng.controller;

import com.logmng.config.HrSyncPocProperties;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.HrSyncPocMigratePreviewResponse;
import com.logmng.dto.response.HrSyncPocReplicaDepartmentTreeResponse;
import com.logmng.dto.response.HrSyncPocReplicaUsersPageResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AuthService;
import com.logmng.service.HrSyncPocService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * PoC User Management (UM v2 clone): read-only replica APIs + no-op migrate stub.
 * {@code specs/hr-sync-poc.spec.yaml} §4.5–4.7.
 */
@RestController
@RequestMapping("/api/hr-sync/poc/user-mgmt")
public class HrSyncPocUserMgmtController {

    private static final String STUB_MESSAGE_CODE = "POC_ACTION_NOT_PERSISTED";

    private final HrSyncPocProperties pocProperties;
    private final HrSyncPocService hrSyncPocService;
    private final AuthService authService;

    public HrSyncPocUserMgmtController(
            HrSyncPocProperties pocProperties,
            HrSyncPocService hrSyncPocService,
            AuthService authService) {
        this.pocProperties = pocProperties;
        this.hrSyncPocService = hrSyncPocService;
        this.authService = authService;
    }

    @GetMapping("/replica-departments/tree")
    public ResponseEntity<ApiResponse<HrSyncPocReplicaDepartmentTreeResponse>> replicaDepartmentsTree(
            @RequestParam(name = "sourceSystem", defaultValue = "HR_SAMPLE") String sourceSystem,
            HttpServletRequest request) {
        requireAuth(request);
        requirePocEnabled();
        HrSyncPocReplicaDepartmentTreeResponse data = hrSyncPocService.loadReplicaDepartmentTree(sourceSystem);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/replica-users")
    public ResponseEntity<ApiResponse<HrSyncPocReplicaUsersPageResponse>> replicaUsers(
            @RequestParam(name = "sourceSystem", defaultValue = "HR_SAMPLE") String sourceSystem,
            @RequestParam(name = "snapshotId", required = false) String snapshotId,
            @RequestParam(name = "departmentKey", required = false) String departmentKey,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        requireAuth(request);
        requirePocEnabled();
        HrSyncPocReplicaUsersPageResponse data =
                hrSyncPocService.loadReplicaUsersPage(sourceSystem, snapshotId, departmentKey, page, size);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/actions/migrate-preview")
    public ResponseEntity<ApiResponse<HrSyncPocMigratePreviewResponse>> migratePreview(
            @SuppressWarnings("unused") @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        requireAuth(request);
        requirePocEnabled();
        HrSyncPocMigratePreviewResponse data = new HrSyncPocMigratePreviewResponse(false, STUB_MESSAGE_CODE);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private void requireAuth(HttpServletRequest request) {
        if (!authService.checkAuth(request)) {
            throw CustomException.unauthorized("로그인이 필요합니다.", "UNAUTHORIZED");
        }
    }

    private void requirePocEnabled() {
        if (!pocProperties.isEnabled()) {
            throw CustomException.forbidden("HR Sync PoC가 비활성화되어 있습니다.", "POC_DISABLED");
        }
    }
}
