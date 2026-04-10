package com.logmng.util;

import com.logmng.constants.ScreenConstants;

import java.util.List;

/**
 * Maps log type (API/DB) to screen_id for permission checks.
 * Per req 20260318: pb_feplog → pb-feplog, java_fw_imglog → java-fw-imagelog.
 * {@link #userHasAccessToLogType(List, String)}: pb_feplog allows either {@code pb-feplog} or {@code pb-fep-log-search}.
 */
public final class LogTypeScreenHelper {

    private LogTypeScreenHelper() {
    }

    /**
     * Returns the screen_id required for the given log type, or null if unknown.
     */
    public static String screenIdForLogType(String logType) {
        if (logType == null || logType.isBlank()) {
            return null;
        }
        switch (logType.trim()) {
            case "pb_feplog":
                return ScreenConstants.PB_FEPLOG;
            case "java_fw_imglog":
                return ScreenConstants.JAVA_FW_IMAGELOG;
            default:
                return null;
        }
    }

    /**
     * Returns true if the log type is known and has a corresponding log-search screen.
     */
    public static boolean isKnownLogType(String logType) {
        return screenIdForLogType(logType) != null;
    }

    /**
     * True if allowedScreenIds grants access to search the given API logType (read path).
     */
    public static boolean userHasAccessToLogType(List<String> allowedScreenIds, String logType) {
        if (allowedScreenIds == null || allowedScreenIds.isEmpty()) {
            return false;
        }
        if (logType == null || logType.isBlank()) {
            return false;
        }
        switch (logType.trim()) {
            case "pb_feplog":
                return allowedScreenIds.contains(ScreenConstants.PB_FEPLOG)
                        || allowedScreenIds.contains(ScreenConstants.PB_FEP_LOG_SEARCH);
            case "java_fw_imglog":
                return allowedScreenIds.contains(ScreenConstants.JAVA_FW_IMAGELOG);
            default:
                return false;
        }
    }
}
