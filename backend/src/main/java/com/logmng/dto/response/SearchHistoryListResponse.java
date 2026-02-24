package com.logmng.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 검색 이력 목록 응답 DTO
 */
public class SearchHistoryListResponse {

    private List<Map<String, Object>> data;
    private UserActivityLogResponse.PaginationInfo pagination;

    public SearchHistoryListResponse() {
    }

    public SearchHistoryListResponse(List<Map<String, Object>> data,
                                     UserActivityLogResponse.PaginationInfo pagination) {
        this.data = data;
        this.pagination = pagination;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    public UserActivityLogResponse.PaginationInfo getPagination() {
        return pagination;
    }

    public void setPagination(UserActivityLogResponse.PaginationInfo pagination) {
        this.pagination = pagination;
    }
}
