package com.logmng.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 사용자 활동 이력 응답 DTO
 */
public class UserActivityLogResponse {
    
    private List<Map<String, Object>> data;
    private PaginationInfo pagination;
    
    public UserActivityLogResponse() {
    }
    
    public UserActivityLogResponse(List<Map<String, Object>> data, PaginationInfo pagination) {
        this.data = data;
        this.pagination = pagination;
    }
    
    public List<Map<String, Object>> getData() {
        return data;
    }
    
    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }
    
    public PaginationInfo getPagination() {
        return pagination;
    }
    
    public void setPagination(PaginationInfo pagination) {
        this.pagination = pagination;
    }
    
    public static class PaginationInfo {
        private Integer currentPage;
        private Integer totalPages;
        private Long totalCount;
        
        public PaginationInfo() {
        }
        
        public PaginationInfo(Integer currentPage, Integer totalPages, Long totalCount) {
            this.currentPage = currentPage;
            this.totalPages = totalPages;
            this.totalCount = totalCount;
        }
        
        public Integer getCurrentPage() {
            return currentPage;
        }
        
        public void setCurrentPage(Integer currentPage) {
            this.currentPage = currentPage;
        }
        
        public Integer getTotalPages() {
            return totalPages;
        }
        
        public void setTotalPages(Integer totalPages) {
            this.totalPages = totalPages;
        }
        
        public Long getTotalCount() {
            return totalCount;
        }
        
        public void setTotalCount(Long totalCount) {
            this.totalCount = totalCount;
        }
    }
}





