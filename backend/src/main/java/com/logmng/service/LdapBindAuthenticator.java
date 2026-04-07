package com.logmng.service;

import com.logmng.config.AuthProperties;
import com.logmng.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;

/**
 * Validates end-user credentials against the directory using LDAP bind (auth.login.mode=ad).
 */
@Service
@ConditionalOnProperty(prefix = "auth.login", name = "mode", havingValue = "ad")
public class LdapBindAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(LdapBindAuthenticator.class);

    private final LdapTemplate ldapTemplate;
    private final AuthProperties authProperties;

    public LdapBindAuthenticator(LdapTemplate ldapTemplate, AuthProperties authProperties) {
        this.ldapTemplate = ldapTemplate;
        this.authProperties = authProperties;
    }

    /**
     * Performs LDAP authenticate (bind as user). Does not persist passwords.
     *
     * @throws CustomException UNAUTHORIZED DIRECTORY_AUTH_FAILED on failure
     */
    public void authenticate(String principal, String password) {
        if (principal == null || principal.isBlank()) {
            throw CustomException.unauthorized("디렉터리 인증에 실패했습니다.", "DIRECTORY_AUTH_FAILED");
        }
        AuthProperties.Ad ad = authProperties.getAd();
        String base = ad.getUserSearchBase().trim();
        String filterTemplate = ad.getUserSearchFilter() != null && !ad.getUserSearchFilter().isBlank()
                ? ad.getUserSearchFilter().trim()
                : "(sAMAccountName={0})";
        try {
            ldapTemplate.authenticate(
                    LdapQueryBuilder.query().base(base).filter(filterTemplate, principal),
                    password);
        } catch (org.springframework.ldap.AuthenticationException e) {
            log.warn("LDAP authentication failed for principal (masked)");
            throw CustomException.unauthorized("디렉터리 인증에 실패했습니다.", "DIRECTORY_AUTH_FAILED");
        } catch (Exception e) {
            log.warn("LDAP error during authenticate: {}", e.getClass().getSimpleName());
            throw CustomException.unauthorized("디렉터리 인증에 실패했습니다.", "DIRECTORY_AUTH_FAILED");
        }
    }
}
