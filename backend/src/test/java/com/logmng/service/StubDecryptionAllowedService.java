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
        super(null);
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    @Override
    public boolean isAllowed(Long userId, String screen, String guid) {
        return allowed;
    }

    @Override
    public Map<String, Object> getAllowed(Long userId, String screen) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("screen", screen);
        out.put("validUntil", allowed ? "2099-12-31T23:59:59" : null);
        out.put("guids", allowed ? List.of("guid-any", "guid-in-snapshot") : Collections.emptyList());
        return out;
    }
}
