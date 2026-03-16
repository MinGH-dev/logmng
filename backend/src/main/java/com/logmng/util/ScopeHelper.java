package com.logmng.util;

import com.logmng.constants.ScreenConstants;
import com.logmng.dto.request.UserActivityLogSearchRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helper to resolve effective scope per screen for activity-log, statistics, search-history.
 * Per req 20250303-activity-statistics-self-only-scope; req 20250304-team-scope-default-and-approval.
 */
public final class ScopeHelper {

    private ScopeHelper() {
    }

    /**
     * Resolves effective scope for the given screen.
     *
     * @param screenId      activity-log, statistics, or search-history
     * @param isSystemAdmin true if user is system administrator
     * @param screenScopes  per-screen scope from auth (may be null)
     * @return 'all' if isSystemAdmin; else from screenScopes for screenId; default 'team' when null/omitted (scope-supporting screens)
     */
    public static String resolveScope(String screenId, boolean isSystemAdmin, Map<String, String> screenScopes) {
        if (!ScreenConstants.supportsScope(screenId)) {
            return "all";
        }
        if (isSystemAdmin) {
            return "all";
        }
        if (screenScopes != null) {
            String s = screenScopes.get(screenId);
            if ("all".equalsIgnoreCase(s)) {
                return "all";
            }
            if ("team".equalsIgnoreCase(s)) {
                return "team";
            }
            if ("self".equalsIgnoreCase(s)) {
                return "self";
            }
        }
        return "team";
    }

    public static String normalizeOptionalParam(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String normalizeDepartmentFilter(String value) {
        String normalized = normalizeOptionalParam(value);
        if (normalized == null) {
            return null;
        }
        return isAllSelection(normalized) ? null : normalized;
    }

    public static boolean isAllSelection(String value) {
        String normalized = normalizeOptionalParam(value);
        if (normalized == null) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return "all".equals(lower)
                || "전체".equals(normalized)
                || "전체부서".equals(normalized)
                || "전체 부서".equals(normalized);
    }

    public static List<String> normalizeAllowedUserIds(List<String> allowedUserIds) {
        if (allowedUserIds == null) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String allowedUserId : allowedUserIds) {
            String trimmed = normalizeOptionalParam(allowedUserId);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        return new ArrayList<>(normalized);
    }

    /**
     * Applies server-side scope enforcement for activity-log search requests.
     * Uses userIdForFilter (username for DB); when scope=self overrides to currentUserId.
     */
    public static void applyActivityLogSearchScope(UserActivityLogSearchRequest request,
                                                   String scope,
                                                   String currentUserId,
                                                   List<String> teamUserIds) {
        if (request == null) {
            return;
        }

        request.setUserIdForFilter(normalizeOptionalParam(request.getUserIdForFilter()));
        request.setUsername(normalizeOptionalParam(request.getUsername()));
        request.setIpAddress(normalizeOptionalParam(request.getIpAddress()));
        request.setDepartment(normalizeDepartmentFilter(request.getDepartment()));
        request.setAllowedUserIds(null);

        if ("self".equals(scope)) {
            request.setUserIdForFilter(normalizeOptionalParam(currentUserId));
            request.setUsername(null);
            request.setIpAddress(null);
            request.setDepartment(null);
            return;
        }

        if ("team".equals(scope)) {
            request.setAllowedUserIds(normalizeAllowedUserIds(teamUserIds));
        }
    }
}
