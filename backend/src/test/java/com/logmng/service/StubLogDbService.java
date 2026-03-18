package com.logmng.service;

import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.response.LogDbSearchResponse;
import com.logmng.util.CryptoUtil;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test stub for LogDbService (used when Mockito cannot mock concrete class on Java 17+).
 * Overrides searchLogs, decryptRow, and getApplicationServiceGroupByGuids; other methods throw or return defaults.
 */
public class StubLogDbService extends LogDbService {

    private LogDbSearchResponse searchLogsResponse = new LogDbSearchResponse(Collections.emptyList(), null);
    private Map<String, Object> decryptRowResult = Collections.emptyMap();
    /** guid -> { "application", "serviceGroup" }. Default empty = simulates log DB unavailable or guid not in imagelog. */
    private Map<String, Map<String, String>> applicationServiceGroupByGuids = new LinkedHashMap<>();
    /** When non-null: pageSize==1 → totalCount only; else → rows (POST /api/search-history create snapshot). */
    private Long createSnapshotTotalCount;
    private List<Map<String, Object>> createSnapshotRows = Collections.emptyList();

    public StubLogDbService(DataSource dataSource, CryptoUtil cryptoUtil) {
        super(dataSource, cryptoUtil);
    }

    public void setSearchLogsResponse(LogDbSearchResponse response) {
        this.searchLogsResponse = response != null ? response : new LogDbSearchResponse(Collections.emptyList(), null);
    }

    public void setSearchLogsData(List<Map<String, Object>> data) {
        this.searchLogsResponse = new LogDbSearchResponse(data, null);
    }

    /** First searchLogs call uses pageSize 1 → returns this totalCount; second call returns rows (create-time counts). */
    public void setCreateSnapshotBehavior(long totalCount, List<Map<String, Object>> rowsForFullPage) {
        this.createSnapshotTotalCount = totalCount;
        this.createSnapshotRows = rowsForFullPage != null ? new ArrayList<>(rowsForFullPage) : new ArrayList<>();
    }

    public void clearCreateSnapshotBehavior() {
        this.createSnapshotTotalCount = null;
        this.createSnapshotRows = Collections.emptyList();
    }

    public void setDecryptRowResult(Map<String, Object> result) {
        this.decryptRowResult = result != null ? result : Collections.emptyMap();
    }

    @Override
    public LogDbSearchResponse searchLogs(LogDbSearchRequest request) {
        if (createSnapshotTotalCount != null) {
            int ps = request.getPageSize() != null ? request.getPageSize() : 20;
            if (ps == 1) {
                return new LogDbSearchResponse(Collections.emptyList(),
                        new LogDbSearchResponse.PaginationInfo(1, 1, createSnapshotTotalCount));
            }
            return new LogDbSearchResponse(new ArrayList<>(createSnapshotRows),
                    new LogDbSearchResponse.PaginationInfo(1, 1, createSnapshotTotalCount));
        }
        return searchLogsResponse;
    }

    @Override
    public Map<String, Object> decryptRow(String logType, String guid, String status) {
        return decryptRowResult;
    }

    /** Set resolution for getApplicationServiceGroupByGuids (req 20260318 detail modal tests). */
    public void setApplicationServiceGroupByGuids(Map<String, Map<String, String>> map) {
        this.applicationServiceGroupByGuids = map != null ? new LinkedHashMap<>(map) : new LinkedHashMap<>();
    }

    @Override
    public Map<String, Map<String, String>> getApplicationServiceGroupByGuids(List<String> guids) {
        if (guids == null || guids.isEmpty()) return Collections.emptyMap();
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (String guid : guids) {
            if (applicationServiceGroupByGuids.containsKey(guid)) {
                result.put(guid, new LinkedHashMap<>(applicationServiceGroupByGuids.get(guid)));
            }
        }
        return result;
    }
}
