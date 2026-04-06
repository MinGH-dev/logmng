package com.logmng.util;

import com.logmng.constants.ActivityActionType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityLogAuditMaskingTest {

    @Test
    void masksInAppCopyTextToPreviewMax() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("action_type", ActivityActionType.IN_APP_COPY.getCode());
        Map<String, Object> detail = new LinkedHashMap<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        String longText = "x".repeat(ActivityLogAuditMasking.COPY_TEXT_PREVIEW_MAX_LEN + 50);
        payload.put("text", longText);
        payload.put("was_truncated", false);
        detail.put("copyPayload", payload);
        row.put("action_detail", detail);

        ActivityLogAuditMasking.applyToRow(row, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) row.get("action_detail");
        @SuppressWarnings("unchecked")
        Map<String, Object> cp = (Map<String, Object>) out.get("copyPayload");
        assertThat(cp.get("text").toString()).hasSize(ActivityLogAuditMasking.COPY_TEXT_PREVIEW_MAX_LEN);
        assertThat(cp.get("was_truncated")).isEqualTo(true);
        assertThat(row.get("actionDetailMasked")).isEqualTo(true);
    }

    @Test
    void masksIpv4LastOctetForPeer() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("action_type", "LOGIN");
        row.put("ip_address", "192.168.1.100");
        row.put("action_detail", Map.of("k", "v"));
        ActivityLogAuditMasking.applyToRow(row, true);
        assertThat(row.get("ip_address")).isEqualTo("192.168.1.*");
    }
}
