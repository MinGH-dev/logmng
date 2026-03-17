package com.logmng.dto.request;

import java.util.ArrayList;
import java.util.List;

/**
 * 검색 이력 목록 조회용 정규화 DTO.
 * 컨트롤러에서 scope를 해석한 뒤 서비스에 전달한다.
 * userId/actorUserId/allowedUserIds are numeric app_user.id (req 20260316).
 * requestedAtFrom/To: yyyy-MM-dd HH:mm:ss. approvalStatuses: multi. requestReason: ILIKE. Req 20260317.
 */
public class SearchHistoryListRequest {

    private Long actorUserId;
    private List<Long> allowedUserIds;
    private String department;
    private String username;
    private Long userId;
    private String requestedAtFrom;
    private String requestedAtTo;
    private List<String> approvalStatuses;
    private String requestReason;
    private int page = 1;
    private int pageSize = 20;
    private String sortField = "requested_at";
    private String sortDirection = "desc";

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public List<Long> getAllowedUserIds() {
        return allowedUserIds;
    }

    public void setAllowedUserIds(List<Long> allowedUserIds) {
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRequestedAtFrom() {
        return requestedAtFrom;
    }

    public void setRequestedAtFrom(String requestedAtFrom) {
        this.requestedAtFrom = requestedAtFrom;
    }

    public String getRequestedAtTo() {
        return requestedAtTo;
    }

    public void setRequestedAtTo(String requestedAtTo) {
        this.requestedAtTo = requestedAtTo;
    }

    public List<String> getApprovalStatuses() {
        return approvalStatuses;
    }

    public void setApprovalStatuses(List<String> approvalStatuses) {
        this.approvalStatuses = approvalStatuses == null ? null : new ArrayList<>(approvalStatuses);
    }

    public String getRequestReason() {
        return requestReason;
    }

    public void setRequestReason(String requestReason) {
        this.requestReason = requestReason;
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
