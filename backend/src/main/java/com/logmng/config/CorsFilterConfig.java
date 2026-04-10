package com.logmng.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 필터를 체인 최상단에 등록하여 OPTIONS preflight가
 * 인터셉터에 막히기 전에 CORS 헤더와 함께 200으로 응답하도록 함.
 */
@Configuration
@EnableConfigurationProperties(AppCorsProperties.class)
public class CorsFilterConfig {

    @Bean
    public FilterRegistrationBean<CorsPreflightFilter> corsPreflightFilterRegistration(AppCorsProperties cors) {
        FilterRegistrationBean<CorsPreflightFilter> bean =
                new FilterRegistrationBean<>(new CorsPreflightFilter(cors.allowedOriginList()));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(AppCorsProperties cors) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(cors.allowedOriginList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // No "*" with allowCredentials(true) — browsers reject it on credentialed preflight
        config.setAllowedHeaders(Arrays.asList(
                "Content-Type", "Authorization", "Accept", "Accept-Language",
                "X-Requested-With", "Cache-Control", "Pragma"));
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/api/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }
}
