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

    @Test
    void masksRequestParamsSecretsAndKeepsOtherKeys() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("action_type", "LOGIN");
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("username", "u1");
        req.put("password", "secret");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("accessToken", "tok");
        nested.put("scope", "read");
        req.put("meta", nested);
        row.put("request_params", req);
        row.put("action_detail", Map.of("k", "v"));

        ActivityLogAuditMasking.applyToRow(row, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) row.get("request_params");
        assertThat(out.get("username")).isEqualTo("u1");
        assertThat(out.get("password")).isEqualTo(ActivityLogAuditMasking.MASKED_SECRET_PLACEHOLDER);
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) out.get("meta");
        assertThat(meta.get("accessToken")).isEqualTo(ActivityLogAuditMasking.MASKED_SECRET_PLACEHOLDER);
        assertThat(meta.get("scope")).isEqualTo("read");
    }

    @Test
    void preservesBeforeAfterShapeMasksSensitiveLeaves() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("action_type", "PERMISSION_GROUP_UPDATE");
        Map<String, Object> detail = new LinkedHashMap<>();
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", "old");
        before.put("clientSecret", "sec");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", "new");
        detail.put("before", before);
        detail.put("after", after);
        row.put("action_detail", detail);

        ActivityLogAuditMasking.applyToRow(row, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) row.get("action_detail");
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) out.get("before");
        assertThat(b.get("name")).isEqualTo("old");
        assertThat(b.get("clientSecret")).isEqualTo(ActivityLogAuditMasking.MASKED_SECRET_PLACEHOLDER);
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) out.get("after");
        assertThat(a.get("name")).isEqualTo("new");
    }
}
