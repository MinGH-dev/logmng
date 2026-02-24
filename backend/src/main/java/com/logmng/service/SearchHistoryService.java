package com.logmng.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.dto.request.SearchHistoryCreateRequest;
import com.logmng.dto.response.SearchHistoryListResponse;
import com.logmng.dto.response.UserActivityLogResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 검색 이력 서비스 (복호화 승인 부가 기능)
 * - 승인 유효 기간: 요청일시 + 1일
 * - 만료 시 재요청 가능
 */
@Service
public class SearchHistoryService {

    private static final Logger log = LoggerFactory.getLogger(SearchHistoryService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int APPROVAL_VALIDITY_HOURS = 24;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public SearchHistoryService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 검색 이력 저장 (승인 요청 시점)
     * expires_at = requested_at + 1일
     */
    public Map<String, Object> create(String userId, SearchHistoryCreateRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        String logType = request.getLogType();
        if (logType == null || logType.isBlank()) {
            throw new IllegalArgumentException("logType is required");
        }
        String searchParamsJson;
        try {
            searchParamsJson = objectMapper.writeValueAsString(request.getSearchParams() != null ? request.getSearchParams() : new HashMap<>());
        } catch (Exception e) {
            log.warn("searchParams JSON 직렬화 실패: {}", e.getMessage());
            searchParamsJson = "{}";
        }

        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO search_history (user_id, log_type, search_params, requested_at, expires_at, approval_status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + (? || ' hours')::interval, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                    "RETURNING id, requested_at, expires_at, approval_status";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                ps.setString(2, logType);
                ps.setString(3, searchParamsJson);
                ps.setString(4, String.valueOf(APPROVAL_VALIDITY_HOURS));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", rs.getLong("id"));
                        result.put("requestedAt", formatTimestamp(rs.getTimestamp("requested_at")));
                        result.put("expiresAt", formatTimestamp(rs.getTimestamp("expires_at")));
                        result.put("approvalStatus", rs.getString("approval_status"));
                        log.info("검색 이력 저장 완료: userId={}, id={}", userId, result.get("id"));
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 저장 실패: userId={}", userId, e);
            throw new RuntimeException("검색 이력 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        throw new RuntimeException("검색 이력 저장 후 ID를 읽지 못했습니다.");
    }

    /**
     * 사용자별 검색 이력 목록 (최신순)
     * seq = 목록 순번, isExpired = expires_at < now 또는 status EXPIRED
     */
    public SearchHistoryListResponse list(String userId, int page, int pageSize, String sortField, String sortDirection) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        String safeSort = "requested_at".equals(sortField) ? "requested_at" : "requested_at";
        String safeDir = "asc".equalsIgnoreCase(sortDirection) ? "ASC" : "DESC";

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String countSql = "SELECT COUNT(*) FROM search_history WHERE user_id = ?";
            long totalCount = 0;
            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                countPs.setString(1, userId);
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) totalCount = rs.getLong(1);
                }
            }

            int offset = (page - 1) * pageSize;
            String sql = "SELECT id, log_type, search_params, requested_at, expires_at, approval_status " +
                    "FROM search_history WHERE user_id = ? ORDER BY " + safeSort + " " + safeDir + " LIMIT ? OFFSET ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                ps.setInt(2, pageSize);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    int seq = offset + 1;
                    LocalDateTime now = LocalDateTime.now();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        long id = rs.getLong("id");
                        row.put("seq", seq++);
                        row.put("id", id);
                        row.put("logType", rs.getString("log_type"));
                        Timestamp reqAt = rs.getTimestamp("requested_at");
                        Timestamp expAt = rs.getTimestamp("expires_at");
                        String status = rs.getString("approval_status");
                        row.put("requestedAt", formatTimestamp(reqAt));
                        row.put("expiresAt", formatTimestamp(expAt));
                        row.put("approvalStatus", status);
                        row.put("searchParamsSummary", buildSummary(rs.getString("search_params")));
                        boolean expired = expAt != null && expAt.toLocalDateTime().isBefore(now) || "EXPIRED".equals(status);
                        row.put("isExpired", expired);
                        results.add(row);
                    }
                }
            }

            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            UserActivityLogResponse.PaginationInfo pagination =
                    new UserActivityLogResponse.PaginationInfo(page, totalPages, totalCount);
            return new SearchHistoryListResponse(results, pagination);
        } catch (SQLException e) {
            log.error("검색 이력 목록 조회 실패: userId={}", userId, e);
            throw new RuntimeException("검색 이력 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 만료된 건 재요청: 상태 PENDING, requested_at/expires_at 갱신
     */
    public Map<String, Object> reRequest(String userId, Long id) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        try (Connection conn = dataSource.getConnection()) {
            String selectSql = "SELECT id, user_id, approval_status, expires_at FROM search_history WHERE id = ?";
            try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                selectPs.setLong(1, id);
                try (ResultSet rs = selectPs.executeQuery()) {
                    if (!rs.next()) {
                        throw new NoSuchElementException("검색 이력을 찾을 수 없습니다: id=" + id);
                    }
                    if (!userId.equals(rs.getString("user_id"))) {
                        throw new SecurityException("다른 사용자의 검색 이력에는 재요청할 수 없습니다.");
                    }
                    String status = rs.getString("approval_status");
                    Timestamp expiresAt = rs.getTimestamp("expires_at");
                    boolean expired = expiresAt != null && expiresAt.toLocalDateTime().isBefore(LocalDateTime.now());
                    if (!expired && !"EXPIRED".equals(status) && !"REJECTED".equals(status)) {
                        throw new IllegalArgumentException("만료되거나 반려된 건만 재요청할 수 있습니다.");
                    }
                }
            }

            String updateSql = "UPDATE search_history SET approval_status = 'PENDING', requested_at = CURRENT_TIMESTAMP, " +
                    "expires_at = CURRENT_TIMESTAMP + (? || ' hours')::interval, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id = ? AND user_id = ? RETURNING id, requested_at, expires_at, approval_status";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, String.valueOf(APPROVAL_VALIDITY_HOURS));
                ps.setLong(2, id);
                ps.setString(3, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", rs.getLong("id"));
                        result.put("approvalStatus", rs.getString("approval_status"));
                        result.put("requestedAt", formatTimestamp(rs.getTimestamp("requested_at")));
                        result.put("expiresAt", formatTimestamp(rs.getTimestamp("expires_at")));
                        log.info("검색 이력 재요청 완료: userId={}, id={}", userId, id);
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 재요청 실패: id={}, userId={}", id, userId, e);
            throw new RuntimeException("재요청 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        throw new NoSuchElementException("검색 이력을 찾을 수 없습니다: id=" + id);
    }

    /**
     * 검색 이력 상세 (재조회 시 검색 조건 반환)
     */
    public Map<String, Object> getDetail(String userId, Long id) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, user_id, log_type, search_params, requested_at, expires_at, approval_status " +
                    "FROM search_history WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new NoSuchElementException("검색 이력을 찾을 수 없습니다: id=" + id);
                    }
                    if (!userId.equals(rs.getString("user_id"))) {
                        throw new SecurityException("다른 사용자의 검색 이력은 조회할 수 없습니다.");
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("logType", rs.getString("log_type"));
                    row.put("requestedAt", formatTimestamp(rs.getTimestamp("requested_at")));
                    row.put("expiresAt", formatTimestamp(rs.getTimestamp("expires_at")));
                    row.put("approvalStatus", rs.getString("approval_status"));
                    String paramsJson = rs.getString("search_params");
                    if (paramsJson != null && !paramsJson.isEmpty()) {
                        try {
                            row.put("searchParams", objectMapper.readValue(paramsJson, Map.class));
                        } catch (Exception e) {
                            log.warn("search_params JSON 파싱 실패: {}", e.getMessage());
                            row.put("searchParams", Collections.emptyMap());
                        }
                    } else {
                        row.put("searchParams", Collections.emptyMap());
                    }
                    return row;
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 상세 조회 실패: id={}", id, e);
            throw new RuntimeException("검색 이력 상세 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private static String formatTimestamp(Timestamp ts) {
        if (ts == null) return null;
        return ts.toLocalDateTime().format(DATE_FORMATTER);
    }

    private String buildSummary(String searchParamsJson) {
        if (searchParamsJson == null || searchParamsJson.isEmpty()) return "";
        try {
            Map<String, Object> m = objectMapper.readValue(searchParamsJson, Map.class);
            List<String> parts = new ArrayList<>();
            if (m.get("startDate") != null) parts.add("시작: " + m.get("startDate"));
            if (m.get("endDate") != null) parts.add("종료: " + m.get("endDate"));
            if (m.get("logType") != null) parts.add("타입: " + m.get("logType"));
            return String.join(", ", parts.isEmpty() ? List.of("(조건 없음)") : parts);
        } catch (Exception e) {
            return "(요약 불가)";
        }
    }
}
