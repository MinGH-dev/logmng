package com.logmng.util;

import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates local decrypt test SQL files (dev key 12345678901234567890123456789012):
 * <ul>
 *   <li>{@code init-data-local-decrypt-test-imagelog.sql} — ImageLog (E002 + bracket JSON)</li>
 *   <li>{@code init-data-local-decrypt-test-pbfep.sql} — PB FEP (ProObject without E002 prefix)</li>
 * </ul>
 * Run from {@code backend/}:
 * <pre>
 * mvn -q test-compile && java -cp "target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt && cat /tmp/cp.txt)" \
 *   com.logmng.util.LocalDecryptSampleSeedGenerator \
 *   src/main/resources/db/init-data-local-decrypt-test-imagelog.sql \
 *   src/main/resources/db/init-data-local-decrypt-test-pbfep.sql
 * </pre>
 */
public final class LocalDecryptSampleSeedGenerator {

    private static final String DEV_KEY_32 = "12345678901234567890123456789012";

    public static void main(String[] args) throws Exception {
        CryptoUtil u = newCryptoUtil(DEV_KEY_32);
        if (args.length >= 1) {
            Path out = Paths.get(args[0]);
            Files.writeString(out, generateFullSeedSql(u), StandardCharsets.UTF_8);
            System.out.println("Wrote: " + out.toAbsolutePath());
        } else {
            System.out.print(generateFullSeedSql(u));
        }
        if (args.length >= 2) {
            Path outPb = Paths.get(args[1]);
            Files.writeString(outPb, generatePbfepSeedSql(u), StandardCharsets.UTF_8);
            System.out.println("Wrote: " + outPb.toAbsolutePath());
        }
    }

    private static String enc(CryptoUtil u, String plain) {
        String c = u.encryptImageLogPayload(plain);
        String round = u.decryptLogPayload(c, CryptoUtil.LogPayloadCryptoVariant.IMAGE_LOG);
        if (!plain.equals(round)) {
            throw new IllegalStateException("round-trip failed for: " + plain);
        }
        return c;
    }

    private static String encPb(CryptoUtil u, String plain) {
        String c = u.encryptPbFepPayload(plain);
        String round = u.decryptLogPayload(c, CryptoUtil.LogPayloadCryptoVariant.PB_FEP);
        if (!plain.equals(round)) {
            throw new IllegalStateException("PB FEP round-trip failed for: " + plain);
        }
        return c;
    }

    private static String jEnc(CryptoUtil u, String plain) {
        return enc(u, plain).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** JSON string field: "key":"[cipher]" */
    private static String bracket(CryptoUtil u, String plain) {
        return "[" + enc(u, plain) + "]";
    }

    private static String generateFullSeedSql(CryptoUtil u) {
        // --- IM-0001 (same semantics as before; ciphertexts change each run — OK for seed) ---
        String pD1 = "LOCAL-DECRYPT-PLAIN-DATA-0001";
        String pH1 = "LOCAL-DECRYPT-PLAIN-HDR-0001";
        String pP1 = "한글복호화검증-필드p-0001";
        String pBlob1 = "LOCAL-DECRYPT-PLAIN-BLOB-OUT-0001";
        String pRawOut1 = "LOCAL-DECRYPT-PLAIN-RAW-OUT-0001";
        String pHdrOut1 = "LOCAL-DECRYPT-PLAIN-HDR-OUT-0001";
        String eD1 = enc(u, pD1);
        String eH1 = enc(u, pH1);
        String eP1 = enc(u, pP1);
        String eBlob1 = enc(u, pBlob1);
        String eRawOut1 = enc(u, pRawOut1);
        String eHdrOut1 = enc(u, pHdrOut1);
        String ds1in = String.format("{\"id\":\"LD1\",\"name\":\"시드평문이름\",\"p\":\"[%s]\"}", jEnc(u, pP1));
        String ds1out = String.format("{\"result\":\"ok\",\"blob\":\"[%s]\"}", jEnc(u, pBlob1));

        StringBuilder sb = new StringBuilder();
        sb.append("-- =============================================================================\n");
        sb.append("-- LOCAL / DEVELOPMENT ONLY — ProObject AES (E002+Base64) via dev key\n");
        sb.append("-- 12345678901234567890123456789012 (application.yml / ENCRYPTION_KEY).\n");
        sb.append("-- Regenerate: cd backend && mvn -q test-compile && java -cp \"target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt && cat /tmp/cp.txt)\" \\\n");
        sb.append("--   com.logmng.util.LocalDecryptSampleSeedGenerator src/main/resources/db/init-data-local-decrypt-test-imagelog.sql\n");
        sb.append("--\n");
        sb.append("-- Plaintext for each field: see generateFullSeedSql() in this generator class (source).\n");
        sb.append("-- =============================================================================\n\n");

        sb.append("-- Replace prior local decrypt test rows (re-seed safe)\n");
        sb.append("DELETE FROM imagelog WHERE guid LIKE 'LOCAL-DECRYPT-TST-IM-%';\n\n");

        // 0001 input / output
        ins(sb, "LDP", "EduSG", "SE10002_select", "input", eD1, ds1in, "LOCAL-DECRYPT-TST-IM-0001", eH1,
                "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"LOCAL-DECRYPT-TST-IM-0001\"}", "5 minutes");
        ins(sb, "LDP", "EduSG", "SE10002_select", "output", eRawOut1, ds1out, "LOCAL-DECRYPT-TST-IM-0001", eHdrOut1,
                "{\"flag\":\"\\u0000\",\"responseCode\":\"200\"}", "6 minutes");

        // 0002
        String ds2 = "{\"user\":\"tester\",\"secret\":\"" + bracket(u, "LOCAL-DECRYPT-PT-SECRET-0002") + "\"}";
        String hs2 = "{\"token\":\"" + bracket(u, "LOCAL-DECRYPT-PT-TOKEN-0002") + "\"}";
        ins(sb, "LDP", "EduSG", "SE10003_insert", "input",
                enc(u, "LOCAL-DECRYPT-PT-DATA-0002"), ds2, "LOCAL-DECRYPT-TST-IM-0002",
                enc(u, "LOCAL-DECRYPT-PT-HDR-0002"), hs2, "7 minutes");

        // 0003 error
        String ds3 = "{\"code\":\"ERR\",\"detail\":\"" + bracket(u, "LOCAL-DECRYPT-PT-ERR-DETAIL-0003") + "\"}";
        ins(sb, "SYSTEM_B", "GrpLoc", "SVC_DEC", "error",
                enc(u, "LOCAL-DECRYPT-PT-ERR-DATA-0003"), ds3, "LOCAL-DECRYPT-TST-IM-0003",
                enc(u, "LOCAL-DECRYPT-PT-ERR-HDR-0003"),
                "{\"guid\":\"LOCAL-DECRYPT-TST-IM-0003\",\"code\":\"500\"}", "8 minutes");

        // 0004 input — nested bracket (inner long enough for UI “encrypted style”)
        String ds4 = "{\"mixed\":\"plain\",\"nested\":{\"c\":\"" + bracket(u, "LOCAL-DECRYPT-PT-NESTED-C-0004") + "\"}}";
        ins(sb, "APP_C", "GrpLoc", "SVC_MIX", "input",
                enc(u, "LOCAL-DECRYPT-PT-DATA-0004"), ds4, "LOCAL-DECRYPT-TST-IM-0004",
                enc(u, "LOCAL-DECRYPT-PT-HDR-0004-EMPTY"),
                "{\"phase\":\"request\",\"sid\":\"" + bracket(u, "LOCAL-DECRYPT-PT-SID-0004") + "\"}", "9 minutes");

        // 0005 input / output
        String ds5in = "{\"id\":\"9001\",\"name\":\"로컬테스트\",\"diff\":\"" + bracket(u, "LOCAL-DECRYPT-PT-DIFF-0005") + "\"}";
        String ds5out = "{\"updated\":1,\"audit\":\"" + bracket(u, "LOCAL-DECRYPT-PT-AUDIT-0005") + "\"}";
        ins(sb, "LDP", "EduSG", "SE10004_update", "input",
                enc(u, "pre_update_plain_0005"), ds5in, "LOCAL-DECRYPT-TST-IM-0005",
                enc(u, "LOCAL-DECRYPT-PT-H5-0005"), "{\"inputMsgType\":\"JSON\"}", "10 minutes");
        ins(sb, "LDP", "EduSG", "SE10004_update", "output",
                enc(u, "post_update_plain_0005"), ds5out, "LOCAL-DECRYPT-TST-IM-0005",
                enc(u, "LOCAL-DECRYPT-PT-H5O-0005"), "{\"responseCode\":\"200\"}", "11 minutes");

        // 0006
        String ds6 = "{\"span\":\"" + bracket(u, "LOCAL-DECRYPT-PT-SPAN-0006") + "\",\"parent\":\"" + bracket(u, "LOCAL-DECRYPT-PT-PARENT-0006") + "\"}";
        ins(sb, "MONITOR", "Ops", "Trace", "input",
                enc(u, "LOCAL-DECRYPT-PT-TRACE-DATA-0006"), ds6, "LOCAL-DECRYPT-TST-IM-0006",
                enc(u, "LOCAL-DECRYPT-PT-TRACE-HDR-0006"), "{}", "12 minutes");

        // 0007
        String ds7 = "{\"batchId\":\"b1\",\"checksum\":\"" + bracket(u, "LOCAL-DECRYPT-PT-CHK-0007") + "\"}";
        ins(sb, "BATCH", "Job", "J_DEC", "output",
                enc(u, "LOCAL-DECRYPT-PT-BATCH-DATA-0007"), ds7, "LOCAL-DECRYPT-TST-IM-0007",
                enc(u, "LOCAL-DECRYPT-PT-BATCH-HDR-0007"), "{\"done\":true}", "13 minutes");

        // 0008 dup guid
        String ds8in = "{\"pair\":\"A\",\"payload\":\"" + bracket(u, "LOCAL-DECRYPT-PT-PAYLOAD-A-0008") + "\"}";
        String ds8out = "{\"pair\":\"B\",\"payload\":\"" + bracket(u, "LOCAL-DECRYPT-PT-PAYLOAD-B-0008") + "\"}";
        ins(sb, "LDP", "EduSG", "SE10002_select", "input",
                enc(u, "dup_pair_a_plain_0008"), ds8in, "LOCAL-DECRYPT-TST-IM-0008",
                enc(u, "LOCAL-DECRYPT-PT-H-0008-IN"), "{}", "14 minutes");
        ins(sb, "LDP", "EduSG", "SE10002_select", "output",
                enc(u, "dup_pair_b_plain_0008"), ds8out, "LOCAL-DECRYPT-TST-IM-0008",
                enc(u, "LOCAL-DECRYPT-PT-H-0008-OUT"), "{}", "15 minutes");

        // 0009
        String ds9 = "{\"id\":\"" + bracket(u, "LOCAL-DECRYPT-PT-ID-0009") + "\"}";
        ins(sb, "LDP", "EduSG", "SE10005_delete", "input",
                enc(u, "LOCAL-DECRYPT-PT-DEL-DATA-0009"), ds9, "LOCAL-DECRYPT-TST-IM-0009",
                enc(u, "LOCAL-DECRYPT-PT-DEL-HDR-0009"),
                "{\"reason\":\"" + bracket(u, "LOCAL-DECRYPT-PT-REASON-0009") + "\"}", "16 minutes");

        // 0010 whitespace in JSON value (bracket decrypt path)
        String ds10 = "{\"ws\":\"  " + bracket(u, "LOCAL-DECRYPT-PT-WS-0010") + "  \\t\"}";
        ins(sb, "LDP", "EduSG", "SE10002_select", "input",
                enc(u, "whitespace_probe_data_0010"), ds10, "LOCAL-DECRYPT-TST-IM-0010",
                enc(u, "LOCAL-DECRYPT-PT-H-0010"), "{\"note\":\"bracket edge cases\"}", "17 minutes");

        // 0011 array
        String ds11 = "{\"items\":[{\"k\":\"" + bracket(u, "LOCAL-DECRYPT-PT-ITEM0-0011") + "\"},{\"k\":\"" + bracket(u, "LOCAL-DECRYPT-PT-ITEM1-0011") + "\"}]}";
        ins(sb, "LDP", "EduSG", "SE10002_select", "output",
                enc(u, "longish_sim_data_0011"), ds11, "LOCAL-DECRYPT-TST-IM-0011",
                enc(u, "LOCAL-DECRYPT-PT-LONG-H-0011"), "{\"responseCode\":\"200\"}", "18 minutes");

        return sb.toString();
    }

    /** Wire-style fixed widths: IP 12, log channel 6, msg 4. */
    private static String padRight(String s, int len) {
        if (s == null) {
            s = "";
        }
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        StringBuilder b = new StringBuilder(s);
        while (b.length() < len) {
            b.append(' ');
        }
        return b.toString();
    }

    private static String generatePbfepSeedSql(CryptoUtil u) {
        String reqS1 = encPb(u, "LOCAL-PB-SEND1-REQUEST-PLAIN");
        String rspS1 = encPb(u, "LOCAL-PB-SEND1-RESPONSE-PLAIN");
        String reqS2 = encPb(u, "LOCAL-PB-SEND2-REQUEST-PLAIN");
        String rspS2 = encPb(u, "LOCAL-PB-SEND2-RESPONSE-PLAIN");
        String reqR1 = encPb(u, "LOCAL-PB-RECV1-REQUEST-PLAIN");
        String rspR1 = encPb(u, "LOCAL-PB-RECV1-RESPONSE-PLAIN");
        String reqR2 = encPb(u, "LOCAL-PB-RECV2-REQUEST-PLAIN");
        String rspR2 = encPb(u, "LOCAL-PB-RECV2-RESPONSE-PLAIN");

        String pubIp = padRight("127.0.0.1", 12);
        String prtIp = padRight("127.0.0.1", 12);
        String logChPc = padRight("PC", 6);
        String logChMob = padRight("MOB", 6);

        StringBuilder sb = new StringBuilder();
        sb.append("-- =============================================================================\n");
        sb.append("-- LOCAL / DEVELOPMENT ONLY — PB FEP (pb_send / pb_recv). ProObject AES without E002;\n");
        sb.append("-- decrypt with LogPayloadCryptoVariant.PB_FEP and dev key 12345678901234567890123456789012.\n");
        sb.append("-- Rows marked reserve = 'LDPT' (4-char tag) for idempotent delete.\n");
        sb.append("-- =============================================================================\n\n");
        sb.append("DO $$\nBEGIN\n");
        sb.append("  IF to_regclass('pb_send') IS NULL THEN\n");
        sb.append("    RAISE NOTICE 'init-data-local-decrypt-test-pbfep: pb_send not found — skip.';\n");
        sb.append("  ELSE\n");
        sb.append("    DELETE FROM pb_send WHERE reserve = 'LDPT';\n");
        sb.append("    INSERT INTO pb_send (\n");
        sb.append("      log_timestamp, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,\n");
        sb.append("      msg_code, data, bmsg, reserve, con_key, wire_ts\n");
        sb.append("    )\n");
        sb.append("    VALUES (\n");
        sb.append("      TIMESTAMP '2026-04-13 10:20:00', '99', 'SLDECT01', 'local_decrypt',\n");
        sb.append("      ").append(sqlQuote(pubIp)).append(", ").append(sqlQuote(prtIp)).append(", ").append(sqlQuote(logChPc)).append(",\n");
        sb.append("      '0200',\n");
        sb.append("      ").append(sqlQuote(reqS1)).append(",\n");
        sb.append("      ").append(sqlQuote(rspS1)).append(",\n");
        sb.append("      'LDPT',\n");
        sb.append("      rpad('LOCAL-DEC-SND1', 18),\n");
        sb.append("      '2026-04-13 10:20:00'\n");
        sb.append("    );\n");
        sb.append("    INSERT INTO pb_send (\n");
        sb.append("      log_timestamp, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,\n");
        sb.append("      msg_code, data, bmsg, reserve, con_key, wire_ts\n");
        sb.append("    )\n");
        sb.append("    VALUES (\n");
        sb.append("      TIMESTAMP '2026-04-13 10:25:00', '99', 'SLDECT02', 'local_decrypt',\n");
        sb.append("      ").append(sqlQuote(pubIp)).append(", ").append(sqlQuote(prtIp)).append(", ").append(sqlQuote(logChMob)).append(",\n");
        sb.append("      '0201',\n");
        sb.append("      ").append(sqlQuote(reqS2)).append(",\n");
        sb.append("      ").append(sqlQuote(rspS2)).append(",\n");
        sb.append("      'LDPT',\n");
        sb.append("      rpad('LOCAL-DEC-SND2', 18),\n");
        sb.append("      '2026-04-13 10:25:00'\n");
        sb.append("    );\n");
        sb.append("  END IF;\n\n");
        sb.append("  IF to_regclass('pb_recv') IS NULL THEN\n");
        sb.append("    RAISE NOTICE 'init-data-local-decrypt-test-pbfep: pb_recv not found — skip.';\n");
        sb.append("  ELSE\n");
        sb.append("    DELETE FROM pb_recv WHERE reserve = 'LDPT';\n");
        sb.append("    INSERT INTO pb_recv (\n");
        sb.append("      log_timestamp, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,\n");
        sb.append("      msg_code, data, bmsg, reserve, con_key, wire_ts\n");
        sb.append("    )\n");
        sb.append("    VALUES (\n");
        sb.append("      TIMESTAMP '2026-04-13 10:21:00', '99', 'RLDECT01', 'local_decrypt',\n");
        sb.append("      ").append(sqlQuote(pubIp)).append(", ").append(sqlQuote(prtIp)).append(", ").append(sqlQuote(logChPc)).append(",\n");
        sb.append("      '0200',\n");
        sb.append("      ").append(sqlQuote(reqR1)).append(",\n");
        sb.append("      ").append(sqlQuote(rspR1)).append(",\n");
        sb.append("      'LDPT',\n");
        sb.append("      rpad('LOCAL-DEC-RCV1', 18),\n");
        sb.append("      '2026-04-13 10:21:00'\n");
        sb.append("    );\n");
        sb.append("    INSERT INTO pb_recv (\n");
        sb.append("      log_timestamp, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,\n");
        sb.append("      msg_code, data, bmsg, reserve, con_key, wire_ts\n");
        sb.append("    )\n");
        sb.append("    VALUES (\n");
        sb.append("      TIMESTAMP '2026-04-13 10:26:00', '99', 'RLDECT02', 'local_decrypt',\n");
        sb.append("      ").append(sqlQuote(pubIp)).append(", ").append(sqlQuote(prtIp)).append(", ").append(sqlQuote(logChMob)).append(",\n");
        sb.append("      '0200',\n");
        sb.append("      ").append(sqlQuote(reqR2)).append(",\n");
        sb.append("      ").append(sqlQuote(rspR2)).append(",\n");
        sb.append("      'LDPT',\n");
        sb.append("      rpad('LOCAL-DEC-RCV2', 18),\n");
        sb.append("      '2026-04-13 10:26:00'\n");
        sb.append("    );\n");
        sb.append("  END IF;\n");
        sb.append("END $$;\n");
        return sb.toString();
    }

    private static void ins(StringBuilder sb,
            String app, String sg, String svc, String status,
            String dataEnc, String datastring, String guid, String headerEnc, String headerstring,
            String intervalLabel) {
        sb.append("INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)\n");
        sb.append("VALUES (\n");
        sb.append("  ").append(sqlQuote(app)).append(",\n");
        sb.append("  ").append(sqlQuote(sg)).append(",\n");
        sb.append("  ").append(sqlQuote(svc)).append(",\n");
        sb.append("  ").append(sqlQuote(status)).append(",\n");
        sb.append("  ").append(sqlQuote(dataEnc)).append(",\n");
        sb.append("  ").append(sqlQuote(datastring)).append(",\n");
        sb.append("  ").append(sqlQuote(guid)).append(",\n");
        sb.append("  ").append(sqlQuote(headerEnc)).append(",\n");
        sb.append("  ").append(sqlQuote(headerstring)).append(",\n");
        sb.append("  (EXTRACT(EPOCH FROM NOW() - INTERVAL '").append(intervalLabel).append("') * 1000)::bigint\n");
        sb.append(");\n\n");
    }

    private static String sqlQuote(String s) {
        if (s == null) {
            return "NULL";
        }
        return "'" + s.replace("'", "''") + "'";
    }

    private static CryptoUtil newCryptoUtil(String key) {
        CryptoUtil cu = new CryptoUtil();
        ReflectionTestUtils.setField(cu, "encryptionKey", key);
        ReflectionTestUtils.setField(cu, "decryptionEnabled", true);
        ReflectionTestUtils.setField(cu, "failureHandling", "fallback");
        return cu;
    }
}
