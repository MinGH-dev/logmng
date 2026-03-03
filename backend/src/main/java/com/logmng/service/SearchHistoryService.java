package com.logmng.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.request.SearchHistoryCreateRequest;
import com.logmng.dto.response.LogDbSearchResponse;
import com.logmng.dto.response.SearchHistoryListResponse;
import com.logmng.dto.response.UserActivityLogResponse;
import com.logmng.exception.CustomException;
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
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int APPROVAL_VALIDITY_HOURS = 24;
    private static final int SNAPSHOT_MAX_ROWS = 10_000;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final LogDbService logDbService;
    private final DecryptApproverService decryptApproverService;

    public SearchHistoryService(DataSource dataSource, LogDbService logDbService, DecryptApproverService decryptApproverService) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
        this.logDbService = logDbService;
        this.decryptApproverService = decryptApproverService;
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
            String sql = "INSERT INTO search_history (user_id, log_type, search_params, requested_at, expires_at, approval_status, approved_by, approved_at, created_at, updated_at) " +
                    "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + (? || ' hours')::interval, 'PENDING', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                    "RETURNING id, requested_at, expires_at, approval_status, approved_by, approved_at, rejected_by, rejected_at, rejection_reason";
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
                        putApprovalFields(rs, result);
                        log.info("검색 이력 저장 완료: userId={}, id={}, status=PENDING", userId, result.get("id"));
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
     * 지정한 검색 이력 ID가 해당 사용자 소유이고, APPROVED이며 미만료인지 여부.
     * 복호화는 "현재 검색에 대한 승인"만 허용하기 위해 사용.
     */
    public boolean isValidApprovalForUser(Long searchHistoryId, String userId) {
        if (searchHistoryId == null || userId == null || userId.isBlank()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM search_history WHERE id = ? AND user_id = ? AND approval_status = 'APPROVED' AND expires_at > CURRENT_TIMESTAMP LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, searchHistoryId);
                ps.setString(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 승인 여부 조회 실패: searchHistoryId={}, userId={}", searchHistoryId, userId, e);
            return false;
        }
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
            String sql = "SELECT id, log_type, search_params, requested_at, expires_at, approval_status, approved_by, approved_at, rejected_by, rejected_at, rejection_reason " +
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
                        putApprovalFields(rs, row);
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
     * 승인 대기(PENDING) 목록. 결재자/관리자 전용. §6.1.5
     * 관리자: 전체. 그 외: canApproveForRequester(approverUserId, requester)인 건만.
     */
    public SearchHistoryListResponse listPending(String approverUserId, boolean isSystemAdmin, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        boolean isAdmin = decryptApproverService.isAdmin(isSystemAdmin);
        List<Map<String, Object>> allFiltered = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, user_id, search_params, requested_at FROM search_history WHERE approval_status = 'PENDING' ORDER BY requested_at DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String requester = rs.getString("user_id");
                        if (isAdmin || decryptApproverService.canApproveForRequester(approverUserId, requester)) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", rs.getLong("id"));
                            row.put("requester", requester);
                            row.put("searchParamsSummary", buildSummary(rs.getString("search_params")));
                            row.put("requestedAt", formatTimestampISO(rs.getTimestamp("requested_at")));
                            allFiltered.add(row);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("승인 대기 목록 조회 실패", e);
            throw new RuntimeException("승인 대기 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        long totalCount = allFiltered.size();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, allFiltered.size());
        List<Map<String, Object>> pageItems = from < allFiltered.size() ? allFiltered.subList(from, to) : new ArrayList<>();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        UserActivityLogResponse.PaginationInfo pagination = new UserActivityLogResponse.PaginationInfo(page, totalPages, totalCount);
        return new SearchHistoryListResponse(pageItems, pagination);
    }

    /**
     * 승인. PENDING 건만 갱신. §6.1.6
     * Approval snapshot: run search with search_params, collect row_id per log_type, insert into search_history_approved_row, then set APPROVED.
     * Ref: docs/requirements/20260224-decryption-snapshot-final-design-en.md §6.1
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> approve(Long id, String approverUserId) {
        String logType;
        String searchParamsJson;
        String requesterUserId;
        try (Connection conn = dataSource.getConnection()) {
            String sel = "SELECT user_id, log_type, search_params FROM search_history WHERE id = ? AND approval_status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(sel)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw CustomException.notFound("해당 검색 이력을 찾을 수 없거나 이미 처리되었습니다: id=" + id, "NOT_FOUND");
                    }
                    requesterUserId = rs.getString("user_id");
                    logType = rs.getString("log_type");
                    searchParamsJson = rs.getString("search_params");
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 조회 실패: id={}", id, e);
            throw new RuntimeException("승인 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        if (!decryptApproverService.canApproveForRequester(approverUserId, requesterUserId)) {
            throw CustomException.forbidden("해당 요청에 대한 승인 권한이 없습니다.", "FORBIDDEN_NOT_APPROVER");
        }

        LogDbSearchRequest searchRequest;
        try {
            Map<String, Object> paramsMap = searchParamsJson != null && !searchParamsJson.isEmpty()
                ? objectMapper.readValue(searchParamsJson, Map.class)
                : new HashMap<>();
            searchRequest = objectMapper.convertValue(paramsMap, LogDbSearchRequest.class);
        } catch (Exception e) {
            log.warn("search_params 파싱 실패: id={}, {}", id, e.getMessage());
            throw new RuntimeException("저장된 검색 조건을 실행할 수 없습니다. 검색 조건 형식을 확인해 주세요.", e);
        }
        if (searchRequest.getLogType() == null || searchRequest.getLogType().isEmpty()) {
            searchRequest.setLogType(logType);
        }
        searchRequest.setPage(1);
        searchRequest.setPageSize(SNAPSHOT_MAX_ROWS);

        LogDbSearchResponse searchResponse = logDbService.searchLogs(searchRequest);
        List<Map<String, Object>> data = searchResponse.getData() != null ? searchResponse.getData() : Collections.emptyList();
        List<String> rowIds = new ArrayList<>();
        for (Map<String, Object> row : data) {
            String rowId = extractRowIdForSnapshot(logType, row);
            if (rowId != null && !rowId.isEmpty()) {
                rowIds.add(rowId);
            }
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String ins = "INSERT INTO search_history_approved_row (search_history_id, log_type, row_id) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(ins)) {
                    for (String rowId : rowIds) {
                        ps.setLong(1, id);
                        ps.setString(2, logType);
                        ps.setString(3, rowId);
                        ps.addBatch();
                    }
                    if (!rowIds.isEmpty()) {
                        ps.executeBatch();
                    }
                }
                String updateSql = "UPDATE search_history SET approval_status = 'APPROVED', approved_by = ?, approved_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND approval_status = 'PENDING'";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, approverUserId);
                    ps.setLong(2, id);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        conn.rollback();
                        throw CustomException.notFound("해당 검색 이력을 찾을 수 없거나 이미 처리되었습니다: id=" + id, "NOT_FOUND");
                    }
                }
                conn.commit();
                log.info("검색 이력 승인 및 스냅샷 저장: id={}, approvedBy={}, snapshotRows={}", id, approverUserId, rowIds.size());
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("검색 이력 승인(스냅샷) 실패: id={}", id, e);
            throw new RuntimeException("승인 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }

        try (Connection conn = dataSource.getConnection()) {
            String selectSql = "SELECT id, approval_status, approved_by, approved_at FROM search_history WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", rs.getLong("id"));
                        result.put("approvalStatus", rs.getString("approval_status"));
                        result.put("approvedBy", rs.getString("approved_by"));
                        result.put("approvedAt", formatTimestampISO(rs.getTimestamp("approved_at")));
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            log.error("승인 결과 조회 실패: id={}", id, e);
        }
        throw CustomException.notFound("해당 검색 이력을 찾을 수 없습니다: id=" + id, "NOT_FOUND");
    }

    /**
     * Extract row_id for snapshot from a search result row. java_fw_imglog: guid; pb_feplog: log_type|id.
     */
    private static String extractRowIdForSnapshot(String logType, Map<String, Object> row) {
        if (row == null) return null;
        if ("java_fw_imglog".equals(logType)) {
            Object g = row.get("guid");
            return g != null ? g.toString() : null;
        }
        if ("pb_feplog".equals(logType)) {
            Object lt = row.get("log_type");
            Object id = row.get("id");
            if (lt != null && id != null) {
                return lt.toString() + "|" + id.toString();
            }
        }
        return null;
    }

    /**
     * Returns true if the row is in the approved snapshot for the given search_history (decrypt allowed).
     * Ref: docs/requirements/20260224-decryption-snapshot-final-design-en.md §6.1
     */
    public boolean isRowInApprovedSnapshot(Long searchHistoryId, String logType, String rowId) {
        if (searchHistoryId == null || logType == null || logType.isBlank() || rowId == null || rowId.isBlank()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM search_history_approved_row WHERE search_history_id = ? AND log_type = ? AND row_id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, searchHistoryId);
                ps.setString(2, logType);
                ps.setString(3, rowId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("스냅샷 조회 실패: searchHistoryId={}, logType={}, rowId={}", searchHistoryId, logType, rowId, e);
            return false;
        }
    }

    /**
     * 반려. PENDING 건만 갱신. §6.1.7. 권한: canApproveForRequester(approver, requester)
     */
    public Map<String, Object> reject(Long id, String approverUserId, String rejectionReason) {
        String requesterUserId = null;
        try (Connection conn = dataSource.getConnection()) {
            String sel = "SELECT user_id FROM search_history WHERE id = ? AND approval_status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(sel)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw CustomException.notFound("해당 검색 이력을 찾을 수 없거나 이미 처리되었습니다: id=" + id, "NOT_FOUND");
                    }
                    requesterUserId = rs.getString("user_id");
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 조회 실패: id={}", id, e);
            throw new RuntimeException("반려 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        if (!decryptApproverService.canApproveForRequester(approverUserId, requesterUserId)) {
            throw CustomException.forbidden("해당 요청에 대한 반려 권한이 없습니다.", "FORBIDDEN_NOT_APPROVER");
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE search_history SET approval_status = 'REJECTED', rejected_by = ?, rejected_at = CURRENT_TIMESTAMP, rejection_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND approval_status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, approverUserId);
                ps.setString(2, rejectionReason != null ? rejectionReason : "");
                ps.setLong(3, id);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw CustomException.notFound("해당 검색 이력을 찾을 수 없거나 이미 처리되었습니다: id=" + id, "NOT_FOUND");
                }
            }
            String selectSql = "SELECT id, approval_status, rejected_by, rejected_at, rejection_reason FROM search_history WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", rs.getLong("id"));
                        result.put("approvalStatus", rs.getString("approval_status"));
                        result.put("rejectedBy", rs.getString("rejected_by"));
                        result.put("rejectedAt", formatTimestampISO(rs.getTimestamp("rejected_at")));
                        result.put("rejectionReason", rs.getString("rejection_reason"));
                        log.info("검색 이력 반려: id={}, rejectedBy={}", id, approverUserId);
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 반려 실패: id={}", id, e);
            throw new RuntimeException("반려 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        throw CustomException.notFound("해당 검색 이력을 찾을 수 없습니다: id=" + id, "NOT_FOUND");
    }

    /**
     * 검색 이력 상세 (재조회 시 검색 조건 반환)
     */
    public Map<String, Object> getDetail(String userId, Long id) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, user_id, log_type, search_params, requested_at, expires_at, approval_status, approved_by, approved_at, rejected_by, rejected_at, rejection_reason " +
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
                    putApprovalFields(rs, row);
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

    private static String formatTimestampISO(Timestamp ts) {
        if (ts == null) return null;
        return ts.toLocalDateTime().format(ISO_FORMATTER);
    }

    /** Approval-history fields from ResultSet into Map (approvedBy, approvedAt, rejectedBy, rejectedAt, rejectionReason). */
    private static void putApprovalFields(ResultSet rs, Map<String, Object> map) throws SQLException {
        map.put("approvedBy", rs.getString("approved_by"));
        map.put("approvedAt", formatTimestamp(rs.getTimestamp("approved_at")));
        map.put("rejectedBy", rs.getString("rejected_by"));
        map.put("rejectedAt", formatTimestamp(rs.getTimestamp("rejected_at")));
        map.put("rejectionReason", rs.getString("rejection_reason"));
    }

    @SuppressWarnings("unchecked")
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
