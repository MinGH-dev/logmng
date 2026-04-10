package com.logmng.testsupport;

import org.h2.tools.RunScript;

import javax.sql.DataSource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Objects;

/**
 * Runs SQL scripts from {@code src/test/resources} via H2 {@link RunScript}.
 * Keeps DML for PB FEP / imagelog fixtures out of Java string literals (req: no app/test Java DML on those tables).
 */
public final class H2ClasspathSql {

    private H2ClasspathSql() {
    }

    /**
     * @param classpathResource path starting with {@code /}, e.g. {@code /sql/foo.sql}
     */
    public static void runScript(Connection connection, String classpathResource) throws Exception {
        try (var in = H2ClasspathSql.class.getResourceAsStream(classpathResource)) {
            Objects.requireNonNull(in, "Classpath resource missing: " + classpathResource);
            RunScript.execute(connection, new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    public static void runScript(DataSource dataSource, String classpathResource) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            runScript(c, classpathResource);
        }
    }

    /**
     * Single statement from a resource; use for parameterized {@code INSERT} templates where placeholders are {@code ?}.
     */
    public static PreparedStatement prepareFromResource(Connection connection, String classpathResource) throws Exception {
        try (var in = H2ClasspathSql.class.getResourceAsStream(classpathResource)) {
            Objects.requireNonNull(in, "Classpath resource missing: " + classpathResource);
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return connection.prepareStatement(sql);
        }
    }
}
