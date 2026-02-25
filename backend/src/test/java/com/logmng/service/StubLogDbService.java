package com.logmng.service;

import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.response.LogDbSearchResponse;
import com.logmng.util.CryptoUtil;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test stub for LogDbService (used when Mockito cannot mock concrete class on Java 17+).
 * Overrides searchLogs and decryptRow; other methods throw or return defaults.
 */
public class StubLogDbService extends LogDbService {

    private LogDbSearchResponse searchLogsResponse = new LogDbSearchResponse(Collections.emptyList(), null);
    private Map<String, Object> decryptRowResult = Collections.emptyMap();

    public StubLogDbService(DataSource dataSource, CryptoUtil cryptoUtil) {
        super(dataSource, cryptoUtil);
    }

    public void setSearchLogsResponse(LogDbSearchResponse response) {
        this.searchLogsResponse = response != null ? response : new LogDbSearchResponse(Collections.emptyList(), null);
    }

    public void setSearchLogsData(List<Map<String, Object>> data) {
        this.searchLogsResponse = new LogDbSearchResponse(data, null);
    }

    public void setDecryptRowResult(Map<String, Object> result) {
        this.decryptRowResult = result != null ? result : Collections.emptyMap();
    }

    @Override
    public LogDbSearchResponse searchLogs(LogDbSearchRequest request) {
        return searchLogsResponse;
    }

    @Override
    public Map<String, Object> decryptRow(String logType, String guid, String status) {
        return decryptRowResult;
    }
}
