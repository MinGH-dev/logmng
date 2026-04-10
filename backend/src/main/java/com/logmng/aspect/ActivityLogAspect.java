package com.logmng.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.activity.ActivityAuditDetailEnricher;
import com.logmng.annotation.ActivityLog;
import com.logmng.dto.DecryptionRowKey;
import com.logmng.dto.response.LoginResponse;
import com.logmng.service.AuthService;
import com.logmng.service.PermissionGroupService;
import com.logmng.service.UserActivityLogService;
import com.logmng.util.IpUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 활동 이력 자동 기록 AOP Aspect
 */
@Aspect
@Component
public class ActivityLogAspect {
    
    private static final Logger log = LoggerFactory.getLogger(ActivityLogAspect.class);
    
    private final UserActivityLogService userActivityLogService;
    private final AuthService authService;
    private final PermissionGroupService permissionGroupService;
    private final IpUtil ipUtil;
    private final ObjectMapper objectMapper;
    
    public ActivityLogAspect(
            UserActivityLogService userActivityLogService,
            AuthService authService,
            PermissionGroupService permissionGroupService,
            IpUtil ipUtil) {
        this.userActivityLogService = userActivityLogService;
        this.authService = authService;
        this.permissionGroupService = permissionGroupService;
        this.ipUtil = ipUtil;
        this.objectMapper = new ObjectMapper();
    }
    
    @Around("@annotation(com.logmng.annotation.ActivityLog)")
    public Object logActivity(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        Integer responseStatus = 200;
        
        Object methodResult = null;
        try {
            // 메서드 실행
            methodResult = joinPoint.proceed();
            return methodResult;
        } catch (Throwable e) {
            success = false;
            errorMessage = e.getMessage();
            responseStatus = 500;
            throw e;
        } finally {
            long endTime = System.currentTimeMillis();
            int responseTimeMs = (int) (endTime - startTime);
            
            // 로깅 로직을 별도 메서드로 분리하여 finally 블록에서 return 문제 해결
            logActivityInternal(joinPoint, methodResult, responseStatus, responseTimeMs, success, errorMessage);
        }
    }
    
    /**
     * 활동 이력 기록 내부 로직
     */
    private void logActivityInternal(ProceedingJoinPoint joinPoint, Object methodResult, 
                                     Integer responseStatus, int responseTimeMs, 
                                     boolean success, String errorMessage) {
        try {
            // 어노테이션 정보 가져오기
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            ActivityLog activityLog = method.getAnnotation(ActivityLog.class);
            
            // 어노테이션이 없으면 로깅하지 않음
            if (activityLog == null) {
                return;
            }
            
            // HTTP 요청 정보 가져오기
            ServletRequestAttributes attributes = 
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            // HTTP 요청 정보가 없으면 로깅하지 않음
            if (attributes == null) {
                log.debug("HTTP 요청 정보를 가져올 수 없습니다");
                return;
            }
            
            HttpServletRequest request = attributes.getRequest();
            
            // 사용자 정보 (AuthService가 세션 userId(Long)를 username으로 해석; user_activity_log.user_id = app_user.username)
            String userId = getUserId(request);
            String username = getUsername(request);
            
            // 액션 타입 결정
            String actionType = activityLog.actionType();
            if (actionType == null || actionType.isEmpty()) {
                actionType = generateActionType(method.getName());
            }
            
            // action_detail 구성
            Map<String, Object> actionDetail = new HashMap<>();
            
            if (activityLog.includeParams()) {
                // 요청 파라미터 추가
                Map<String, Object> params = new HashMap<>();
                String[] paramNames = signature.getParameterNames();
                Object[] args = joinPoint.getArgs();
                
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    // 민감한 정보 제외 (비밀번호 등)
                    if (isSensitiveField(paramNames[i])) {
                        continue;
                    }
                    // Early replacement: never pass Servlet API to ObjectMapper (RequestFacade.getHeaderNames()
                    // returns NamesEnumerator; getParts()/getAsyncContext() throw for non-multipart/non-async).
                    if (isNonSerializableServletParam(args[i])) {
                        params.put(paramNames[i], getPlaceholderForServletParam(args[i]));
                        continue;
                    }
                    try {
                            // 객체를 JSON으로 변환 시도
                            if (args[i] != null) {
                                if (isJavaFwImglogDecryptRequestMap(paramNames[i], args[i], paramNames, args)) {
                                    params.put(paramNames[i],
                                            buildJavaFwImglogDecryptActivityParams((Map<?, ?>) args[i]));
                                } else if (args[i] instanceof com.logmng.dto.request.LogDbSearchRequest) {
                                    com.logmng.dto.request.LogDbSearchRequest searchRequest = 
                                        (com.logmng.dto.request.LogDbSearchRequest) args[i];
                                    Map<String, Object> searchConditions = new HashMap<>();
                                    searchConditions.put("logType", searchRequest.getLogType());
                                    searchConditions.put("startDate", searchRequest.getStartDate());
                                    searchConditions.put("endDate", searchRequest.getEndDate());
                                    searchConditions.put("page", searchRequest.getPage());
                                    searchConditions.put("pageSize", searchRequest.getPageSize());
                                    
                                    // 로그 타입별 검색 조건 추가 (민감한 정보 마스킹)
                                    if ("java_fw_imglog".equals(searchRequest.getLogType())) {
                                        searchConditions.put("application", searchRequest.getApplication());
                                        searchConditions.put("servicegroup", searchRequest.getServicegroup());
                                        searchConditions.put("service", searchRequest.getService());
                                        // datastring과 headerstring은 민감한 정보가 포함될 수 있으므로 마스킹
                                        searchConditions.put("datastring", maskSensitiveData(searchRequest.getDatastring()));
                                        searchConditions.put("headerstring", maskSensitiveData(searchRequest.getHeaderstring()));
                                        if (searchRequest.getKeywords() != null && !searchRequest.getKeywords().isEmpty()) {
                                            // keywords도 마스킹 처리
                                            java.util.List<String> maskedKeywords = new java.util.ArrayList<>();
                                            for (String keyword : searchRequest.getKeywords()) {
                                                maskedKeywords.add(maskSensitiveData(keyword));
                                            }
                                            searchConditions.put("keywords", maskedKeywords);
                                        }
                                    } else if ("pb_feplog".equals(searchRequest.getLogType())) {
                                        searchConditions.put("mediaCode", searchRequest.getMediaCode());
                                        searchConditions.put("trCode", searchRequest.getTrCode());
                                        searchConditions.put("loginId", searchRequest.getLoginId());
                                    }
                                    
                                    params.put(paramNames[i], searchConditions);
                                } else {
                                    putRequestParamForActivityDetail(params, paramNames[i], args[i]);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("파라미터 처리 실패: paramName={}, error={}", paramNames[i], e.getMessage(), e);
                            if (isNonSerializableServletParam(args[i])) {
                                params.put(paramNames[i], getPlaceholderForServletParam(args[i]));
                            } else if (args[i] instanceof com.logmng.dto.request.LogDbSearchRequest) {
                                try {
                                    com.logmng.dto.request.LogDbSearchRequest searchRequest = 
                                        (com.logmng.dto.request.LogDbSearchRequest) args[i];
                                    Map<String, Object> searchConditions = new HashMap<>();
                                    searchConditions.put("logType", searchRequest.getLogType());
                                    searchConditions.put("startDate", searchRequest.getStartDate());
                                    searchConditions.put("endDate", searchRequest.getEndDate());
                                    searchConditions.put("page", searchRequest.getPage());
                                    searchConditions.put("pageSize", searchRequest.getPageSize());
                                    
                                    if ("java_fw_imglog".equals(searchRequest.getLogType())) {
                                        searchConditions.put("application", searchRequest.getApplication());
                                        searchConditions.put("servicegroup", searchRequest.getServicegroup());
                                        searchConditions.put("service", searchRequest.getService());
                                        // datastring과 headerstring은 민감한 정보가 포함될 수 있으므로 마스킹
                                        searchConditions.put("datastring", maskSensitiveData(searchRequest.getDatastring()));
                                        searchConditions.put("headerstring", maskSensitiveData(searchRequest.getHeaderstring()));
                                        if (searchRequest.getKeywords() != null && !searchRequest.getKeywords().isEmpty()) {
                                            // keywords도 마스킹 처리
                                            java.util.List<String> maskedKeywords = new java.util.ArrayList<>();
                                            for (String keyword : searchRequest.getKeywords()) {
                                                maskedKeywords.add(maskSensitiveData(keyword));
                                            }
                                            searchConditions.put("keywords", maskedKeywords);
                                        }
                                    } else if ("pb_feplog".equals(searchRequest.getLogType())) {
                                        searchConditions.put("mediaCode", searchRequest.getMediaCode());
                                        searchConditions.put("trCode", searchRequest.getTrCode());
                                        searchConditions.put("loginId", searchRequest.getLoginId());
                                    }
                                    
                                    params.put(paramNames[i], searchConditions);
                                } catch (Exception e2) {
                                    log.error("LogDbSearchRequest 파싱 재시도 실패: {}", e2.getMessage());
                                    try {
                                        putRequestParamForActivityDetail(params, paramNames[i], args[i]);
                                    } catch (Exception e3) {
                                        params.put(paramNames[i], null);
                                    }
                                }
                            } else {
                                try {
                                    putRequestParamForActivityDetail(params, paramNames[i], args[i]);
                                } catch (Exception e2) {
                                    params.put(paramNames[i], null);
                                }
                            }
                        }
                }
                actionDetail.put("requestParams", params);
            }
            
            if (activityLog.includeResponse() && methodResult != null) {
                // 응답 데이터 추가 (크기 제한)
                try {
                    // 검색 결과인 경우 요약 정보 추출
                    if (methodResult instanceof org.springframework.http.ResponseEntity) {
                        org.springframework.http.ResponseEntity<?> responseEntity = 
                            (org.springframework.http.ResponseEntity<?>) methodResult;
                        Object body = responseEntity.getBody();
                        
                        if (body != null) {
                            // ApiResponse 형태인지 확인
                            try {
                                java.lang.reflect.Method getDataMethod = body.getClass().getMethod("getData");
                                Object data = getDataMethod.invoke(body);
                                
                                if (data != null) {
                                    // LogDbSearchResponse 또는 UserActivityLogResponse 형태인지 확인
                                    try {
                                        java.lang.reflect.Method getPaginationMethod = data.getClass().getMethod("getPagination");
                                        Object pagination = getPaginationMethod.invoke(data);
                                        
                                        if (pagination != null) {
                                            // 검색 결과 요약 정보 추출
                                            Map<String, Object> searchSummary = new HashMap<>();
                                            
                                            try {
                                                java.lang.reflect.Method getTotalCountMethod = pagination.getClass().getMethod("getTotalCount");
                                                Object totalCount = getTotalCountMethod.invoke(pagination);
                                                if (totalCount != null) {
                                                    searchSummary.put("totalCount", totalCount);
                                                }
                                            } catch (Exception e) {
                                                log.debug("totalCount 추출 실패: {}", e.getMessage());
                                            }
                                            
                                            try {
                                                java.lang.reflect.Method getCurrentPageMethod = pagination.getClass().getMethod("getCurrentPage");
                                                Object currentPage = getCurrentPageMethod.invoke(pagination);
                                                if (currentPage != null) {
                                                    searchSummary.put("currentPage", currentPage);
                                                }
                                            } catch (Exception e) {
                                                log.debug("currentPage 추출 실패: {}", e.getMessage());
                                            }
                                            
                                            try {
                                                java.lang.reflect.Method getTotalPagesMethod = pagination.getClass().getMethod("getTotalPages");
                                                Object totalPages = getTotalPagesMethod.invoke(pagination);
                                                if (totalPages != null) {
                                                    searchSummary.put("totalPages", totalPages);
                                                }
                                            } catch (Exception e) {
                                                log.debug("totalPages 추출 실패: {}", e.getMessage());
                                            }
                                            
                                            // 검색 결과 데이터 개수 추출
                                            try {
                                                java.lang.reflect.Method getDataMethod2 = data.getClass().getMethod("getData");
                                                Object resultData = getDataMethod2.invoke(data);
                                                if (resultData instanceof java.util.List) {
                                                    int resultCount = ((java.util.List<?>) resultData).size();
                                                    searchSummary.put("resultCount", resultCount);
                                                }
                                            } catch (Exception e) {
                                                log.debug("resultCount 추출 실패: {}", e.getMessage());
                                            }
                                            
                                            if (!searchSummary.isEmpty()) {
                                                actionDetail.put("searchSummary", searchSummary);
                                            }
                                        }
                                    } catch (Exception e) {
                                        // Pagination이 없는 경우 무시
                                    }
                                }
                            } catch (Exception e) {
                                // getData 메서드가 없는 경우 무시
                            }
                        }
                    }
                    
                    // 전체 응답은 저장하지 않음 (용량 절약 및 보안)
                    // 필요시 searchSummary만 저장
                } catch (Exception e) {
                    log.debug("응답 데이터 직렬화 실패: {}", e.getMessage());
                }
            }

            // Permission group admin: structured action_detail when includeParams=false (req 20260330)
            ActivityAuditDetailEnricher.enrichPermissionGroup(
                    signature.getDeclaringType(),
                    method.getName(),
                    joinPoint.getArgs(),
                    methodResult,
                    actionDetail,
                    permissionGroupService);
            
            // 요청 파라미터 JSON 문자열 생성 (Servlet 타입은 직렬화 전에 플레이스홀더로 대체)
            String requestParamsJson = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawParams = (Map<String, Object>) actionDetail.get("requestParams");
                Map<String, Object> paramsToSerialize = rawParams != null ? sanitizeParamsForSerialization(rawParams) : new HashMap<>();
                requestParamsJson = objectMapper.writeValueAsString(paramsToSerialize);
            } catch (Exception e) {
                log.debug("요청 파라미터 직렬화 실패: {}", e.getMessage());
            }
            
            // IP 주소 가져오기 (헤더 비리터럴은 저장하지 않음; IpUtil과 동일 체인 + 검증)
            String ipAddress = resolveClientIpForActivityLog(request);
            
            // User-Agent 가져오기
            String userAgent = request.getHeader("User-Agent");
            
            // 미인증 사용자(anonymous)는 활동 이력에 저장하지 않음 (로그인 사용자만 기록)
            if (userId == null) {
                log.debug("활동 이력 저장 생략: 사용자 미인증 (userId 없음)");
                return;
            }
            
            // 활동 이력 저장 (비동기로 처리하지 않고 동기로 처리 - 간단한 구현)
            userActivityLogService.saveActivityLog(
                    userId,
                    username,
                    actionType,
                    actionDetail,
                    ipAddress,
                    userAgent,
                    request.getMethod(),
                    request.getRequestURI(),
                    requestParamsJson,
                    responseStatus,
                    responseTimeMs,
                    success,
                    errorMessage
            );
            
        } catch (Exception e) {
            // 활동 이력 저장 실패는 로그만 남기고 예외를 던지지 않음
            log.error("❌ 활동 이력 저장 중 오류 발생", e);
        }
    }
    
    /**
     * 활동 이력용 user_id (app_user.username). 세션 userId(Long)는 AuthService에서 username으로 해석.
     */
    private String getUserId(HttpServletRequest request) {
        LoginResponse user = authService.getCurrentUserInfo(request);
        if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        if (request != null) {
            String header = request.getHeader("X-User-Id");
            if (header != null && !header.isEmpty()) return header;
        }
        return null;
    }
    
    /**
     * 활동 이력용 표시 이름 (selfContext.username = app_user.name or app_user.username).
     */
    private String getUsername(HttpServletRequest request) {
        LoginResponse user = authService.getCurrentUserInfo(request);
        if (user != null) {
            if (user.getSelfContext() != null && user.getSelfContext().getUsername() != null) {
                return user.getSelfContext().getUsername();
            }
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                return user.getUsername();
            }
        }
        if (request != null) {
            String header = request.getHeader("X-Username");
            if (header != null && !header.isEmpty()) return header;
        }
        return null;
    }
    
    /**
     * Activity log persistence: validated IP literals only (see req 20260410). DEBUG diagnostic traces
     * header inputs and resolution outcome; disabled at INFO in production by default.
     */
    private String resolveClientIpForActivityLog(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        String xReal = request.getHeader("X-Real-IP");
        String remoteRaw = request.getRemoteAddr();
        String remoteNorm = IpUtil.normalizeServletRemoteAddr(remoteRaw);
        String getClientIp = ipUtil.getClientIP(request);
        List<String> ordered = ipUtil.collectIpCandidatesInTrustOrder(request);
        String resolved = ipUtil.getResolvedClientIpForActivityLog(request);
        log.debug(
                "activity client IP diagnostic: X-Forwarded-For={}, X-Real-IP={}, remoteAddr(raw)={}, "
                        + "remoteAddr(normalized)={}, getClientIP(raw)={}, orderedCandidates={}, resolved(validated)={}",
                xff, xReal, remoteRaw, remoteNorm, getClientIp, ordered, resolved);
        return resolved;
    }
    
    /**
     * 메서드명으로부터 액션 타입 생성
     */
    private String generateActionType(String methodName) {
        // 메서드명을 기반으로 액션 타입 추정
        if (methodName.toLowerCase().contains("search")) {
            return "SEARCH";
        } else if (methodName.toLowerCase().contains("get") || methodName.toLowerCase().contains("view")) {
            return "VIEW";
        } else if (methodName.toLowerCase().contains("export")) {
            return "EXPORT";
        } else if (methodName.toLowerCase().contains("decrypt")) {
            return "DECRYPT";
        } else if (methodName.toLowerCase().contains("stats")) {
            return "STATS_VIEW";
        } else if (methodName.toLowerCase().contains("schema")) {
            return "SCHEMA_VIEW";
        } else {
            return "UNKNOWN";
        }
    }
    
    /**
     * Servlet API 파라미터 여부. 이 타입들은 Jackson으로 직렬화하면 안 됨
     * (RequestFacade.getHeaderNames() → NamesEnumerator; getParts()/getAsyncContext() 예외).
     */
    private static boolean isNonSerializableServletParam(Object arg) {
        return arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                || arg instanceof ServletRequest || arg instanceof ServletResponse;
    }

    private static String getPlaceholderForServletParam(Object arg) {
        if (arg instanceof HttpServletRequest) return "<HttpServletRequest>";
        if (arg instanceof HttpServletResponse) return "<HttpServletResponse>";
        if (arg instanceof ServletRequest) return "<ServletRequest>";
        if (arg instanceof ServletResponse) return "<ServletResponse>";
        return "<Servlet>";
    }

    /**
     * ObjectMapper에 넘기기 전에 구조 전체에서 Servlet 참조를 플레이스홀더로 치환.
     * Map/List 중첩 시에도 재귀 적용 (e.g. request body Map에 httpRequest 키가 있는 경우).
     */
    @SuppressWarnings("unchecked")
    private static Object deepSanitizeForSerialization(Object value) {
        if (value == null) return null;
        if (isNonSerializableServletParam(value)) return getPlaceholderForServletParam(value);
        if (value instanceof Map) {
            Map<Object, Object> in = (Map<Object, Object>) value;
            Map<Object, Object> out = new HashMap<>();
            for (Map.Entry<Object, Object> e : in.entrySet()) {
                out.put(e.getKey(), deepSanitizeForSerialization(e.getValue()));
            }
            return out;
        }
        if (value instanceof Collection) {
            List<Object> out = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                out.add(deepSanitizeForSerialization(item));
            }
            return out;
        }
        return value;
    }

    /**
     * requestParams map을 ObjectMapper로 직렬화하기 전에 Servlet API 참조를 플레이스홀더로 치환.
     * 중첩 Map/List도 재귀 치환.
     */
    private static Map<String, Object> sanitizeParamsForSerialization(Map<String, Object> params) {
        if (params == null) return new HashMap<>();
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            Object v = deepSanitizeForSerialization(e.getValue());
            out.put(e.getKey(), v);
        }
        return out;
    }

    /**
     * One entry in {@code requestParams} for {@link #logActivityInternal}.
     * String / Number / Boolean / Character are stored as JSON scalars so the outer {@code action_detail}
     * serialization contains {@code "logType":"java_fw_imglog"} (not double-encoded string values).
     * Other types: deep-sanitize, JSON-stringify, then {@link #maskSensitiveData(String)} on that JSON text.
     */
    private void putRequestParamForActivityDetail(Map<String, Object> params, String paramName, Object arg)
            throws Exception {
        if (arg == null) {
            return;
        }
        if (arg instanceof String) {
            params.put(paramName, maskSensitiveData((String) arg));
            return;
        }
        if (arg instanceof Number || arg instanceof Boolean || arg instanceof Character) {
            params.put(paramName, arg);
            return;
        }
        Object toSerialize = deepSanitizeForSerialization(arg);
        String json = objectMapper.writeValueAsString(toSerialize);
        params.put(paramName, maskSensitiveData(json));
    }

    /**
     * POST /api/logs/decrypt/java_fw_imglog body: structured audit fields (guid, status, optional searchHistoryId).
     * Does not log full JSON body or decrypted payload. Req: imagelog composite (guid, status) in activity params.
     */
    private static boolean isJavaFwImglogDecryptRequestMap(String paramName, Object arg,
                                                           String[] paramNames, Object[] args) {
        if (!"request".equals(paramName) || !(arg instanceof Map)) {
            return false;
        }
        for (int j = 0; j < paramNames.length && j < args.length; j++) {
            if ("logType".equals(paramNames[j]) && "java_fw_imglog".equals(args[j])) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> buildJavaFwImglogDecryptActivityParams(Map<?, ?> body) {
        Map<String, Object> out = new HashMap<>();
        Object g = body.get("guid");
        out.put("guid", g != null ? g.toString().trim() : null);
        Object s = body.get("status");
        String st = DecryptionRowKey.normalizeStatus(s != null ? s.toString() : null);
        out.put("status", st.isEmpty() ? null : st);
        if (body.containsKey("searchHistoryId")) {
            Object sh = body.get("searchHistoryId");
            out.put("searchHistoryId", sh != null ? sh.toString() : null);
        }
        return out;
    }

    /**
     * 민감한 필드인지 확인
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lowerFieldName = fieldName.toLowerCase();
        return lowerFieldName.contains("password") || 
               lowerFieldName.contains("pwd") || 
               lowerFieldName.contains("secret") ||
               lowerFieldName.contains("token") ||
               lowerFieldName.contains("ssn") ||
               lowerFieldName.contains("creditcard") ||
               lowerFieldName.contains("cardnumber");
    }
    
    /**
     * 민감한 정보 마스킹 처리
     */
    private String maskSensitiveData(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        // 비밀번호 패턴 마스킹 (password, pwd 등)
        String masked = data.replaceAll("(?i)(\"password\"\\s*:\\s*\")([^\"]+)(\")", "$1***$3");
        masked = masked.replaceAll("(?i)(\"pwd\"\\s*:\\s*\")([^\"]+)(\")", "$1***$3");
        masked = masked.replaceAll("(?i)(\"secret\"\\s*:\\s*\")([^\"]+)(\")", "$1***$3");
        masked = masked.replaceAll("(?i)(\"token\"\\s*:\\s*\")([^\"]+)(\")", "$1***$3");
        
        // 주민등록번호 패턴 마스킹 (123456-1234567 -> 123456-*******)
        masked = masked.replaceAll("(\\d{6}-)\\d{7}", "$1*******");
        
        // 신용카드 번호 패턴 마스킹 (4자리씩 구분)
        masked = masked.replaceAll("(\\d{4}-)\\d{4}-\\d{4}-(\\d{4})", "$1****-****-$2");
        
        // 전화번호 패턴 마스킹 (010-1234-5678 -> 010-****-5678)
        masked = masked.replaceAll("(\\d{3}-)\\d{4}(-\\d{4})", "$1****$2");
        
        // 이메일 패턴 마스킹 (user@example.com -> u***@example.com)
        masked = masked.replaceAll("([a-zA-Z0-9])[a-zA-Z0-9]*@", "$1***@");
        
        return masked;
    }
}
