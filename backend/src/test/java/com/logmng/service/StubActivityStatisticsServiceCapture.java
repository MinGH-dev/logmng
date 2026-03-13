package com.logmng.service;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stub ActivityStatisticsService that captures the last getDailyStatistics arguments (for TC-13).
 * Use when Mockito cannot mock the concrete class (e.g. Java 25).
 */
public class StubActivityStatisticsServiceCapture extends ActivityStatisticsService {

    private String lastUserId;
    private List<String> lastAllowedUserIds;
    private String lastDepartment;
    private String lastIp;
    private String lastUsername;

    public StubActivityStatisticsServiceCapture(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Map<String, Object> getDailyStatistics(String startDate, String endDate, String logType,
                                                   String userId, List<String> allowedUserIds, String department, String ip, String username) {
        this.lastUserId = userId;
        this.lastAllowedUserIds = allowedUserIds;
        this.lastDepartment = department;
        this.lastIp = ip;
        this.lastUsername = username;
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("dailyStats", Collections.emptyList());
        empty.put("summary", Map.of("totalSearches", 0L, "totalDecrypts", 0L, "totalLogins", 0L, "uniqueUsers", 0));
        return empty;
    }

    public String getLastUserId() { return lastUserId; }
    public List<String> getLastAllowedUserIds() { return lastAllowedUserIds; }
    public String getLastDepartment() { return lastDepartment; }
    public String getLastIp() { return lastIp; }
    public String getLastUsername() { return lastUsername; }
}
