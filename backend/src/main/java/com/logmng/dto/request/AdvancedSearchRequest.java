package com.logmng.dto.request;

import java.util.List;

/**
 * 고급 검색 요청 DTO (AST 기반)
 */
public class AdvancedSearchRequest {
    private String logType;
    private String queryText; // 전체 검색 키워드 (선택)
    private String startDate; // 시작 날짜
    private String endDate; // 종료 날짜
    private List<FilterCondition> filters;
    private List<SortCondition> sort;
    private Pagination pagination;
    private Boolean decryptData = false;
    
    public AdvancedSearchRequest() {
    }
    
    // Getters and Setters
    public String getLogType() {
        return logType;
    }
    
    public void setLogType(String logType) {
        this.logType = logType;
    }
    
    public String getQueryText() {
        return queryText;
    }
    
    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }
    
    public List<FilterCondition> getFilters() {
        return filters;
    }
    
    public void setFilters(List<FilterCondition> filters) {
        this.filters = filters;
    }
    
    public List<SortCondition> getSort() {
        return sort;
    }
    
    public void setSort(List<SortCondition> sort) {
        this.sort = sort;
    }
    
    public Pagination getPagination() {
        return pagination;
    }
    
    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }
    
    public Boolean getDecryptData() {
        return decryptData;
    }
    
    public void setDecryptData(Boolean decryptData) {
        this.decryptData = decryptData;
    }
    
    public String getStartDate() {
        return startDate;
    }
    
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }
    
    public String getEndDate() {
        return endDate;
    }
    
    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
    
    /**
     * 정렬 조건
     */
    public static class SortCondition {
        private String field;
        private String direction; // asc | desc
        
        public SortCondition() {
        }
        
        public SortCondition(String field, String direction) {
            this.field = field;
            this.direction = direction;
        }
        
        public String getField() {
            return field;
        }
        
        public void setField(String field) {
            this.field = field;
        }
        
        public String getDirection() {
            return direction;
        }
        
        public void setDirection(String direction) {
            this.direction = direction;
        }
    }
    
    /**
     * 페이지네이션
     */
    public static class Pagination {
        private Integer page = 1;
        private Integer pageSize = 50;
        
        public Pagination() {
        }
        
        public Pagination(Integer page, Integer pageSize) {
            this.page = page;
            this.pageSize = pageSize;
        }
        
        public Integer getPage() {
            return page;
        }
        
        public void setPage(Integer page) {
            this.page = page;
        }
        
        public Integer getPageSize() {
            return pageSize;
        }
        
        public void setPageSize(Integer pageSize) {
            this.pageSize = pageSize;
        }
    }
}

