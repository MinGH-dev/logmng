package com.logmng.activity;

import com.logmng.dto.response.AllowedScreenItem;
import com.logmng.dto.response.PermissionGroupResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local state for permission-group activity audit (read-before-write for UPDATE/DELETE,
 * group code for UNASSIGN). Cleared by {@link ActivityAuditDetailEnricher} after enrichment.
 */
public final class PermissionGroupAuditContext {

    private static final ThreadLocal<PermissionGroupResponse> BEFORE_STATE = new ThreadLocal<>();
    private static final ThreadLocal<String> UNASSIGN_GROUP_CODE = new ThreadLocal<>();

    private PermissionGroupAuditContext() {
    }

    /**
     * Snapshot of the group before UPDATE or DELETE (deep copy for safe use after mutation).
     */
    public static void setBeforeState(PermissionGroupResponse existing) {
        BEFORE_STATE.set(cloneForAudit(existing));
    }

    public static PermissionGroupResponse peekBeforeState() {
        return BEFORE_STATE.get();
    }

    public static void setUnassignGroupCode(String code) {
        UNASSIGN_GROUP_CODE.set(code);
    }

    public static String peekUnassignGroupCode() {
        return UNASSIGN_GROUP_CODE.get();
    }

    public static void clear() {
        BEFORE_STATE.remove();
        UNASSIGN_GROUP_CODE.remove();
    }

    static PermissionGroupResponse cloneForAudit(PermissionGroupResponse src) {
        if (src == null) {
            return null;
        }
        PermissionGroupResponse copy = new PermissionGroupResponse(
                src.getId(),
                src.getCode(),
                src.getName(),
                src.getDescription(),
                src.getSortOrder() != null ? src.getSortOrder() : 0);
        if (src.getAllowedScreens() != null) {
            List<AllowedScreenItem> screens = new ArrayList<>();
            for (AllowedScreenItem item : src.getAllowedScreens()) {
                if (item == null) {
                    continue;
                }
                AllowedScreenItem n = new AllowedScreenItem();
                n.setScreenId(item.getScreenId());
                n.setScope(item.getScope());
                n.setRead(item.getRead());
                n.setWrite(item.getWrite());
                n.setApprove(item.getApprove());
                n.setDecrypt(item.getDecrypt());
                screens.add(n);
            }
            copy.setAllowedScreens(screens);
        }
        return copy;
    }
}
