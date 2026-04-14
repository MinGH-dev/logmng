package com.logmng.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.logmng.dto.request.AdvancedSearchRequest;
import com.logmng.dto.request.FilterCondition;
import com.logmng.dto.DecryptionRowKey;
import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.request.LogDbSortSpec;
import com.logmng.dto.response.LogDbSearchResponse;
import com.logmng.exception.CustomException;
import com.logmng.util.CryptoUtil;
import com.logmng.util.CryptoUtil.LogPayloadCryptoVariant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DB 기반 로그 서비스
 */
@Service
public class LogDbService {

    /** Max rows to fetch when data/header/keyword filters are present; filter in memory then paginate. Per req 20260318. */
    private static final int IMGLOG_FILTER_PREFETCH_CAP = 5000;

    /**
     * In-memory text filter terms for java_fw_imglog: field terms (datastring + headerstring, AND) vs keyword terms
     * (keywords list, OR). See {@link #buildJavaFwImglogTextFilterTerms(LogDbSearchRequest)}.
     */
    private static final class JavaFwImglogTextFilterTerms {
        private final List<String> fieldTerms;
        private final List<String> keywordTerms;

        private JavaFwImglogTextFilterTerms(List<String> fieldTerms, List<String> keywordTerms) {
            this.fieldTerms = fieldTerms != null ? fieldTerms : Collections.emptyList();
            this.keywordTerms = keywordTerms != null ? keywordTerms : Collections.emptyList();
        }

        boolean needsFiltering() {
            return !fieldTerms.isEmpty() || !keywordTerms.isEmpty();
        }

        List<String> getFieldTerms() {
            return fieldTerms;
        }

        List<String> getKeywordTerms() {
            return keywordTerms;
        }
    }

    /**
     * When JSON bracket-wrapped ImageLog ciphertext cannot be decrypted, substitute this instead of echoing E002+Base64.
     */
    private static final String IMAGE_LOG_JSON_DECRYPT_FAILED = "복호화에 실패했습니다 (키 불일치 또는 손상된 데이터)";

    private static final Logger log = LoggerFactory.getLogger(LogDbService.class);

    private final DataSource primaryDataSource;
    private final DataSource pbDataSource;
    private final DataSource imagelogDataSource;
    private final CryptoUtil cryptoUtil;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter PB_FEPLOG_TIME_LEXICAL_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Canonical PB FEP timestamp column used by search/filter path.
     */
    private static final String PB_FEPLOG_TIMESTAMP_COLUMN = "log_time";

    /**
     * Canonical product-facing PB FEP time key.
     */
    private static final String PB_FEPLOG_CANONICAL_TIME_KEY = "log_time";

    /**
     * Allowlisted ORDER BY columns for pb_send ∪ pb_recv: stable legacy JSON keys matching SELECT aliases.
     * Keeps wire-logical contract wording while preserving backward compatibility for existing clients.
     */
    private static final Set<String> PB_FEPLOG_SORTABLE_COLUMNS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "id", PB_FEPLOG_CANONICAL_TIME_KEY, "media_code", "tr_code", "user_id", "ip_address",
            "user_agent", "request_data", "response_data", "status_code", "response_time",
            "error_message", "session_id", "device_type", "created_at", "updated_at", "log_type"
    )));

    /**
     * Shared column projection using canonical time key only.
     * Keep pb_send / pb_recv lists identical for UNION ALL.
     */
    private static final String PB_FEPLOG_WIRE_SELECT_BODY =
            "id, " + PB_FEPLOG_TIMESTAMP_COLUMN + ", "
                    + "media_gb AS media_code, tr_code, brodid AS user_id, pub_ip AS ip_address, "
                    + "CAST(NULL AS VARCHAR(500)) AS user_agent, "
                    + "vlen AS request_data, data AS response_data, "
                    + "msg_code AS status_code, "
                    + "COALESCE(CAST(NULLIF(TRIM(term_no), '') AS BIGINT), 0) AS response_time, "
                    + "bmsg AS error_message, prt_ip AS session_id, log_ch_cd AS device_type, "
                    + "created_at, updated_at ";
    
    public LogDbService(@Qualifier("dataSource") DataSource primaryDataSource,
                        @Qualifier("pbDataSource") DataSource pbDataSource,
                        @Qualifier("imagelogDataSource") DataSource imagelogDataSource,
                        CryptoUtil cryptoUtil) {
        this.primaryDataSource = primaryDataSource;
        this.pbDataSource = pbDataSource;
        this.imagelogDataSource = imagelogDataSource;
        this.cryptoUtil = cryptoUtil;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * DB 로그 검색
     */
    public LogDbSearchResponse searchLogs(LogDbSearchRequest request) {
        String logType = request.getLogType() != null ? request.getLogType() : "pb_feplog";
        log.debug("searchLogs: logType={}, startDate={}, endDate={}, mediaCode={}, trCode={}, loginId={}, page={}, pageSize={}",
                logType, request.getStartDate(), request.getEndDate(), request.getMediaCode(),
                request.getTrCode(), request.getLoginId(), request.getPage(), request.getPageSize());
        
        // 로그 타입에 따라 다른 쿼리 실행
        if ("pb_feplog".equals(logType)) {
            return searchPbFeplog(request);
        } else if ("java_fw_imglog".equals(logType)) {
            return searchJavaFwImglog(request);
        } else {
            throw new RuntimeException("지원하지 않는 로그 타입입니다: " + logType);
        }
    }

    private String normalizePbFeplogSortField(String raw) {
        if (raw == null) {
            return null;
        }
        String f = raw.trim();
        if (f.isEmpty()) {
            return null;
        }
        if ("prc_time".equalsIgnoreCase(f)) {
            return PB_FEPLOG_CANONICAL_TIME_KEY;
        }
        if ("log_timestamp".equalsIgnoreCase(f)) {
            throw CustomException.badRequest("정렬 필드 log_timestamp는 더 이상 지원되지 않습니다. log_time을 사용하세요.", "INVALID_INPUT");
        }
        if ("brodid".equalsIgnoreCase(f)) {
            return "user_id";
        }
        if ("media_gb".equalsIgnoreCase(f)) {
            return "media_code";
        }
        if ("msg_code".equalsIgnoreCase(f)) {
            return "status_code";
        }
        if ("bmsg".equalsIgnoreCase(f)) {
            return "error_message";
        }
        if ("vlen".equalsIgnoreCase(f)) {
            return "request_data";
        }
        if ("data".equalsIgnoreCase(f)) {
            return "response_data";
        }
        if ("log_ch_cd".equalsIgnoreCase(f)) {
            return "device_type";
        }
        if ("log_io_cd".equalsIgnoreCase(f)) {
            return "log_type";
        }
        if ("pub_ip".equalsIgnoreCase(f) || "src_ip".equalsIgnoreCase(f)) {
            return "ip_address";
        }
        if ("prt_ip".equalsIgnoreCase(f) || "app_id".equalsIgnoreCase(f)) {
            return "session_id";
        }
        if ("login_id".equalsIgnoreCase(f)) {
            return "user_id";
        }
        if ("send_recv".equalsIgnoreCase(f)) {
            return "log_type";
        }
        if ("term_no".equalsIgnoreCase(f)) {
            return "response_time";
        }
        return f;
    }

    /**
     * Safe ORDER BY for PB FEP union query. Uses sortSpecs when non-empty; else legacy sortField/sortDirection.
     */
    String buildPbFeplogOrderBy(LogDbSearchRequest request) {
        List<LogDbSortSpec> specs = request.getSortSpecs();
        if (specs != null && !specs.isEmpty()) {
            StringBuilder ob = new StringBuilder();
            Set<String> used = new HashSet<>();
            for (LogDbSortSpec spec : specs) {
                if (spec == null) {
                    continue;
                }
                String col = normalizePbFeplogSortField(spec.getField());
                if (col == null || !PB_FEPLOG_SORTABLE_COLUMNS.contains(col) || used.contains(col)) {
                    continue;
                }
                used.add(col);
                String dirRaw = spec.getDirection();
                String dir = dirRaw != null && "asc".equalsIgnoreCase(dirRaw.trim()) ? "ASC" : "DESC";
                if (ob.length() > 0) {
                    ob.append(", ");
                }
                ob.append(col).append(" ").append(dir);
            }
            if (ob.length() > 0) {
                return ob.toString();
            }
        }
        String sortField = normalizePbFeplogSortField(request.getSortField() != null
                ? request.getSortField()
                : PB_FEPLOG_CANONICAL_TIME_KEY);
        if (sortField == null || !PB_FEPLOG_SORTABLE_COLUMNS.contains(sortField)) {
            sortField = PB_FEPLOG_CANONICAL_TIME_KEY;
        }
        String sortDirection = request.getSortDirection() != null ? request.getSortDirection() : "desc";
        String dir = "asc".equalsIgnoreCase(sortDirection.trim()) ? "ASC" : "DESC";
        return sortField + " " + dir;
    }
    
    /**
     * PB FEP search: SQL {@code pb_send} {@code UNION ALL} {@code pb_recv}. Shared by legacy {@link #searchLogs} (pb_feplog) and wireframe {@link #searchPbFepLogWireframe}.
     */
    private LogDbSearchResponse executePbFeplogUnionSearch(LogDbSearchRequest request) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection connection = pbDataSource.getConnection()) {
            // SQL 쿼리 구성
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ").append(PB_FEPLOG_WIRE_SELECT_BODY);
            sql.append(", 'send' AS log_type ");
            sql.append("FROM pb_send WHERE 1=1 ");
            
            List<Object> params = new ArrayList<>();
            
        // 날짜 조건
        LocalDateTime startDateTime = request.getStartDateAsDateTime();
        LocalDateTime endDateTime = request.getEndDateAsDateTime();
        
        if (startDateTime != null) {
            sql.append("AND ").append(PB_FEPLOG_TIMESTAMP_COLUMN).append(" >= ? ");
            params.add(toPbFeplogLogTimeLexical(startDateTime));
        }
        if (endDateTime != null) {
            sql.append("AND ").append(PB_FEPLOG_TIMESTAMP_COLUMN).append(" <= ? ");
            params.add(toPbFeplogLogTimeLexical(endDateTime));
        }
            
            // 매체코드 조건
            if (request.getMediaCode() != null && !request.getMediaCode().trim().isEmpty()) {
                sql.append("AND media_gb = ? ");
                params.add(request.getMediaCode());
            }
            
            // TR 코드 조건
            if (request.getTrCode() != null && !request.getTrCode().trim().isEmpty()) {
                sql.append("AND tr_code = ? ");
                params.add(request.getTrCode());
            }
            
            // 사용자 ID 조건 (wire broker id)
            if (request.getLoginId() != null && !request.getLoginId().trim().isEmpty()) {
                sql.append("AND brodid = ? ");
                params.add(request.getLoginId());
            }
            
            // UNION ALL로 pb_recv도 포함
            sql.append("UNION ALL ");
            sql.append("SELECT ").append(PB_FEPLOG_WIRE_SELECT_BODY);
            sql.append(", 'recv' AS log_type ");
            sql.append("FROM pb_recv WHERE 1=1 ");
            
            // 동일한 조건 적용
            if (startDateTime != null) {
                sql.append("AND ").append(PB_FEPLOG_TIMESTAMP_COLUMN).append(" >= ? ");
                params.add(toPbFeplogLogTimeLexical(startDateTime));
            }
            if (endDateTime != null) {
                sql.append("AND ").append(PB_FEPLOG_TIMESTAMP_COLUMN).append(" <= ? ");
                params.add(toPbFeplogLogTimeLexical(endDateTime));
            }
            if (request.getMediaCode() != null && !request.getMediaCode().trim().isEmpty()) {
                sql.append("AND media_gb = ? ");
                params.add(request.getMediaCode());
            }
            if (request.getTrCode() != null && !request.getTrCode().trim().isEmpty()) {
                sql.append("AND tr_code = ? ");
                params.add(request.getTrCode());
            }
            if (request.getLoginId() != null && !request.getLoginId().trim().isEmpty()) {
                sql.append("AND brodid = ? ");
                params.add(request.getLoginId());
            }
            
            sql.append("ORDER BY ").append(buildPbFeplogOrderBy(request));
            
            log.debug("실행 SQL: {}", sql.toString());
            log.debug("파라미터: {}", params);
            
            // 전체 개수 조회
            String countSql = "SELECT COUNT(*) FROM (" + sql.toString() + ") as total";
            long totalCount = 0;
            try (PreparedStatement countStmt = connection.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++) {
                    countStmt.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        totalCount = rs.getLong(1);
                    }
                }
            }
            
            // 페이징 적용
            int page = request.getPage() != null ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
            int offset = (page - 1) * pageSize;
            sql.append(" LIMIT ? OFFSET ?");
            params.add(pageSize);
            params.add(offset);
            
            // 데이터 조회
            try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            
                            // Timestamp를 문자열로 변환
                            if (value instanceof Timestamp) {
                                value = ((Timestamp) value).toLocalDateTime().format(DATE_FORMATTER);
                            }

                            row.put(columnName, value);
                        }
                        // 복호화 옵션이 활성화된 경우 복호화된 데이터 포함
                        if (Boolean.TRUE.equals(request.getDecryptData())
                                && request.getKeywords() != null
                                && !request.getKeywords().isEmpty()) {
                            try {
                                // Wire vlen → legacy request_data alias; wire data → response_data alias
                                String encryptedRequest = jdbcValueToString(row.get("request_data"));
                                if (encryptedRequest != null && !encryptedRequest.isEmpty()) {
                                    String decryptedRequest = cryptoUtil.decryptLogPayload(encryptedRequest, LogPayloadCryptoVariant.PB_FEP);
                                    row.put("decrypted_request_data", decryptedRequest);
                                }
                                String encryptedResponse = jdbcValueToString(row.get("response_data"));
                                if (encryptedResponse != null && !encryptedResponse.isEmpty()) {
                                    String decryptedResponse = cryptoUtil.decryptLogPayload(encryptedResponse, LogPayloadCryptoVariant.PB_FEP);
                                    row.put("decrypted_response_data", decryptedResponse);
                                }
                            } catch (Exception e) {
                                log.warn("복호화 실패 (ID: {}): {}", row.get("id"), e.getMessage());
                                row.put("decrypted_request_data", "복호화 실패");
                                row.put("decrypted_response_data", "복호화 실패");
                            }
                        }
                        
                        results.add(row);
                    }
                }
            }
            
            // 페이징 정보 계산
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            
            log.info("✅ 검색 완료: {}건 중 {}건 반환 (페이지: {}/{})", totalCount, results.size(), page, totalPages);
            
            LogDbSearchResponse.PaginationInfo pagination = new LogDbSearchResponse.PaginationInfo(
                    page, totalPages, totalCount
            );
            
            return new LogDbSearchResponse(results, pagination);
            
        } catch (SQLException e) {
            log.error("PB FEP 로그 검색 중 오류 발생", e);
            throw new RuntimeException("PB FEP 로그 검색 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * PB FEP `log_time` is stored as lexical string `yyyyMMddHHmmss`; normalize date filters to the same format.
     */
    private static String toPbFeplogLogTimeLexical(LocalDateTime dateTime) {
        return dateTime.withNano(0).format(PB_FEPLOG_TIME_LEXICAL_FORMATTER);
    }

    private LogDbSearchResponse searchPbFeplog(LogDbSearchRequest request) {
        return executePbFeplogUnionSearch(request);
    }

    /**
     * PB FEP wireframe screen ({@code pb-fep-log-search}): same UNION/query as legacy {@code searchPbFeplog}, response rows use wireframe JSON keys (req 20260326).
     */
    public LogDbSearchResponse searchPbFepLogWireframe(LogDbSearchRequest request) {
        validatePbFepWireframeSearchRequest(request);
        LogDbSearchRequest exec = copyRequestForPbFeplogWireframeExecution(request);
        LogDbSearchResponse raw = executePbFeplogUnionSearch(exec);
        List<Map<String, Object>> mapped = new ArrayList<>(raw.getData().size());
        for (Map<String, Object> row : raw.getData()) {
            mapped.add(mapPbFepRowToWireframe(row));
        }
        return new LogDbSearchResponse(mapped, raw.getPagination());
    }

    /**
     * Validates wireframe-only rules: required dates/loginId, range order, pageSize allowlist, strict sortSpecs allowlist.
     */
    void validatePbFepWireframeSearchRequest(LogDbSearchRequest request) {
        if (request.getLoginId() == null || request.getLoginId().trim().isEmpty()) {
            throw CustomException.badRequest("Login ID는 필수입니다.", "INVALID_INPUT");
        }
        if (request.getStartDate() == null || request.getStartDate().trim().isEmpty()) {
            throw CustomException.badRequest("시작 일시(startDate)는 필수입니다.", "INVALID_INPUT");
        }
        if (request.getEndDate() == null || request.getEndDate().trim().isEmpty()) {
            throw CustomException.badRequest("종료 일시(endDate)는 필수입니다.", "INVALID_INPUT");
        }
        LocalDateTime start = request.getStartDateAsDateTime();
        LocalDateTime end = request.getEndDateAsDateTime();
        if (start == null) {
            throw CustomException.badRequest("시작 일시(startDate) 형식이 올바르지 않습니다.", "INVALID_INPUT");
        }
        if (end == null) {
            throw CustomException.badRequest("종료 일시(endDate) 형식이 올바르지 않습니다.", "INVALID_INPUT");
        }
        if (start.isAfter(end)) {
            throw CustomException.badRequest("검색 기간이 올바르지 않습니다. 시작 일시가 종료 일시보다 늦을 수 없습니다.", "INVALID_INPUT");
        }
        Integer ps = request.getPageSize();
        if (ps != null && ps != 25 && ps != 50 && ps != 100) {
            throw CustomException.badRequest("pageSize는 25, 50, 100 중 하나여야 합니다.", "INVALID_INPUT");
        }
        List<LogDbSortSpec> specs = request.getSortSpecs();
        if (specs != null) {
            for (LogDbSortSpec spec : specs) {
                if (spec == null) {
                    throw CustomException.badRequest("sortSpecs 항목이 null일 수 없습니다.", "INVALID_INPUT");
                }
                String rawField = spec.getField();
                if (rawField == null || rawField.trim().isEmpty()) {
                    throw CustomException.badRequest("sortSpecs.field는 비어 있을 수 없습니다.", "INVALID_INPUT");
                }
                String col = normalizePbFeplogSortField(rawField.trim());
                if (col == null || !PB_FEPLOG_SORTABLE_COLUMNS.contains(col)) {
                    throw CustomException.badRequest("유효하지 않은 정렬 필드입니다: " + rawField.trim(), "INVALID_INPUT");
                }
                String dir = spec.getDirection();
                if (dir != null && !dir.trim().isEmpty()
                        && !"asc".equalsIgnoreCase(dir.trim())
                        && !"desc".equalsIgnoreCase(dir.trim())) {
                    throw CustomException.badRequest("sortSpecs.direction은 asc 또는 desc여야 합니다.", "INVALID_INPUT");
                }
            }
        }
    }

    private static LogDbSearchRequest copyRequestForPbFeplogWireframeExecution(LogDbSearchRequest in) {
        LogDbSearchRequest r = new LogDbSearchRequest();
        r.setStartDate(in.getStartDate());
        r.setEndDate(in.getEndDate());
        r.setLoginId(in.getLoginId());
        r.setTrCode(in.getTrCode());
        r.setMediaCode(in.getMediaCode());
        r.setKeywords(in.getKeywords() != null ? new ArrayList<>(in.getKeywords()) : new ArrayList<>());
        r.setDecryptData(in.getDecryptData());
        r.setSortSpecs(in.getSortSpecs() != null ? new ArrayList<>(in.getSortSpecs()) : new ArrayList<>());
        r.setSortField(in.getSortField());
        r.setSortDirection(in.getSortDirection());
        r.setPage(in.getPage() != null ? in.getPage() : 1);
        r.setPageSize(in.getPageSize() != null ? in.getPageSize() : 25);
        r.setLogType("pb_feplog");
        r.setDisplayTemplate(in.getDisplayTemplate());
        return r;
    }

    private static String formatPbFepMsgCode(Object statusCode) {
        if (statusCode == null) {
            return null;
        }
        return String.valueOf(statusCode);
    }

    /** Strip H2 debug form when a CLOB column was already converted to a plain String. */
    private static String unwrapH2ClobDebugStringIfPresent(String s) {
        if (s == null || s.length() < 7) {
            return s;
        }
        if (s.startsWith("clob") && s.contains(": '") && s.endsWith("'")) {
            int start = s.lastIndexOf(": '") + 3;
            if (start >= 3 && start < s.length()) {
                return s.substring(start, s.length() - 1).replace("''", "'");
            }
        }
        return s;
    }

    /** JDBC CLOB (e.g. H2) → string for JSON-safe wireframe fields. */
    private static String jdbcValueToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Clob) {
            try {
                Clob c = (Clob) value;
                long n = c.length();
                if (n <= 0) {
                    return "";
                }
                int len = n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
                return unwrapH2ClobDebugStringIfPresent(c.getSubString(1, len));
            } catch (SQLException e) {
                return unwrapH2ClobDebugStringIfPresent(value.toString());
            }
        }
        String s = value instanceof String ? (String) value : value.toString();
        return unwrapH2ClobDebugStringIfPresent(s);
    }

    private static String buildWireframeDataCellSummary(String requestPayload, String responsePayload) {
        String res = responsePayload != null ? responsePayload : "";
        String req = requestPayload != null ? requestPayload : "";
        String pick = !res.isEmpty() ? res : req;
        if (pick.isEmpty()) {
            return null;
        }
        int max = 200;
        if (pick.length() <= max) {
            return pick;
        }
        return pick.substring(0, max) + "...";
    }

    private static Map<String, Object> mapPbFepRowToWireframe(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        String branch = row.get("log_type") != null ? row.get("log_type").toString().trim().toLowerCase(Locale.ROOT) : "";
        out.put("id", row.get("id"));
        out.put("log_type", row.get("log_type"));
        Object logTime = row.get(PB_FEPLOG_CANONICAL_TIME_KEY);
        out.put(PB_FEPLOG_CANONICAL_TIME_KEY, logTime);
        out.put("tr_code", jdbcValueToString(row.get("tr_code")));
        out.put("login_id", jdbcValueToString(row.get("user_id")));
        out.put("msg_code", formatPbFepMsgCode(row.get("status_code")));
        out.put("bmsg", jdbcValueToString(row.get("error_message")));
        out.put("log_ch_cd", jdbcValueToString(row.get("device_type")));
        if ("send".equals(branch)) {
            out.put("send_recv", "SEND");
        } else if ("recv".equals(branch)) {
            out.put("send_recv", "RECV");
        } else {
            out.put("send_recv", "RECV");
        }
        out.put("src_ip", jdbcValueToString(row.get("ip_address")));
        out.put("dest_ip", "");
        Object sessionId = row.get("session_id");
        out.put("app_id", sessionId != null ? sessionId.toString() : "");

        Object reqRaw = row.get("decrypted_request_data") != null ? row.get("decrypted_request_data") : row.get("request_data");
        Object resRaw = row.get("decrypted_response_data") != null ? row.get("decrypted_response_data") : row.get("response_data");
        String reqOut = jdbcValueToString(reqRaw);
        String resOut = jdbcValueToString(resRaw);
        out.put("request_data", reqOut);
        out.put("response_data", resOut);
        out.put("data", buildWireframeDataCellSummary(reqOut, resOut));
        return out;
    }
    
    /**
     * Java FW Image 로그 검색 (imagelog)
     */
    private LogDbSearchResponse searchJavaFwImglog(LogDbSearchRequest request) {
        // Per req 20260318: do not log full datastring/headerstring/keywords. DEBUG, length/null only.
        int dsLen = request.getDatastring() != null ? request.getDatastring().length() : -1;
        int hsLen = request.getHeaderstring() != null ? request.getHeaderstring().length() : -1;
        int kwSize = request.getKeywords() != null ? request.getKeywords().size() : -1;
        log.debug("searchJavaFwImglog request: startDate={}, endDate={}, application={}, servicegroup={}, service={}, datastringLen={}, headerstringLen={}, keywordsSize={}",
                request.getStartDate(), request.getEndDate(), request.getApplication(),
                request.getServicegroup(), request.getService(), dsLen, hsLen, kwSize);

        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection connection = imagelogDataSource.getConnection()) {
            // SQL 쿼리 구성
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT application, servicegroup, service, status, data, datastring, ");
            sql.append("guid, header, headerstring, insert_time ");
            sql.append("FROM imagelog WHERE 1=1 ");
            
            List<Object> params = new ArrayList<>();
            
            // 날짜 조건 (insert_time은 bigint 타임스탬프)
            Long startTimestamp = request.getStartDateAsTimestamp();
            Long endTimestamp = request.getEndDateAsTimestamp();
            log.debug("image log date: startTs={}, endTs={} (null=no date filter)", startTimestamp, endTimestamp);
            if (startTimestamp == null || endTimestamp == null) {
                log.warn("🔍 이미지로그 검색: startDate 또는 endDate 파싱 실패로 날짜 조건이 일부/전부 미적용됨. raw startDate={}, endDate={}",
                        request.getStartDate(), request.getEndDate());
            }

            if (startTimestamp != null) {
                sql.append("AND insert_time >= ? ");
                params.add(startTimestamp);
            }
            if (endTimestamp != null) {
                sql.append("AND insert_time <= ? ");
                params.add(endTimestamp);
            }
            
            // application 조건
            if (request.getApplication() != null && !request.getApplication().trim().isEmpty()) {
                sql.append("AND application = ? ");
                params.add(request.getApplication());
            }
            
            // servicegroup 조건
            if (request.getServicegroup() != null && !request.getServicegroup().trim().isEmpty()) {
                sql.append("AND servicegroup = ? ");
                params.add(request.getServicegroup());
            }
            
            // service 조건
            if (request.getService() != null && !request.getService().trim().isEmpty()) {
                sql.append("AND service = ? ");
                params.add(request.getService());
            }
            
            // datastring, headerstring, keywords: in-memory filter — field terms AND, keyword terms OR (req java_fw_imglog).
            JavaFwImglogTextFilterTerms textFilterTerms = buildJavaFwImglogTextFilterTerms(request);
            log.debug("image log text filter: fieldTermCount={}, keywordTermCount={}",
                    textFilterTerms.getFieldTerms().size(), textFilterTerms.getKeywordTerms().size());
            
            // 정렬 (이미지로그는 insert_time 사용)
            String sortField = request.getSortField() != null ? request.getSortField() : "insert_time";
            // 프론트엔드에서 보내는 필드명을 실제 컬럼명으로 매핑
            if ("prc_time".equalsIgnoreCase(sortField)
                    || PB_FEPLOG_CANONICAL_TIME_KEY.equalsIgnoreCase(sortField)) {
                sortField = "insert_time";
            }
            String sortDirection = request.getSortDirection() != null ? request.getSortDirection() : "desc";
            sql.append("ORDER BY ").append(sortField).append(" ").append(sortDirection);
            
            log.debug("실행 SQL: {}", sql.toString());
            log.debug("파라미터: {}", params);

            int page = request.getPage() != null ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
            int offset = (page - 1) * pageSize;
            boolean needsFiltering = textFilterTerms.needsFiltering();

            if (needsFiltering) {
                // Per req 20260318: when unified text filters present, fetch larger set (capped) then filter then paginate.
                String prefetchSql = sql.toString() + " LIMIT ?";
                List<Object> prefetchParams = new ArrayList<>(params);
                prefetchParams.add(IMGLOG_FILTER_PREFETCH_CAP);
                try (PreparedStatement stmt = connection.prepareStatement(prefetchSql)) {
                    for (int i = 0; i < prefetchParams.size(); i++) {
                        stmt.setObject(i + 1, prefetchParams.get(i));
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        results = readImageLogResultSet(rs);
                    }
                }
                int prefetchN = results.size();
                log.debug("image log prefetch rows before in-memory filter: {}", prefetchN);

                List<Map<String, Object>> filteredResults = filterImageLogRowsByFieldAndKeywordTerms(results,
                        textFilterTerms);
                int filteredM = filteredResults.size();
                log.debug("image log rows after in-memory filter: {}", filteredM);

                long finalCount = filteredResults.size();
                int totalPages = (int) Math.ceil((double) finalCount / pageSize);
                int fromIndex = (page - 1) * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, filteredResults.size());
                List<Map<String, Object>> pageResults = fromIndex >= filteredResults.size()
                        ? new ArrayList<>()
                        : new ArrayList<>(filteredResults.subList(fromIndex, toIndex));
                for (Map<String, Object> row : pageResults) {
                    sanitizeJavaFwImglogSearchRow(row);
                }

                log.info("✅ 이미지로그 검색 완료 (필터 적용): {}건 중 {}건 반환 (페이지: {}/{})",
                        finalCount, pageResults.size(), page, totalPages);

                LogDbSearchResponse.PaginationInfo pagination = new LogDbSearchResponse.PaginationInfo(
                        page, totalPages, finalCount);
                return new LogDbSearchResponse(pageResults, pagination);
            }

            // No data/header/keyword filter: use count + LIMIT/OFFSET as before
            String countSql = "SELECT COUNT(*) FROM (" + sql.toString() + ") as total";
            long totalCount = 0;
            try (PreparedStatement countStmt = connection.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++) {
                    countStmt.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        totalCount = rs.getLong(1);
                    }
                }
            }

            sql.append(" LIMIT ? OFFSET ?");
            params.add(pageSize);
            params.add(offset);

            try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    results = readImageLogResultSet(rs);
                }
            }
            for (Map<String, Object> row : results) {
                sanitizeJavaFwImglogSearchRow(row);
            }

            long finalCount = totalCount;
            int totalPages = (int) Math.ceil((double) finalCount / pageSize);
            log.info("✅ 이미지로그 검색 완료: {}건 중 {}건 반환 (페이지: {}/{})", finalCount, results.size(), page, totalPages);
            LogDbSearchResponse.PaginationInfo pagination = new LogDbSearchResponse.PaginationInfo(page, totalPages, finalCount);
            return new LogDbSearchResponse(results, pagination);
        } catch (SQLException e) {
            log.error("이미지로그 검색 중 오류 발생", e);
            throw new RuntimeException("이미지로그 검색 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Removes internal {@code _*} match flags and {@code decrypted_*} keys from java_fw_imglog search/advanced-search row maps.
     * Public booleans {@code hasEncryptedMatchDatastring} / {@code hasEncryptedMatchHeaderstring} are kept (req imagelog highlight).
     * Optional {@code hasEncryptedMatchData} / {@code hasEncryptedMatchHeader}: keyword matched only via in-memory decrypt of
     * {@code data} / {@code header} columns (not {@code datastring}/{@code headerstring}); same sanitize rules — not stripped.
     * Per req 20260413: responses must not expose plaintext via {@code decrypted_*}; decrypt-for-match stays in-memory only.
     */
    private static void sanitizeJavaFwImglogSearchRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        row.entrySet().removeIf(e -> {
            String k = e.getKey();
            if (k == null) {
                return false;
            }
            return k.startsWith("_") || k.startsWith("decrypted_");
        });
    }

    /**
     * Reads imagelog ResultSet into list of row maps (insert_time formatted as string). Used by searchJavaFwImglog.
     * Does not attach decrypted_data/decrypted_header — search responses must not expose full payload plaintext (req 20260413).
     */
    private List<Map<String, Object>> readImageLogResultSet(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                Object value = rs.getObject(i);
                if ("insert_time".equals(columnName) && value != null) {
                    long timestamp = ((Number) value).longValue();
                    LocalDateTime dateTime = LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(timestamp),
                            java.time.ZoneId.systemDefault());
                    value = dateTime.format(DATE_FORMATTER);
                }
                row.put(columnName, value);
            }
            list.add(row);
        }
        return list;
    }

    /**
     * Builds field + keyword term lists for java_fw_imglog in-memory filter: field terms from trim(datastring) and
     * trim(headerstring) only (case-insensitive dedupe, first spelling wins); keyword terms from keywords list only
     * (trim, skip empty; order preserved; not deduped against field terms).
     */
    private JavaFwImglogTextFilterTerms buildJavaFwImglogTextFilterTerms(LogDbSearchRequest request) {
        return new JavaFwImglogTextFilterTerms(
                buildJavaFwImglogFieldTextTerms(request),
                buildJavaFwImglogKeywordTerms(request));
    }

    /** Non-empty trim(datastring) and trim(headerstring), deduped case-insensitively (first wins); datastring before headerstring. */
    private List<String> buildJavaFwImglogFieldTextTerms(LogDbSearchRequest request) {
        Map<String, String> byLower = new LinkedHashMap<>();
        if (request.getDatastring() != null) {
            String t = request.getDatastring().trim();
            if (!t.isEmpty()) {
                byLower.putIfAbsent(t.toLowerCase(Locale.ROOT), t);
            }
        }
        if (request.getHeaderstring() != null) {
            String t = request.getHeaderstring().trim();
            if (!t.isEmpty()) {
                byLower.putIfAbsent(t.toLowerCase(Locale.ROOT), t);
            }
        }
        return new ArrayList<>(byLower.values());
    }

    /** Trim each keyword; skip empty; preserve list order (no dedupe). */
    private List<String> buildJavaFwImglogKeywordTerms(LogDbSearchRequest request) {
        List<String> out = new ArrayList<>();
        if (request.getKeywords() == null) {
            return out;
        }
        for (String kw : request.getKeywords()) {
            if (kw == null) {
                continue;
            }
            String t = kw.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * Plaintext datastring/headerstring contains term, or bracket-JSON decrypt-for-match on either column.
     * Bracket decrypt-for-match is always attempted when {@code '['} is present (independent of {@code decryptData}).
     * Sets {@code hasEncryptedMatch*} when the match is only via decrypted JSON text.
     */
    private boolean javaFwImglogTermMatchesForFilter(Map<String, Object> row,
            String datastring, String headerstring,
            String decryptedDatastring, String decryptedHeaderstring,
            String term) {
        boolean plainData = datastring != null && datastring.contains(term);
        boolean plainHeader = headerstring != null && headerstring.contains(term);
        boolean decDataMatch = decryptedDatastring != null && decryptedDatastring.contains(term);
        boolean decHeaderMatch = decryptedHeaderstring != null && decryptedHeaderstring.contains(term);
        boolean matches = plainData || plainHeader || decDataMatch || decHeaderMatch;
        if (matches) {
            if (!plainData && decDataMatch) {
                row.put("hasEncryptedMatchDatastring", true);
            }
            if (!plainHeader && decHeaderMatch) {
                row.put("hasEncryptedMatchHeaderstring", true);
            }
        }
        return matches;
    }

    /**
     * Keyword OR branch: same as {@link #javaFwImglogTermMatchesForFilter} on {@code datastring}/{@code headerstring}, or
     * match inside decrypted {@code data}/{@code header} column ciphertext (IMAGE_LOG variant). Never puts decrypted payload on the row.
     * Sets {@code hasEncryptedMatchData} / {@code hasEncryptedMatchHeader} when the keyword is satisfied solely via the binary column path
     * (i.e. not via the term helper above).
     */
    private boolean javaFwImglogKeywordMatchesForFilter(Map<String, Object> row,
            String datastring, String headerstring,
            String decryptedDatastring, String decryptedHeaderstring,
            String keyword) {
        if (javaFwImglogTermMatchesForFilter(row, datastring, headerstring,
                decryptedDatastring, decryptedHeaderstring, keyword)) {
            return true;
        }
        boolean dataBin = javaFwImglogBinaryColumnDecryptContainsKeyword(row, keyword, "data");
        boolean headerBin = javaFwImglogBinaryColumnDecryptContainsKeyword(row, keyword, "header");
        if (dataBin) {
            row.put("hasEncryptedMatchData", true);
        }
        if (headerBin) {
            row.put("hasEncryptedMatchHeader", true);
        }
        return dataBin || headerBin;
    }

    /**
     * @param columnKey {@code data} or {@code header} (row map keys from JDBC)
     * @return true if ciphertext decrypts (IMAGE_LOG) and plaintext contains {@code keyword}; false on blank input or any failure
     */
    private boolean javaFwImglogBinaryColumnDecryptContainsKeyword(Map<String, Object> row, String keyword,
            String columnKey) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }
        String encrypted = coerceImagelogBinaryColumnToDecryptString(row != null ? row.get(columnKey) : null);
        if (encrypted == null) {
            return false;
        }
        try {
            String plain = cryptoUtil.decryptLogPayload(encrypted, LogPayloadCryptoVariant.IMAGE_LOG);
            return plain != null && plain.contains(keyword);
        } catch (Exception e) {
            log.debug("java_fw_imglog keyword binary decrypt: column={}, reason={}", columnKey, e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Non-blank string for {@link CryptoUtil#decryptLogPayload(String, LogPayloadCryptoVariant)}; {@code byte[]} as UTF-8 (best-effort).
     * Unknown JDBC types are skipped.
     */
    private static String coerceImagelogBinaryColumnToDecryptString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            return s.isEmpty() ? null : s;
        }
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            if (bytes.length == 0) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * Field terms: AND (each must match). Keyword terms: OR (at least one must match if non-empty).
     * Row included iff fieldOk and keywordOk.
     */
    private List<Map<String, Object>> filterImageLogRowsByFieldAndKeywordTerms(List<Map<String, Object>> rows,
            JavaFwImglogTextFilterTerms terms) {
        if (terms == null || !terms.needsFiltering()) {
            return new ArrayList<>(rows);
        }
        List<String> fieldTerms = terms.getFieldTerms();
        List<String> keywordTerms = terms.getKeywordTerms();
        List<Map<String, Object>> filteredResults = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String datastring = (String) row.get("datastring");
            String headerstring = (String) row.get("headerstring");

            String decryptedDatastring = null;
            if (datastring != null && datastring.contains("[")) {
                decryptedDatastring = decryptJsonStringValues(datastring);
            }
            String decryptedHeaderstring = null;
            if (headerstring != null && headerstring.contains("[")) {
                decryptedHeaderstring = decryptJsonStringValues(headerstring);
            }

            boolean fieldOk = true;
            for (String t : fieldTerms) {
                if (!javaFwImglogTermMatchesForFilter(row, datastring, headerstring,
                        decryptedDatastring, decryptedHeaderstring, t)) {
                    fieldOk = false;
                    break;
                }
            }

            boolean keywordOk = keywordTerms.isEmpty();
            if (!keywordOk) {
                for (String k : keywordTerms) {
                    if (javaFwImglogKeywordMatchesForFilter(row, datastring, headerstring,
                            decryptedDatastring, decryptedHeaderstring, k)) {
                        keywordOk = true;
                        break;
                    }
                }
            }

            if (fieldOk && keywordOk) {
                filteredResults.add(row);
            }
        }
        return filteredResults;
    }
    
    /**
     * Resolve application and servicegroup for a set of guids from imagelog (java_fw_imglog).
     * Used by search history detail to show decryption-requested rows. On SQLException returns empty map
     * so the caller can still return rows with guid and null application/serviceGroup.
     *
     * @param guids list of guid values (row_id from search_history_approved_row for java_fw_imglog)
     * @return map keyed by guid, value = map with "application" and "serviceGroup" (camelCase); missing guids not present
     */
    public Map<String, Map<String, String>> getApplicationServiceGroupByGuids(List<String> guids) {
        if (guids == null || guids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DecryptionRowKey> keys = new ArrayList<>();
        for (String g : guids) {
            if (g != null && !g.isBlank()) {
                keys.add(new DecryptionRowKey(g, ""));
            }
        }
        return getApplicationServiceGroupByGuidStatusPairs(keys);
    }

    /**
     * Resolve application/servicegroup per (guid, status) composite. Map key = {@link DecryptionRowKey#compositeMapKey()}.
     */
    public Map<String, Map<String, String>> getApplicationServiceGroupByGuidStatusPairs(List<DecryptionRowKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        try (Connection connection = imagelogDataSource.getConnection()) {
            for (DecryptionRowKey k : keys) {
                if (k == null || k.getGuid().isEmpty()) {
                    continue;
                }
                String sql = "SELECT application, servicegroup FROM imagelog WHERE guid = ? AND COALESCE(NULLIF(TRIM(status), ''), '') = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, k.getGuid());
                    stmt.setString(2, k.getStatus());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Map<String, String> row = new LinkedHashMap<>();
                            row.put("application", rs.getString("application"));
                            row.put("serviceGroup", rs.getString("servicegroup"));
                            result.put(k.compositeMapKey(), row);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("imagelog resolution by guid+status failed (returning partial map): {}", e.getMessage());
            return result;
        }
        return result;
    }

    /**
     * DB 로그 상세 조회
     */
    public Map<String, Object> getLogDetail(String logType, String type, String identifier, String status) {
        log.info("🔍 DB 로그 상세 조회: logType={}, type={}, identifier={}, status={}", logType, type, identifier, status);
        
        if ("pb_feplog".equals(logType)) {
            Long id = Long.parseLong(identifier);
            return getPbFeplogDetail(type, id);
        } else if ("java_fw_imglog".equals(logType)) {
            String st = DecryptionRowKey.normalizeStatus(status);
            if (st.isEmpty()) {
                throw new IllegalArgumentException("java_fw_imglog 상세 조회에는 status 쿼리 파라미터가 필요합니다.");
            }
            return getJavaFwImglogDetail(identifier, st);
        } else {
            throw new RuntimeException("지원하지 않는 로그 타입입니다: " + logType);
        }
    }
    
    /**
     * PB FEP 로그 상세 조회
     */
    private Map<String, Object> getPbFeplogDetail(String type, Long id) {
        String tableName = "send".equalsIgnoreCase(type) ? "pb_send" : "pb_recv";
        
        try (Connection connection = pbDataSource.getConnection()) {
            String sql = "SELECT " + PB_FEPLOG_WIRE_SELECT_BODY +
                        "FROM " + tableName + " WHERE id = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, id);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            
                            // Timestamp를 문자열로 변환
                            if (value instanceof Timestamp) {
                                value = ((Timestamp) value).toLocalDateTime().format(DATE_FORMATTER);
                            }
                            
                            row.put(columnName, value);
                        }
                        row.put("log_type", type);
                        
                        log.info("✅ 로그 상세 조회 완료: ID={}", id);
                        return row;
                    } else {
                        throw new RuntimeException("로그를 찾을 수 없습니다: type=" + type + ", id=" + id);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("DB 로그 상세 조회 중 오류 발생", e);
            throw new RuntimeException("DB 로그 상세 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * Java FW Image 로그 상세 조회 (guid + status)
     */
    private Map<String, Object> getJavaFwImglogDetail(String guid, String status) {
        log.info("🔍 이미지로그 상세 조회: guid={}, status={}", guid, status);
        
        try (Connection connection = imagelogDataSource.getConnection()) {
            String sql = "SELECT application, servicegroup, service, status, data, datastring, " +
                        "guid, header, headerstring, insert_time " +
                        "FROM imagelog WHERE guid = ? AND COALESCE(NULLIF(TRIM(status), ''), '') = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, guid);
                stmt.setString(2, DecryptionRowKey.normalizeStatus(status));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            
                            // insert_time을 날짜 문자열로 변환
                            if ("insert_time".equals(columnName) && value != null) {
                                long timestamp = ((Number) value).longValue();
                                LocalDateTime dateTime = LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(timestamp),
                                    java.time.ZoneId.systemDefault()
                                );
                                value = dateTime.format(DATE_FORMATTER);
                            }
                            
                            row.put(columnName, value);
                        }
                        
                        // datastring과 headerstring의 JSON 내부 암호화 값 복호화
                        if (row.get("datastring") != null) {
                            String datastring = (String) row.get("datastring");
                            String decryptedDatastring = decryptJsonStringValues(datastring);
                            row.put("datastring", decryptedDatastring);
                        }
                        if (row.get("headerstring") != null) {
                            String headerstring = (String) row.get("headerstring");
                            String decryptedHeaderstring = decryptJsonStringValues(headerstring);
                            row.put("headerstring", decryptedHeaderstring);
                        }
                        
                        log.info("✅ 이미지로그 상세 조회 완료: GUID={}, status={}", guid, status);
                        return row;
                    } else {
                        throw new RuntimeException("이미지로그를 찾을 수 없습니다: guid=" + guid + ", status=" + status);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("이미지로그 상세 조회 중 오류 발생", e);
            throw new RuntimeException("이미지로그 상세 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 단일 로우 복호화 (java_fw_imglog)
     * guid와 status를 함께 사용하여 고유하게 식별
     */
    public Map<String, Object> decryptRow(String logType, String guid, String status) {
        long startTime = System.currentTimeMillis();
        log.info("🔓 단일 로우 복호화 시작: logType={}, guid={}, status={}", logType, guid, status);
        
        if (!"java_fw_imglog".equals(logType)) {
            throw CustomException.badRequest("현재 java_fw_imglog만 지원됩니다.", "UNSUPPORTED_LOG_TYPE");
        }
        String st = DecryptionRowKey.normalizeStatus(status);
        if (st.isEmpty()) {
            throw CustomException.badRequest("java_fw_imglog 복호화에는 status가 필요합니다.", "MISSING_STATUS");
        }
        
        try (Connection connection = imagelogDataSource.getConnection()) {
            String sql = "SELECT application, servicegroup, service, status, data, datastring, " +
                        "guid, header, headerstring, insert_time " +
                        "FROM imagelog WHERE guid = ? AND COALESCE(NULLIF(TRIM(status), ''), '') = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, guid);
                stmt.setString(2, st);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            
                            // insert_time을 날짜 문자열로 변환
                            if ("insert_time".equals(columnName) && value != null) {
                                long timestamp = ((Number) value).longValue();
                                LocalDateTime dateTime = LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(timestamp),
                                    java.time.ZoneId.systemDefault()
                                );
                                value = dateTime.format(DATE_FORMATTER);
                            }
                            
                            row.put(columnName, value);
                        }
                        
                        // data 필드 복호화 (실패 시 클라이언트 복구 가능 오류 — 본문에 내부 메시지 미포함)
                        if (row.get("data") != null) {
                            try {
                                String encryptedData = (String) row.get("data");
                                String decryptedData = cryptoUtil.decryptLogPayload(encryptedData, LogPayloadCryptoVariant.IMAGE_LOG);
                                row.put("decrypted_data", decryptedData);
                                row.put("data_encrypted", true);
                            } catch (Exception e) {
                                log.warn("data 복호화 실패 (GUID: {}): {}", guid, e.toString());
                                throw CustomException.badRequest(
                                        "복호화할 수 없습니다. 암호문 형식이 올바르지 않거나 키가 일치하지 않을 수 있습니다.",
                                        "DECRYPTION_FAILED");
                            }
                        }
                        
                        // header 필드 복호화
                        if (row.get("header") != null) {
                            try {
                                String encryptedHeader = (String) row.get("header");
                                String decryptedHeader = cryptoUtil.decryptLogPayload(encryptedHeader, LogPayloadCryptoVariant.IMAGE_LOG);
                                row.put("decrypted_header", decryptedHeader);
                                row.put("header_encrypted", true);
                            } catch (Exception e) {
                                log.warn("header 복호화 실패 (GUID: {}): {}", guid, e.toString());
                                throw CustomException.badRequest(
                                        "복호화할 수 없습니다. 암호문 형식이 올바르지 않거나 키가 일치하지 않을 수 있습니다.",
                                        "DECRYPTION_FAILED");
                            }
                        }
                        
                        // datastring과 headerstring의 JSON 내부 암호화 값 복호화
                        if (row.get("datastring") != null) {
                            String datastring = (String) row.get("datastring");
                            String decryptedDatastring = decryptJsonStringValues(datastring);
                            row.put("decrypted_datastring", decryptedDatastring);
                        }
                        if (row.get("headerstring") != null) {
                            String headerstring = (String) row.get("headerstring");
                            String decryptedHeaderstring = decryptJsonStringValues(headerstring);
                            row.put("decrypted_headerstring", decryptedHeaderstring);
                        }
                        
                        String rowStatus = (String) row.get("status");
                        long endTime = System.currentTimeMillis();
                        long duration = endTime - startTime;
                        log.info("✅ 단일 로우 복호화 완료: logType={}, guid={}, status={}, 소요시간={}ms", 
                                logType, guid, rowStatus, duration);
                        return row;
                    } else {
                        throw CustomException.notFound("해당 로그 행을 찾을 수 없습니다.", "LOG_ROW_NOT_FOUND");
                    }
                }
            }
        } catch (SQLException e) {
            log.error("단일 로우 복호화 중 오류 발생", e);
            throw CustomException.internalError(
                    "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
                    "INTERNAL_SERVER_ERROR");
        }
    }
    
    /**
     * 복호화된 데이터 조회
     */
    public Map<String, Object> getDecryptedData(String logType, String type, String identifier, String status) {
        long startTime = System.currentTimeMillis();
        log.info("🔓 복호화 요청 시작: logType={}, type={}, identifier={}, status={}", logType, type, identifier, status);
        
        Map<String, Object> logData = getLogDetail(logType, type, identifier, status);
        
        try {
            if ("pb_feplog".equals(logType)) {
                String encryptedRequest = jdbcValueToString(logData.get("request_data"));
                if (encryptedRequest != null && !encryptedRequest.isEmpty()) {
                    String decryptedRequest = cryptoUtil.decryptLogPayload(encryptedRequest, LogPayloadCryptoVariant.PB_FEP);
                    logData.put("decrypted_request_data", decryptedRequest);
                }

                String encryptedResponse = jdbcValueToString(logData.get("response_data"));
                if (encryptedResponse != null && !encryptedResponse.isEmpty()) {
                    String decryptedResponse = cryptoUtil.decryptLogPayload(encryptedResponse, LogPayloadCryptoVariant.PB_FEP);
                    logData.put("decrypted_response_data", decryptedResponse);
                }
                
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                log.info("✅ 복호화 완료: logType={}, type={}, id={}, 소요시간={}ms", 
                        logType, type, logData.get("id"), duration);
            } else if ("java_fw_imglog".equals(logType)) {
                // datastring과 headerstring의 JSON 내부 암호화 값 복호화
                if (logData.get("datastring") != null) {
                    String datastring = (String) logData.get("datastring");
                    String decryptedDatastring = decryptJsonStringValues(datastring);
                    logData.put("datastring", decryptedDatastring);
                }
                if (logData.get("headerstring") != null) {
                    String headerstring = (String) logData.get("headerstring");
                    String decryptedHeaderstring = decryptJsonStringValues(headerstring);
                    logData.put("headerstring", decryptedHeaderstring);
                }
                
                // data 복호화
                if (logData.get("data") != null) {
                    String encryptedData = (String) logData.get("data");
                    String decryptedData = cryptoUtil.decryptLogPayload(encryptedData, LogPayloadCryptoVariant.IMAGE_LOG);
                    logData.put("decrypted_data", decryptedData);
                }
                
                // header 복호화
                if (logData.get("header") != null) {
                    String encryptedHeader = (String) logData.get("header");
                    String decryptedHeader = cryptoUtil.decryptLogPayload(encryptedHeader, LogPayloadCryptoVariant.IMAGE_LOG);
                    logData.put("decrypted_header", decryptedHeader);
                }
                
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                String guid = (String) logData.get("guid");
                log.info("✅ 복호화 완료: logType={}, guid={}, 소요시간={}ms, 데이터크기={}bytes", 
                        logType, guid, duration, 
                        logData.get("data") != null ? ((String) logData.get("data")).length() : 0);
            }
            
            return logData;
            
        } catch (Exception e) {
            log.error("복호화 중 오류 발생", e);
            throw new RuntimeException("복호화에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * JSON 문자열 내부의 암호화된 값([]로 감싸진 값)을 복호화
     *
     * @param jsonString JSON 문자열
     * @return 복호화된 JSON 문자열 (필드 단위 복호화 실패 시 해당 값은 고정 안내 문구로 대체)
     */
    private String decryptJsonStringValues(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return jsonString;
        }
        
        try {
            // JSON 파싱
            JsonNode rootNode = objectMapper.readTree(jsonString);
            
            // 재귀적으로 모든 문자열 값을 확인하고 복호화
            decryptJsonNode(rootNode);
            
            // JSON을 다시 문자열로 변환
            return objectMapper.writeValueAsString(rootNode);
            
        } catch (Exception e) {
            log.warn("JSON 파싱 또는 복호화 실패, 원본 값 반환: {}", e.getMessage());
            return jsonString; // 파싱 실패 시 원본 반환
        }
    }
    
    /**
     * JSON 노드를 재귀적으로 순회하며 암호화된 값 복호화
     */
    private void decryptJsonNode(JsonNode node) {
        if (node == null) {
            return;
        }
        
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    String textValue = value.asText();
                    // []로 감싸진 값인지 확인
                    if (textValue.startsWith("[") && textValue.endsWith("]") && textValue.length() > 2) {
                        String encryptedValue = textValue.substring(1, textValue.length() - 1);
                        try {
                            // 복호화 시도
                            String decryptedValue = cryptoUtil.decryptLogPayload(encryptedValue, LogPayloadCryptoVariant.IMAGE_LOG);
                            // 복호화 성공 시 값 교체
                            objectNode.put(entry.getKey(), decryptedValue);
                            log.debug("✅ JSON 내부 값 복호화 성공: key={}", entry.getKey());
                        } catch (Exception e) {
                            log.debug("복호화 실패, 플레이스홀더로 대체: key={}, error={}", entry.getKey(), e.getMessage());
                            objectNode.put(entry.getKey(), IMAGE_LOG_JSON_DECRYPT_FAILED);
                        }
                    }
                } else if (value.isObject() || value.isArray()) {
                    // 중첩된 객체나 배열인 경우 재귀 호출
                    decryptJsonNode(value);
                }
            });
        } else if (node.isArray()) {
            // 배열의 각 요소 확인
            for (int i = 0; i < node.size(); i++) {
                JsonNode element = node.get(i);
                if (element.isTextual()) {
                    String textValue = element.asText();
                    // []로 감싸진 값인지 확인
                    if (textValue.startsWith("[") && textValue.endsWith("]") && textValue.length() > 2) {
                        String encryptedValue = textValue.substring(1, textValue.length() - 1);
                        try {
                            // 복호화 시도
                            String decryptedValue = cryptoUtil.decryptLogPayload(encryptedValue, LogPayloadCryptoVariant.IMAGE_LOG);
                            // 복호화 성공 시 값 교체
                            ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i, 
                                objectMapper.valueToTree(decryptedValue));
                            log.debug("✅ JSON 배열 내부 값 복호화 성공: index={}", i);
                        } catch (Exception e) {
                            log.debug("복호화 실패, 플레이스홀더로 대체: index={}, error={}", i, e.getMessage());
                            ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i,
                                    objectMapper.valueToTree(IMAGE_LOG_JSON_DECRYPT_FAILED));
                        }
                    }
                } else if (element.isObject() || element.isArray()) {
                    // 중첩된 객체나 배열인 경우 재귀 호출
                    decryptJsonNode(element);
                }
            }
        }
    }
    
    /**
     * 고급 검색 (AST 기반)
     */
    public LogDbSearchResponse advancedSearch(AdvancedSearchRequest request) {
        log.info("🔍 고급 검색 요청: logType={}, filters={}", request.getLogType(), request.getFilters());
        
        if (!"java_fw_imglog".equals(request.getLogType())) {
            throw new RuntimeException("현재 java_fw_imglog만 지원됩니다.");
        }
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection connection = imagelogDataSource.getConnection()) {
            // SQL 쿼리 구성
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT application, servicegroup, service, status, data, datastring, ");
            sql.append("guid, header, headerstring, insert_time ");
            sql.append("FROM imagelog WHERE 1=1 ");
            
            List<Object> params = new ArrayList<>();
            
            // 날짜 범위 필터링 (insert_time은 bigint 타임스탬프)
            if (request.getStartDate() != null && !request.getStartDate().trim().isEmpty()) {
                try {
                    LocalDateTime startDateTime = parseDateTime(request.getStartDate());
                    if (startDateTime != null) {
                        long startTimestamp = startDateTime.atZone(java.time.ZoneId.systemDefault())
                            .toInstant().toEpochMilli();
                        sql.append("AND insert_time >= ? ");
                        params.add(startTimestamp);
                        log.debug("시작 날짜 필터: {} -> {}", request.getStartDate(), startTimestamp);
                    }
                } catch (Exception e) {
                    log.warn("시작 날짜 파싱 실패: {}", request.getStartDate(), e);
                }
            }
            
            if (request.getEndDate() != null && !request.getEndDate().trim().isEmpty()) {
                try {
                    LocalDateTime endDateTime = parseDateTime(request.getEndDate());
                    if (endDateTime != null) {
                        // endDate가 날짜만 있으면 23:59:59.999로 설정
                        if (request.getEndDate().trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
                            endDateTime = endDateTime.toLocalDate().atTime(23, 59, 59, 999_000_000);
                        }
                        long endTimestamp = endDateTime.atZone(java.time.ZoneId.systemDefault())
                            .toInstant().toEpochMilli();
                        sql.append("AND insert_time <= ? ");
                        params.add(endTimestamp);
                        log.debug("종료 날짜 필터: {} -> {}", request.getEndDate(), endTimestamp);
                    }
                } catch (Exception e) {
                    log.warn("종료 날짜 파싱 실패: {}", request.getEndDate(), e);
                }
            }
            
            // AST 기반 필터 처리
            if (request.getFilters() != null && !request.getFilters().isEmpty()) {
                for (FilterCondition filter : request.getFilters()) {
                    String field = filter.getField();
                    String operator = filter.getOperator();
                    Object value = filter.getValue();
                    
                    if (field == null || operator == null || value == null) {
                        continue;
                    }
                    
                    // 필드명 검증 및 SQL 안전 처리
                    String safeField = validateFieldName(field);
                    if (safeField == null) {
                        log.warn("유효하지 않은 필드명: {}", field);
                        continue;
                    }
                    
                    // 연산자별 SQL 조건 추가
                    appendFilterCondition(sql, params, safeField, operator, value);
                }
            }
            
            // 전체 검색 키워드 (queryText) 처리
            if (request.getQueryText() != null && !request.getQueryText().trim().isEmpty()) {
                String queryText = request.getQueryText().trim();
                sql.append("AND (datastring LIKE ? OR headerstring LIKE ?) ");
                params.add("%" + queryText + "%");
                params.add("%" + queryText + "%");
            }
            
            // 정렬 처리
            String sortField = "insert_time";
            String sortDirection = "desc";
            if (request.getSort() != null && !request.getSort().isEmpty()) {
                AdvancedSearchRequest.SortCondition sortCond = request.getSort().get(0);
                String field = validateFieldName(sortCond.getField());
                if (field != null) {
                    sortField = field;
                    sortDirection = sortCond.getDirection() != null ? sortCond.getDirection() : "desc";
                }
            }
            sql.append("ORDER BY ").append(sortField).append(" ").append(sortDirection);
            
            log.debug("실행 SQL: {}", sql.toString());
            log.debug("파라미터: {}", params);
            
            // 전체 개수 조회
            String countSql = "SELECT COUNT(*) FROM (" + sql.toString() + ") as total";
            long totalCount = 0;
            try (PreparedStatement countStmt = connection.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++) {
                    countStmt.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        totalCount = rs.getLong(1);
                    }
                }
            }
            
            // 페이징 적용
            int page = 1;
            int pageSize = 50;
            if (request.getPagination() != null) {
                page = request.getPagination().getPage() != null ? request.getPagination().getPage() : 1;
                pageSize = request.getPagination().getPageSize() != null ? request.getPagination().getPageSize() : 50;
            }
            int offset = (page - 1) * pageSize;
            sql.append(" LIMIT ? OFFSET ?");
            params.add(pageSize);
            params.add(offset);
            
            // 데이터 조회
            try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            
                            // insert_time을 날짜 문자열로 변환
                            if ("insert_time".equals(columnName) && value != null) {
                                long timestamp = ((Number) value).longValue();
                                LocalDateTime dateTime = LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(timestamp),
                                    java.time.ZoneId.systemDefault()
                                );
                                value = dateTime.format(DATE_FORMATTER);
                            }
                            
                            row.put(columnName, value);
                        }
                        // Keep DB ciphertext in datastring/headerstring; decrypt-for-match only elsewhere (req 20260413).
                        sanitizeJavaFwImglogSearchRow(row);
                        results.add(row);
                    }
                }
            }
            
            // 페이징 정보 계산
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            
            log.info("✅ 고급 검색 완료: {}건 중 {}건 반환 (페이지: {}/{})", totalCount, results.size(), page, totalPages);
            
            LogDbSearchResponse.PaginationInfo pagination = new LogDbSearchResponse.PaginationInfo(
                    page, totalPages, totalCount
            );
            
            return new LogDbSearchResponse(results, pagination);
            
        } catch (SQLException e) {
            log.error("고급 검색 중 오류 발생", e);
            throw new RuntimeException("고급 검색 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 날짜 문자열 파싱 (고급 검색용)
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
            
            // yyyy-MM-dd 형식
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return java.time.LocalDate.parse(trimmed).atStartOfDay();
            }
            
            // yyyy-MM-dd HH:mm:ss 형식
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                return LocalDateTime.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            
            return null;
        } catch (Exception e) {
            log.warn("날짜 파싱 실패: {}", dateStr, e);
            return null;
        }
    }
    
    /**
     * 필드명 검증 (SQL Injection 방지)
     */
    private String validateFieldName(String fieldName) {
        // 허용된 필드명 목록
        Set<String> allowedFields = new HashSet<>(Arrays.asList(
            "application", "servicegroup", "service", "status", "guid",
            "datastring", "headerstring", "insert_time", "data", "header"
        ));
        
        if (fieldName != null && allowedFields.contains(fieldName.toLowerCase())) {
            return fieldName.toLowerCase();
        }
        return null;
    }
    
    /**
     * 필터 조건을 SQL에 추가
     */
    private void appendFilterCondition(StringBuilder sql, List<Object> params, 
                                      String field, String operator, Object value) {
        switch (operator) {
            case ":":
            case "=":
                // 포함 또는 일치
                if (field.equals("datastring") || field.equals("headerstring")) {
                    sql.append("AND ").append(field).append(" LIKE ? ");
                    params.add("%" + value.toString() + "%");
                } else {
                    sql.append("AND ").append(field).append(" = ? ");
                    params.add(value);
                }
                break;
                
            case ">=":
                sql.append("AND ").append(field).append(" >= ? ");
                params.add(convertValue(field, value));
                break;
                
            case "<=":
                sql.append("AND ").append(field).append(" <= ? ");
                params.add(convertValue(field, value));
                break;
                
            case ">":
                sql.append("AND ").append(field).append(" > ? ");
                params.add(convertValue(field, value));
                break;
                
            case "<":
                sql.append("AND ").append(field).append(" < ? ");
                params.add(convertValue(field, value));
                break;
                
            case "IN":
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> values = (List<String>) value;
                    if (!values.isEmpty()) {
                        sql.append("AND ").append(field).append(" IN (");
                        for (int i = 0; i < values.size(); i++) {
                            if (i > 0) sql.append(", ");
                            sql.append("?");
                            params.add(values.get(i));
                        }
                        sql.append(") ");
                    }
                }
                break;
                
            case "NOT IN":
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> values = (List<String>) value;
                    if (!values.isEmpty()) {
                        sql.append("AND ").append(field).append(" NOT IN (");
                        for (int i = 0; i < values.size(); i++) {
                            if (i > 0) sql.append(", ");
                            sql.append("?");
                            params.add(values.get(i));
                        }
                        sql.append(") ");
                    }
                }
                break;
                
            case "~":
                // 부분일치
                sql.append("AND ").append(field).append(" LIKE ? ");
                params.add("%" + value.toString() + "%");
                break;
                
            default:
                log.warn("지원하지 않는 연산자: {}", operator);
                break;
        }
    }
    
    /**
     * 필드 타입에 따라 값 변환
     */
    private Object convertValue(String field, Object value) {
        if ("insert_time".equals(field)) {
            // bigint 타임스탬프로 변환
            if (value instanceof String) {
                try {
                    // 날짜 문자열을 파싱하여 타임스탬프로 변환
                    LocalDateTime dateTime = LocalDateTime.parse(value.toString().replace(" ", "T"), 
                        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    return dateTime.atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                } catch (Exception e) {
                    log.warn("날짜 파싱 실패: {}", value, e);
                    return value;
                }
            } else if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }
        return value;
    }
}

