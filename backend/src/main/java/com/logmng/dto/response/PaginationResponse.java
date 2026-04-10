package com.logmng.dto.response;

public class PaginationResponse {

    private int currentPage;
    private int totalPages;
    private long totalCount;

    public PaginationResponse() {
    }

    public PaginationResponse(int currentPage, int totalPages, long totalCount) {
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalCount = totalCount;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }
}
