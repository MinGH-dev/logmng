package com.logmng.service;

import com.logmng.config.AuthProperties;
import com.logmng.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import java.util.Arrays;
import java.util.Hashtable;

/**
 * Validates end-user credentials against the directory using JNDI LDAP simple bind (auth.login.mode=ad).
 * Bind principal: full UPN if principal contains {@code @}, else {@code principal + "@" + domain}.
 */
@Service
@ConditionalOnProperty(prefix = "auth.login", name = "mode", havingValue = "ad")
public class LdapBindAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(LdapBindAuthenticator.class);

    private final AuthProperties authProperties;
    private final LdapDirContextFactory dirContextFactory;

    public LdapBindAuthenticator(AuthProperties authProperties) {
        this(authProperties, LdapDirContextFactory.jdkDefault());
    }

    LdapBindAuthenticator(AuthProperties authProperties, LdapDirContextFactory dirContextFactory) {
        this.authProperties = authProperties;
        this.dirContextFactory = dirContextFactory;
    }

    /**
     * Performs LDAP simple bind. Does not persist passwords.
     *
     * @throws CustomException UNAUTHORIZED DIRECTORY_AUTH_FAILED on failure
     */
    public void authenticate(String principal, String password) {
        if (principal == null || principal.isBlank()) {
            throw CustomException.unauthorized("디렉터리 인증에 실패했습니다.", "DIRECTORY_AUTH_FAILED");
        }
        if (password == null || password.isBlank()) {
            throw CustomException.unauthorized("디렉터리 인증에 실패했습니다.", "DIRECTORY_AUTH_FAILED");
        }
        AuthProperties.Ad ad = authProperties.getAd();
        String bindPrincipal = resolveBindPrincipal(principal, ad.getDomain());
        char[] creds = password.toCharArray();
        Hashtable<String, Object> env = buildEnvironment(ad, bindPrincipal, creds);
        DirContext ctx = null;
        try {
            ctx = dirContextFactory.create(env);
        } catch (AuthenticationException e) {
            log.warn("LDAP authentication failed (simple bind)");
            throw CustomException.unauthorized("디렉터리 인증에 실패했습니다.", "DIRECTORY_AUTH_FAILED");
        } catch (NamingException e) {
            log.warn("LDAP error during bind: {}", e.getClass().getSimpleName());
            throw CustomException.unauthorized("디렉터리 인증에 실패했습니다.", "DIRECTORY_AUTH_FAILED");
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    log.warn("LDAP context close: {}", e.getClass().getSimpleName());
                }
            }
            Arrays.fill(creds, '\0');
        }
    }

    static String resolveBindPrincipal(String principal, String domain) {
        String p = principal.trim();
        if (p.contains("@")) {
            return p;
        }
        String d = domain == null ? "" : domain.trim();
        if (d.isEmpty()) {
            throw CustomException.unauthorized("디렉터리 인증에 실패했습니다.", "DIRECTORY_AUTH_FAILED");
        }
        return p + "@" + d;
    }

    private static Hashtable<String, Object> buildEnvironment(
            AuthProperties.Ad ad, String bindPrincipal, char[] credentials) {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ad.getLdapUrl().trim());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, bindPrincipal);
        env.put(Context.SECURITY_CREDENTIALS, credentials);
        if (ad.getConnectTimeoutMs() != null) {
            env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(ad.getConnectTimeoutMs()));
        }
        if (ad.getReadTimeoutMs() != null) {
            env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(ad.getReadTimeoutMs()));
        }
        return env;
    }
}
