package com.logmng.activity;

import com.logmng.dto.response.AllowedScreenItem;
import com.logmng.dto.response.PermissionGroupResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local state for permission-group activity audit (read-before-write for UPDATE/DELETE,
 * assign/unassign snapshots). Cleared by {@link ActivityAuditDetailEnricher} after enrichment.
 */
public final class PermissionGroupAuditContext {

    private static final ThreadLocal<PermissionGroupResponse> BEFORE_STATE = new ThreadLocal<>();
    private static final ThreadLocal<String> UNASSIGN_GROUP_CODE = new ThreadLocal<>();
    /** Previous group membership before ASSIGN (nullable if user had no prior group). */
    private static final ThreadLocal<PermissionGroupResponse> ASSIGN_PREVIOUS = new ThreadLocal<>();
    /** Target group snapshot after successful ASSIGN. */
    private static final ThreadLocal<PermissionGroupResponse> ASSIGN_AFTER = new ThreadLocal<>();
    /** Full group snapshot before UNASSIGN row removal. */
    private static final ThreadLocal<PermissionGroupResponse> UNASSIGN_BEFORE = new ThreadLocal<>();

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

    /**
     * Snapshot of the user's previous permission group before assign replaces membership (or {@code null} if none).
     */
    public static void setAssignPreviousState(PermissionGroupResponse existing) {
        if (existing == null) {
            ASSIGN_PREVIOUS.remove();
        } else {
            ASSIGN_PREVIOUS.set(cloneForAudit(existing));
        }
    }

    public static PermissionGroupResponse peekAssignPreviousState() {
        return ASSIGN_PREVIOUS.get();
    }

    /**
     * Snapshot of the target group after assign (full clone, same as UPDATE audit).
     */
    public static void setAssignAfterState(PermissionGroupResponse after) {
        if (after == null) {
            ASSIGN_AFTER.remove();
        } else {
            ASSIGN_AFTER.set(cloneForAudit(after));
        }
    }

    public static PermissionGroupResponse peekAssignAfterState() {
        return ASSIGN_AFTER.get();
    }

    /** Clears assign-only slots; call at start of assign to avoid stale thread-local state. */
    public static void clearAssignAudit() {
        ASSIGN_PREVIOUS.remove();
        ASSIGN_AFTER.remove();
    }

    /**
     * Full group state before unassign DELETE (path group id).
     */
    public static void setUnassignBeforeState(PermissionGroupResponse group) {
        if (group == null) {
            UNASSIGN_BEFORE.remove();
        } else {
            UNASSIGN_BEFORE.set(cloneForAudit(group));
        }
    }

    public static PermissionGroupResponse peekUnassignBeforeState() {
        return UNASSIGN_BEFORE.get();
    }

    /** Clears unassign-only slots; call at start of unassign. */
    public static void clearUnassignAudit() {
        UNASSIGN_GROUP_CODE.remove();
        UNASSIGN_BEFORE.remove();
    }

    public static void clear() {
        BEFORE_STATE.remove();
        UNASSIGN_GROUP_CODE.remove();
        ASSIGN_PREVIOUS.remove();
        ASSIGN_AFTER.remove();
        UNASSIGN_BEFORE.remove();
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
