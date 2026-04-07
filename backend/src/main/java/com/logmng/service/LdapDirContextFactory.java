package com.logmng.service;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

/**
 * Opens a JNDI {@link DirContext} for LDAP simple bind (test seam).
 */
@FunctionalInterface
public interface LdapDirContextFactory {

    DirContext create(Hashtable<String, Object> env) throws NamingException;

    static LdapDirContextFactory jdkDefault() {
        return env -> new InitialDirContext(env);
    }
}
