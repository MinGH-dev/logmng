package com.logmng.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

/**
 * LDAP context for directory authentication when auth.login.mode=ad.
 */
@Configuration
@ConditionalOnProperty(prefix = "auth.login", name = "mode", havingValue = "ad")
public class LdapClientConfig {

    @Bean
    public LdapContextSource ldapContextSource(AuthProperties authProperties) {
        AuthProperties.Ad ad = authProperties.getAd();
        LdapContextSource source = new LdapContextSource();
        source.setUrl(ad.getLdapUrl().trim());
        source.setUserDn(ad.getManagerDn().trim());
        source.setPassword(ad.getManagerPassword());
        source.setPooled(false);
        if (ad.getConnectTimeoutMs() != null) {
            source.setBaseEnvironmentProperties(java.util.Collections.singletonMap(
                    "com.sun.jndi.ldap.connect.timeout", String.valueOf(ad.getConnectTimeoutMs())));
        }
        try {
            source.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("AUTH_CONFIGURATION_ERROR: failed to initialize LDAP context source", e);
        }
        return source;
    }

    @Bean
    public LdapTemplate ldapTemplate(LdapContextSource ldapContextSource) {
        return new LdapTemplate(ldapContextSource);
    }
}
