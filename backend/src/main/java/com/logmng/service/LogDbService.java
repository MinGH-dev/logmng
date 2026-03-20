package com.logmng.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.logmng.dto.request.AdvancedSearchRequest;
import com.logmng.dto.request.FilterCondition;
import com.logmng.dto.request.LogDbSearchRequest;
import com.logmng.dto.response.LogDbSearchResponse;
import com.logmng.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
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

    private static final Logger log = LoggerFactory.getLogger(LogDbService.class);

    private final DataSource primaryDataSource;
    private final DataSource imagelogDataSource;
    private final CryptoUtil cryptoUtil;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public LogDbService(@Qualifier("dataSource") DataSource primaryDataSource,
                        @Qualifier("imagelogDataSource") DataSource imagelogDataSource,
                        CryptoUtil cryptoUtil) {
        this.primaryDataSource = primaryDataSource;
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
    
    /**
     * PB FEP 로그 검색 (pb_send, pb_recv)
     */
    private LogDbSearchResponse searchPbFeplog(LogDbSearchRequest request) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection connection = primaryDataSource.getConnection()) {
            // SQL 쿼리 구성
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT id, log_timestamp, media_code, tr_code, user_id, ip_address, ");
            sql.append("user_agent, request_data, response_data, status_code, response_time, ");
            sql.append("error_message, session_id, device_type, created_at, updated_at, ");
            sql.append("'send' as log_type ");
            sql.append("FROM pb_send WHERE 1=1 ");
            
            List<Object> params = new ArrayList<>();
            
        // 날짜 조건
        LocalDateTime startDateTime = request.getStartDateAsDateTime();
        LocalDateTime endDateTime = request.getEndDateAsDateTime();
        
        if (startDateTime != null) {
            sql.append("AND log_timestamp >= ? ");
            params.add(Timestamp.valueOf(startDateTime));
        }
        if (endDateTime != null) {
            sql.append("AND log_timestamp <= ? ");
            params.add(Timestamp.valueOf(endDateTime));
        }
            
            // 매체코드 조건
            if (request.getMediaCode() != null && !request.getMediaCode().trim().isEmpty()) {
                sql.append("AND media_code = ? ");
                params.add(request.getMediaCode());
            }
            
            // TR 코드 조건
            if (request.getTrCode() != null && !request.getTrCode().trim().isEmpty()) {
                sql.append("AND tr_code = ? ");
                params.add(request.getTrCode());
            }
            
            // 사용자 ID 조건
            if (request.getLoginId() != null && !request.getLoginId().trim().isEmpty()) {
                sql.append("AND user_id = ? ");
                params.add(request.getLoginId());
            }
            
            // UNION ALL로 pb_recv도 포함
            sql.append("UNION ALL ");
            sql.append("SELECT id, log_timestamp, media_code, tr_code, user_id, ip_address, ");
            sql.append("user_agent, request_data, response_data, status_code, response_time, ");
            sql.append("error_message, session_id, device_type, created_at, updated_at, ");
            sql.append("'recv' as log_type ");
            sql.append("FROM pb_recv WHERE 1=1 ");
            
            // 동일한 조건 적용
            if (startDateTime != null) {
                sql.append("AND log_timestamp >= ? ");
                params.add(Timestamp.valueOf(startDateTime));
            }
            if (endDateTime != null) {
                sql.append("AND log_timestamp <= ? ");
                params.add(Timestamp.valueOf(endDateTime));
            }
            if (request.getMediaCode() != null && !request.getMediaCode().trim().isEmpty()) {
                sql.append("AND media_code = ? ");
                params.add(request.getMediaCode());
            }
            if (request.getTrCode() != null && !request.getTrCode().trim().isEmpty()) {
                sql.append("AND tr_code = ? ");
                params.add(request.getTrCode());
            }
            if (request.getLoginId() != null && !request.getLoginId().trim().isEmpty()) {
                sql.append("AND user_id = ? ");
                params.add(request.getLoginId());
            }
            
            // 정렬 (prc_time을 log_timestamp로 매핑)
            String sortField = request.getSortField() != null ? request.getSortField() : "log_timestamp";
            // 프론트엔드에서 보내는 prc_time을 log_timestamp로 변환
            if ("prc_time".equalsIgnoreCase(sortField)) {
                sortField = "log_timestamp";
            }
            String sortDirection = request.getSortDirection() != null ? request.getSortDirection() : "desc";
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
                        if (request.getDecryptData() && !request.getKeywords().isEmpty()) {
                            try {
                                // request_data 복호화
                                if (row.get("request_data") != null) {
                                    String encryptedRequest = (String) row.get("request_data");
                                    String decryptedRequest = cryptoUtil.decrypt(encryptedRequest);
                                    row.put("decrypted_request_data", decryptedRequest);
                                }
                                // response_data 복호화
                                if (row.get("response_data") != null) {
                                    String encryptedResponse = (String) row.get("response_data");
                                    String decryptedResponse = cryptoUtil.decrypt(encryptedResponse);
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
            
            // datastring, headerstring, keywords 검색은 암호화된 값도 복호화하여 검색해야 하므로
            // SQL에서는 제외하고 나중에 애플리케이션 레벨에서 필터링 처리
            boolean hasDatastringSearch = request.getDatastring() != null && !request.getDatastring().trim().isEmpty();
            boolean hasHeaderstringSearch = request.getHeaderstring() != null && !request.getHeaderstring().trim().isEmpty();
            boolean hasKeywordsSearch = request.getKeywords() != null && !request.getKeywords().isEmpty();
            log.debug("image log filter flags: hasDatastringSearch={}, hasHeaderstringSearch={}, hasKeywordsSearch={}",
                    hasDatastringSearch, hasHeaderstringSearch, hasKeywordsSearch);
            
            // 정렬 (이미지로그는 insert_time 사용)
            String sortField = request.getSortField() != null ? request.getSortField() : "insert_time";
            // 프론트엔드에서 보내는 필드명을 실제 컬럼명으로 매핑
            if ("prc_time".equalsIgnoreCase(sortField) || "log_timestamp".equalsIgnoreCase(sortField)) {
                sortField = "insert_time";
            }
            String sortDirection = request.getSortDirection() != null ? request.getSortDirection() : "desc";
            sql.append("ORDER BY ").append(sortField).append(" ").append(sortDirection);
            
            log.debug("실행 SQL: {}", sql.toString());
            log.debug("파라미터: {}", params);

            int page = request.getPage() != null ? request.getPage() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
            int offset = (page - 1) * pageSize;
            boolean needsFiltering = hasDatastringSearch || hasHeaderstringSearch || hasKeywordsSearch;

            if (needsFiltering) {
                // Per req 20260318: when data/header/keyword filters present, fetch larger set (capped) then filter then paginate.
                String prefetchSql = sql.toString() + " LIMIT ?";
                List<Object> prefetchParams = new ArrayList<>(params);
                prefetchParams.add(IMGLOG_FILTER_PREFETCH_CAP);
                try (PreparedStatement stmt = connection.prepareStatement(prefetchSql)) {
                    for (int i = 0; i < prefetchParams.size(); i++) {
                        stmt.setObject(i + 1, prefetchParams.get(i));
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        results = readImageLogResultSet(rs, request);
                    }
                }
                int prefetchN = results.size();
                log.debug("image log prefetch rows before in-memory filter: {}", prefetchN);
                // Temporary INFO for diagnosis only: remove or downgrade to DEBUG after root cause found (req 20260318 follow-up).
                log.info("[DIAG] image log filter path: prefetch SQL returned N={} rows", prefetchN);

                List<Map<String, Object>> filteredResults = filterImageLogRowsByDataHeaderKeywords(results, request,
                        hasDatastringSearch, hasHeaderstringSearch, hasKeywordsSearch);
                int filteredM = filteredResults.size();
                log.debug("image log rows after in-memory filter: {}", filteredM);
                log.info("[DIAG] image log filter path: filterImageLogRowsByDataHeaderKeywords returned M={} rows (if N>0 and M=0, check filter/decrypt or date range)", filteredM);

                long finalCount = filteredResults.size();
                int totalPages = (int) Math.ceil((double) finalCount / pageSize);
                int fromIndex = (page - 1) * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, filteredResults.size());
                List<Map<String, Object>> pageResults = fromIndex >= filteredResults.size()
                        ? new ArrayList<>()
                        : new ArrayList<>(filteredResults.subList(fromIndex, toIndex));

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
                    results = readImageLogResultSet(rs, request);
                }
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
     * Reads imagelog ResultSet into list of row maps (insert_time formatted as string). Used by searchJavaFwImglog.
     */
    private List<Map<String, Object>> readImageLogResultSet(ResultSet rs, LogDbSearchRequest request) throws SQLException {
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
            if (request.getDecryptData() != null && request.getDecryptData()
                    && request.getKeywords() != null && !request.getKeywords().isEmpty()) {
                try {
                    if (row.get("data") != null) {
                        String encryptedData = (String) row.get("data");
                        String decryptedData = cryptoUtil.decrypt(encryptedData);
                        row.put("decrypted_data", decryptedData);
                    }
                    if (row.get("header") != null) {
                        String encryptedHeader = (String) row.get("header");
                        String decryptedHeader = cryptoUtil.decrypt(encryptedHeader);
                        row.put("decrypted_header", decryptedHeader);
                    }
                } catch (Exception e) {
                    log.warn("복호화 실패 (GUID: {}): {}", row.get("guid"), e.getMessage());
                    row.put("decrypted_data", "복호화 실패");
                    row.put("decrypted_header", "복호화 실패");
                }
            }
            list.add(row);
        }
        return list;
    }

    /**
     * Filters imagelog rows by datastring, headerstring, and keywords (and decrypted content when present). Per req 20260318.
     */
    private List<Map<String, Object>> filterImageLogRowsByDataHeaderKeywords(List<Map<String, Object>> rows,
            LogDbSearchRequest request, boolean hasDatastringSearch, boolean hasHeaderstringSearch, boolean hasKeywordsSearch) {
        List<Map<String, Object>> filteredResults = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            boolean matches = true;
            String datastring = (String) row.get("datastring");
            String headerstring = (String) row.get("headerstring");

            if (hasDatastringSearch) {
                String searchTerm = request.getDatastring().trim();
                boolean datastringMatches = false;
                boolean matchedInEncrypted = false;
                if (datastring != null && datastring.contains(searchTerm)) {
                    datastringMatches = true;
                }
                if (!datastringMatches && datastring != null && datastring.contains("[")) {
                    try {
                        String decryptedDatastring = decryptJsonStringValues(datastring);
                        if (decryptedDatastring.contains(searchTerm)) {
                            datastringMatches = true;
                            matchedInEncrypted = true;
                        }
                    } catch (Exception e) {
                        log.debug("datastring search decrypt failed: {}", e.getMessage());
                    }
                }
                if (!datastringMatches) {
                    matches = false;
                } else if (matchedInEncrypted) {
                    row.put("_datastring_has_encrypted_match", true);
                }
            }

            if (hasHeaderstringSearch && matches) {
                String searchTerm = request.getHeaderstring().trim();
                boolean headerstringMatches = false;
                boolean matchedInEncrypted = false;
                if (headerstring != null && headerstring.contains(searchTerm)) {
                    headerstringMatches = true;
                }
                if (!headerstringMatches && headerstring != null && headerstring.contains("[")) {
                    try {
                        String decryptedHeaderstring = decryptJsonStringValues(headerstring);
                        if (decryptedHeaderstring.contains(searchTerm)) {
                            headerstringMatches = true;
                            matchedInEncrypted = true;
                        }
                    } catch (Exception e) {
                        log.debug("headerstring search decrypt failed: {}", e.getMessage());
                    }
                }
                if (!headerstringMatches) {
                    matches = false;
                } else if (matchedInEncrypted) {
                    row.put("_headerstring_has_encrypted_match", true);
                }
            }

            if (hasKeywordsSearch && matches) {
                boolean matchesKeyword = false;
                boolean matchedInEncryptedDatastring = false;
                boolean matchedInEncryptedHeaderstring = false;
                for (String keyword : request.getKeywords()) {
                    if ((datastring != null && datastring.contains(keyword))
                            || (headerstring != null && headerstring.contains(keyword))) {
                        matchesKeyword = true;
                        break;
                    }
                    if (datastring != null && datastring.contains("[")) {
                        try {
                            String decryptedDatastring = decryptJsonStringValues(datastring);
                            if (decryptedDatastring.contains(keyword)) {
                                matchesKeyword = true;
                                matchedInEncryptedDatastring = true;
                                break;
                            }
                        } catch (Exception e) {
                            log.debug("keyword search decrypt failed: {}", e.getMessage());
                        }
                    }
                    if (headerstring != null && headerstring.contains("[")) {
                        try {
                            String decryptedHeaderstring = decryptJsonStringValues(headerstring);
                            if (decryptedHeaderstring.contains(keyword)) {
                                matchesKeyword = true;
                                matchedInEncryptedHeaderstring = true;
                                break;
                            }
                        } catch (Exception e) {
                            log.debug("keyword search decrypt failed: {}", e.getMessage());
                        }
                    }
                }
                if (!matchesKeyword) {
                    matches = false;
                } else {
                    if (matchedInEncryptedDatastring) {
                        row.put("_datastring_has_encrypted_match", true);
                    }
                    if (matchedInEncryptedHeaderstring) {
                        row.put("_headerstring_has_encrypted_match", true);
                    }
                }
            }

            if (matches) {
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
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        try (Connection connection = imagelogDataSource.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT guid, application, servicegroup FROM imagelog WHERE guid IN (");
            for (int i = 0; i < guids.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("?");
            }
            sql.append(")");
            try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < guids.size(); i++) {
                    stmt.setString(i + 1, guids.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String guid = rs.getString("guid");
                        if (guid == null) continue;
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("application", rs.getString("application"));
                        row.put("serviceGroup", rs.getString("servicegroup"));
                        result.put(guid, row);
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("imagelog resolution by guids failed (returning empty map): {}", e.getMessage());
            return Collections.emptyMap();
        }
        return result;
    }

    /**
     * DB 로그 상세 조회
     */
    public Map<String, Object> getLogDetail(String logType, String type, String identifier) {
        log.info("🔍 DB 로그 상세 조회: logType={}, type={}, identifier={}", logType, type, identifier);
        
        if ("pb_feplog".equals(logType)) {
            Long id = Long.parseLong(identifier);
            return getPbFeplogDetail(type, id);
        } else if ("java_fw_imglog".equals(logType)) {
            return getJavaFwImglogDetail(identifier); // guid 사용
        } else {
            throw new RuntimeException("지원하지 않는 로그 타입입니다: " + logType);
        }
    }
    
    /**
     * PB FEP 로그 상세 조회
     */
    private Map<String, Object> getPbFeplogDetail(String type, Long id) {
        String tableName = "send".equalsIgnoreCase(type) ? "pb_send" : "pb_recv";
        
        try (Connection connection = primaryDataSource.getConnection()) {
            String sql = "SELECT id, log_timestamp, media_code, tr_code, user_id, ip_address, " +
                        "user_agent, request_data, response_data, status_code, response_time, " +
                        "error_message, session_id, device_type, created_at, updated_at " +
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
     * Java FW Image 로그 상세 조회 (guid로 조회)
     */
    private Map<String, Object> getJavaFwImglogDetail(String guid) {
        log.info("🔍 이미지로그 상세 조회: guid={}", guid);
        
        try (Connection connection = imagelogDataSource.getConnection()) {
            String sql = "SELECT application, servicegroup, service, status, data, datastring, " +
                        "guid, header, headerstring, insert_time " +
                        "FROM imagelog WHERE guid = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, guid);
                
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
                        
                        log.info("✅ 이미지로그 상세 조회 완료: GUID={}", guid);
                        return row;
                    } else {
                        throw new RuntimeException("이미지로그를 찾을 수 없습니다: guid=" + guid);
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
            throw new RuntimeException("현재 java_fw_imglog만 지원됩니다.");
        }
        
        try (Connection connection = imagelogDataSource.getConnection()) {
            String sql = "SELECT application, servicegroup, service, status, data, datastring, " +
                        "guid, header, headerstring, insert_time " +
                        "FROM imagelog WHERE guid = ?";
            
            // status가 제공된 경우 WHERE 조건에 추가
            if (status != null && !status.trim().isEmpty()) {
                sql += " AND status = ?";
            }
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, guid);
                if (status != null && !status.trim().isEmpty()) {
                    stmt.setString(2, status);
                }
                
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
                        
                        // data 필드 복호화
                        if (row.get("data") != null) {
                            try {
                                String encryptedData = (String) row.get("data");
                                String decryptedData = cryptoUtil.decrypt(encryptedData);
                                row.put("decrypted_data", decryptedData);
                                row.put("data_encrypted", true);
                            } catch (Exception e) {
                                log.warn("data 복호화 실패 (GUID: {}): {}", guid, e.getMessage());
                                row.put("decrypted_data", "복호화 실패: " + e.getMessage());
                                row.put("data_encrypted", true);
                            }
                        }
                        
                        // header 필드 복호화
                        if (row.get("header") != null) {
                            try {
                                String encryptedHeader = (String) row.get("header");
                                String decryptedHeader = cryptoUtil.decrypt(encryptedHeader);
                                row.put("decrypted_header", decryptedHeader);
                                row.put("header_encrypted", true);
                            } catch (Exception e) {
                                log.warn("header 복호화 실패 (GUID: {}): {}", guid, e.getMessage());
                                row.put("decrypted_header", "복호화 실패: " + e.getMessage());
                                row.put("header_encrypted", true);
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
                        throw new RuntimeException("이미지로그를 찾을 수 없습니다: guid=" + guid + 
                                (status != null ? ", status=" + status : ""));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("단일 로우 복호화 중 오류 발생", e);
            throw new RuntimeException("단일 로우 복호화 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 복호화된 데이터 조회
     */
    public Map<String, Object> getDecryptedData(String logType, String type, String identifier) {
        long startTime = System.currentTimeMillis();
        log.info("🔓 복호화 요청 시작: logType={}, type={}, identifier={}", logType, type, identifier);
        
        Map<String, Object> logData = getLogDetail(logType, type, identifier);
        
        try {
            if ("pb_feplog".equals(logType)) {
                // request_data 복호화
                if (logData.get("request_data") != null) {
                    String encryptedRequest = (String) logData.get("request_data");
                    String decryptedRequest = cryptoUtil.decrypt(encryptedRequest);
                    logData.put("decrypted_request_data", decryptedRequest);
                }
                
                // response_data 복호화
                if (logData.get("response_data") != null) {
                    String encryptedResponse = (String) logData.get("response_data");
                    String decryptedResponse = cryptoUtil.decrypt(encryptedResponse);
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
                    String decryptedData = cryptoUtil.decrypt(encryptedData);
                    logData.put("decrypted_data", decryptedData);
                }
                
                // header 복호화
                if (logData.get("header") != null) {
                    String encryptedHeader = (String) logData.get("header");
                    String decryptedHeader = cryptoUtil.decrypt(encryptedHeader);
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
     * @return 복호화된 JSON 문자열 (복호화 실패 시 원본 값 유지)
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
                            String decryptedValue = cryptoUtil.decrypt(encryptedValue);
                            // 복호화 성공 시 값 교체
                            objectNode.put(entry.getKey(), decryptedValue);
                            log.debug("✅ JSON 내부 값 복호화 성공: key={}", entry.getKey());
                        } catch (Exception e) {
                            // 복호화 실패 시 원본 값 유지
                            log.debug("복호화 실패, 원본 값 유지: key={}, error={}", entry.getKey(), e.getMessage());
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
                            String decryptedValue = cryptoUtil.decrypt(encryptedValue);
                            // 복호화 성공 시 값 교체
                            ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i, 
                                objectMapper.valueToTree(decryptedValue));
                            log.debug("✅ JSON 배열 내부 값 복호화 성공: index={}", i);
                        } catch (Exception e) {
                            // 복호화 실패 시 원본 값 유지
                            log.debug("복호화 실패, 원본 값 유지: index={}, error={}", i, e.getMessage());
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
                        
                        // 복호화 옵션이 활성화된 경우
                        if (request.getDecryptData() != null && request.getDecryptData()) {
                            try {
                                if (row.get("data") != null) {
                                    String encryptedData = (String) row.get("data");
                                    String decryptedData = cryptoUtil.decrypt(encryptedData);
                                    row.put("decrypted_data", decryptedData);
                                }
                                if (row.get("header") != null) {
                                    String encryptedHeader = (String) row.get("header");
                                    String decryptedHeader = cryptoUtil.decrypt(encryptedHeader);
                                    row.put("decrypted_header", decryptedHeader);
                                }
                            } catch (Exception e) {
                                log.warn("복호화 실패 (GUID: {}): {}", row.get("guid"), e.getMessage());
                            }
                        }
                        
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

