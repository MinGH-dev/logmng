package com.logmng.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DB 기반 로그 검색 요청 DTO.
 * UI-only fields (e.g. showDecryptOption) in stored search_params are ignored on deserialize.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogDbSearchRequest {
    
    private static final Logger log = LoggerFactory.getLogger(LogDbSearchRequest.class);
    
    // 날짜는 문자열로 받아서 파싱 (프론트엔드에서 다양한 형식으로 전송 가능)
    private String startDate;
    private String endDate;
    
    @JsonProperty("mediaCode")
    private String mediaCode;
    
    @JsonProperty("media_gb")
    private String mediaGb;
    
    @JsonProperty("trCode")
    private String trCode;
    
    @JsonProperty("tr_code")
    private String trCodeAlt;
    
    @JsonProperty("loginId")
    private String loginId;
    
    @JsonProperty("accountNumbers")
    private List<String> accountNumbers = new ArrayList<>();
    
    // 이미지로그 전용 필드
    @JsonProperty("application")
    private String application;
    
    @JsonProperty("servicegroup")
    private String servicegroup;
    
    @JsonProperty("service")
    private String service;
    
    @JsonProperty("datastring")
    private String datastring;
    
    @JsonProperty("headerstring")
    private String headerstring;
    
    @JsonProperty("keywords")
    private List<String> keywords = new ArrayList<>();
    
    @JsonProperty("decryptData")
    private Boolean decryptData = false;
    
    @JsonProperty("logType")
    private String logType = "pb_feplog"; // 기본값: pb_feplog
    
    private Integer page = 1;
    private Integer pageSize = 10;
    private String sortField = "log_timestamp";
    private String sortDirection = "desc";
    /** Optional multi-column sort for pb_feplog (ordered). When non-empty, takes precedence over sortField/sortDirection for PB FEP. */
    @JsonProperty("sortSpecs")
    private List<LogDbSortSpec> sortSpecs = new ArrayList<>();
    private String displayTemplate = "detailed";
    
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
    
    public String getMediaCode() {
        return mediaCode != null ? mediaCode : mediaGb;
    }
    
    public void setMediaCode(String mediaCode) {
        this.mediaCode = mediaCode;
    }
    
    public String getMediaGb() {
        return mediaGb;
    }
    
    public void setMediaGb(String mediaGb) {
        this.mediaGb = mediaGb;
    }
    
    public String getTrCode() {
        return trCode != null ? trCode : trCodeAlt;
    }
    
    public void setTrCode(String trCode) {
        this.trCode = trCode;
    }
    
    public String getTrCodeAlt() {
        return trCodeAlt;
    }
    
    public void setTrCodeAlt(String trCodeAlt) {
        this.trCodeAlt = trCodeAlt;
    }
    
    public String getLoginId() {
        return loginId;
    }
    
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
    
    /**
     * startDate를 LocalDateTime으로 변환
     */
    public LocalDateTime getStartDateAsDateTime() {
        return parseDateTime(startDate);
    }
    
    /**
     * endDate를 LocalDateTime으로 변환
     */
    public LocalDateTime getEndDateAsDateTime() {
        return parseDateTime(endDate);
    }
    
    /**
     * 날짜 문자열을 LocalDateTime으로 파싱
     * 프론트엔드에서 보내는 형식: yyyy-MM-dd HH:mm:ss, yyyy-MM-ddTHH:mm:ss, HHMMSSMS (예: 090855950)
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
            
            // yyyy-MM-dd HH:mm:ss 형식 (프론트엔드에서 보내는 형식)
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                java.time.format.DateTimeFormatter formatter =
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(trimmed, formatter);
            }
            // yyyy-MM-dd HH:mm:ss.SSS 형식 (밀리초 포함)
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{1,3}")) {
                java.time.format.DateTimeFormatter formatter =
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
                return LocalDateTime.parse(trimmed, formatter);
            }

            // yyyy-MM-dd 형식 (날짜만)
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
                java.time.format.DateTimeFormatter formatter = 
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return java.time.LocalDate.parse(trimmed, formatter).atStartOfDay();
            }
            
            // HHMMSSMS 형식 (예: 090855950) - 프론트엔드에서 보내는 형식
            if (trimmed.length() >= 6 && trimmed.matches("\\d+")) {
                java.time.LocalDate today = java.time.LocalDate.now();
                int hour = Integer.parseInt(trimmed.substring(0, 2));
                int minute = Integer.parseInt(trimmed.substring(2, 4));
                int second = trimmed.length() >= 6 ? Integer.parseInt(trimmed.substring(4, 6)) : 0;
                int millisecond = trimmed.length() >= 9 ? Integer.parseInt(trimmed.substring(6, 9)) : 0;
                return today.atTime(hour, minute, second).plusNanos(millisecond * 1_000_000L);
            }
            
            // yyyy-MM-dd 형식
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return java.time.LocalDate.parse(trimmed).atStartOfDay();
            }
            
            // yyyy-MM-dd HH:mm:ss 형식
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                return LocalDateTime.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            
            log.debug("날짜 파싱 미지원 형식(null 반환): raw={}", dateStr);
            return null;
        } catch (Exception e) {
            log.warn("날짜 파싱 실패: raw={}", dateStr, e);
            return null;
        }
    }
    
    public List<String> getAccountNumbers() {
        return accountNumbers;
    }
    
    public void setAccountNumbers(List<String> accountNumbers) {
        this.accountNumbers = accountNumbers;
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

    public List<LogDbSortSpec> getSortSpecs() {
        return sortSpecs;
    }

    public void setSortSpecs(List<LogDbSortSpec> sortSpecs) {
        this.sortSpecs = sortSpecs != null ? sortSpecs : new ArrayList<>();
    }
    
    public String getDisplayTemplate() {
        return displayTemplate;
    }
    
    public void setDisplayTemplate(String displayTemplate) {
        this.displayTemplate = displayTemplate;
    }
    
    public List<String> getKeywords() {
        return keywords;
    }
    
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
    
    public Boolean getDecryptData() {
        return decryptData != null ? decryptData : false;
    }
    
    public void setDecryptData(Boolean decryptData) {
        this.decryptData = decryptData;
    }
    
    public String getLogType() {
        return logType != null ? logType : "pb_feplog";
    }
    
    public void setLogType(String logType) {
        this.logType = logType;
    }
    
    public String getApplication() {
        return application;
    }
    
    public void setApplication(String application) {
        this.application = application;
    }
    
    public String getServicegroup() {
        return servicegroup;
    }
    
    public void setServicegroup(String servicegroup) {
        this.servicegroup = servicegroup;
    }
    
    public String getService() {
        return service;
    }
    
    public void setService(String service) {
        this.service = service;
    }
    
    public String getDatastring() {
        return datastring;
    }
    
    public void setDatastring(String datastring) {
        this.datastring = datastring;
    }
    
    public String getHeaderstring() {
        return headerstring;
    }
    
    public void setHeaderstring(String headerstring) {
        this.headerstring = headerstring;
    }
    
    /**
     * insert_time을 bigint 타임스탬프로 변환 (이미지로그용)
     */
    public Long getStartDateAsTimestamp() {
        LocalDateTime dateTime = getStartDateAsDateTime();
        if (dateTime == null) {
            log.debug("getStartDateAsTimestamp: startDate 파싱 결과 null (raw startDate={})", startDate);
            return null;
        }
        return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public Long getEndDateAsTimestamp() {
        LocalDateTime dateTime = getEndDateAsDateTime();
        if (dateTime == null) {
            log.debug("getEndDateAsTimestamp: endDate 파싱 결과 null (raw endDate={})", endDate);
            return null;
        }
        // endDate가 날짜만 있으면 23:59:59.999로 설정
        String trimmedEndDate = endDate != null ? endDate.trim() : "";
        if (trimmedEndDate.matches("\\d{4}-\\d{2}-\\d{2}") && !trimmedEndDate.contains(":")) {
            dateTime = dateTime.toLocalDate().atTime(23, 59, 59, 999_000_000);
        }
        // endDate가 yyyy-MM-dd HH:mm:ss 형식이고 시간이 00:00:00이면 23:59:59.999로 설정
        else if (trimmedEndDate.matches("\\d{4}-\\d{2}-\\d{2} 00:00:00")) {
            dateTime = dateTime.toLocalDate().atTime(23, 59, 59, 999_000_000);
        }
        return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

