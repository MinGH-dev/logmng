package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 로그인 응답 DTO
 */
public class LoginResponse {
    
    private String username;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime loginTime;
    
    private String clientIP;

    /** @deprecated Use isSystemAdmin. Not exposed in JSON (req 20250303). */
    @Deprecated
    @JsonIgnore
    private String role;

    /** System administrator flag (is_system_admin). Admin-only access uses this. */
    private Boolean isSystemAdmin;

    /** Union of allowed screens from user's permission groups. System admin gets all. */
    private List<String> allowedScreenIds;

    /** Per-screen scope for activity-log, statistics, search-history. Key=screenId, value='self'|'team'|'all'. is_system_admin=true → omit or all 'all'. */
    private Map<String, String> screenScopes;

    /** Per-screen function availability. Key=screenId, value={read, write?, approve?}. Per req 20250303-screen-function-availability. */
    private Map<String, ScreenFunctionCapability> screenFunctions;

    /** Authoritative locked self-context for visible self-scoped user/requester fields. */
    private SelfContext selfContext;

    /** Numeric app_user.id for current user (req 20260316). Convenience for APIs that need Long. */
    private Long userId;

    public LoginResponse() {
    }
    
    public LoginResponse(String username, LocalDateTime loginTime, String clientIP) {
        this.username = username;
        this.loginTime = loginTime;
        this.clientIP = clientIP;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public LocalDateTime getLoginTime() {
        return loginTime;
    }
    
    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }
    
    public String getClientIP() {
        return clientIP;
    }
    
    public void setClientIP(String clientIP) {
        this.clientIP = clientIP;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getAllowedScreenIds() {
        return allowedScreenIds;
    }

    public void setAllowedScreenIds(List<String> allowedScreenIds) {
        this.allowedScreenIds = allowedScreenIds;
    }

    public Boolean getIsSystemAdmin() {
        return isSystemAdmin;
    }

    public void setIsSystemAdmin(Boolean isSystemAdmin) {
        this.isSystemAdmin = isSystemAdmin;
    }

    public Map<String, String> getScreenScopes() {
        return screenScopes;
    }

    public void setScreenScopes(Map<String, String> screenScopes) {
        this.screenScopes = screenScopes;
    }

    public Map<String, ScreenFunctionCapability> getScreenFunctions() {
        return screenFunctions;
    }

    public void setScreenFunctions(Map<String, ScreenFunctionCapability> screenFunctions) {
        this.screenFunctions = screenFunctions;
    }

    public SelfContext getSelfContext() {
        return selfContext;
    }

    public void setSelfContext(SelfContext selfContext) {
        this.selfContext = selfContext;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /** Self-context for locked user/requester display. userId = numeric app_user.id (req 20260316). */
    public static class SelfContext {

        private String department;
        private String username;
        /** Numeric app_user.id; JSON serializes as number. */
        private Long userId;
        /** Human-facing user identifier = app_user.employee_number (nullable during transition). */
        private String employeeNumber;

        public SelfContext() {
        }

        public SelfContext(String department, String username, Long userId, String employeeNumber) {
            this.department = department;
            this.username = username;
            this.userId = userId;
            this.employeeNumber = employeeNumber;
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

        public String getEmployeeNumber() {
            return employeeNumber;
        }

        public void setEmployeeNumber(String employeeNumber) {
            this.employeeNumber = employeeNumber;
        }
    }
}





