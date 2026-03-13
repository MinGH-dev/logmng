package com.logmng.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 사용자 활동 이력 검색 요청 DTO
 */
public class UserActivityLogSearchRequest {
    
    private String startDate;
    private String endDate;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("actionType")
    private String actionType;
    
    @JsonProperty("ipAddress")
    private String ipAddress;

    /** Optional department (or departmentCode) filter. Ignored when scope=self; applied when scope=team/all. */
    @JsonProperty("department")
    private String department;

    @JsonProperty("departmentCode")
    public void setDepartmentCode(String departmentCode) {
        if (this.department == null || this.department.isBlank()) {
            this.department = departmentCode;
        }
    }

    /** When scope=team, set by server-side scope enforcement only. Never trust client input. */
    private List<String> allowedUserIds;

    private Integer page = 1;
    private Integer pageSize = 20;
    private String sortField = "created_at";
    private String sortDirection = "desc";
    
    // Getters and Setters
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
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getActionType() {
        return actionType;
    }
    
    public void setActionType(String actionType) {
        this.actionType = actionType;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    @JsonIgnore
    public List<String> getAllowedUserIds() {
        return allowedUserIds;
    }

    @JsonIgnore
    public void setAllowedUserIds(List<String> allowedUserIds) {
        this.allowedUserIds = allowedUserIds;
    }

    public Integer getPage() {
        return page != null ? page : 1;
    }
    
    public void setPage(Integer page) {
        this.page = page;
    }
    
    public Integer getPageSize() {
        return pageSize != null ? pageSize : 20;
    }
    
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
    
    public String getSortField() {
        return sortField != null ? sortField : "created_at";
    }
    
    public void setSortField(String sortField) {
        this.sortField = sortField;
    }
    
    public String getSortDirection() {
        return sortDirection != null ? sortDirection : "desc";
    }
    
    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }
    
    /**
     * startDate를 LocalDateTime으로 변환
     */
    public LocalDateTime getStartDateAsDateTime() {
        return parseDateTime(startDate);
    }
    
    /**
     * endDate를 LocalDateTime으로 변환.
     * 날짜만(yyyy-MM-dd)이면 당일 23:59:59로 해석하여 당일 전체가 조회되도록 함.
     */
    public LocalDateTime getEndDateAsDateTime() {
        return parseEndDateTime(endDate);
    }
    
    /**
     * startDate의 날짜 부분만 반환 (날짜만 비교 시 사용)
     */
    public LocalDate getStartDateAsLocalDate() {
        LocalDateTime dt = getStartDateAsDateTime();
        return dt != null ? dt.toLocalDate() : null;
    }
    
    /**
     * endDate의 날짜 부분만 반환 (날짜만 비교 시 사용)
     */
    public LocalDate getEndDateAsLocalDate() {
        LocalDateTime dt = getEndDateAsDateTime();
        return dt != null ? dt.toLocalDate() : null;
    }
    
    /**
     * 날짜 문자열을 LocalDateTime으로 파싱 (시작일용: 날짜만이면 00:00:00)
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            String trimmed = dateStr.trim();
            
            // ISO 8601 형식 (yyyy-MM-ddTHH:mm:ss)
            if (trimmed.contains("T")) {
                return LocalDateTime.parse(trimmed.replace("Z", ""));
            }
            
            // yyyy-MM-dd HH:mm:ss 형식
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(trimmed, formatter);
            }
            
            // yyyy-MM-dd 형식 (날짜만) → 시작일이므로 00:00:00
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return java.time.LocalDate.parse(trimmed, formatter).atStartOfDay();
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 종료일 전용 파싱: 날짜만(yyyy-MM-dd)이면 당일 23:59:59로 반환.
     */
    private LocalDateTime parseEndDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            String trimmed = dateStr.trim();
            // 날짜만 오면 당일 끝(23:59:59)으로 해석
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return java.time.LocalDate.parse(trimmed, formatter).atTime(23, 59, 59);
            }
            return parseDateTime(dateStr);
        } catch (Exception e) {
            return null;
        }
    }
}





