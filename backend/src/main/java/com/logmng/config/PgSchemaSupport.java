package com.logmng.config;

import java.util.LinkedHashSet;
import java.util.regex.Pattern;

/**
 * Validates PostgreSQL schema identifiers and builds {@code SET search_path} init SQL for Hikari.
 * Used by primary (sys ± PB), optional dedicated PB pool, and ImageLog. Identifiers are restricted to unquoted PG rules
 * to avoid SQL injection from configuration.
 */
public final class PgSchemaSupport {

    private static final Pattern IDENT = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private PgSchemaSupport() {
    }

    public static String requireValidSchemaName(String name) {
        if (name == null || name.isBlank()) {
            return "public";
        }
        String trimmed = name.trim();
        if (!IDENT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid PostgreSQL schema name: " + name);
        }
        return trimmed;
    }

    /**
     * Primary pool: resolve app tables (sys) first, then PB tables (pb), then public fallback.
     */
    public static String buildPrimarySearchPathInitSql(String sysSchema, String pbSchema) {
        String s1 = requireValidSchemaName(sysSchema);
        String s2 = requireValidSchemaName(pbSchema);
        LinkedHashSet<String> order = new LinkedHashSet<>();
        order.add(s1);
        order.add(s2);
        order.add("public");
        return "SET search_path TO " + String.join(", ", order);
    }

    /**
     * Primary pool when PB FEP uses a dedicated JDBC URL: resolve system tables only, then {@code public}.
     * PB tables are accessed via the {@code pbDataSource} pool (PB schema + public).
     */
    public static String buildSysOnlySearchPathInitSql(String sysSchema) {
        String s1 = requireValidSchemaName(sysSchema);
        LinkedHashSet<String> order = new LinkedHashSet<>();
        order.add(s1);
        order.add("public");
        return "SET search_path TO " + String.join(", ", order);
    }

    public static String buildImagelogSearchPathInitSql(String imagelogSchema) {
        return buildSchemaFirstThenPublicSearchPath(imagelogSchema);
    }

    /**
     * Dedicated PB FEP pool: PB schema first (unqualified {@code pb_send}/{@code pb_recv}), then {@code public}.
     */
    public static String buildPbSearchPathInitSql(String pbSchema) {
        return buildSchemaFirstThenPublicSearchPath(pbSchema);
    }

    private static String buildSchemaFirstThenPublicSearchPath(String schema) {
        String s = requireValidSchemaName(schema);
        LinkedHashSet<String> order = new LinkedHashSet<>();
        order.add(s);
        order.add("public");
        return "SET search_path TO " + String.join(", ", order);
    }
}
