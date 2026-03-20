package com.logmng.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test stub for DecryptionAllowedService (DecryptController tests, req 20260318).
 */
public class StubDecryptionAllowedService extends DecryptionAllowedService {

    private boolean allowed = false;

    public StubDecryptionAllowedService() {
        super(null, false);
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    @Override
    public boolean isAllowed(Long userId, String screen, String guid, String status) {
        return allowed;
    }

    @Override
    public Map<String, Object> getAllowed(Long userId, String screen) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("screen", screen);
        out.put("validUntil", allowed ? "2099-12-31T23:59:59" : null);
        out.put("guids", allowed ? List.of("guid-any", "guid-in-snapshot") : Collections.emptyList());
        if (allowed) {
            List<Map<String, String>> rows = new java.util.ArrayList<>();
            Map<String, String> a = new java.util.LinkedHashMap<>();
            a.put("guid", "guid-any");
            a.put("status", "input");
            rows.add(a);
            Map<String, String> b = new java.util.LinkedHashMap<>();
            b.put("guid", "guid-in-snapshot");
            b.put("status", "output");
            rows.add(b);
            out.put("allowedRows", rows);
        } else {
            out.put("allowedRows", Collections.emptyList());
        }
        return out;
    }
}
