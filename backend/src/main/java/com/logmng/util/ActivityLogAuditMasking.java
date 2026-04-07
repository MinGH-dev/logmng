package com.logmng.util;

import com.logmng.constants.ActivityActionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Masks {@code user_activity_log} detail/list payloads per specs/activity-log-audit-evidence.spec.yaml §3.
 * Full copy body is never returned here — use privileged-reveal API only.
 */
public final class ActivityLogAuditMasking {

    /** Max characters of copy text exposed in list/detail GET (truncation only). */
    public static final int COPY_TEXT_PREVIEW_MAX_LEN = 200;

    /** Placeholder for masked secret values (passwords, tokens, etc.). */
    static final String MASKED_SECRET_PLACEHOLDER = "***";

    private ActivityLogAuditMasking() {
    }

    /**
     * Applies masking to a single activity-log row map (mutates copy).
     *
     * @param row           keys include {@code action_type}, {@code action_detail}, {@code request_params}, {@code ip_address}
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
        if (detailObj instanceof Map) {
            Map<String, Object> detail = (Map<String, Object>) detailObj;
            Map<String, Object> working = new LinkedHashMap<>(detail);
            if (ActivityActionType.IN_APP_COPY.getCode().equals(actionType)) {
                working = maskInAppCopyDetail(working);
                row.put("actionDetailMasked", true);
            }
            row.put("action_detail", maskSensitiveKeysInTree(working));
        }

        Object reqParams = row.get("request_params");
        if (reqParams instanceof Map) {
            row.put("request_params", maskSensitiveKeysInTree((Map<String, Object>) reqParams));
        }
    }

    /**
     * Recursively masks values for keys that indicate secrets (password, token, etc.).
     * Preserves map/list shape; only replaces values under sensitive keys (leaf scalars or full subtrees).
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> maskSensitiveKeysInTree(Map<String, Object> root) {
        if (root == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (isSensitiveKey(k)) {
                out.put(k, maskSubtreeUnderSensitiveKey(v));
            } else if (v instanceof Map) {
                out.put(k, maskSensitiveKeysInTree((Map<String, Object>) v));
            } else if (v instanceof List) {
                out.put(k, maskSensitiveListPreservingStructure((List<?>) v));
            } else {
                out.put(k, v);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> maskSensitiveListPreservingStructure(List<?> list) {
        List<Object> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map) {
                out.add(maskSensitiveKeysInTree((Map<String, Object>) item));
            } else if (item instanceof List) {
                out.add(maskSensitiveListPreservingStructure((List<?>) item));
            } else {
                out.add(item);
            }
        }
        return out;
    }

    static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("passwd")
                || lower.equals("pwd")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("apikey")
                || lower.contains("api_key")
                || lower.equals("authorization")
                || lower.contains("bearer");
    }

    private static Object maskSubtreeUnderSensitiveKey(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String) {
            return MASKED_SECRET_PLACEHOLDER;
        }
        if (v instanceof Number || v instanceof Boolean) {
            return MASKED_SECRET_PLACEHOLDER;
        }
        if (v instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) v;
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : m.entrySet()) {
                out.put(e.getKey(), maskSubtreeUnderSensitiveKey(e.getValue()));
            }
            return out;
        }
        if (v instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object item : (List<?>) v) {
                out.add(maskSubtreeUnderSensitiveKey(item));
            }
            return out;
        }
        return MASKED_SECRET_PLACEHOLDER;
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
