package com.logmng.service;

import com.logmng.dto.DecryptionRowKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

/**
 * Test double that records the last addOrReplaceAllowed call (composite keys).
 */
public class RecordingDecryptionAllowedService extends DecryptionAllowedService {

    private Long lastUserId;
    private String lastScreen;
    private List<DecryptionRowKey> lastKeys = Collections.emptyList();

    public RecordingDecryptionAllowedService(DataSource dataSource) {
        super(dataSource, false);
    }

    @Override
    public void addOrReplaceAllowed(Long userId, String screen, List<DecryptionRowKey> keys) {
        addOrReplaceAllowed(userId, screen, keys, null);
    }

    @Override
    public void addOrReplaceAllowed(Long userId, String screen, List<DecryptionRowKey> keys, Long searchHistoryIdForDiagnostics) {
        this.lastUserId = userId;
        this.lastScreen = screen;
        this.lastKeys = keys != null ? new ArrayList<>(keys) : Collections.emptyList();
        super.addOrReplaceAllowed(userId, screen, keys, searchHistoryIdForDiagnostics);
    }

    @Override
    public int deleteExpiredForUser(Long userId) {
        return 0;
    }

    public Long getLastUserId() {
        return lastUserId;
    }

    public String getLastScreen() {
        return lastScreen;
    }

    public List<DecryptionRowKey> getLastKeys() {
        return lastKeys;
    }

    /** @deprecated Use {@link #getLastKeys()} */
    @Deprecated
    public List<String> getLastGuids() {
        List<String> guids = new ArrayList<>();
        for (DecryptionRowKey k : lastKeys) {
            guids.add(k.getGuid());
        }
        return guids;
    }
}
