package com.logmng.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logmng.controller.DecryptController;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.service.AuthService;
import com.logmng.service.PermissionGroupService;
import com.logmng.service.StubUserActivityLogServiceSaveCapture;
import com.logmng.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.Part;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * ActivityLogAspect tests. Ensures Servlet API params (HttpServletRequest/Response)
 * are not serialized to JSON (avoids asyncContext IllegalStateException in real containers).
 * Mocks only AspectJ types (ProceedingJoinPoint, MethodSignature); uses stubs for services to avoid Java 25 Mockito limits.
 */
@ExtendWith(MockitoExtension.class)
class ActivityLogAspectTest {

    private StubUserActivityLogServiceSaveCapture userActivityLogService;
    private AuthService authService;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature methodSignature;

    private ActivityLogAspect aspect;

    @BeforeEach
    void setUp() throws Throwable {
        userActivityLogService = new StubUserActivityLogServiceSaveCapture(createH2DataSource());
        authService = new StubAuthServiceForAspect();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.setMethod("POST");
        request.setRequestURI("/api/logs/decrypt/java_fw_imglog");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method method = DecryptController.class.getMethod(
                "decryptRow", String.class, Map.class, HttpServletRequest.class);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"logType", "request", "httpRequest"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                "java_fw_imglog",
                Map.of("searchHistoryId", "1", "guid", "g"),
                request
        });
        doReturn(ResponseEntity.ok(ApiResponse.success(Map.of("decrypted", "data"))))
                .when(joinPoint).proceed();

        PermissionGroupService permissionGroupService = new PermissionGroupService(null, null);
        aspect = new ActivityLogAspect(userActivityLogService, authService, permissionGroupService, new IpUtil());
    }

    private static javax.sql.DataSource createH2DataSource() throws Exception {
        Class.forName("org.h2.Driver");
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:aspect_test;DB_CLOSE_DELAY=-1");
        return ds;
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Regression: decryptRow args (logType, request Map, httpRequest) → no 500; activity log has placeholders for Servlet param")
    void logActivity_doesNotSerializeHttpServletRequest_putsPlaceholderInRequestParams() throws Throwable {
        aspect.logActivity(joinPoint);

        Map<String, Object> actionDetail = userActivityLogService.getLastActionDetail();
        assertThat(actionDetail).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> requestParams = (Map<String, Object>) actionDetail.get("requestParams");
        assertThat(requestParams).isNotNull();
        assertThat(requestParams.get("httpRequest")).isEqualTo("<HttpServletRequest>");
        assertThat(requestParams.get("request")).isNotNull();
        assertThat(requestParams).containsKey("logType");
        assertThat(requestParams.get("logType")).isEqualTo("java_fw_imglog");
        @SuppressWarnings("unchecked")
        Map<String, Object> decryptBody = (Map<String, Object>) requestParams.get("request");
        assertThat(decryptBody).containsKey("status");
        assertThat(decryptBody).containsKey("guid");
    }

    /**
     * When the controller has HttpServletRequest and the request would throw on getParts() (e.g. Tomcat
     * for application/json), the aspect must never pass it to ObjectMapper so no exception is thrown.
     */
    @Test
    void logActivity_withRequestThatThrowsOnGetParts_doesNotThrow_applicationJson() throws Throwable {
        HttpServletRequest requestThatThrowsOnGetParts = new MockHttpServletRequest() {
            @Override
            public Collection<Part> getParts() {
                throw new IllegalStateException(
                    "the request doesn't contain a multipart/form-data or multipart/mixed stream, content type header is application/json");
            }
        };
        ((MockHttpServletRequest) requestThatThrowsOnGetParts).setContentType("application/json");
        ((MockHttpServletRequest) requestThatThrowsOnGetParts).setMethod("POST");
        ((MockHttpServletRequest) requestThatThrowsOnGetParts).setRequestURI("/api/logs/decrypt/java_fw_imglog");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestThatThrowsOnGetParts));

        when(joinPoint.getArgs()).thenReturn(new Object[]{
                "java_fw_imglog",
                Map.of("searchHistoryId", "1", "guid", "g"),
                requestThatThrowsOnGetParts
        });

        aspect.logActivity(joinPoint);

        @SuppressWarnings("unchecked")
        Map<String, Object> requestParams = (Map<String, Object>) userActivityLogService.getLastActionDetail().get("requestParams");
        assertThat(requestParams.get("httpRequest")).isEqualTo("<HttpServletRequest>");
    }

    /**
     * When a parameter is a Map that contains HttpServletRequest (e.g. body with "httpRequest" key),
     * the aspect must not pass it to ObjectMapper; deepSanitize replaces Servlet with placeholder so no
     * NamesEnumerator/serialization error occurs.
     */
    @Test
    void logActivity_withMapContainingHttpServletRequest_doesNotThrow_placeholderInParams() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/logs/decrypt/java_fw_imglog");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Simulate a controller that receives a map (e.g. request body) that contains the request object
        Map<String, Object> bodyWithRequest = new HashMap<>();
        bodyWithRequest.put("searchHistoryId", "1");
        bodyWithRequest.put("guid", "g");
        bodyWithRequest.put("httpRequest", request);

        when(methodSignature.getParameterNames()).thenReturn(new String[]{"logType", "request", "httpRequest"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                "java_fw_imglog",
                bodyWithRequest,
                request
        });

        aspect.logActivity(joinPoint);

        Map<String, Object> actionDetail = userActivityLogService.getLastActionDetail();
        assertThat(actionDetail).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> requestParams = (Map<String, Object>) actionDetail.get("requestParams");
        assertThat(requestParams).isNotNull();
        assertThat(requestParams.get("httpRequest")).isEqualTo("<HttpServletRequest>");
        // java_fw_imglog decrypt body: only audit fields (guid, status, …); no raw map / no Servlet leakage
        Object requestParam = requestParams.get("request");
        assertThat(requestParam).isNotNull();
        assertThat(requestParam).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> requestMap = (Map<String, Object>) requestParam;
        assertThat(requestMap).doesNotContainKey("httpRequest");
        assertThat(requestMap).containsKey("guid");
        assertThat(requestMap).containsKey("status");
        assertThat(requestMap.toString()).doesNotContain("NamesEnumerator");
    }

    /**
     * Activity statistics filters non-LOGIN rows with
     * {@code action_detail::text LIKE '%"logType":"java_fw_imglog"%'}.
     * Path-variable {@code logType} must not be stored as a JSON string value (double-encoded quotes).
     */
    @Test
    @DisplayName("Regression: decryptRow action_detail JSON contains exact statistics substring for logType")
    void logActivity_decryptRow_actionDetailJsonContainsBareLogTypeForStatisticsLike() throws Throwable {
        aspect.logActivity(joinPoint);
        String json = new ObjectMapper().writeValueAsString(userActivityLogService.getLastActionDetail());
        assertThat(json).contains("\"logType\":\"java_fw_imglog\"");
        assertThat(json).doesNotContain("\"logType\":\"\\\"java_fw_imglog\\\"\"");
    }

    @Test
    @DisplayName("TC-01: valid X-Forwarded-For literal persisted (ahead of proxy RemoteAddr)")
    void tc01_validXffLiteral_persisted() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5");
        request.setRemoteAddr("10.0.0.1");
        request.setMethod("POST");
        request.setRequestURI("/api/logs/decrypt/java_fw_imglog");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                "java_fw_imglog",
                Map.of("searchHistoryId", "1", "guid", "g"),
                request
        });

        aspect.logActivity(joinPoint);

        assertThat(userActivityLogService.getLastIpAddress()).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("TC-02: non-literal X-Forwarded-For falls back to valid RemoteAddr, not pattern")
    void tc02_invalidXffPattern_fallsBackToRemoteAddr() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "172.23.111.*");
        request.setRemoteAddr("172.23.111.10");
        request.setMethod("POST");
        request.setRequestURI("/api/logs/decrypt/java_fw_imglog");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                "java_fw_imglog",
                Map.of("searchHistoryId", "1", "guid", "g"),
                request
        });

        aspect.logActivity(joinPoint);

        assertThat(userActivityLogService.getLastIpAddress()).isEqualTo("172.23.111.10");
        assertThat(userActivityLogService.getLastIpAddress()).isNotEqualTo("172.23.111.*");
    }

    @Test
    @DisplayName("TC-03: no forwarded headers; RemoteAddr literal persisted")
    void tc03_noForwardedHeaders_usesRemoteAddr() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.23.111.10");
        request.setMethod("POST");
        request.setRequestURI("/api/logs/decrypt/java_fw_imglog");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                "java_fw_imglog",
                Map.of("searchHistoryId", "1", "guid", "g"),
                request
        });

        aspect.logActivity(joinPoint);

        assertThat(userActivityLogService.getLastIpAddress()).isEqualTo("172.23.111.10");
    }

    private static class StubAuthServiceForAspect extends AuthService {
        StubAuthServiceForAspect() {
            super(null, null, null, null, new com.logmng.config.AuthProperties(), null, null);
        }

        @Override
        public LoginResponse getCurrentUserInfo(HttpServletRequest req) {
            LoginResponse r = new LoginResponse();
            r.setUsername("testuser");
            r.setUserId(1000L);
            r.setSelfContext(new LoginResponse.SelfContext(null, "Test User", 1000L, "EMP-1000"));
            return r;
        }
    }
}
