package com.logmng.service;

import com.logmng.dto.request.UserActivityLogSearchRequest;
import com.logmng.dto.response.UserActivityLogResponse;

import javax.sql.DataSource;
import java.util.Collections;

/**
 * Stub UserActivityLogService that captures the last search request (for TC-12).
 * Use when Mockito cannot mock the concrete class (e.g. Java 25).
 */
public class StubUserActivityLogServiceCapture extends UserActivityLogService {

    private UserActivityLogSearchRequest lastRequest;

    public StubUserActivityLogServiceCapture(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public UserActivityLogResponse searchActivityLogs(UserActivityLogSearchRequest request) {
        this.lastRequest = request;
        return new UserActivityLogResponse(
                Collections.emptyList(),
                new UserActivityLogResponse.PaginationInfo(1, 1, 0L));
    }

    public UserActivityLogSearchRequest getLastRequest() {
        return lastRequest;
    }
}
