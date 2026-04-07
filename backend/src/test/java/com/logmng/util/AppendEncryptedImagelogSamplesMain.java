package com.logmng.util;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * Dev utility: idempotently appends encrypted imagelog sample row(s) without deleting existing data.
 * Uses {@link GenerateEncryptedSampleData#generateAppendEncryptedSamples()} and
 * {@link AppendEncryptedImagelogSampleAppender#insertIfAbsent(java.sql.Connection, GenerateEncryptedSampleData.SampleData, long)}.
 * <p>
 * Env (optional, aligned with {@link RegeneratePbFepEncryptedSeedMain} and {@code application.yml}):
 * {@code ENCRYPTION_KEY} / {@code APP_SECURITY_ENCRYPTION_KEY};
 * ImageLog DB: {@code APP_DATASOURCE_IMAGELOG_URL}, {@code APP_DATASOURCE_IMAGELOG_USERNAME}, {@code APP_DATASOURCE_IMAGELOG_PASSWORD},
 * or fallback {@code JDBC_URL}, {@code JDBC_USER}, {@code JDBC_PASSWORD}.
 * </p>
 * Run (from {@code backend/}):
 * <pre>
 *   mvn -q test-compile
 *   java -cp "target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
 *     com.logmng.util.AppendEncryptedImagelogSamplesMain
 * </pre>
 * Or: {@code ./scripts/append-imagelog-encrypted-samples.sh} from repo root.
 */
public final class AppendEncryptedImagelogSamplesMain {

    private AppendEncryptedImagelogSamplesMain() {
    }

    public static void main(String[] args) throws Exception {
        String key = System.getenv().getOrDefault("ENCRYPTION_KEY",
                System.getenv().getOrDefault("APP_SECURITY_ENCRYPTION_KEY", "12345678901234567890123456789012"));
        String jdbcUrl = firstNonBlank(
                System.getenv("APP_DATASOURCE_IMAGELOG_URL"),
                System.getenv("JDBC_URL"),
                "jdbc:postgresql://localhost:5432/logmng");
        String jdbcUser = firstNonBlank(
                System.getenv("APP_DATASOURCE_IMAGELOG_USERNAME"),
                System.getenv("JDBC_USER"),
                "logmng");
        String jdbcPass = firstNonBlank(
                System.getenv("APP_DATASOURCE_IMAGELOG_PASSWORD"),
                System.getenv("JDBC_PASSWORD"),
                "logmng123");

        CryptoUtil crypto = new CryptoUtil();
        setField(crypto, "encryptionKey", key);
        setField(crypto, "decryptionEnabled", true);
        setField(crypto, "failureHandling", "fallback");

        GenerateEncryptedSampleData generator = new GenerateEncryptedSampleData(crypto);
        List<GenerateEncryptedSampleData.SampleData> rows = generator.generateAppendEncryptedSamples();

        Class.forName("org.postgresql.Driver");
        long insertTime = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPass)) {
            int inserted = 0;
            for (GenerateEncryptedSampleData.SampleData row : rows) {
                int n = AppendEncryptedImagelogSampleAppender.insertIfAbsent(conn, row, insertTime);
                inserted += n;
                if (n == 0) {
                    System.out.println("Skip (already exists): guid=" + row.guid + ", status=" + row.status);
                }
            }
            System.out.println("Append encrypted imagelog samples: inserted " + inserted + " row(s), total candidates " + rows.size() + ".");
        }
    }

    private static String firstNonBlank(String a, String b, String defaultVal) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        if (b != null && !b.trim().isEmpty()) {
            return b;
        }
        return defaultVal;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
