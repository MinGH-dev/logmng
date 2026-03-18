package com.logmng.service;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test stub for SearchHistoryService (used when Mockito cannot mock concrete class on Java 17+).
 * Overrides only the methods needed for DecryptController snapshot tests.
 */
public class StubSearchHistoryService extends SearchHistoryService {

    private boolean validApprovalForUser = true;
    private boolean rowInApprovedSnapshot = false;

    public StubSearchHistoryService(DataSource dataSource, LogDbService logDbService, DecryptApproverService decryptApproverService) {
        super(dataSource, logDbService, decryptApproverService);
    }

    public void setValidApprovalForUser(boolean value) {
        this.validApprovalForUser = value;
    }

    public void setRowInApprovedSnapshot(boolean value) {
        this.rowInApprovedSnapshot = value;
    }

    @Override
    public boolean isValidApprovalForUser(Long searchHistoryId, Long userId) {
        return validApprovalForUser;
    }

    @Override
    public boolean isRowInApprovedSnapshot(Long searchHistoryId, String logType, String rowId) {
        return rowInApprovedSnapshot;
    }

    @Override
    public Optional<ApprovalFailureDiagnostic> getApprovalFailureReason(Long searchHistoryId, Long userId) {
        return Optional.empty();
    }
}
