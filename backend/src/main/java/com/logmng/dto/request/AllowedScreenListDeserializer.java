package com.logmng.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.logmng.dto.response.AllowedScreenItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserializes allowedScreens from either string[] or [{ screenId, scope?, read?, write?, approve? }].
 * Backward compatible with frontend sending ["activity-log", "statistics"].
 * Per spec §1.1: read/write/approve must be parsed and persisted; null in JSON = use derived default.
 */
public class AllowedScreenListDeserializer extends JsonDeserializer<List<AllowedScreenItem>> {

    /** Parse boolean from JSON node; null/missing → null (use derivation). */
    private static Boolean readBooleanOrNull(JsonNode parent, String key) {
        if (parent == null || !parent.has(key)) return null;
        JsonNode n = parent.get(key);
        if (n == null || n.isNull()) return null;
        return n.asBoolean();
    }

    @Override
    public List<AllowedScreenItem> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node == null || !node.isArray()) {
            return null;
        }
        List<AllowedScreenItem> result = new ArrayList<>();
        for (JsonNode elem : node) {
            if (elem.isTextual()) {
                String screenId = elem.asText();
                if (screenId != null && !screenId.isBlank()) {
                    result.add(new AllowedScreenItem(screenId.trim(), null));
                }
            } else if (elem.isObject()) {
                String screenId = elem.has("screenId") ? elem.get("screenId").asText(null) : null;
                if (screenId != null && !screenId.isBlank()) {
                    AllowedScreenItem item = new AllowedScreenItem();
                    item.setScreenId(screenId.trim());
                    if (elem.has("scope")) item.setScope(elem.get("scope").asText(null));
                    // Parse read/write/approve; preserve explicit false and null (null = use derivation)
                    if (elem.has("read")) item.setRead(readBooleanOrNull(elem, "read"));
                    if (elem.has("write")) item.setWrite(readBooleanOrNull(elem, "write"));
                    if (elem.has("approve")) item.setApprove(readBooleanOrNull(elem, "approve"));
                    result.add(item);
                }
            }
        }
        return result;
    }
}
