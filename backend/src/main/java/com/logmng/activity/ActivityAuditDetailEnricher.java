package com.logmng.activity;

import com.logmng.controller.PermissionGroupController;
import com.logmng.dto.request.PermissionGroupCreateRequest;
import com.logmng.dto.request.PermissionGroupUpdateRequest;
import com.logmng.dto.response.AllowedScreenItem;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.AssignUserToGroupResponse;
import com.logmng.dto.response.PermissionGroupResponse;
import com.logmng.service.PermissionGroupService;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured, non-sensitive {@code action_detail} for admin controllers when {@code @ActivityLog(includeParams=false)}.
 * Persists {@code permissionGroupAuditV1} per {@code specs/activity-permission-group-audit.spec.yaml}.
 * Security: allowlisted fields only; no passwords, tokens, or raw request bodies.
 */
public final class ActivityAuditDetailEnricher {

    /** Persisted max length for {@code permissionGroupAuditV1.changeReason} (contract MF-03). */
    public static final int MAX_CHANGE_REASON_AUDIT_CHARS = 500;

    /**
     * Max {@code allowedScreens} items per snapshot; larger lists are truncated and {@code allowedScreensTruncated}
     * is set on that snapshot (spec activity-permission-group-audit §3.2). Full nested lists for typical sizes.
     */
    public static final int MAX_ALLOWED_SCREEN_ITEMS_IN_AUDIT_SNAPSHOT = 100;

    private ActivityAuditDetailEnricher() {
    }

    /**
     * Merges permission-group audit fields into {@code actionDetail} after the aspect runs with {@code includeParams=false}.
     * {@code permissionGroupService} is used to resolve {@code after} snapshot for assign when ThreadLocal is empty.
     */
    public static void enrichPermissionGroup(
            Class<?> controllerClass,
            String methodName,
            Object[] args,
            Object methodResult,
            Map<String, Object> actionDetail) {
        enrichPermissionGroup(controllerClass, methodName, args, methodResult, actionDetail, null);
    }

    /**
     * @param permissionGroupService optional; when non-null, {@code ASSIGN_USER} audit can load {@code after} via
     *                                 {@link PermissionGroupService#findById(Long)} if ThreadLocal did not capture it.
     */
    public static void enrichPermissionGroup(
            Class<?> controllerClass,
            String methodName,
            Object[] args,
            Object methodResult,
            Map<String, Object> actionDetail,
            PermissionGroupService permissionGroupService) {
        try {
            // Spring CGLIB proxies use a subclass (e.g. PermissionGroupController$$SpringCGLIB$$0), not the raw class.
            if (controllerClass == null
                    || !PermissionGroupController.class.isAssignableFrom(controllerClass)
                    || args == null
                    || actionDetail == null) {
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
                    enrichDelete(args, methodResult, actionDetail);
                    break;
                case "assignUser":
                    enrichAssign(args, methodResult, actionDetail, permissionGroupService);
                    break;
                case "unassignUser":
                    enrichUnassign(args, methodResult, actionDetail);
                    break;
                default:
                    break;
            }
        } finally {
            PermissionGroupAuditContext.clear();
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
            Map<String, Object> v1 = baseV1("CREATE", data.getId(), data.getCode());
            v1.put("before", null);
            v1.put("after", toSnapshotMap(data));
            actionDetail.put("permissionGroupAuditV1", v1);
        } else if (args.length > 0 && args[0] instanceof PermissionGroupCreateRequest req) {
            putIfNotNull(actionDetail, "permissionGroupCode", req.getCode());
        }
    }

    private static void enrichUpdate(Object[] args, Object methodResult, Map<String, Object> actionDetail) {
        Long id = null;
        if (args.length > 0 && args[0] instanceof Long lid) {
            id = lid;
            actionDetail.put("permissionGroupId", id);
        }
        PermissionGroupUpdateRequest body = args.length > 1 && args[1] instanceof PermissionGroupUpdateRequest u ? u : null;

        PermissionGroupResponse after = extractApiData(methodResult);
        PermissionGroupResponse before = PermissionGroupAuditContext.peekBeforeState();

        if (after != null) {
            putIfNotNull(actionDetail, "permissionGroupCode", after.getCode());
            Map<String, Object> v1 = baseV1("UPDATE", after.getId(), after.getCode());
            if (before != null) {
                v1.put("before", toSnapshotMap(before));
            }
            v1.put("after", toSnapshotMap(after));
            if (body != null && body.getChangeReason() != null && !body.getChangeReason().isBlank()) {
                String cr = body.getChangeReason().trim();
                if (cr.length() > MAX_CHANGE_REASON_AUDIT_CHARS) {
                    cr = cr.substring(0, MAX_CHANGE_REASON_AUDIT_CHARS);
                }
                v1.put("changeReason", cr);
            }
            actionDetail.put("permissionGroupAuditV1", v1);
        }
    }

    private static void enrichDelete(Object[] args, Object methodResult, Map<String, Object> actionDetail) {
        Long id = args.length > 0 && args[0] instanceof Long lid ? lid : null;
        if (id != null) {
            actionDetail.put("permissionGroupId", id);
        }
        PermissionGroupResponse before = PermissionGroupAuditContext.peekBeforeState();
        if (before != null) {
            putIfNotNull(actionDetail, "permissionGroupCode", before.getCode());
            Map<String, Object> v1 = baseV1("DELETE", before.getId(), before.getCode());
            v1.put("before", toSnapshotMap(before));
            v1.put("after", null);
            actionDetail.put("permissionGroupAuditV1", v1);
        }
    }

    private static void enrichAssign(
            Object[] args,
            Object methodResult,
            Map<String, Object> actionDetail,
            PermissionGroupService permissionGroupService) {
        AssignUserToGroupResponse data = extractApiData(methodResult);
        if (data != null) {
            putIfNotNull(actionDetail, "targetUserId", data.getUserId());
            putIfNotNull(actionDetail, "permissionGroupId", data.getPermissionGroupId());
            putIfNotNull(actionDetail, "permissionGroupCode", data.getPermissionGroupCode());
            Map<String, Object> v1 = baseV1("ASSIGN_USER", data.getPermissionGroupId(), data.getPermissionGroupCode());
            v1.put("targetUserId", data.getUserId());
            PermissionGroupResponse prev = PermissionGroupAuditContext.peekAssignPreviousState();
            PermissionGroupResponse after = PermissionGroupAuditContext.peekAssignAfterState();
            if (after == null && permissionGroupService != null && data.getPermissionGroupId() != null) {
                try {
                    after = permissionGroupService.findById(data.getPermissionGroupId());
                } catch (Exception ignored) {
                    // leave after null
                }
            }
            v1.put("before", prev == null ? null : toSnapshotMap(prev));
            v1.put("after", after == null ? null : toSnapshotMap(after));
            actionDetail.put("permissionGroupAuditV1", v1);
        } else if (args.length > 0 && args[0] instanceof Long gid) {
            actionDetail.put("permissionGroupId", gid);
        }
    }

    private static void enrichUnassign(Object[] args, Object methodResult, Map<String, Object> actionDetail) {
        Long groupId = args.length > 0 && args[0] instanceof Long gid ? gid : null;
        Long targetUserId = args.length > 1 && args[1] instanceof Long uid ? uid : null;
        if (groupId != null) {
            actionDetail.put("permissionGroupId", groupId);
        }
        if (targetUserId != null) {
            actionDetail.put("targetUserId", targetUserId);
        }
        PermissionGroupResponse beforeSnap = PermissionGroupAuditContext.peekUnassignBeforeState();
        String code = beforeSnap != null && beforeSnap.getCode() != null && !beforeSnap.getCode().isBlank()
                ? beforeSnap.getCode()
                : PermissionGroupAuditContext.peekUnassignGroupCode();
        putIfNotNull(actionDetail, "permissionGroupCode", code);

        Map<String, Object> v1 = baseV1("UNASSIGN_USER", groupId, code);
        if (targetUserId != null) {
            v1.put("targetUserId", targetUserId);
        }
        v1.put("before", beforeSnap == null ? null : toSnapshotMap(beforeSnap));
        v1.put("after", null);
        actionDetail.put("permissionGroupAuditV1", v1);
    }

    private static Map<String, Object> baseV1(String operation, Long permissionGroupId, String permissionGroupCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemaVersion", "1");
        m.put("operation", operation);
        if (permissionGroupId != null) {
            m.put("permissionGroupId", permissionGroupId);
        }
        if (permissionGroupCode != null && !permissionGroupCode.isBlank()) {
            m.put("permissionGroupCode", permissionGroupCode);
        }
        return m;
    }

    static Map<String, Object> toSnapshotMap(PermissionGroupResponse r) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (r == null) {
            return m;
        }
        putIfNotNull(m, "code", r.getCode());
        putIfNotNull(m, "name", r.getName());
        if (r.getDescription() != null) {
            m.put("description", r.getDescription());
        }
        m.put("sortOrder", r.getSortOrder() != null ? r.getSortOrder() : 0);
        List<AllowedScreenItem> raw = r.getAllowedScreens();
        if (raw != null && !raw.isEmpty()) {
            boolean truncated = raw.size() > MAX_ALLOWED_SCREEN_ITEMS_IN_AUDIT_SNAPSHOT;
            List<AllowedScreenItem> slice = truncated
                    ? raw.subList(0, MAX_ALLOWED_SCREEN_ITEMS_IN_AUDIT_SNAPSHOT)
                    : raw;
            List<Map<String, Object>> screens = new ArrayList<>();
            for (AllowedScreenItem item : slice) {
                if (item == null) {
                    continue;
                }
                screens.add(toScreenItemMap(item));
            }
            m.put("allowedScreens", screens);
            if (truncated) {
                m.put("allowedScreensTruncated", true);
            }
        } else {
            m.put("allowedScreens", List.of());
        }
        return m;
    }

    private static Map<String, Object> toScreenItemMap(AllowedScreenItem item) {
        Map<String, Object> sm = new LinkedHashMap<>();
        sm.put("screenId", item.getScreenId());
        if (item.getScope() != null && !item.getScope().isBlank()) {
            sm.put("scope", item.getScope());
        }
        if (item.getRead() != null) {
            sm.put("read", item.getRead());
        }
        if (item.getWrite() != null) {
            sm.put("write", item.getWrite());
        }
        if (item.getApprove() != null) {
            sm.put("approve", item.getApprove());
        }
        if (item.getDecrypt() != null) {
            sm.put("decrypt", item.getDecrypt());
        }
        return sm;
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

    /**
     * True if {@code key} is a denylisted audit key (spec §6, TC-07).
     */
    public static boolean isDenylistedKey(String key) {
        if (key == null) {
            return false;
        }
        String k = key.toLowerCase();
        return Objects.equals(k, "password")
                || Objects.equals(k, "newpassword")
                || Objects.equals(k, "currentpassword")
                || Objects.equals(k, "confirmpassword")
                || Objects.equals(k, "token")
                || Objects.equals(k, "accesstoken")
                || Objects.equals(k, "refreshtoken")
                || Objects.equals(k, "authorization")
                || Objects.equals(k, "secret")
                || Objects.equals(k, "clientsecret")
                || Objects.equals(k, "apikey");
    }
}
