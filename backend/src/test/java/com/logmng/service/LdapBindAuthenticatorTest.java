package com.logmng.service;

import com.logmng.config.AuthProperties;
import com.logmng.exception.CustomException;
import org.junit.jupiter.api.Test;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.DirContext;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LDAP JNDI simple bind (req 20260407 §3).
 */
class LdapBindAuthenticatorTest {

    @Test
    void resolveBindPrincipal_shortName_appendsDomain() {
        assertThat(LdapBindAuthenticator.resolveBindPrincipal("jdoe", "corp.example.com"))
                .isEqualTo("jdoe@corp.example.com");
    }

    @Test
    void resolveBindPrincipal_alreadyUpn_unchanged() {
        assertThat(LdapBindAuthenticator.resolveBindPrincipal("jdoe@corp.example.com", "other.domain"))
                .isEqualTo("jdoe@corp.example.com");
    }

    @Test
    void resolveBindPrincipal_blankDomainWithoutAt_throwsDirectoryAuthFailed() {
        assertThatThrownBy(() -> LdapBindAuthenticator.resolveBindPrincipal("jdoe", ""))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("DIRECTORY_AUTH_FAILED"));
    }

    @Test
    void authenticate_success_closesContextOnce() throws Exception {
        AuthProperties p = adProperties();
        AtomicInteger closeCount = new AtomicInteger();
        DirContext fakeCtx = fakeDirContext(closeCount);
        AtomicReference<Hashtable<String, Object>> captured = new AtomicReference<>();
        LdapDirContextFactory factory = env -> {
            captured.set(env);
            return fakeCtx;
        };
        LdapBindAuthenticator auth = new LdapBindAuthenticator(p, factory);
        auth.authenticate("jdoe", "secret");
        Hashtable<String, Object> env = captured.get();
        assertThat(env.get(Context.SECURITY_PRINCIPAL)).isEqualTo("jdoe@corp.example.com");
        assertThat(env.get("com.sun.jndi.ldap.connect.timeout")).isEqualTo("3000");
        assertThat(env.get("com.sun.jndi.ldap.read.timeout")).isEqualTo("4000");
        assertThat(closeCount.get()).isEqualTo(1);
    }

    @Test
    void authenticate_upnPrincipal_passedToBind() throws Exception {
        AuthProperties p = adProperties();
        AtomicInteger closeCount = new AtomicInteger();
        DirContext fakeCtx = fakeDirContext(closeCount);
        AtomicReference<Hashtable<String, Object>> captured = new AtomicReference<>();
        LdapDirContextFactory factory = env -> {
            captured.set(env);
            return fakeCtx;
        };
        LdapBindAuthenticator auth = new LdapBindAuthenticator(p, factory);
        auth.authenticate("jdoe@corp.example.com", "secret");
        assertThat(captured.get().get(Context.SECURITY_PRINCIPAL)).isEqualTo("jdoe@corp.example.com");
        assertThat(closeCount.get()).isEqualTo(1);
    }

    @Test
    void authenticate_authenticationException_mapsToDirectoryAuthFailed() {
        AuthProperties p = adProperties();
        LdapDirContextFactory factory = env -> {
            throw new AuthenticationException();
        };
        LdapBindAuthenticator auth = new LdapBindAuthenticator(p, factory);
        assertThatThrownBy(() -> auth.authenticate("jdoe", "secret"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("DIRECTORY_AUTH_FAILED"));
    }

    @Test
    void authenticate_otherNamingException_mapsToDirectoryAuthFailed() {
        AuthProperties p = adProperties();
        LdapDirContextFactory factory = env -> {
            throw new ServiceUnavailableException();
        };
        LdapBindAuthenticator auth = new LdapBindAuthenticator(p, factory);
        assertThatThrownBy(() -> auth.authenticate("jdoe", "secret"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo("DIRECTORY_AUTH_FAILED"));
    }

    private static AuthProperties adProperties() {
        AuthProperties p = new AuthProperties();
        p.getAd().setLdapUrl("ldap://ldap.example.com:389");
        p.getAd().setDomain("corp.example.com");
        p.getAd().setConnectTimeoutMs(3000);
        p.getAd().setReadTimeoutMs(4000);
        return p;
    }

    /**
     * Mockito cannot mock {@link DirContext} on some JDKs; minimal proxy tracks {@link DirContext#close()}.
     */
    private static DirContext fakeDirContext(AtomicInteger closeCalls) {
        return (DirContext) Proxy.newProxyInstance(
                DirContext.class.getClassLoader(),
                new Class<?>[] { DirContext.class },
                (proxy, method, args) -> {
                    String n = method.getName();
                    if ("close".equals(n)) {
                        closeCalls.incrementAndGet();
                        return null;
                    }
                    if ("equals".equals(n) && args != null && args.length == 1) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(n)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(n)) {
                        return "fake DirContext";
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == byte.class) {
                        return (byte) 0;
                    }
                    if (rt == char.class) {
                        return (char) 0;
                    }
                    if (rt == short.class) {
                        return (short) 0;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    if (rt == long.class) {
                        return 0L;
                    }
                    if (rt == float.class) {
                        return 0f;
                    }
                    if (rt == double.class) {
                        return 0d;
                    }
                    return null;
                });
    }
}
