package com.logmng.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 검색 이력 저장 요청 DTO
 */
public class SearchHistoryCreateRequest {

    @NotBlank(message = "logType is required")
    private String logType;

    @NotNull(message = "searchParams is required")
    private Map<String, Object> searchParams;

    /** 요청 사유 (optional). Max 500 chars; overlength → 400. Req 20260317. */
    private String requestReason;

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public Map<String, Object> getSearchParams() {
        return searchParams;
    }

    public void setSearchParams(Map<String, Object> searchParams) {
        this.searchParams = searchParams;
    }

    public String getRequestReason() {
        return requestReason;
    }

    public void setRequestReason(String requestReason) {
        this.requestReason = requestReason;
    }
}
