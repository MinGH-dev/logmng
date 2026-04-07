package com.logmng.dto.response;

import java.util.List;

public class ExternalEmployeeSearchResult {

    private List<ExternalEmployeeItemResponse> items;
    private PaginationResponse pagination;

    public ExternalEmployeeSearchResult() {
    }

    public ExternalEmployeeSearchResult(List<ExternalEmployeeItemResponse> items, PaginationResponse pagination) {
        this.items = items;
        this.pagination = pagination;
    }

    public List<ExternalEmployeeItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ExternalEmployeeItemResponse> items) {
        this.items = items;
    }

    public PaginationResponse getPagination() {
        return pagination;
    }

    public void setPagination(PaginationResponse pagination) {
        this.pagination = pagination;
    }
}
