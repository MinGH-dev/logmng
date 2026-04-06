package com.logmng.service;

import com.logmng.repository.UserActivityAccessAuditRepository;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Stub that captures the last actionDetail passed to saveActivityLog.
 * Use when testing ActivityLogAspect without Mockito (e.g. Java 25).
 */
public class StubUserActivityLogServiceSaveCapture extends UserActivityLogService {

    private Map<String, Object> lastActionDetail;

    public StubUserActivityLogServiceSaveCapture(DataSource dataSource) {
        super(dataSource, new UserActivityAccessAuditRepository(dataSource));
    }

    @Override
    public void saveActivityLog(String userId, String username, String actionType,
                                Map<String, Object> actionDetail, String ipAddress,
                                String userAgent, String requestMethod, String requestPath,
                                String requestParamsJson, Integer responseStatus,
                                Integer responseTimeMs, Boolean success, String errorMessage) {
        this.lastActionDetail = actionDetail;
    }

    public Map<String, Object> getLastActionDetail() {
        return lastActionDetail;
    }
}
