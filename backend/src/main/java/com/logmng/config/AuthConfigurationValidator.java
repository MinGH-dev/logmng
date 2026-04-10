package com.logmng.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Fail-closed validation for auth.login.mode and auth.ad.* (req 20260407, TC-B10).
 */
@Component
public class AuthConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(AuthConfigurationValidator.class);

    private final AuthProperties authProperties;
    private final Environment environment;

    public AuthConfigurationValidator(AuthProperties authProperties, Environment environment) {
        this.authProperties = authProperties;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        String mode = authProperties.getLogin().getMode();
        if (!StringUtils.hasText(mode)) {
            fail("auth.login.mode is required");
        }
        String normalized = mode.trim().toLowerCase();
        if (!"local".equals(normalized) && !"ad".equals(normalized)) {
            fail("auth.login.mode must be 'local' or 'ad', got: " + mode);
        }

        if ("local".equals(normalized) && isProductionProfile()
                && !authProperties.getLogin().isAllowLocalInProduction()) {
            fail("auth.login.mode=local is not allowed in production unless auth.login.allow-local-in-production=true");
        }

        if ("ad".equals(normalized)) {
            AuthProperties.Ad ad = authProperties.getAd();
            if (!StringUtils.hasText(ad.getLdapUrl()) || !StringUtils.hasText(ad.getDomain())) {
                fail("auth.login.mode=ad requires non-blank auth.ad.ldap-url and auth.ad.domain");
            }
        }

        log.info("Auth configuration validated: login.mode={}", normalized);
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }

    private static void fail(String message) {
        throw new IllegalStateException("AUTH_CONFIGURATION_ERROR: " + message);
    }
}
