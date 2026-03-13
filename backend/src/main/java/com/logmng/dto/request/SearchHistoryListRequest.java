package com.logmng.dto.request;

import java.util.ArrayList;
import java.util.List;

/**
 * 검색 이력 목록 조회용 정규화 DTO.
 * 컨트롤러에서 scope를 해석한 뒤 서비스에 전달한다.
 */
public class SearchHistoryListRequest {

    private String actorUserId;
    private List<String> allowedUserIds;
    private String department;
    private String username;
    private String userId;
    private int page = 1;
    private int pageSize = 20;
    private String sortField = "requested_at";
    private String sortDirection = "desc";

    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
    }

    public List<String> getAllowedUserIds() {
        return allowedUserIds;
    }

    public void setAllowedUserIds(List<String> allowedUserIds) {
        this.allowedUserIds = allowedUserIds == null ? null : new ArrayList<>(allowedUserIds);
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }
}
