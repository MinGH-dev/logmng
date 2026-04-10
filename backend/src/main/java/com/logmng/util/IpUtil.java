package com.logmng.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
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
     * Resolves a client IP safe to persist in audit rows: only syntactically valid IPv4/IPv6 literals.
     * Aligns with {@link #getClientIP} when that value is already a valid literal; if headers contain
     * non-literals (e.g. allowlist patterns), walks the same header chain and comma-separated XFF parts
     * for the first valid literal, then falls back to {@code RemoteAddr} when valid.
     *
     * @return normalized literal (IPv6 loopback normalized to {@code 127.0.0.1}) or {@code null}
     */
    public String getResolvedClientIpForActivityLog(jakarta.servlet.http.HttpServletRequest request) {
        String primary = getClientIP(request);
        String validatedPrimary = parseValidIpLiteralOrNull(primary);
        if (validatedPrimary != null) {
            return validatedPrimary;
        }
        for (String c : collectIpCandidatesInTrustOrder(request)) {
            String v = parseValidIpLiteralOrNull(c);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * Ordered candidates (no dedup) mirroring {@link #getClientIP} header precedence; X-Forwarded-For
     * entries are split so invalid leading tokens can be skipped.
     */
    public List<String> collectIpCandidatesInTrustOrder(jakarta.servlet.http.HttpServletRequest request) {
        List<String> out = new ArrayList<>();
        appendForwardedForParts(request.getHeader("X-Forwarded-For"), out);
        appendSingleHeader(request.getHeader("X-Real-IP"), out);
        appendSingleHeader(request.getHeader("Proxy-Client-IP"), out);
        appendSingleHeader(request.getHeader("WL-Proxy-Client-IP"), out);
        appendSingleHeader(request.getHeader("HTTP_CLIENT_IP"), out);
        appendForwardedForParts(request.getHeader("HTTP_X_FORWARDED_FOR"), out);
        out.add(normalizeServletRemoteAddr(request.getRemoteAddr()));
        return out;
    }

    private static void appendForwardedForParts(String headerValue, List<String> out) {
        if (headerValue == null || headerValue.isEmpty() || "unknown".equalsIgnoreCase(headerValue)) {
            return;
        }
        for (String part : headerValue.split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && !"unknown".equalsIgnoreCase(t)) {
                out.add(t);
            }
        }
    }

    private static void appendSingleHeader(String headerValue, List<String> out) {
        if (headerValue == null || headerValue.isEmpty() || "unknown".equalsIgnoreCase(headerValue)) {
            return;
        }
        out.add(headerValue.trim());
    }

    /**
     * Normalizes servlet remote address for comparison and parsing (IPv6 loopback → IPv4 loopback).
     */
    public static String normalizeServletRemoteAddr(String remoteAddr) {
        if (remoteAddr == null) {
            return "";
        }
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            return "127.0.0.1";
        }
        return remoteAddr;
    }

    /**
     * Parses {@code s} as a host IP literal only (no DNS). Returns a normalized form or {@code null}.
     */
    public static String parseValidIpLiteralOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty() || "unknown".equalsIgnoreCase(t)) {
            return null;
        }
        if (t.contains(":")) {
            try {
                InetAddress a = InetAddress.getByName(t);
                if (a.isLoopbackAddress()) {
                    return "127.0.0.1";
                }
                return a.getHostAddress();
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return parseIpv4LiteralOrNull(t);
    }

    private static String parseIpv4LiteralOrNull(String t) {
        String[] parts = t.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) {
                return null;
            }
            for (int j = 0; j < p.length(); j++) {
                if (!Character.isDigit(p.charAt(j))) {
                    return null;
                }
            }
            int v = Integer.parseInt(p);
            if (v < 0 || v > 255) {
                return null;
            }
            octets[i] = v;
        }
        return octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
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





