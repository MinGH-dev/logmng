package com.logmng.aspect;

import com.logmng.controller.DecryptController;
import com.logmng.dto.response.ApiResponse;
import com.logmng.dto.response.LoginResponse;
import com.logmng.service.AuthService;
import com.logmng.service.StubUserActivityLogServiceSaveCapture;
import com.logmng.service.UserActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        aspect = new ActivityLogAspect(userActivityLogService, authService);
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
    }

    private static class StubAuthServiceForAspect extends AuthService {
        StubAuthServiceForAspect() {
            super(null, null, null, null, null);
        }

        @Override
        public LoginResponse getCurrentUserInfo(HttpServletRequest req) {
            LoginResponse r = new LoginResponse();
            r.setUsername("testuser");
            r.setUserId(1000L);
            r.setSelfContext(new LoginResponse.SelfContext(null, "Test User", 1000L));
            return r;
        }
    }
}
