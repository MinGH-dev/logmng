package com.logmng.util;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One-shot dev utility: re-encrypts {@code pb_send}/{@code pb_recv} {@code request_data}/{@code response_data}
 * using {@link CryptoUtil#encryptPbFepPayload(String)} (ProObject wire, no E002).
 * <p>
 * Resolves plaintext by {@link CryptoUtil#decryptLogPayload(String, CryptoUtil.LogPayloadCryptoVariant, boolean)}
 * so legacy {@code iv:hex} and existing ProObject payloads round-trip to plain, then re-encrypts.
 * </p>
 * Run (from {@code backend/}):
 * <pre>
 *   mvn -q test-compile
 *   java -cp "target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
 *     com.logmng.util.RegeneratePbFepEncryptedSeedMain
 * </pre>
 */
public final class RegeneratePbFepEncryptedSeedMain {

    private RegeneratePbFepEncryptedSeedMain() {
    }

    public static void main(String[] args) throws Exception {
        String key = System.getenv().getOrDefault("ENCRYPTION_KEY",
                System.getenv().getOrDefault("APP_SECURITY_ENCRYPTION_KEY", "12345678901234567890123456789012"));
        String jdbcUrl = System.getenv().getOrDefault("JDBC_URL", "jdbc:postgresql://localhost:5432/logmng");
        String jdbcUser = System.getenv().getOrDefault("JDBC_USER", "logmng");
        String jdbcPass = System.getenv().getOrDefault("JDBC_PASSWORD", "logmng123");

        CryptoUtil crypto = new CryptoUtil();
        setField(crypto, "encryptionKey", key);
        setField(crypto, "decryptionEnabled", true);
        setField(crypto, "failureHandling", "fallback");

        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPass)) {
            int send = regenerateTable(conn, crypto, "pb_send");
            int recv = regenerateTable(conn, crypto, "pb_recv");
            System.out.println("Updated pb_send rows: " + send + ", pb_recv rows: " + recv);
        }
    }

    private static int regenerateTable(Connection conn, CryptoUtil crypto, String table) throws Exception {
        int updated = 0;
        String select = "SELECT id, request_data, response_data FROM " + table + " ORDER BY id";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(select);
             PreparedStatement up = conn.prepareStatement(
                     "UPDATE " + table + " SET request_data = ?, response_data = ? WHERE id = ?")) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String reqPlain = toPlain(crypto, rs.getString("request_data"));
                String resPlain = toPlain(crypto, rs.getString("response_data"));
                String encReq = crypto.encryptPbFepPayload(reqPlain == null ? "" : reqPlain);
                String encRes = crypto.encryptPbFepPayload(resPlain == null ? "" : resPlain);
                up.setString(1, encReq);
                up.setString(2, encRes);
                up.setLong(3, id);
                updated += up.executeUpdate();
            }
        }
        return updated;
    }

    /**
     * Best-effort: decrypt existing log payload (ProObject PB or legacy); fallback leaves string as-is (plaintext).
     * Plain JSON rows (dev seed) skip decrypt to avoid noisy failure logs.
     */
    private static String toPlain(CryptoUtil crypto, String stored) {
        if (stored == null) {
            return "";
        }
        String t = stored.trim();
        if (!t.isEmpty() && (t.startsWith("{") || t.startsWith("["))) {
            return stored;
        }
        return crypto.decryptLogPayload(stored, CryptoUtil.LogPayloadCryptoVariant.PB_FEP, true);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
