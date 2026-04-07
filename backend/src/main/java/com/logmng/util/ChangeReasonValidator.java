package com.logmng.util;

import com.logmng.activity.ActivityAuditDetailEnricher;
import com.logmng.exception.CustomException;
import org.springframework.util.StringUtils;

/**
 * Validates admin {@code changeReason} per contract (MF-03 alignment: max 500, trim, non-empty).
 */
public final class ChangeReasonValidator {

    public static final int MAX_CHANGE_REASON_LENGTH = ActivityAuditDetailEnricher.MAX_CHANGE_REASON_AUDIT_CHARS;

    private ChangeReasonValidator() {
    }

    /**
     * @return trimmed non-empty reason
     * @throws CustomException bad request {@code INVALID_INPUT} when missing, blank, or over max length
     */
    public static String requireValidChangeReason(String changeReason) {
        if (changeReason == null || !StringUtils.hasText(changeReason.trim())) {
            throw CustomException.badRequest("changeReason은 필수이며 공백만일 수 없습니다.", "INVALID_INPUT");
        }
        String trimmed = changeReason.trim();
        if (trimmed.length() > MAX_CHANGE_REASON_LENGTH) {
            throw CustomException.badRequest(
                    "changeReason은 " + MAX_CHANGE_REASON_LENGTH + "자 이하여야 합니다.",
                    "INVALID_INPUT");
        }
        return trimmed;
    }
}
