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
 * Deserializes allowedScreens from either string[] or [{ screenId, scope? }].
 * Backward compatible with frontend sending ["activity-log", "statistics"].
 */
public class AllowedScreenListDeserializer extends JsonDeserializer<List<AllowedScreenItem>> {

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
                    String scope = elem.has("scope") ? elem.get("scope").asText(null) : null;
                    result.add(new AllowedScreenItem(screenId.trim(), scope));
                }
            }
        }
        return result;
    }
}
