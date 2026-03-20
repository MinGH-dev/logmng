package com.logmng;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 애플리케이션 컨텍스트 로드 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class LogManagementApplicationTests {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void contextLoads() {
        // 컨텍스트가 정상적으로 로드되는지 확인
    }

    @Test
    void filterOptionsDepartmentsRoute_isRegisteredInApplicationContext() {
        Set<String> patterns = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(patterns).contains("/api/filter-options/departments");
    }
}





