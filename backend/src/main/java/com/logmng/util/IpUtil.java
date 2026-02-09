package com.logmng.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * IP 주소 유틸리티
 */
@Component
public class IpUtil {
    
    private static final Logger log = LoggerFactory.getLogger(IpUtil.class);
    
    /**
     * 클라이언트 IP 주소 추출
     * 
     * @param request HTTP 요청
     * @return 클라이언트 IP 주소
     */
    public String getClientIP(jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // X-Forwarded-For는 여러 IP가 콤마로 구분될 수 있음
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
    
    /**
     * IP 주소가 인가된 IP 목록에 있는지 확인
     * 
     * @param clientIP 클라이언트 IP 주소
     * @param authorizedIPs 인가된 IP 목록 (콤마로 구분)
     * @return 인가 여부
     */
    public boolean isAuthorizedIP(String clientIP, String authorizedIPs) {
        if (authorizedIPs == null || authorizedIPs.trim().isEmpty()) {
            log.warn("인가된 IP 목록이 설정되지 않았습니다. 모든 IP를 허용합니다.");
            return true;
        }
        
        if (clientIP == null || clientIP.trim().isEmpty()) {
            return false;
        }
        
        List<String> allowedIPs = Arrays.asList(authorizedIPs.split(","));
        
        // localhost 허용
        if ("127.0.0.1".equals(clientIP) || "0:0:0:0:0:0:0:1".equals(clientIP) || "localhost".equals(clientIP)) {
            return true;
        }
        
        // 정확한 IP 매칭 또는 와일드카드 지원
        for (String allowedIP : allowedIPs) {
            String trimmed = allowedIP.trim();
            
            // 정확한 매칭
            if (trimmed.equals(clientIP)) {
                return true;
            }
            
            // 와일드카드 지원 (예: 192.168.1.*)
            if (trimmed.endsWith("*")) {
                String prefix = trimmed.substring(0, trimmed.length() - 1);
                if (clientIP.startsWith(prefix)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}





