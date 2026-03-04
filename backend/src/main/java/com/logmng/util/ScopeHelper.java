package com.logmng.util;

import com.logmng.constants.ScreenConstants;

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
}
