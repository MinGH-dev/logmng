package com.logmng.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * Comma-separated {@code app.cors.allowed-origins}; override with env {@code CORS_ALLOWED_ORIGINS} for air-gap hosts.
 */
@ConfigurationProperties(prefix = "app.cors")
public class AppCorsProperties {

    /**
     * Comma-separated origins (trimmed). Example: {@code http://10.0.0.5:3001,http://10.0.0.5:9200} is invalid — only browser UI origins.
     */
    private String allowedOrigins =
            "http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000,http://127.0.0.1:3001";

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> allowedOriginList() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
