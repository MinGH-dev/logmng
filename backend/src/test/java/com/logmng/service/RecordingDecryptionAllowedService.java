package com.logmng.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

/**
 * Test double that records the last addOrReplaceAllowed(userId, screen, guids) call.
 * Used to verify that only encrypted row IDs are passed (req 20260318).
 */
public class RecordingDecryptionAllowedService extends DecryptionAllowedService {

    private Long lastUserId;
    private String lastScreen;
    private List<String> lastGuids = Collections.emptyList();

    public RecordingDecryptionAllowedService(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void addOrReplaceAllowed(Long userId, String screen, List<String> guids) {
        this.lastUserId = userId;
        this.lastScreen = screen;
        this.lastGuids = guids != null ? new ArrayList<>(guids) : Collections.emptyList();
    }

    @Override
    public int deleteExpiredForUser(Long userId) {
        return 0;
    }

    public Long getLastUserId() { return lastUserId; }
    public String getLastScreen() { return lastScreen; }
    public List<String> getLastGuids() { return lastGuids; }
}
