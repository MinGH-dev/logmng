package com.logmng.util;

import com.logmng.constants.ActivityActionType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Masks {@code user_activity_log} detail/list payloads per specs/activity-log-audit-evidence.spec.yaml §3.
 * Full copy body is never returned here — use privileged-reveal API only.
 */
public final class ActivityLogAuditMasking {

    /** Max characters of copy text exposed in list/detail GET (truncation only). */
    public static final int COPY_TEXT_PREVIEW_MAX_LEN = 200;

    private ActivityLogAuditMasking() {
    }

    /**
     * Applies masking to a single activity-log row map (mutates copy).
     *
     * @param row           keys include {@code action_type}, {@code action_detail}, {@code ip_address}
     * @param maskIpForPeer when true, mask {@code ip_address} last octet (non–system-admin callers)
     */
    @SuppressWarnings("unchecked")
    public static void applyToRow(Map<String, Object> row, boolean maskIpForPeer) {
        if (row == null) {
            return;
        }
        if (maskIpForPeer) {
            Object ip = row.get("ip_address");
            if (ip instanceof String s && !s.isBlank()) {
                row.put("ip_address", maskIpLastOctet(s));
            }
        }
        Object at = row.get("action_type");
        String actionType = at != null ? String.valueOf(at) : "";
        Object detailObj = row.get("action_detail");
        if (!(detailObj instanceof Map)) {
            return;
        }
        Map<String, Object> detail = (Map<String, Object>) detailObj;
        if (ActivityActionType.IN_APP_COPY.getCode().equals(actionType)) {
            row.put("action_detail", maskInAppCopyDetail(detail));
            row.put("actionDetailMasked", true);
        }
        // TODO(field-classification matrix): mask deleteSnapshot / before-after / other payloads per spec; pass-through for now.
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> maskInAppCopyDetail(Map<String, Object> detail) {
        Map<String, Object> copy = new LinkedHashMap<>(detail);
        Object cp = copy.get("copyPayload");
        if (cp instanceof Map) {
            Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) cp);
            Object textObj = payload.get("text");
            String text = textObj != null ? String.valueOf(textObj) : "";
            boolean truncated = text.length() > COPY_TEXT_PREVIEW_MAX_LEN;
            String preview = truncated ? text.substring(0, COPY_TEXT_PREVIEW_MAX_LEN) : text;
            payload.put("text", preview);
            payload.put("was_truncated", truncated || Boolean.TRUE.equals(payload.get("was_truncated")));
            copy.put("copyPayload", payload);
        }
        return copy;
    }

    static String maskIpLastOctet(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0 && lastDot < ip.length() - 1) {
            return ip.substring(0, lastDot + 1) + "*";
        }
        // IPv6 or unusual: redact tail
        if (ip.length() > 8) {
            return ip.substring(0, Math.min(8, ip.length())) + "…";
        }
        return "*";
    }
}
