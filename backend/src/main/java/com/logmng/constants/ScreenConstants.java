package com.logmng.constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Allowed screen IDs per specs/permission-group-hierarchy.spec.yaml §4.1.
 * Used for validation of allowedScreens in permission group CRUD.
 */
public final class ScreenConstants {

    public static final String MAIN = "main";
    public static final String SEARCH_HISTORY = "search-history";
    public static final String ACTIVITY_LOG = "activity-log";
    public static final String STATISTICS = "statistics";
    public static final String PENDING_APPROVALS = "pending-approvals";
    public static final String USER_MANAGEMENT = "user-management";
    public static final String USER_PERMISSION_HIERARCHY = "user-permission-hierarchy";
    public static final String PERMISSION_GROUP_MANAGEMENT = "permission-group-management";

    private static final Set<String> ALL_ALLOWED_SCREENS = Collections.unmodifiableSet(
            Arrays.asList(
                    MAIN, SEARCH_HISTORY, ACTIVITY_LOG, STATISTICS,
                    PENDING_APPROVALS, USER_MANAGEMENT, USER_PERMISSION_HIERARCHY,
                    PERMISSION_GROUP_MANAGEMENT
            ).stream().collect(Collectors.toSet())
    );

    private ScreenConstants() {
    }

    /**
     * Returns all allowed screen IDs. ADMIN users get all screens.
     */
    public static Set<String> getAllAllowedScreens() {
        return ALL_ALLOWED_SCREENS;
    }

    /**
     * Validates that each screen ID is in the allowed list.
     *
     * @param screenIds screen IDs to validate (may be null or empty)
     * @return first invalid screen ID, or null if all valid
     */
    public static String findFirstInvalid(String... screenIds) {
        if (screenIds == null || screenIds.length == 0) {
            return null;
        }
        for (String id : screenIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!ALL_ALLOWED_SCREENS.contains(id.trim())) {
                return id.trim();
            }
        }
        return null;
    }

    public static boolean isValid(String screenId) {
        return screenId != null && !screenId.isBlank() && ALL_ALLOWED_SCREENS.contains(screenId.trim());
    }
}
