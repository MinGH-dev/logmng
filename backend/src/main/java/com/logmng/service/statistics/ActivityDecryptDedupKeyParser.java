package com.logmng.service.statistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.dto.DecryptionRowKey;

import java.util.Map;
import java.util.Optional;

/**
 * Builds stable dedup keys for activity-statistics DECRYPT KPIs from {@code action_detail} JSON
 * (req 20260408-activity-statistics-decrypt-unique-rows-per-day).
 * <p>
 * Supports {@code java_fw_imglog} only; {@code pb_feplog} has no stable audit shape yet (returns empty).
 */
public final class ActivityDecryptDedupKeyParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    /** Legacy rows may store {@code logType} with extra JSON quote characters in the scalar (double-encoding). */
    private static final int MAX_LOG_TYPE_SURROUNDING_QUOTE_STRIPS = 3;
    private static final String JAVA_FW_IMGLOG = "java_fw_imglog";

    private ActivityDecryptDedupKeyParser() {
    }

    /**
     * Trims and strips up to {@link #MAX_LOG_TYPE_SURROUNDING_QUOTE_STRIPS} pairs of surrounding {@code "} only.
     * Does not alter {@code guid} / identifiers.
     */
    static String normalizeRequestParamsLogType(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        for (int i = 0; i < MAX_LOG_TYPE_SURROUNDING_QUOTE_STRIPS && s.length() >= 2; i++) {
            if (s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                s = s.substring(1, s.length() - 1).trim();
            } else {
                break;
            }
        }
        return s;
    }

    /**
     * @param statisticsLogType filter id e.g. {@code java_fw_imglog}, {@code LOGIN}, {@code pb_feplog}
     * @param actionDetailJson   full {@code action_detail} column text
     * @return dedup key when {@code logType} matches filter, audit has complete guid+status+java_fw_imglog; else empty
     */
    public static Optional<String> tryDedupKey(String statisticsLogType, String actionDetailJson) {
        if (statisticsLogType == null || statisticsLogType.isEmpty()) {
            return Optional.empty();
        }
        if (!JAVA_FW_IMGLOG.equalsIgnoreCase(statisticsLogType)) {
            return Optional.empty();
        }
        if (actionDetailJson == null || actionDetailJson.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> root = MAPPER.readValue(actionDetailJson, MAP_TYPE);
            Object rpObj = root.get("requestParams");
            if (!(rpObj instanceof Map)) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rp = (Map<String, Object>) rpObj;
            Object ltObj = rp.get("logType");
            String ltNorm = normalizeRequestParamsLogType(ltObj != null ? ltObj.toString() : null);
            if (!JAVA_FW_IMGLOG.equals(ltNorm)) {
                return Optional.empty();
            }
            String guid = null;
            String statusNorm = "";
            Object req = rp.get("request");
            if (req instanceof Map<?, ?> reqMap) {
                Object g = reqMap.get("guid");
                guid = g != null ? g.toString().trim() : null;
                Object s = reqMap.get("status");
                statusNorm = DecryptionRowKey.normalizeStatus(s != null ? s.toString() : null);
            } else if (req instanceof String reqStr && !reqStr.isBlank()) {
                try {
                    Map<String, Object> inner = MAPPER.readValue(reqStr, MAP_TYPE);
                    Object g = inner.get("guid");
                    guid = g != null ? g.toString().trim() : null;
                    Object s = inner.get("status");
                    statusNorm = DecryptionRowKey.normalizeStatus(s != null ? s.toString() : null);
                } catch (Exception ignored) {
                    return Optional.empty();
                }
            } else {
                Object id = rp.get("identifier");
                guid = id != null ? id.toString().trim() : null;
                Object s = rp.get("status");
                statusNorm = DecryptionRowKey.normalizeStatus(s != null ? s.toString() : null);
            }
            if (guid == null || guid.isEmpty() || statusNorm.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(JAVA_FW_IMGLOG + "\u001f" + guid + "\u001f" + statusNorm);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
