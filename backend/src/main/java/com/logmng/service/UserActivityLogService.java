package com.logmng.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.dto.request.UserActivityLogSearchRequest;
import com.logmng.dto.response.UserActivityLogResponse;
import com.logmng.exception.CustomException;
import com.logmng.util.ScopeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 사용자 활동 이력 서비스
 */
@Service
public class UserActivityLogService {
    
    private static final Logger log = LoggerFactory.getLogger(UserActivityLogService.class);
    
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public UserActivityLogService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 사용자 활동 이력 저장
     */
    public void saveActivityLog(String userId, String username, String actionType, 
                                Map<String, Object> actionDetail, String ipAddress, 
                                String userAgent, String requestMethod, String requestPath,
                                String requestParams, Integer responseStatus, 
                                Integer responseTimeMs, Boolean success, String errorMessage) {
        
        try (Connection connection = dataSource.getConnection()) {
            String sql = "INSERT INTO user_activity_log " +
                        "(user_id, username, action_type, action_detail, ip_address, user_agent, " +
                        "request_method, request_path, request_params, response_status, " +
                        "response_time_ms, success, error_message, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                int paramIndex = 1;
                stmt.setString(paramIndex++, userId);
                stmt.setString(paramIndex++, username);
                stmt.setString(paramIndex++, actionType);
                
                // action_detail을 JSON 문자열로 변환
                String actionDetailJson = null;
                if (actionDetail != null && !actionDetail.isEmpty()) {
                    try {
                        actionDetailJson = objectMapper.writeValueAsString(actionDetail);
                    } catch (Exception e) {
                        log.warn("action_detail JSON 변환 실패: {}", e.getMessage());
                    }
                }
                stmt.setString(paramIndex++, actionDetailJson);
                
                stmt.setString(paramIndex++, ipAddress);
                stmt.setString(paramIndex++, userAgent);
                stmt.setString(paramIndex++, requestMethod);
                stmt.setString(paramIndex++, requestPath);
                
                // request_params를 JSON 문자열로 변환
                String requestParamsJson = null;
                if (requestParams != null && !requestParams.isEmpty()) {
                    requestParamsJson = requestParams;
                }
                stmt.setString(paramIndex++, requestParamsJson);
                
                if (responseStatus != null) {
                    stmt.setInt(paramIndex++, responseStatus);
                } else {
                    stmt.setNull(paramIndex++, Types.INTEGER);
                }
                
                if (responseTimeMs != null) {
                    stmt.setInt(paramIndex++, responseTimeMs);
                } else {
                    stmt.setNull(paramIndex++, Types.INTEGER);
                }
                
                stmt.setBoolean(paramIndex++, success != null ? success : true);
                stmt.setString(paramIndex++, errorMessage);
                
                stmt.executeUpdate();
                log.debug("✅ 사용자 활동 이력 저장 완료: userId={}, actionType={}", userId, actionType);
            }
        } catch (SQLException e) {
            log.error("❌ 사용자 활동 이력 저장 실패: userId={}, actionType={}", userId, actionType, e);
            // 이력 저장 실패는 시스템에 치명적이지 않으므로 예외를 던지지 않음
        }
    }
    
    /**
     * 사용자 활동 이력 검색
     */
    public UserActivityLogResponse searchActivityLogs(UserActivityLogSearchRequest request) {
        String normalizedUserId = ScopeHelper.normalizeOptionalParam(request.getUserId());
        String normalizedUsername = ScopeHelper.normalizeOptionalParam(request.getUsername());
        String normalizedDepartment = ScopeHelper.normalizeDepartmentFilter(request.getDepartment());
        String normalizedIpAddress = ScopeHelper.normalizeOptionalParam(request.getIpAddress());
        List<String> normalizedAllowedUserIds = ScopeHelper.normalizeAllowedUserIds(request.getAllowedUserIds());

        log.info("🔍 사용자 활동 이력 검색 요청: userId={}, actionType={}, startDate={}, endDate={}",
                normalizedUserId, request.getActionType(), request.getStartDate(), request.getEndDate());
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (Connection connection = dataSource.getConnection()) {
            boolean useDepartmentJoin = normalizedDepartment != null;
            String prefix = useDepartmentJoin ? "u." : "";

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ").append(prefix).append("id, ").append(prefix).append("user_id, ").append(prefix).append("username, ").append(prefix).append("action_type, ").append(prefix).append("action_detail, ").append(prefix).append("ip_address, ");
            sql.append(prefix).append("user_agent, ").append(prefix).append("request_method, ").append(prefix).append("request_path, ").append(prefix).append("request_params, ");
            sql.append(prefix).append("response_status, ").append(prefix).append("response_time_ms, ").append(prefix).append("success, ").append(prefix).append("error_message, ");
            sql.append(prefix).append("created_at, ").append(prefix).append("updated_at ");
            if (useDepartmentJoin) {
                sql.append("FROM user_activity_log u INNER JOIN app_user a ON u.user_id = a.username WHERE 1=1 ");
            } else {
                sql.append("FROM user_activity_log WHERE 1=1 ");
            }

            List<Object> params = new ArrayList<>();

            // 날짜 조건: DATE(created_at)로 비교하여 타임존/시각 경계 이슈 방지
            LocalDate startDate = request.getStartDateAsLocalDate();
            LocalDate endDate = request.getEndDateAsLocalDate();

            if (startDate != null) {
                sql.append("AND DATE(").append(prefix).append("created_at) >= ? ");
                params.add(java.sql.Date.valueOf(startDate));
            }
            if (endDate != null) {
                sql.append("AND DATE(").append(prefix).append("created_at) <= ? ");
                params.add(java.sql.Date.valueOf(endDate));
            }

            // 사용자 ID 조건: allowedUserIds(scope=team) is always a hard boundary; userId can only narrow within it.
            if (normalizedAllowedUserIds != null) {
                if (normalizedAllowedUserIds.isEmpty()) {
                    sql.append("AND 1 = 0 ");
                } else if (normalizedUserId != null) {
                    if (normalizedAllowedUserIds.contains(normalizedUserId)) {
                        sql.append("AND ").append(prefix).append("user_id = ? ");
                        params.add(normalizedUserId);
                    } else {
                        sql.append("AND 1 = 0 ");
                    }
                } else if (normalizedAllowedUserIds.size() == 1) {
                    sql.append("AND ").append(prefix).append("user_id = ? ");
                    params.add(normalizedAllowedUserIds.get(0));
                } else {
                    String placeholders = String.join(",", Collections.nCopies(normalizedAllowedUserIds.size(), "?"));
                    sql.append("AND ").append(prefix).append("user_id IN (").append(placeholders).append(") ");
                    params.addAll(normalizedAllowedUserIds);
                }
            } else if (normalizedUserId != null) {
                sql.append("AND ").append(prefix).append("user_id = ? ");
                params.add(normalizedUserId);
            }

            // 부서 조건: app_user 조인 시 department_code 필터
            if (useDepartmentJoin) {
                sql.append("AND a.department_code = ? ");
                params.add(normalizedDepartment);
            }

            // 사용자명 조건
            if (normalizedUsername != null) {
                sql.append("AND ").append(prefix).append("username LIKE ? ");
                params.add("%" + normalizedUsername + "%");
            }

            // 액션 타입 조건
            if (request.getActionType() != null && !request.getActionType().trim().isEmpty()) {
                sql.append("AND ").append(prefix).append("action_type = ? ");
                params.add(request.getActionType());
            }

            // IP 주소 조건
            if (normalizedIpAddress != null) {
                sql.append("AND ").append(prefix).append("ip_address = ? ");
                params.add(normalizedIpAddress);
            }

            // 정렬
            String sortField = request.getSortField();
            String sortDirection = request.getSortDirection();
            sql.append("ORDER BY ").append(prefix).append(sortField).append(" ").append(sortDirection);
            
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
            int page = request.getPage();
            int pageSize = request.getPageSize();
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
                        
                        // action_detail JSON 파싱 (필요 시)
                        if (row.get("action_detail") != null && row.get("action_detail") instanceof String) {
                            try {
                                String actionDetailJson = (String) row.get("action_detail");
                                Map<String, Object> actionDetail = objectMapper.readValue(
                                        actionDetailJson,
                                        new TypeReference<Map<String, Object>>() {});
                                row.put("action_detail", actionDetail);
                            } catch (Exception e) {
                                log.debug("action_detail JSON 파싱 실패: {}", e.getMessage());
                            }
                        }
                        
                        results.add(row);
                    }
                }
            }
            
            // 페이징 정보 계산
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            
            log.info("✅ 사용자 활동 이력 검색 완료: {}건 중 {}건 반환 (페이지: {}/{})", 
                    totalCount, results.size(), page, totalPages);
            
            UserActivityLogResponse.PaginationInfo pagination = 
                    new UserActivityLogResponse.PaginationInfo(page, totalPages, totalCount);
            
            return new UserActivityLogResponse(results, pagination);
            
        } catch (SQLException e) {
            log.error("❌ 사용자 활동 이력 검색 중 오류 발생", e);
            throw new RuntimeException("사용자 활동 이력 검색 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 사용자 활동 이력 상세 조회.
     * @param currentUserIdForOwnership when scope='self', verify row.user_id == currentUserIdForOwnership; 403 if not owner.
     * @param allowedUserIdsForTeam when scope='team', row.user_id must be in this list; ignored if null/empty.
     */
    public Map<String, Object> getActivityLogDetail(Long id, String currentUserIdForOwnership, List<String> allowedUserIdsForTeam) {
        log.info("🔍 사용자 활동 이력 상세 조회: id={}", id);
        
        try (Connection connection = dataSource.getConnection()) {
            String sql = "SELECT id, user_id, username, action_type, action_detail, ip_address, " +
                        "user_agent, request_method, request_path, request_params, " +
                        "response_status, response_time_ms, success, error_message, " +
                        "created_at, updated_at " +
                        "FROM user_activity_log WHERE id = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, id);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String rowUserId = rs.getString("user_id");
                        if (currentUserIdForOwnership != null && !currentUserIdForOwnership.equals(rowUserId)) {
                            throw CustomException.forbidden("다른 사용자의 활동 이력은 조회할 수 없습니다.", "FORBIDDEN");
                        }
                        if (allowedUserIdsForTeam != null && !allowedUserIdsForTeam.isEmpty() && !allowedUserIdsForTeam.contains(rowUserId)) {
                            throw CustomException.forbidden("다른 사용자의 활동 이력은 조회할 수 없습니다.", "FORBIDDEN");
                        }
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
                        
                        // action_detail JSON 파싱
                        if (row.get("action_detail") != null && row.get("action_detail") instanceof String) {
                            try {
                                String actionDetailJson = (String) row.get("action_detail");
                                Map<String, Object> actionDetail = objectMapper.readValue(
                                        actionDetailJson,
                                        new TypeReference<Map<String, Object>>() {});
                                row.put("action_detail", actionDetail);
                            } catch (Exception e) {
                                log.debug("action_detail JSON 파싱 실패: {}", e.getMessage());
                            }
                        }
                        
                        log.info("✅ 사용자 활동 이력 상세 조회 완료: ID={}", id);
                        return row;
                    } else {
                        throw new RuntimeException("활동 이력을 찾을 수 없습니다: id=" + id);
                    }
                }
            }
        } catch (CustomException e) {
            throw e;
        } catch (SQLException e) {
            log.error("❌ 사용자 활동 이력 상세 조회 중 오류 발생", e);
            throw new RuntimeException("사용자 활동 이력 상세 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
}





