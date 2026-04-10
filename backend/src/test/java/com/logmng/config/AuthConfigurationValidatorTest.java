package com.logmng.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TC-B10: invalid auth.login.mode or local-in-prod without opt-in fails closed at startup.
 */
class AuthConfigurationValidatorTest {

    @Test
    void invalidMode_throwsIllegalStateException() {
        AuthProperties p = new AuthProperties();
        p.getLogin().setMode("ldap");
        AuthConfigurationValidator v = new AuthConfigurationValidator(p, new MockEnvironment());
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_CONFIGURATION_ERROR");
    }

    @Test
    void localModeInProduction_withoutAllow_throws() {
        AuthProperties p = new AuthProperties();
        p.getLogin().setMode("local");
        p.getLogin().setAllowLocalInProduction(false);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        AuthConfigurationValidator v = new AuthConfigurationValidator(p, env);
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local");
    }

    @Test
    void validLocalMode_succeeds() {
        AuthProperties p = new AuthProperties();
        p.getLogin().setMode("local");
        AuthConfigurationValidator v = new AuthConfigurationValidator(p, new MockEnvironment());
        v.validate();
    }

    @Test
    void adMode_minimalConfig_succeeds() {
        AuthProperties p = new AuthProperties();
        p.getLogin().setMode("ad");
        p.getAd().setLdapUrl("ldaps://dc.example.com:636");
        p.getAd().setDomain("corp.example.com");
        AuthConfigurationValidator v = new AuthConfigurationValidator(p, new MockEnvironment());
        v.validate();
    }

    @Test
    void adMode_blankLdapUrl_throws() {
        AuthProperties p = new AuthProperties();
        p.getLogin().setMode("ad");
        p.getAd().setLdapUrl("  ");
        p.getAd().setDomain("corp.example.com");
        AuthConfigurationValidator v = new AuthConfigurationValidator(p, new MockEnvironment());
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_CONFIGURATION_ERROR")
                .hasMessageContaining("ldap-url");
    }

    @Test
    void adMode_blankDomain_throws() {
        AuthProperties p = new AuthProperties();
        p.getLogin().setMode("ad");
        p.getAd().setLdapUrl("ldaps://dc.example.com:636");
        p.getAd().setDomain("");
        AuthConfigurationValidator v = new AuthConfigurationValidator(p, new MockEnvironment());
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_CONFIGURATION_ERROR")
                .hasMessageContaining("domain");
    }
}
