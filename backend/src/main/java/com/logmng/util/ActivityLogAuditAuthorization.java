package com.logmng.util;

import com.logmng.constants.ScreenConstants;
import com.logmng.dto.response.LoginResponse;

import java.util.List;

/**
 * Authorization for privileged copy reveal and access-audit query (req 20260330, contract AAE-02).
 * Gate: system admin OR {@code activity-log-access-audit} screen (enumerated privileged auditor).
 */
public final class ActivityLogAuditAuthorization {

    private ActivityLogAuditAuthorization() {
    }

    public static boolean canRevealFullCopy(LoginResponse user) {
        if (user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(user.getIsSystemAdmin())) {
            return true;
        }
        return hasScreen(user, ScreenConstants.ACTIVITY_LOG_ACCESS_AUDIT);
    }

    public static boolean canQueryAccessAudit(LoginResponse user) {
        return canRevealFullCopy(user);
    }

    private static boolean hasScreen(LoginResponse user, String screenId) {
        List<String> allowed = user.getAllowedScreenIds();
        return allowed != null && allowed.contains(screenId);
    }
}
