package com.logmng.dto.response;

import java.util.List;

public class ExternalDepartmentSearchResult {

    private List<ExternalDepartmentItemResponse> items;
    private PaginationResponse pagination;

    public ExternalDepartmentSearchResult() {
    }

    public ExternalDepartmentSearchResult(List<ExternalDepartmentItemResponse> items, PaginationResponse pagination) {
        this.items = items;
        this.pagination = pagination;
    }

    public List<ExternalDepartmentItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ExternalDepartmentItemResponse> items) {
        this.items = items;
    }

    public PaginationResponse getPagination() {
        return pagination;
    }

    public void setPagination(PaginationResponse pagination) {
        this.pagination = pagination;
    }
}
