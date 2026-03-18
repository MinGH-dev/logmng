package com.logmng.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.constants.ScreenConstants;
import com.logmng.util.LogTypeScreenHelper;
import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.request.SearchHistoryCreateRequest;
import com.logmng.dto.request.SearchHistoryListRequest;
import com.logmng.dto.response.LogDbSearchResponse;
import com.logmng.dto.response.SearchHistoryListResponse;
import com.logmng.dto.response.UserActivityLogResponse;
import com.logmng.exception.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

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
    /** Max length for request_reason (req 20260317 §2.1). Overlength → 400. */
    private static final int MAX_REQUEST_REASON_LENGTH = 500;
    private static final Pattern CONTROL_OR_HTML = Pattern.compile("[\\x00-\\x1F\\x7F]|<[^>]*>");

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final LogDbService logDbService;
    private final DecryptApproverService decryptApproverService;
    private final AppUserResolver appUserResolver;
    private final DecryptionAllowedService decryptionAllowedService;

    public SearchHistoryService(DataSource dataSource, LogDbService logDbService, DecryptApproverService decryptApproverService) {
        this(dataSource, logDbService, decryptApproverService, null, null);
    }

    @Autowired
    public SearchHistoryService(DataSource dataSource, LogDbService logDbService, DecryptApproverService decryptApproverService, AppUserResolver appUserResolver, DecryptionAllowedService decryptionAllowedService) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
        this.logDbService = logDbService;
        this.decryptApproverService = decryptApproverService;
        this.appUserResolver = appUserResolver != null ? appUserResolver : new AppUserResolver(dataSource);
        this.decryptionAllowedService = decryptionAllowedService;
    }

    /**
     * 검색 이력 저장 (승인 요청 시점). user_id = numeric app_user.id (req 20260316).
     * expires_at = requested_at + 1일.
     * request_reason: optional, max 500 chars; sanitized (control chars/HTML stripped). §2.1: do not log full value.
     */
    public Map<String, Object> create(Long userId, SearchHistoryCreateRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        String logType = request.getLogType();
        if (logType == null || logType.isBlank()) {
            throw new IllegalArgumentException("logType is required");
        }
        if (request.getSearchParams() == null) {
            throw new IllegalArgumentException("searchParams is required");
        }
        String requestReasonRaw = request.getRequestReason();
        String requestReason = sanitizeRequestReason(requestReasonRaw);
        if (requestReason != null && requestReason.length() > MAX_REQUEST_REASON_LENGTH) {
            throw new IllegalArgumentException("requestReason must not exceed " + MAX_REQUEST_REASON_LENGTH + " characters");
        }
        Integer overrideTotal = request.getSearchResultTotalCount();
        Integer overrideDecrypt = request.getDecryptionTargetCount();
        if ((overrideTotal != null) != (overrideDecrypt != null)) {
            throw new IllegalArgumentException("searchResultTotalCount and decryptionTargetCount must both be provided or both omitted");
        }
        CountsAtCreate countsAtCreate;
        if (overrideTotal != null) {
            countsAtCreate = new CountsAtCreate(overrideTotal, overrideDecrypt);
        } else {
            countsAtCreate = computeCountsAtCreate(logType, request.getSearchParams());
        }

        String searchParamsJson;
        try {
            searchParamsJson = objectMapper.writeValueAsString(request.getSearchParams() != null ? request.getSearchParams() : new HashMap<>());
        } catch (Exception e) {
            log.warn("searchParams JSON 직렬화 실패: {}", e.getMessage());
            searchParamsJson = "{}";
        }

        Timestamp requestedAtTs = Timestamp.valueOf(LocalDateTime.now());
        Timestamp expiresAtTs = Timestamp.valueOf(LocalDateTime.now().plusHours(APPROVAL_VALIDITY_HOURS));
        try (Connection conn = dataSource.getConnection()) {
            String insertSql = "INSERT INTO search_history (user_id, log_type, search_params, request_reason, requested_at, expires_at, approval_status, approved_by_user_id, approved_by, approved_at, created_at, updated_at, search_result_total_count, decryption_target_count) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                bindUserId(ps, 1, userId);
                ps.setString(2, logType);
                ps.setString(3, searchParamsJson);
                ps.setString(4, requestReason);
                ps.setTimestamp(5, requestedAtTs);
                ps.setTimestamp(6, expiresAtTs);
                if (countsAtCreate.totalCount != null) {
                    ps.setInt(7, countsAtCreate.totalCount);
                } else {
                    ps.setNull(7, Types.INTEGER);
                }
                if (countsAtCreate.decryptionTargetCount != null) {
                    ps.setInt(8, countsAtCreate.decryptionTargetCount);
                } else {
                    ps.setNull(8, Types.INTEGER);
                }
                int inserted = ps.executeUpdate();
                if (inserted == 0) {
                    throw new SQLException("INSERT search_history affected 0 rows");
                }
                long newId;
                try (ResultSet gk = ps.getGeneratedKeys()) {
                    if (!gk.next()) {
                        throw new SQLException("INSERT search_history did not return generated id");
                    }
                    newId = gk.getLong(1);
                }
                String selectSql = "SELECT id, requested_at, expires_at, approval_status, approved_by_user_id, approved_by, approved_at, rejected_by, rejected_at, rejection_reason, search_result_total_count, decryption_target_count FROM search_history WHERE id = ?";
                try (PreparedStatement sel = conn.prepareStatement(selectSql)) {
                    sel.setLong(1, newId);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (rs.next()) {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("id", rs.getLong("id"));
                            result.put("requestedAt", formatTimestamp(rs.getTimestamp("requested_at")));
                            result.put("expiresAt", formatTimestamp(rs.getTimestamp("expires_at")));
                            result.put("approvalStatus", rs.getString("approval_status"));
                            putApprovalFieldsFromRs(rs, result);
                            putNullableIntegerColumn(rs, "search_result_total_count", "searchResultTotalCount", result);
                            putNullableIntegerColumn(rs, "decryption_target_count", "decryptionTargetCount", result);
                            log.debug("create: after insert id={}, search_result_total_count readBack={}, decryption_target_count readBack={}",
                                    newId, result.get("searchResultTotalCount"), result.get("decryptionTargetCount"));
                            int reasonLen = requestReason != null ? requestReason.length() : 0;
                            log.info("검색 이력 저장 완료: userId={}, id={}, status=PENDING, requestReasonLength={}, searchResultTotalCount={}, decryptionTargetCount={}",
                                    userId, result.get("id"), reasonLen, result.get("searchResultTotalCount"), result.get("decryptionTargetCount"));
                            return result;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            String sqlState = e.getSQLState() != null ? e.getSQLState() : "";
            int ec = e.getErrorCode();
            log.error("검색 이력 저장 실패: userId={}, SQLState={}, errorCode={}, message={}", userId, sqlState, ec, e.getMessage(), e);
            throw new RuntimeException("검색 이력 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        throw new RuntimeException("검색 이력 저장 후 ID를 읽지 못했습니다.");
    }

    /** §2.1: Strip control characters and HTML-like tags. Returns null if input is null/blank after trim. */
    private static String sanitizeRequestReason(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.isEmpty()) return null;
        s = CONTROL_OR_HTML.matcher(s).replaceAll("");
        return s.trim().isEmpty() ? null : s;
    }

    /** Snapshot counts at create: total search hits + encrypted-row count in first min(total, SNAPSHOT_MAX_ROWS) rows. */
    private static final class CountsAtCreate {
        final Integer totalCount;
        final Integer decryptionTargetCount;

        CountsAtCreate(Integer totalCount, Integer decryptionTargetCount) {
            this.totalCount = totalCount;
            this.decryptionTargetCount = decryptionTargetCount;
        }
    }

    @SuppressWarnings("unchecked")
    private CountsAtCreate computeCountsAtCreate(String logType, Map<String, Object> searchParams) {
        if (logDbService == null) {
            log.warn("computeCountsAtCreate: logDbService is null, storing null counts");
            return new CountsAtCreate(null, null);
        }
        try {
            Map<String, Object> params = searchParams != null ? searchParams : new HashMap<>();
            LogDbSearchRequest sr = objectMapper.convertValue(params, LogDbSearchRequest.class);
            if (sr.getLogType() == null || sr.getLogType().isBlank()) {
                sr.setLogType(logType);
            }
            sr.setPage(1);
            sr.setPageSize(1);
            LogDbSearchResponse r1 = logDbService.searchLogs(sr);
            Long totalLong = null;
            if (r1.getPagination() != null && r1.getPagination().getTotalCount() != null) {
                totalLong = r1.getPagination().getTotalCount();
            }
            if (totalLong == null) {
                log.warn("computeCountsAtCreate: pagination.totalCount missing, logType={}", logType);
                return new CountsAtCreate(null, null);
            }
            if (totalLong <= 0) {
                return new CountsAtCreate(0, 0);
            }
            int totalInt = totalLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : totalLong.intValue();
            int pageSize = (int) Math.min(totalLong, (long) SNAPSHOT_MAX_ROWS);
            sr.setPageSize(pageSize);
            LogDbSearchResponse r2 = logDbService.searchLogs(sr);
            List<Map<String, Object>> data = r2.getData() != null ? r2.getData() : Collections.emptyList();
            int dec = 0;
            for (Map<String, Object> row : data) {
                if (hasEncryptedData(logType, row)) {
                    dec++;
                }
            }
            log.debug("computeCountsAtCreate: computed totalInt={}, dec={} (decryption count) before CountsAtCreate", totalInt, dec);
            return new CountsAtCreate(totalInt, dec);
        } catch (Exception e) {
            log.warn("computeCountsAtCreate: search or parse failed, logType={}: {}", logType, e.getMessage());
            return new CountsAtCreate(null, null);
        }
    }

    private static void putNullableIntegerColumn(ResultSet rs, String columnLabel, String jsonKey, Map<String, Object> out) throws SQLException {
        int v = rs.getInt(columnLabel);
        if (rs.wasNull()) {
            out.put(jsonKey, null);
        } else {
            out.put(jsonKey, v);
        }
    }

    /**
     * 지정한 검색 이력 ID가 해당 사용자 소유이고, APPROVED이며 미만료인지 여부.
     * 복호화는 "현재 검색에 대한 승인"만 허용하기 위해 사용. userId = numeric app_user.id (req 20260316).
     */
    public boolean isValidApprovalForUser(Long searchHistoryId, Long userId) {
        if (searchHistoryId == null || userId == null) {
            log.debug("isValidApprovalForUser: missing id (searchHistoryId={}, userId={})", searchHistoryId, userId);
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM search_history WHERE id = ? AND user_id = ? AND approval_status = 'APPROVED' AND expires_at > CURRENT_TIMESTAMP LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, searchHistoryId);
                bindUserId(ps, 2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean found = rs.next();
                    if (!found) {
                        log.debug("isValidApprovalForUser: no matching row (searchHistoryId={}, userId={})", searchHistoryId, userId);
                    }
                    return found;
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 승인 여부 조회 실패: searchHistoryId={}, userId={}", searchHistoryId, userId, e);
            return false;
        }
    }

    /**
     * 복호화 승인 검사 실패 시 진단 정보 (로그·403 상세용, PII 미포함).
     * req 20260317-image-log-decrypt-error-root-cause-and-data-validation
     */
    public Optional<ApprovalFailureDiagnostic> getApprovalFailureReason(Long searchHistoryId, Long userId) {
        if (searchHistoryId == null || userId == null) {
            return Optional.of(new ApprovalFailureDiagnostic(ApprovalFailureReason.ROW_NOT_FOUND, null, null, null));
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT user_id, approval_status, expires_at FROM search_history WHERE id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, searchHistoryId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.of(new ApprovalFailureDiagnostic(ApprovalFailureReason.ROW_NOT_FOUND, null, null, null));
                    }
                    Long rowUserId = null;
                    try {
                        Object uid = rs.getObject("user_id");
                        if (uid instanceof Number) rowUserId = ((Number) uid).longValue();
                    } catch (SQLException ignored) { }
                    String approvalStatus = rs.getString("approval_status");
                    Timestamp expiresAt = rs.getTimestamp("expires_at");
                    boolean expired = expiresAt != null && !expiresAt.toInstant().isAfter(java.time.Instant.now());

                    if (rowUserId != null && !rowUserId.equals(userId)) {
                        return Optional.of(new ApprovalFailureDiagnostic(ApprovalFailureReason.USER_MISMATCH, rowUserId, approvalStatus, expired));
                    }
                    if (!"APPROVED".equals(approvalStatus)) {
                        return Optional.of(new ApprovalFailureDiagnostic(ApprovalFailureReason.NOT_APPROVED, rowUserId, approvalStatus, expired));
                    }
                    if (expired) {
                        return Optional.of(new ApprovalFailureDiagnostic(ApprovalFailureReason.EXPIRED, rowUserId, approvalStatus, true));
                    }
                    return Optional.of(new ApprovalFailureDiagnostic(ApprovalFailureReason.NOT_APPROVED, rowUserId, approvalStatus, false));
                }
            }
        } catch (SQLException e) {
            log.warn("복호화 승인 진단 조회 실패: searchHistoryId={}, userId={}", searchHistoryId, userId, e);
            return Optional.empty();
        }
    }

    /**
     * 사용자별 검색 이력 목록 (최신순)
     * seq = 목록 순번, isExpired = expires_at < now 또는 status EXPIRED
     */
    public SearchHistoryListResponse list(SearchHistoryListRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        int page = request.getPage() < 1 ? 1 : request.getPage();
        int pageSize = request.getPageSize() < 1 || request.getPageSize() > 100 ? 20 : request.getPageSize();
        String safeSort = "requested_at".equals(request.getSortField()) ? "requested_at" : "requested_at";
        String safeDir = "asc".equalsIgnoreCase(request.getSortDirection()) ? "ASC" : "DESC";
        SearchHistoryListQuerySpec querySpec = buildListQuerySpec(request);

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            long totalCount = 0;
            String countSql = "SELECT COUNT(*) " + querySpec.getFromAndWhereClause();
            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                bindParams(countPs, querySpec.getParams());
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) {
                        totalCount = rs.getLong(1);
                    }
                }
            }

            int offset = (page - 1) * pageSize;
            String sql = "SELECT sh.id, sh.user_id AS \"shUserId\", au.id AS \"userId\", au.department_code AS \"requesterDepartmentCode\", d.name AS \"requesterDepartmentName\", au.name AS \"requesterDisplayName\", au.username AS \"requesterUsername\", " +
                    "sh.log_type, sh.search_params, sh.request_reason, sh.requested_at, sh.expires_at, " +
                    "sh.approval_status, sh.approved_by_user_id, sh.approved_by, sh.approved_at, sh.rejected_by, sh.rejected_at, sh.rejection_reason, " +
                    "sh.search_result_total_count AS sh_sr_total, sh.decryption_target_count AS sh_dec_target " +
                    querySpec.getFromAndWhereClause() +
                    " ORDER BY sh." + safeSort + " " + safeDir + " LIMIT ? OFFSET ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                List<Object> listParams = new ArrayList<>(querySpec.getParams());
                listParams.add(pageSize);
                listParams.add(offset);
                bindParams(ps, listParams);
                try (ResultSet rs = ps.executeQuery()) {
                    int seq = offset + 1;
                    LocalDateTime now = LocalDateTime.now();
                    boolean firstListRow = true;
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        long id = rs.getLong("id");
                        Long shUserIdLong = toLongUserId(rs.getObject("shUserId"));
                        Object auIdObj = rs.getObject("userId");
                        Long responseUserId = auIdObj != null ? toLongUserId(auIdObj) : shUserIdLong;
                        String reqUsername = rs.getString("requesterUsername");
                        String reqDisplayName = rs.getString("requesterDisplayName");
                        String reqDeptCode = rs.getString("requesterDepartmentCode");
                        String reqDeptName = rs.getString("requesterDepartmentName");
                        if (responseUserId == null) responseUserId = shUserIdLong;
                        row.put("seq", seq++);
                        row.put("id", id);
                        row.put("userId", responseUserId);
                        if (reqUsername == null || reqUsername.isBlank()) {
                            RequesterDisplay fallback = resolveRequesterDisplayByUserId(conn, shUserIdLong);
                            row.put("requesterDepartmentCode", fallback != null ? fallback.departmentCode : null);
                            row.put("requesterDepartmentName", fallback != null ? fallback.departmentName : null);
                            row.put("requesterDisplayName", fallback != null && fallback.displayName != null ? fallback.displayName : (shUserIdLong != null ? String.valueOf(shUserIdLong) : "—"));
                            row.put("requesterUsername", fallback != null && fallback.username != null ? fallback.username : (shUserIdLong != null ? String.valueOf(shUserIdLong) : "—"));
                        } else {
                            row.put("requesterDepartmentCode", reqDeptCode);
                            row.put("requesterDepartmentName", reqDeptName);
                            row.put("requesterDisplayName", (reqDisplayName != null && !reqDisplayName.isBlank()) ? reqDisplayName : reqUsername);
                            row.put("requesterUsername", reqUsername);
                        }
                        row.put("logType", rs.getString("log_type"));
                        try {
                            row.put("requestReason", rs.getString("request_reason"));
                        } catch (SQLException e) {
                            row.put("requestReason", null);
                        }
                        Timestamp reqAt = rs.getTimestamp("requested_at");
                        Timestamp expAt = rs.getTimestamp("expires_at");
                        String status = rs.getString("approval_status");
                        row.put("requestedAt", formatTimestamp(reqAt));
                        row.put("expiresAt", formatTimestamp(expAt));
                        row.put("approvalStatus", status);
                        putApprovalFieldsFromRs(rs, row);
                        row.put("searchParamsSummary", buildSummary(rs.getString("search_params")));
                        boolean expired = expAt != null && expAt.toLocalDateTime().isBefore(now) || "EXPIRED".equals(status);
                        row.put("isExpired", expired);
                        putNullableIntegerColumn(rs, "sh_sr_total", "searchResultTotalCount", row);
                        putNullableIntegerColumn(rs, "sh_dec_target", "decryptionTargetCount", row);
                        if (firstListRow) {
                            log.debug("list: first row id={}, sh_sr_total→searchResultTotalCount={}, sh_dec_target→decryptionTargetCount={}",
                                    id, row.get("searchResultTotalCount"), row.get("decryptionTargetCount"));
                            firstListRow = false;
                        }
                        results.add(row);
                    }
                }
            }

            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            UserActivityLogResponse.PaginationInfo pagination =
                    new UserActivityLogResponse.PaginationInfo(page, totalPages, totalCount);
            return new SearchHistoryListResponse(results, pagination);
        } catch (SQLException e) {
            log.error("검색 이력 목록 조회 실패: actorUserId={}", request.getActorUserId(), e);
            throw new RuntimeException("검색 이력 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 만료된 건 재요청: 상태 PENDING, requested_at/expires_at 갱신. userId = numeric app_user.id (req 20260316).
     */
    public Map<String, Object> reRequest(Long userId, Long id) {
        if (userId == null) {
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
                    Long rowUserId = toLongUserId(rs.getObject("user_id"));
                    if (rowUserId == null || !rowUserId.equals(userId)) {
                        throw CustomException.forbidden("해당 검색 이력은 요청자만 조회할 수 있습니다.", "FUNCTION_NOT_ALLOWED");
                    }
                    String status = rs.getString("approval_status");
                    Timestamp expiresAt = rs.getTimestamp("expires_at");
                    boolean expired = expiresAt != null && expiresAt.toLocalDateTime().isBefore(LocalDateTime.now());
                    if (!expired && !"EXPIRED".equals(status) && !"REJECTED".equals(status)) {
                        throw new IllegalArgumentException("만료되거나 반려된 건만 재요청할 수 있습니다.");
                    }
                }
            }

            java.sql.Timestamp requestedAt = java.sql.Timestamp.valueOf(LocalDateTime.now());
            java.sql.Timestamp expiresAt = java.sql.Timestamp.valueOf(LocalDateTime.now().plusHours(APPROVAL_VALIDITY_HOURS));
            String updateSql = "UPDATE search_history SET approval_status = 'PENDING', requested_at = ?, " +
                    "expires_at = ?, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id = ? AND user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setTimestamp(1, requestedAt);
                ps.setTimestamp(2, expiresAt);
                ps.setLong(3, id);
                bindUserId(ps, 4, userId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new NoSuchElementException("검색 이력을 찾을 수 없습니다: id=" + id);
                }
            }
            String selectAfterUpdateSql = "SELECT id, requested_at, expires_at, approval_status FROM search_history WHERE id = ? AND user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectAfterUpdateSql)) {
                ps.setLong(1, id);
                bindUserId(ps, 2, userId);
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
     * 승인 대기(PENDING) 목록. 결재자/관리자 전용. §6.1.5. approverUserId and allowedUserIds = numeric app_user.id (req 20260316).
     */
    public SearchHistoryListResponse listPending(Long approverUserId, boolean isSystemAdmin, int page, int pageSize,
                                                boolean scopeAll, List<Long> allowedUserIds) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        boolean isAdmin = decryptApproverService.isAdmin(isSystemAdmin);
        List<Map<String, Object>> allFiltered = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, user_id, search_params, requested_at, search_result_total_count, decryption_target_count FROM search_history WHERE approval_status = 'PENDING' ORDER BY requested_at DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Long requesterId = toLongUserId(rs.getObject("user_id"));
                        if (isAdmin || (approverUserId != null && requesterId != null && decryptApproverService.canApproveForRequester(approverUserId, requesterId))) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", rs.getLong("id"));
                            row.put("requester", requesterId != null ? appUserResolver.getUsernameById(requesterId) : null);
                            row.put("requesterUserId", requesterId);
                            row.put("searchParamsSummary", buildSummary(rs.getString("search_params")));
                            row.put("requestedAt", formatTimestampISO(rs.getTimestamp("requested_at")));
                            putNullableIntegerColumn(rs, "search_result_total_count", "searchResultTotalCount", row);
                            putNullableIntegerColumn(rs, "decryption_target_count", "decryptionTargetCount", row);
                            allFiltered.add(row);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("승인 대기 목록 조회 실패", e);
            throw new RuntimeException("승인 대기 목록 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        List<Map<String, Object>> scopeFiltered = new ArrayList<>();
        for (Map<String, Object> row : allFiltered) {
            Long requesterId = (Long) row.get("requesterUserId");
            if (scopeAll) {
                scopeFiltered.add(row);
            } else if (allowedUserIds != null) {
                if (requesterId != null && allowedUserIds.contains(requesterId)) {
                    scopeFiltered.add(row);
                }
            } else {
                if (approverUserId != null && approverUserId.equals(requesterId)) {
                    scopeFiltered.add(row);
                }
            }
        }
        long totalCount = scopeFiltered.size();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, scopeFiltered.size());
        List<Map<String, Object>> pageItems = from < scopeFiltered.size() ? scopeFiltered.subList(from, to) : new ArrayList<>();
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
    public Map<String, Object> approve(Long id, Long approverUserId) {
        String logType;
        String searchParamsJson;
        Long requesterUserIdLong;
        try (Connection conn = dataSource.getConnection()) {
            String sel = "SELECT user_id, log_type, search_params FROM search_history WHERE id = ? AND approval_status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(sel)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw CustomException.notFound("해당 검색 이력을 찾을 수 없거나 이미 처리되었습니다: id=" + id, "NOT_FOUND");
                    }
                    requesterUserIdLong = toLongUserId(rs.getObject("user_id"));
                    logType = rs.getString("log_type");
                    searchParamsJson = rs.getString("search_params");
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 조회 실패: id={}", id, e);
            throw CustomException.badRequest("승인 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", "APPROVAL_ERROR");
        }
        if (approverUserId == null) {
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
        if (!decryptApproverService.canApproveForRequester(approverUserId, requesterUserIdLong)) {
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }

        List<String> rowIds;
        try {
            rowIds = collectEncryptedSnapshotRowIdsStrict(searchParamsJson, logType);
        } catch (IllegalArgumentException e) {
            log.warn("search_params 파싱 실패: id={}, {}", id, e.getMessage());
            throw CustomException.badRequest("저장된 검색 조건을 실행할 수 없습니다. 검색 조건 형식을 확인해 주세요.", "INVALID_SEARCH_PARAMS");
        }
        log.info("approve snapshot collect: search_history_id={}, logType={}, rowIds_collected={}", id, logType, rowIds.size());

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
                String approverUsernameDisplay = appUserResolver.getUsernameById(approverUserId);
                String updateSql = "UPDATE search_history SET approval_status = 'APPROVED', approved_by_user_id = ?, approved_by = ?, approved_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND approval_status = 'PENDING'";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    bindUserId(ps, 1, approverUserId);
                    ps.setString(2, approverUsernameDisplay != null ? approverUsernameDisplay : "");
                    ps.setLong(3, id);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        conn.rollback();
                        throw CustomException.notFound("해당 검색 이력을 찾을 수 없거나 이미 처리되었습니다: id=" + id, "NOT_FOUND");
                    }
                }
                conn.commit();
                log.info("검색 이력 승인 및 스냅샷 저장: id={}, approvedByUserId={}, snapshotRows={}", id, approverUserId, rowIds.size());
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("검색 이력 승인(스냅샷) 실패: id={}", id, e);
            throw CustomException.badRequest("승인 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", "APPROVAL_ERROR");
        }

        // Req 20260318: refresh requester's decryption-allowed set by screen (logType→screen_id). java_fw_imglog→java-fw-imagelog; pb_feplog has no decrypt support, skip.
        if (decryptionAllowedService != null && logType != null && !rowIds.isEmpty()) {
            String screenId = LogTypeScreenHelper.screenIdForLogType(logType);
            if (ScreenConstants.JAVA_FW_IMAGELOG.equals(screenId)) {
                decryptionAllowedService.deleteExpiredForUser(requesterUserIdLong);
                decryptionAllowedService.addOrReplaceAllowed(requesterUserIdLong, screenId, rowIds);
            }
            // pb_feplog: no decryption support, skip addOrReplaceAllowed
        }

        try (Connection conn = dataSource.getConnection()) {
            String selectSql = "SELECT id, approval_status, approved_by_user_id, approved_by, approved_at FROM search_history WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", rs.getLong("id"));
                        result.put("approvalStatus", rs.getString("approval_status"));
                        result.put("approvedBy", resolveApprovedByDisplay(rs));
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
     * Returns true if the row has encrypted data and should be included in approval snapshot and decryption-allowed set.
     * Definition per log type; must align with contract/spec and req 20260318-image-log-no-decrypt-button-when-plain.
     * - java_fw_imglog: only datastring/headerstring content is considered; a row has encrypted data only when
     *   datastring or headerstring contains encrypted-style content — i.e. a quoted JSON string value that is
     *   bracket-wrapped (e.g. {@code "key":"[ciphertext]"}). Non-empty data/header or bare "[" are not used;
     *   plain JSON (e.g. arrays like [1,2,3]) does not count as encrypted.
     * - pb_feplog: not considered to have encrypted data for approval (no decryption support).
     */
    static boolean hasEncryptedData(String logType, Map<String, Object> row) {
        if (row == null) return false;
        if ("java_fw_imglog".equals(logType)) {
            Object ds = getFromRow(row, "datastring", "dataString");
            if (ds != null && containsEncryptedStylePayload(ds.toString())) return true;
            Object hs = getFromRow(row, "headerstring", "headerString");
            if (hs != null && containsEncryptedStylePayload(hs.toString())) return true;
            return false;
        }
        if ("pb_feplog".equals(logType)) {
            return false;
        }
        return false;
    }

    /**
     * Returns true if the string (typically JSON) contains a quoted value that is bracket-wrapped
     * and the content inside brackets is long enough to be a real cipher payload (not a short
     * plain value like "[100,200]" or "[1,2,3]").
     * Plain JSON arrays like [1,2,3] or short values like "size":"[100,200]" are not matched.
     * Aligns with req 20260318-image-log-no-decrypt-button-when-plain and 20260318-search-history-counts-display.
     */
    static boolean containsEncryptedStylePayload(String s) {
        if (s == null || s.isEmpty()) return false;
        java.util.regex.Matcher m = ENCRYPTED_STYLE_PATTERN.matcher(s);
        while (m.find()) {
            String inner = m.group(1);
            if (inner != null && inner.length() >= MIN_ENCRYPTED_PAYLOAD_LENGTH) return true;
        }
        return false;
    }

    /** Minimum length of content inside "[...]" to treat as encrypted payload (avoids matching "[1,2,3]" or "[100,200]"). */
    private static final int MIN_ENCRYPTED_PAYLOAD_LENGTH = 32;
    /** Pattern: quoted JSON string value whose content is bracket-wrapped; group(1) = content inside brackets. */
    private static final Pattern ENCRYPTED_STYLE_PATTERN = Pattern.compile("\"\\[([^\"]*)\\]\"");

    /** Get first non-null value from row for given keys (e.g. "datastring" then "dataString" for JDBC casing). */
    private static Object getFromRow(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) return null;
        for (String k : keys) {
            Object v = row.get(k);
            if (v != null) return v;
        }
        return null;
    }

    private static String asNonEmptyString(Object o) {
        if (o == null) return "";
        String s = o instanceof String ? (String) o : o.toString();
        return s != null ? s.trim() : "";
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
                    boolean found = rs.next();
                    if (!found) {
                        log.debug("isRowInApprovedSnapshot: row not in snapshot (searchHistoryId={}, logType={})", searchHistoryId, logType);
                    }
                    return found;
                }
            }
        } catch (SQLException e) {
            log.error("스냅샷 조회 실패: searchHistoryId={}, logType={}, rowId={}", searchHistoryId, logType, rowId, e);
            return false;
        }
    }

    /**
     * 반려. PENDING 건만 갱신. §6.1.7. approverUserId = numeric app_user.id (req 20260316).
     */
    public Map<String, Object> reject(Long id, Long approverUserId, String rejectionReason) {
        Long requesterUserIdLong = null;
        try (Connection conn = dataSource.getConnection()) {
            String sel = "SELECT user_id FROM search_history WHERE id = ? AND approval_status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(sel)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw CustomException.notFound("해당 검색 이력을 찾을 수 없거나 이미 처리되었습니다: id=" + id, "NOT_FOUND");
                    }
                    requesterUserIdLong = toLongUserId(rs.getObject("user_id"));
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 조회 실패: id={}", id, e);
            throw new RuntimeException("반려 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        if (!decryptApproverService.canApproveForRequester(approverUserId, requesterUserIdLong)) {
            throw CustomException.forbidden("해당 기능에 대한 권한이 없습니다.", "FUNCTION_NOT_ALLOWED");
        }
        String rejectedByDisplay = appUserResolver.getUsernameById(approverUserId);
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE search_history SET approval_status = 'REJECTED', rejected_by = ?, rejected_at = CURRENT_TIMESTAMP, rejection_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND approval_status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, rejectedByDisplay != null ? rejectedByDisplay : "");
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
                        log.info("검색 이력 반려: id={}, rejectedBy={}", id, rejectedByDisplay);
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
     * 검색 이력 상세 (재조회 시 검색 조건 반환). userId = numeric app_user.id (req 20260316).
     */
    public Map<String, Object> getDetail(Long userId, Long id) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, user_id, log_type, search_params, request_reason, requested_at, expires_at, approval_status, approved_by_user_id, approved_by, approved_at, rejected_by, rejected_at, rejection_reason, " +
                    "search_result_total_count, decryption_target_count FROM search_history WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new NoSuchElementException("검색 이력을 찾을 수 없습니다: id=" + id);
                    }
                    Long rowUserId = toLongUserId(rs.getObject("user_id"));
                    if (rowUserId == null || !rowUserId.equals(userId)) {
                        throw CustomException.forbidden("해당 검색 이력은 요청자만 조회할 수 있습니다.", "FUNCTION_NOT_ALLOWED");
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("logType", rs.getString("log_type"));
                    try {
                        row.put("requestReason", rs.getString("request_reason"));
                    } catch (SQLException e) {
                        row.put("requestReason", null);
                    }
                    row.put("requestedAt", formatTimestamp(rs.getTimestamp("requested_at")));
                    row.put("expiresAt", formatTimestamp(rs.getTimestamp("expires_at")));
                    row.put("approvalStatus", rs.getString("approval_status"));
                    putApprovalFieldsFromRs(rs, row);
                    putNullableIntegerColumn(rs, "search_result_total_count", "searchResultTotalCount", row);
                    putNullableIntegerColumn(rs, "decryption_target_count", "decryptionTargetCount", row);
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
                    String approvalStatus = rs.getString("approval_status");
                    String logTypeCol = rs.getString("log_type");
                    List<Map<String, Object>> decryptionRows;
                    if ("APPROVED".equals(approvalStatus)) {
                        decryptionRows = loadDecryptionRequestedRows(conn, id);
                        if (decryptionRows.isEmpty()) {
                            List<String> fromSearch = collectEncryptedSnapshotRowIdsLenient(paramsJson, logTypeCol);
                            decryptionRows = buildDecryptionRequestedRowsFromSearchRowIds(logTypeCol, fromSearch);
                        }
                    } else {
                        List<String> fromSearch = collectEncryptedSnapshotRowIdsLenient(paramsJson, logTypeCol);
                        decryptionRows = buildDecryptionRequestedRowsFromSearchRowIds(logTypeCol, fromSearch);
                    }
                    row.put("decryptionRequestedRows", decryptionRows);
                    row.put("decryptionRequestedCount", decryptionRows.size());
                    log.info("getDetail: search_history_id={}, approvalStatus={}, decryptionRequestedCount={}", id, approvalStatus, decryptionRows.size());
                    return row;
                }
            }
        } catch (SQLException e) {
            log.error("검색 이력 상세 조회 실패: id={}", id, e);
            throw new RuntimeException("검색 이력 상세 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Load decryption-requested rows from search_history_approved_row and resolve application/serviceGroup from log DB for java_fw_imglog.
     * On log DB unavailability or missing guid, entries still have guid with application/serviceGroup null. Req 20260318.
     */
    private List<Map<String, Object>> loadDecryptionRequestedRows(Connection conn, long searchHistoryId) throws SQLException {
        List<String> orderedRowIds = new ArrayList<>();
        List<String> orderedLogTypes = new ArrayList<>();
        String sql = "SELECT log_type, row_id FROM search_history_approved_row WHERE search_history_id = ? ORDER BY log_type, row_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, searchHistoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String lt = rs.getString("log_type");
                    String rowId = rs.getString("row_id");
                    if (rowId == null || rowId.isBlank()) continue;
                    orderedLogTypes.add(lt);
                    orderedRowIds.add(rowId);
                }
            }
        }
        log.info("loadDecryptionRequestedRows: search_history_id={}, rows_read_from_search_history_approved_row={}", searchHistoryId, orderedRowIds.size());
        return buildDecryptionRequestedRowsDisplay(orderedLogTypes, orderedRowIds);
    }

    /**
     * Same encrypted-row collection as approve: parse search_params + logType, searchLogs(pageSize=SNAPSHOT_MAX_ROWS), hasEncryptedData, extractRowIdForSnapshot.
     * Strict parse failures throw IllegalArgumentException (approve path).
     */
    @SuppressWarnings("unchecked")
    private List<String> collectEncryptedSnapshotRowIdsStrict(String searchParamsJson, String logType) throws IllegalArgumentException {
        LogDbSearchRequest searchRequest;
        try {
            Map<String, Object> paramsMap = searchParamsJson != null && !searchParamsJson.isEmpty()
                    ? objectMapper.readValue(searchParamsJson, Map.class)
                    : new HashMap<>();
            searchRequest = objectMapper.convertValue(paramsMap, LogDbSearchRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid search_params", e);
        }
        return runCollectEncryptedRowIdsFromSearchRequest(searchRequest, logType);
    }

    /**
     * getDetail path: invalid params or search failure → empty list (still return 200 with empty rows).
     */
    private List<String> collectEncryptedSnapshotRowIdsLenient(String searchParamsJson, String logType) {
        try {
            return collectEncryptedSnapshotRowIdsStrict(searchParamsJson, logType);
        } catch (IllegalArgumentException e) {
            log.warn("getDetail: search_params parse failed for decryption row list, logType={}", logType);
            return Collections.emptyList();
        }
    }

    private List<String> runCollectEncryptedRowIdsFromSearchRequest(LogDbSearchRequest searchRequest, String logType) {
        if (searchRequest.getLogType() == null || searchRequest.getLogType().isEmpty()) {
            searchRequest.setLogType(logType);
        }
        searchRequest.setPage(1);
        searchRequest.setPageSize(SNAPSHOT_MAX_ROWS);
        LogDbSearchResponse searchResponse;
        try {
            searchResponse = logDbService.searchLogs(searchRequest);
        } catch (Exception e) {
            log.warn("collectEncryptedRowIds: searchLogs failed, logType={}: {}", logType, e.getMessage());
            return Collections.emptyList();
        }
        List<Map<String, Object>> data = searchResponse.getData() != null ? searchResponse.getData() : Collections.emptyList();
        List<String> rowIds = new ArrayList<>();
        for (Map<String, Object> row : data) {
            if (!hasEncryptedData(logType, row)) {
                continue;
            }
            String rowId = extractRowIdForSnapshot(logType, row);
            if (rowId != null && !rowId.isEmpty()) {
                rowIds.add(rowId);
            }
        }
        return rowIds;
    }

    /**
     * Build API rows: java_fw_imglog resolves application/serviceGroup via {@link LogDbService#getApplicationServiceGroupByGuids}; other log types null app/sg; guid holds row_id.
     */
    private List<Map<String, Object>> buildDecryptionRequestedRowsDisplay(List<String> orderedLogTypes, List<String> orderedRowIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> guidsToResolve = new ArrayList<>();
        for (int i = 0; i < orderedRowIds.size(); i++) {
            String rowId = orderedRowIds.get(i);
            if (rowId == null || rowId.isBlank()) {
                continue;
            }
            if ("java_fw_imglog".equals(orderedLogTypes.get(i))) {
                guidsToResolve.add(rowId);
            }
        }
        Map<String, Map<String, String>> resolution = Collections.emptyMap();
        if (!guidsToResolve.isEmpty() && logDbService != null) {
            try {
                resolution = logDbService.getApplicationServiceGroupByGuids(guidsToResolve);
            } catch (Exception e) {
                log.warn("Log DB resolution for decryption rows failed (returning null app/sg): {}", e.getMessage());
            }
        }
        for (int i = 0; i < orderedRowIds.size(); i++) {
            String rowId = orderedRowIds.get(i);
            if (rowId == null || rowId.isBlank()) {
                continue;
            }
            boolean isImglog = "java_fw_imglog".equals(orderedLogTypes.get(i));
            Map<String, Object> entry = new LinkedHashMap<>();
            if (isImglog) {
                Map<String, String> resolved = resolution.get(rowId);
                entry.put("application", resolved != null ? resolved.get("application") : null);
                entry.put("serviceGroup", resolved != null ? resolved.get("serviceGroup") : null);
            } else {
                entry.put("application", null);
                entry.put("serviceGroup", null);
            }
            entry.put("guid", rowId);
            rows.add(entry);
        }
        return rows;
    }

    private List<Map<String, Object>> buildDecryptionRequestedRowsFromSearchRowIds(String logType, List<String> rowIds) {
        List<String> logTypes = new ArrayList<>(rowIds.size());
        for (int i = 0; i < rowIds.size(); i++) {
            logTypes.add(logType);
        }
        return buildDecryptionRequestedRowsDisplay(logTypes, rowIds);
    }

    private static String formatTimestamp(Timestamp ts) {
        if (ts == null) return null;
        return ts.toLocalDateTime().format(DATE_FORMATTER);
    }

    /** Bind numeric user_id (app_user.id). Works with BIGINT or VARCHAR column. */
    private static void bindUserId(PreparedStatement ps, int index, Long userId) throws SQLException {
        if (userId == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setObject(index, userId);
        }
    }

    /** Parse user_id from ResultSet (BIGINT or VARCHAR storing digits) to Long. */
    private static Long toLongUserId(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Fill requester display from app_user/department when JOIN yielded null (orphan). Req 20260316. */
    private RequesterDisplay resolveRequesterDisplayByUserId(Connection conn, Long userId) {
        if (userId == null) return null;
        try {
            String sql = "SELECT au.username, au.name, au.department_code, d.name AS department_name FROM app_user au LEFT JOIN department d ON d.code = au.department_code WHERE au.id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    String username = rs.getString("username");
                    String name = rs.getString("name");
                    String departmentCode = rs.getString("department_code");
                    String departmentName = rs.getString("department_name");
                    String displayName = (name != null && !name.isBlank()) ? name : username;
                    return new RequesterDisplay(username, displayName, departmentCode, departmentName);
                }
            }
        } catch (SQLException e) {
            log.debug("Requester display resolve by user_id failed: userId={}", userId, e);
            return null;
        }
    }

    private static final class RequesterDisplay {
        final String username;
        final String displayName;
        final String departmentCode;
        final String departmentName;

        RequesterDisplay(String username, String displayName, String departmentCode, String departmentName) {
            this.username = username;
            this.displayName = displayName;
            this.departmentCode = departmentCode;
            this.departmentName = departmentName;
        }
    }

    /**
     * Builds list query spec. Binds user_id params as String so that both VARCHAR and BIGINT
     * search_history.user_id columns work (e.g. before/after migrate-search-history-user-id-to-bigint).
     */
    private SearchHistoryListQuerySpec buildListQuerySpec(SearchHistoryListRequest request) {
        SearchHistoryListQuerySpec querySpec = new SearchHistoryListQuerySpec();

        List<Long> allowedUserIds = request.getAllowedUserIds();
        if (allowedUserIds != null) {
            if (allowedUserIds.isEmpty()) {
                querySpec.addCondition("1 = 0");
            } else if (allowedUserIds.size() == 1) {
                querySpec.addCondition("sh.user_id::text = ?");
                querySpec.addParamUserId(allowedUserIds.get(0));
            } else {
                querySpec.addCondition("sh.user_id::text IN (" + String.join(",", Collections.nCopies(allowedUserIds.size(), "?")) + ")");
                querySpec.addParamUserIds(allowedUserIds);
            }
        }

        if (request.getUserId() != null) {
            querySpec.addCondition("sh.user_id::text = ?");
            querySpec.addParamUserId(request.getUserId());
        }

        if (hasText(request.getDepartment())) {
            querySpec.addCondition("au.department_code = ?");
            querySpec.addParam(request.getDepartment().trim());
        }

        if (hasText(request.getUsername())) {
            querySpec.addCondition("LOWER(au.username) LIKE ?");
            querySpec.addParam("%" + request.getUsername().trim().toLowerCase(Locale.ROOT) + "%");
        }

        if (hasText(request.getRequestedAtFrom())) {
            LocalDateTime from = parseRequestedAt(request.getRequestedAtFrom());
            if (from != null) {
                querySpec.addCondition("sh.requested_at >= ?");
                querySpec.addParam(Timestamp.valueOf(from));
            }
        }
        if (hasText(request.getRequestedAtTo())) {
            LocalDateTime to = parseRequestedAt(request.getRequestedAtTo());
            if (to != null) {
                querySpec.addCondition("sh.requested_at <= ?");
                querySpec.addParam(Timestamp.valueOf(to));
            }
        }
        List<String> statuses = request.getApprovalStatuses();
        if (statuses != null && !statuses.isEmpty()) {
            List<String> valid = new ArrayList<>();
            for (String s : statuses) {
                if (s != null && !s.trim().isEmpty()) valid.add(s.trim());
            }
            if (!valid.isEmpty()) {
                querySpec.addCondition("sh.approval_status IN (" + String.join(",", Collections.nCopies(valid.size(), "?")) + ")");
                querySpec.addParamsString(valid);
            }
        }
        if (hasText(request.getRequestReason())) {
            querySpec.addCondition("sh.request_reason ILIKE ?");
            querySpec.addParam("%" + request.getRequestReason().trim() + "%");
        }

        return querySpec;
    }

    /** Parse requestedAt string. Null/empty → null. Invalid format → IllegalArgumentException (req 20260317: clear validation error). */
    private LocalDateTime parseRequestedAt(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("requestedAt parse failed: value={}, expected format yyyy-MM-dd HH:mm:ss", value, e);
            throw new IllegalArgumentException("requestedAtFrom and requestedAtTo must be in format yyyy-MM-dd HH:mm:ss");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static String formatTimestampISO(Timestamp ts) {
        if (ts == null) return null;
        return ts.toLocalDateTime().format(ISO_FORMATTER);
    }

    /** Approval-history fields from ResultSet. approvedBy = resolved from approved_by_user_id or fallback approved_by (req 20260316). */
    private void putApprovalFieldsFromRs(ResultSet rs, Map<String, Object> map) throws SQLException {
        map.put("approvedBy", resolveApprovedByDisplay(rs));
        map.put("approvedAt", formatTimestamp(rs.getTimestamp("approved_at")));
        map.put("rejectedBy", rs.getString("rejected_by"));
        map.put("rejectedAt", formatTimestamp(rs.getTimestamp("rejected_at")));
        map.put("rejectionReason", rs.getString("rejection_reason"));
    }

    /** Resolve approvedBy display string from approved_by_user_id (via getUsernameById) or fallback to approved_by. */
    private String resolveApprovedByDisplay(ResultSet rs) throws SQLException {
        Long approvedByUserId = null;
        try {
            Object o = rs.getObject("approved_by_user_id");
            if (o instanceof Number) approvedByUserId = ((Number) o).longValue();
        } catch (SQLException ignored) { /* column may be missing in old schema */ }
        if (approvedByUserId != null && appUserResolver != null) {
            String username = appUserResolver.getUsernameById(approvedByUserId);
            if (username != null && !username.isBlank()) return username;
        }
        return rs.getString("approved_by");
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

    private static final class SearchHistoryListQuerySpec {
        private final List<String> conditions = new ArrayList<>();
        private final List<Object> params = new ArrayList<>();

        private void addCondition(String condition) {
            conditions.add(condition);
        }

        private void addParam(Object param) {
            params.add(param);
        }

        /** Bind user_id as String so that both VARCHAR and BIGINT search_history.user_id columns work (req 20260316 bugfix-1). */
        private void addParamUserId(Long userId) {
            params.add(userId != null ? String.valueOf(userId) : null);
        }

        private void addParamUserIds(List<Long> values) {
            if (values != null) {
                for (Long v : values) {
                    params.add(v != null ? String.valueOf(v) : null);
                }
            }
        }

        private void addParamsString(List<String> values) {
            if (values != null) {
                params.addAll(values);
            }
        }

        private void addParamsLong(List<Long> values) {
            params.addAll(values);
        }

        private String getFromAndWhereClause() {
            StringBuilder sql = new StringBuilder("FROM search_history sh LEFT JOIN app_user au ON au.id = sh.user_id::bigint LEFT JOIN department d ON d.code = au.department_code");
            if (!conditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", conditions));
            }
            return sql.toString();
        }

        private List<Object> getParams() {
            return params;
        }
    }
}
