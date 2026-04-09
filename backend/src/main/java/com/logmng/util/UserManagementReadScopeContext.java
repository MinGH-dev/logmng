package com.logmng.util;

import com.logmng.constants.ScreenConstants;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Effective read scope for User Management v2 shared APIs (req 20260409-user-management-v2-read-scope).
 * Resolved once per request and cached on {@link jakarta.servlet.http.HttpServletRequest}.
 */
public final class UserManagementReadScopeContext {

    /** No filtering (legacy UM, admin, or UM v2 with effective scope {@code all}). */
    public static UserManagementReadScopeContext unrestricted() {
        return new UserManagementReadScopeContext(false, "all", null, null, false);
    }

    private final boolean appliesUmV2Screen;
    private final String effectiveScope;
    /** When non-null, only these app_user.id values may appear in user lists / hierarchy users. */
    private final List<Long> allowedNumericUserIds;
    /** When non-null, only these department.code values may appear in hierarchy structure. */
    private final Set<String> visibleDepartmentCodes;
    private final boolean restrictHierarchyDepartments;

    public UserManagementReadScopeContext(boolean appliesUmV2Screen,
                                         String effectiveScope,
                                         List<Long> allowedNumericUserIds,
                                         Set<String> visibleDepartmentCodes,
                                         boolean restrictHierarchyDepartments) {
        this.appliesUmV2Screen = appliesUmV2Screen;
        this.effectiveScope = effectiveScope != null ? effectiveScope : "team";
        this.allowedNumericUserIds = allowedNumericUserIds;
        this.visibleDepartmentCodes = visibleDepartmentCodes;
        this.restrictHierarchyDepartments = restrictHierarchyDepartments;
    }

    public boolean appliesUmV2Screen() {
        return appliesUmV2Screen;
    }

    public String getEffectiveScope() {
        return effectiveScope;
    }

    /**
     * When non-null, SQL / filtering must restrict users to this id set (may be empty = no rows).
     * Null means no id filter.
     */
    public List<Long> getAllowedNumericUserIds() {
        return allowedNumericUserIds;
    }

    public Set<String> getVisibleDepartmentCodes() {
        return visibleDepartmentCodes;
    }

    public boolean restrictHierarchyDepartments() {
        return restrictHierarchyDepartments;
    }

    public boolean restrictsUserIds() {
        return allowedNumericUserIds != null;
    }

    /**
     * True when this screen is {@link ScreenConstants#USER_MANAGEMENT_V2} with self or team (non-all) scope.
     */
    public boolean isNarrowRead() {
        return appliesUmV2Screen && allowedNumericUserIds != null
                && ("self".equals(effectiveScope) || "team".equals(effectiveScope));
    }

    public List<Long> getAllowedNumericUserIdsOrEmpty() {
        return allowedNumericUserIds != null ? allowedNumericUserIds : Collections.emptyList();
    }
}
