package com.logmng.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 설정
 */
@Configuration
@EnableConfigurationProperties({AppCorsProperties.class, AuthProperties.class, HrSyncPocProperties.class})
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ScreenAccessInterceptor screenAccessInterceptor;
    private final AppCorsProperties appCorsProperties;

    public WebConfig(AuthInterceptor authInterceptor, ScreenAccessInterceptor screenAccessInterceptor,
                       AppCorsProperties appCorsProperties) {
        this.authInterceptor = authInterceptor;
        this.screenAccessInterceptor = screenAccessInterceptor;
        this.appCorsProperties = appCorsProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .order(1);
        registry.addInterceptor(screenAccessInterceptor)
                .addPathPatterns("/api/**")
                .order(2);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = appCorsProperties.allowedOriginList().toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowCredentials(true) // 세션 쿠키 전달 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders(
                        "Content-Type", "Authorization", "Accept", "Accept-Language",
                        "X-Requested-With", "Cache-Control", "Pragma")
                .maxAge(3600);
    }
}

