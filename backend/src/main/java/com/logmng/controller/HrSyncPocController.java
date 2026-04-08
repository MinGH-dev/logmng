package com.logmng.controller;

import com.logmng.config.HrSyncPocProperties;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.HrSyncPocConfigResponse;
import com.logmng.dto.response.HrSyncPocEmployeesPageResponse;
import com.logmng.dto.response.HrSyncPocPreviewResponse;
import com.logmng.dto.response.HrSyncPocSnapshotsResponse;
import com.logmng.exception.CustomException;
import com.logmng.service.AuthService;
import com.logmng.service.HrSyncPocService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * HR Sync PoC: config + read-only preview. {@code specs/hr-sync-poc.spec.yaml}.
 */
@RestController
@RequestMapping("/api/hr-sync/poc")
public class HrSyncPocController {

    private static final int MAX_OPTIONAL_ID_LEN = 512;

    private final HrSyncPocProperties pocProperties;
    private final HrSyncPocService hrSyncPocService;
    private final AuthService authService;

    public HrSyncPocController(
            HrSyncPocProperties pocProperties,
            HrSyncPocService hrSyncPocService,
            AuthService authService) {
        this.pocProperties = pocProperties;
        this.hrSyncPocService = hrSyncPocService;
        this.authService = authService;
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<HrSyncPocConfigResponse>> getConfig(HttpServletRequest request) {
        requireAuth(request);
        HrSyncPocConfigResponse data = new HrSyncPocConfigResponse(
                pocProperties.isEnabled(),
                pocProperties.getDefaultMode(),
                pocProperties.isApplyEnabled());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<HrSyncPocPreviewResponse>> preview(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        requireAuth(request);
        requirePocEnabled();
        PreviewIds ids = parseAndValidatePreviewBody(body);
        HrSyncPocPreviewResponse data = hrSyncPocService.buildPreview(ids.snapshotId, ids.ingestRunId);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<ApiResponse<HrSyncPocSnapshotsResponse>> listSnapshots(HttpServletRequest request) {
        requireAuth(request);
        requirePocEnabled();
        HrSyncPocSnapshotsResponse data = hrSyncPocService.loadSnapshots();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/snapshots/{snapshotId}/employees")
    public ResponseEntity<ApiResponse<HrSyncPocEmployeesPageResponse>> listEmployees(
            @PathVariable("snapshotId") String snapshotId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        requireAuth(request);
        requirePocEnabled();
        HrSyncPocService.validateSnapshotIdForPath(snapshotId);
        HrSyncPocService.validateEmployeePageParams(page, size);
        HrSyncPocEmployeesPageResponse data = hrSyncPocService.loadEmployeesPage(snapshotId.trim(), page, size);
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

    private static PreviewIds parseAndValidatePreviewBody(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return new PreviewIds(null, null);
        }
        return new PreviewIds(
                readOptionalString(body, "snapshotId"),
                readOptionalString(body, "ingestRunId"));
    }

    private static String readOptionalString(Map<String, Object> body, String key) {
        if (!body.containsKey(key)) {
            return null;
        }
        Object v = body.get(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof String)) {
            throw CustomException.badRequest("필드 " + key + "는 문자열이어야 합니다.", "VALIDATION_ERROR");
        }
        String s = ((String) v).trim();
        if (s.length() > MAX_OPTIONAL_ID_LEN) {
            throw CustomException.badRequest("필드 " + key + " 길이가 상한을 초과합니다.", "VALIDATION_ERROR");
        }
        return s.isEmpty() ? null : s;
    }

    private static final class PreviewIds {
        final String snapshotId;
        final String ingestRunId;

        PreviewIds(String snapshotId, String ingestRunId) {
            this.snapshotId = snapshotId;
            this.ingestRunId = ingestRunId;
        }
    }
}
