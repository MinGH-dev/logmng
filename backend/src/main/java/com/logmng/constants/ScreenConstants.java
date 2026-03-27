package com.logmng.constants;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Allowed screen IDs per specs/permission-group-hierarchy.spec.yaml §4.1.
 * Used for validation of allowedScreens in permission group CRUD.
 */
public final class ScreenConstants {

    /** @deprecated Use PB_FEPLOG or JAVA_FW_IMAGELOG per log type (req 20260318). Kept for migration. */
    public static final String MAIN = "main";
    /** Log search screen for PB FEP Log. Replaces main for pb_feplog. */
    public static final String PB_FEPLOG = "pb-feplog";
    /**
     * Additional PB FEP log search screen (new UI wireframe, separate permission). Same logType {@code pb_feplog} for APIs.
     */
    public static final String PB_FEP_LOG_SEARCH = "pb-fep-log-search";
    /** Log search screen for Java FW Image Log. Replaces main for java_fw_imglog. */
    public static final String JAVA_FW_IMAGELOG = "java-fw-imagelog";
    /** Legacy typo: normalize to JAVA_FW_IMAGELOG (req 20260318-permission-group-menu-invalid-screen-id-imagelog). */
    public static final String JAVA_FW_IMAGELOG_LEGACY = "java-fw_imagelog";
    public static final String SEARCH_HISTORY = "search-history";
    public static final String ACTIVITY_LOG = "activity-log";
    public static final String STATISTICS = "statistics";

    public static final String PENDING_APPROVALS = "pending-approvals";
    /** Screens that support scope ('self'|'team'|'all'). Per req 20250303, 20250304, 20260305-pending-approvals-scope. */
    private static final Set<String> SCREENS_WITH_SCOPE = Collections.unmodifiableSet(
            Arrays.asList(SEARCH_HISTORY, ACTIVITY_LOG, STATISTICS, PENDING_APPROVALS).stream().collect(Collectors.toSet())
    );
    public static final String USER_MANAGEMENT = "user-management";
    public static final String DEPARTMENT_APPROVERS = "department-approvers";
    public static final String USER_PERMISSION_HIERARCHY = "user-permission-hierarchy";
    public static final String PERMISSION_GROUP_MANAGEMENT = "permission-group-management";

    private static final Set<String> ALL_ALLOWED_SCREENS = Collections.unmodifiableSet(
            Arrays.asList(
                    MAIN, PB_FEPLOG, PB_FEP_LOG_SEARCH, JAVA_FW_IMAGELOG, SEARCH_HISTORY, ACTIVITY_LOG, STATISTICS,
                    PENDING_APPROVALS, USER_MANAGEMENT, DEPARTMENT_APPROVERS,
                    USER_PERMISSION_HIERARCHY, PERMISSION_GROUP_MANAGEMENT
            ).stream().collect(Collectors.toSet())
    );

    /** Screens that support write (create/update/delete). Per spec §4.4. */
    private static final Set<String> SCREENS_WITH_WRITE = Collections.unmodifiableSet(
            Arrays.asList(USER_MANAGEMENT, DEPARTMENT_APPROVERS, USER_PERMISSION_HIERARCHY, PERMISSION_GROUP_MANAGEMENT).stream().collect(Collectors.toSet())
    );

    /** Screens that support approve (decrypt_approver). Per spec §4.4. */
    private static final Set<String> SCREENS_WITH_APPROVE = Collections.unmodifiableSet(
            Arrays.asList(SEARCH_HISTORY, PENDING_APPROVALS).stream().collect(Collectors.toSet())
    );

    /** Screens that support decrypt (request decryption). Per spec §4.4, req 20260318: pb-feplog, java-fw-imagelog; main kept for migration. */
    private static final Set<String> SCREENS_WITH_DECRYPT = Collections.unmodifiableSet(
            Arrays.asList(MAIN, PB_FEPLOG, PB_FEP_LOG_SEARCH, JAVA_FW_IMAGELOG).stream().collect(Collectors.toSet())
    );

    private ScreenConstants() {
    }

    /**
     * Normalizes a screen ID for permission-group validation and persistence.
     * Trims, NFC, strips zero-width/BOM, maps Unicode hyphen/dash characters to ASCII '-',
     * lowercases, then maps legacy {@link #JAVA_FW_IMAGELOG_LEGACY} to {@link #JAVA_FW_IMAGELOG}.
     * Req 20260318-permission-group-menu-invalid-screen-id-imagelog (follow-up: Unicode hyphen vs ASCII).
     */
    public static String normalizeScreenIdForPermissionGroup(String screenId) {
        if (screenId == null || screenId.isBlank()) {
            return screenId;
        }
        String s = screenId.trim();
        s = Normalizer.normalize(s, Normalizer.Form.NFC);
        s = s.replace("\u200B", "").replace("\u200C", "").replace("\u200D", "").replace("\uFEFF", "");
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u2010' || c == '\u2011' || c == '\u2012' || c == '\u2013' || c == '\u2014' || c == '\u2015'
                    || c == '\u2212' || c == '\uFE58' || c == '\uFE63' || c == '\uFF0D') {
                sb.append('-');
            } else {
                sb.append(c);
            }
        }
        s = sb.toString().toLowerCase(Locale.ROOT);
        if (JAVA_FW_IMAGELOG_LEGACY.equals(s)) {
            return JAVA_FW_IMAGELOG;
        }
        return s;
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

    /** Returns true if the screen supports scope (activity-log, statistics, search-history). */
    public static boolean supportsScope(String screenId) {
        return screenId != null && SCREENS_WITH_SCOPE.contains(screenId.trim());
    }

    /** Returns true if the screen supports write (user-management, department-approvers, user-permission-hierarchy, permission-group-management). Per spec §4.4. */
    public static boolean supportsWrite(String screenId) {
        return screenId != null && SCREENS_WITH_WRITE.contains(screenId.trim());
    }

    /** Returns true if the screen supports approve (search-history, pending-approvals). Per spec §4.4. */
    public static boolean supportsApprove(String screenId) {
        return screenId != null && SCREENS_WITH_APPROVE.contains(screenId.trim());
    }

    /** Returns true if the screen supports decrypt. Per spec §4.4, req 20260318: pb-feplog, java-fw-imagelog; main for migration. */
    public static boolean supportsDecrypt(String screenId) {
        return screenId != null && SCREENS_WITH_DECRYPT.contains(screenId.trim());
    }

    /** Log-type search screens (no main). Used by path rules and logType↔screen enforcement. */
    public static Set<String> getLogSearchScreenIds() {
        return Collections.unmodifiableSet(
                Arrays.asList(PB_FEPLOG, PB_FEP_LOG_SEARCH, JAVA_FW_IMAGELOG).stream().collect(Collectors.toSet()));
    }
}
