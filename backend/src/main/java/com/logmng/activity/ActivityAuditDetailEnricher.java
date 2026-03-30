package com.logmng.activity;

import com.logmng.controller.PermissionGroupController;
import com.logmng.dto.request.PermissionGroupCreateRequest;
import com.logmng.dto.request.PermissionGroupUpdateRequest;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.AssignUserToGroupResponse;
import com.logmng.dto.response.PermissionGroupResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Structured, non-sensitive {@code action_detail} for admin controllers when {@code @ActivityLog(includeParams=false)}.
 * Security: ids/codes only; no passwords, tokens, or raw request bodies.
 */
public final class ActivityAuditDetailEnricher {

    private ActivityAuditDetailEnricher() {
    }

    /**
     * Merges permission-group audit fields into {@code actionDetail} after the aspect runs with {@code includeParams=false}.
     */
    public static void enrichPermissionGroup(
            Class<?> controllerClass,
            String methodName,
            Object[] args,
            Object methodResult,
            Map<String, Object> actionDetail) {
        if (controllerClass != PermissionGroupController.class || args == null || actionDetail == null) {
            return;
        }
        switch (methodName) {
            case "create":
                enrichCreate(args, methodResult, actionDetail);
                break;
            case "update":
                enrichUpdate(args, methodResult, actionDetail);
                break;
            case "delete":
                enrichDelete(args, actionDetail);
                break;
            case "assignUser":
                enrichAssign(args, methodResult, actionDetail);
                break;
            case "unassignUser":
                enrichUnassign(args, actionDetail);
                break;
            default:
                break;
        }
    }

    private static void enrichCreate(Object[] args, Object methodResult, Map<String, Object> actionDetail) {
        if (args.length > 0 && args[0] instanceof PermissionGroupCreateRequest req) {
            if (req.getAllowedScreens() != null) {
                actionDetail.put("allowedScreenCount", req.getAllowedScreens().size());
            }
        }
        PermissionGroupResponse data = extractApiData(methodResult);
        if (data != null) {
            putIfNotNull(actionDetail, "permissionGroupId", data.getId());
            putIfNotNull(actionDetail, "permissionGroupCode", data.getCode());
        } else if (args.length > 0 && args[0] instanceof PermissionGroupCreateRequest req) {
            putIfNotNull(actionDetail, "permissionGroupCode", req.getCode());
        }
    }

    private static void enrichUpdate(Object[] args, Object methodResult, Map<String, Object> actionDetail) {
        if (args.length > 0 && args[0] instanceof Long id) {
            actionDetail.put("permissionGroupId", id);
        }
        if (args.length > 1 && args[1] instanceof PermissionGroupUpdateRequest body) {
            if (body.getAllowedScreens() != null) {
                List<String> screenIds = body.getAllowedScreens().stream()
                        .map(s -> s != null ? s.getScreenId() : null)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!screenIds.isEmpty()) {
                    actionDetail.put("screenIds", screenIds);
                }
            }
        }
        PermissionGroupResponse data = extractApiData(methodResult);
        if (data != null) {
            putIfNotNull(actionDetail, "permissionGroupCode", data.getCode());
        }
    }

    private static void enrichDelete(Object[] args, Map<String, Object> actionDetail) {
        if (args.length > 0 && args[0] instanceof Long id) {
            actionDetail.put("permissionGroupId", id);
        }
    }

    private static void enrichAssign(Object[] args, Object methodResult, Map<String, Object> actionDetail) {
        AssignUserToGroupResponse data = extractApiData(methodResult);
        if (data != null) {
            putIfNotNull(actionDetail, "targetUserId", data.getUserId());
            putIfNotNull(actionDetail, "permissionGroupId", data.getPermissionGroupId());
            putIfNotNull(actionDetail, "permissionGroupCode", data.getPermissionGroupCode());
        } else if (args.length > 0 && args[0] instanceof Long gid) {
            actionDetail.put("permissionGroupId", gid);
        }
    }

    private static void enrichUnassign(Object[] args, Map<String, Object> actionDetail) {
        if (args.length > 0 && args[0] instanceof Long gid) {
            actionDetail.put("permissionGroupId", gid);
        }
        if (args.length > 1 && args[1] instanceof Long uid) {
            actionDetail.put("targetUserId", uid);
        }
    }

    private static void putIfNotNull(Map<String, Object> m, String k, Object v) {
        if (v != null) {
            m.put(k, v);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T extractApiData(Object methodResult) {
        if (!(methodResult instanceof ResponseEntity<?> re)) {
            return null;
        }
        Object body = re.getBody();
        if (!(body instanceof ApiResponse<?> ar)) {
            return null;
        }
        Object data = ar.getData();
        return (T) data;
    }
}
