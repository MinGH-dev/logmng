package com.logmng.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 로그인 응답 DTO
 */
public class LoginResponse {
    
    private String username;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime loginTime;
    
    private String clientIP;
    
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
}





