package com.logmng.util;

import com.logmng.constants.ScreenConstants;

/**
 * Maps log type (API/DB) to screen_id for permission checks.
 * Per req 20260318: pb_feplog → pb-feplog, java_fw_imglog → java-fw-imagelog.
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
}
