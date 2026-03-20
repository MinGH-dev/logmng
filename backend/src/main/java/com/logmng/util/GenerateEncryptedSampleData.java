package com.logmng.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates imagelog sample data: a mix of rows without encrypted content (plain/empty)
 * and rows with encrypted or bracket-wrapped content. Used by GenerateSampleDataScript
 * for startup seed when imagelog is empty. Target total ~100 rows (95–105).
 */
public class GenerateEncryptedSampleData {

    /** Target total sample rows (within 95–105 per requirement). */
    public static final int TARGET_TOTAL = 100;
    /** Number of rows with no encrypted data (plain or empty data/datastring/header/headerstring). */
    public static final int NON_ENCRYPTED_COUNT = 20;
    /** Number of rows with encrypted or bracket-wrapped content. */
    public static final int ENCRYPTED_COUNT = TARGET_TOTAL - NON_ENCRYPTED_COUNT;

    private final CryptoUtil cryptoUtil;

    public GenerateEncryptedSampleData(CryptoUtil cryptoUtil) {
        this.cryptoUtil = cryptoUtil;
    }

    /**
     * Generates approximately 100 sample rows: first {@value #NON_ENCRYPTED_COUNT} rows
     * with plain/empty sensitive fields, then {@value #ENCRYPTED_COUNT} rows with
     * encrypted or bracket-wrapped content.
     */
    public List<SampleData> generateSampleData() {
        List<SampleData> samples = new ArrayList<>(TARGET_TOTAL);
        for (int i = 0; i < NON_ENCRYPTED_COUNT; i++) {
            samples.add(buildPlainSample(i));
        }
        for (int i = 0; i < ENCRYPTED_COUNT; i++) {
            samples.add(buildEncryptedSample(i));
        }
        return samples;
    }

    /**
     * One row with no encrypted data: data, datastring, header, headerstring are plain or empty.
     */
    private SampleData buildPlainSample(int index) {
        SampleData s = new SampleData();
        s.application = index % 2 == 0 ? "LDP" : "SYSTEM_B";
        s.servicegroup = "EduSG";
        s.service = "SE10002_select";
        s.status = index % 3 == 0 ? "input" : (index % 3 == 1 ? "output" : "error");
        s.guid = "250315142429291DAOLCS0TT0S01090000045" + String.format("%03d", index + 1);
        s.data = "{\"id\":\"plain-" + index + "\",\"name\":\"plain\",\"age\":0}";
        s.datastring = "{\"id\":\"plain\",\"name\":\"\",\"age\":0}";
        s.header = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + s.guid + "\"}";
        s.headerstring = "{\"flag\":\"\\u0000\",\"outputMsgType\":\"JSON\",\"guid\":\"" + s.guid + "\"}";
        return s;
    }

    /**
     * One row with encrypted or bracket-wrapped content. Cycles through 8 templates.
     */
    private SampleData buildEncryptedSample(int index) {
        int t = index % 8;
        int id = NON_ENCRYPTED_COUNT + index + 1;
        String guid = "250315142429291DAOLCS0TT0S01090000045" + String.format("%03d", id);
        SampleData s = new SampleData();
        s.guid = guid;
        switch (t) {
            case 0:
                s.application = "LDP";
                s.servicegroup = "EduSG";
                s.service = "SE10002_select";
                s.status = "input";
                s.data = cryptoUtil.encrypt("{\"id\":\"1110\",\"name\":\"홍길동\",\"age\":30,\"email\":\"hong@example.com\"}");
                s.datastring = String.format("{\"id\":\"1110\",\"name\":\"\",\"age\":0,\"p\":\"[%s]\"}", cryptoUtil.encrypt("password123"));
                s.header = cryptoUtil.encrypt("{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"sessionId\":\"session123\"}");
                s.headerstring = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + guid + "\"}";
                break;
            case 1:
                s.application = "LDP";
                s.servicegroup = "EduSG";
                s.service = "SE10003_insert";
                s.status = "input";
                s.data = cryptoUtil.encrypt("{\"id\":\"2220\",\"name\":\"김철수\",\"age\":25,\"email\":\"kim@example.com\"}");
                s.datastring = String.format("{\"id\":\"2220\",\"name\":\"홍길동\",\"email\":\"[%s]\"}", cryptoUtil.encrypt("kim@example.com"));
                s.header = cryptoUtil.encrypt("{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"sessionId\":\"session456\"}");
                s.headerstring = String.format("{\"flag\":\"\\u0000\",\"guid\":\"" + guid + "\",\"sessionId\":\"[%s]\"}", cryptoUtil.encrypt("session456"));
                break;
            case 2:
                s.application = "LDP";
                s.servicegroup = "EduSG";
                s.service = "SE10002_select";
                s.status = "output";
                s.data = cryptoUtil.encrypt("{\"result\":\"success\",\"data\":[{\"id\":\"1110\",\"name\":\"홍길동\",\"age\":30}],\"count\":1}");
                s.datastring = String.format("{\"result\":\"success\",\"count\":1,\"message\":\"[%s]\"}", cryptoUtil.encrypt("데이터 조회 성공"));
                s.header = cryptoUtil.encrypt("{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"responseCode\":\"200\"}");
                s.headerstring = "{\"flag\":\"\\u0000\",\"outputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"responseCode\":\"200\"}";
                break;
            case 3:
                s.application = "LDP";
                s.servicegroup = "EduSG";
                s.service = "SE10003_insert";
                s.status = "output";
                s.data = cryptoUtil.encrypt("{\"result\":\"success\",\"insertedId\":\"2220\",\"message\":\"저장되었습니다\"}");
                s.datastring = String.format("{\"result\":\"success\",\"insertedId\":\"2220\",\"timestamp\":\"[%s]\"}", cryptoUtil.encrypt("2026-02-05T15:00:00"));
                s.header = cryptoUtil.encrypt("{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"responseCode\":\"201\"}");
                s.headerstring = "{\"flag\":\"\\u0000\",\"outputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"responseCode\":\"201\"}";
                break;
            case 4:
                s.application = "LDP";
                s.servicegroup = "EduSG";
                s.service = "SE10002_select";
                s.status = "error";
                s.data = cryptoUtil.encrypt("{\"error\":\"Database connection failed\",\"code\":\"DB_ERROR\"}");
                s.datastring = String.format("{\"error\":\"DB_ERROR\",\"details\":\"[%s]\"}", cryptoUtil.encrypt("데이터베이스 연결 실패: 타임아웃"));
                s.header = cryptoUtil.encrypt("{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"responseCode\":\"500\"}");
                s.headerstring = "{\"flag\":\"\\u0000\",\"guid\":\"" + guid + "\",\"responseCode\":\"500\",\"errorCode\":\"DB_CONNECTION_ERROR\"}";
                break;
            case 5:
                s.application = "LDP";
                s.servicegroup = "EduSG";
                s.service = "SE10004_update";
                s.status = "error";
                s.data = cryptoUtil.encrypt("{\"error\":\"Validation failed\",\"code\":\"VALIDATION_ERROR\",\"fields\":[\"name\",\"email\"]}");
                s.datastring = String.format("{\"error\":\"VALIDATION_ERROR\",\"message\":\"[%s]\"}", cryptoUtil.encrypt("유효성 검증 실패: 필수 필드 누락"));
                s.header = cryptoUtil.encrypt("{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"responseCode\":\"400\"}");
                s.headerstring = "{\"flag\":\"\\u0000\",\"guid\":\"" + guid + "\",\"responseCode\":\"400\",\"errorCode\":\"VALIDATION_ERROR\"}";
                break;
            case 6:
                s.application = "SYSTEM_B";
                s.servicegroup = "Group1";
                s.service = "SERVICE_001";
                s.status = "input";
                s.data = cryptoUtil.encrypt("{\"userId\":\"user123\",\"action\":\"login\",\"password\":\"encrypted_password\"}");
                s.datastring = String.format("{\"userId\":\"user123\",\"action\":\"login\",\"password\":\"[%s]\"}", cryptoUtil.encrypt("mySecretPassword123"));
                s.header = cryptoUtil.encrypt("{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"ipAddress\":\"192.168.1.100\"}");
                s.headerstring = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"ipAddress\":\"192.168.1.100\"}";
                break;
            default: // 7
                s.application = "SYSTEM_B";
                s.servicegroup = "Group1";
                s.service = "SERVICE_001";
                s.status = "output";
                s.data = cryptoUtil.encrypt("{\"result\":\"success\",\"token\":\"jwt_token_here\",\"expiresIn\":3600}");
                s.datastring = String.format("{\"result\":\"success\",\"token\":\"[%s]\",\"expiresIn\":3600}", cryptoUtil.encrypt("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
                s.header = cryptoUtil.encrypt("{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"responseCode\":\"200\"}");
                s.headerstring = "{\"flag\":\"\\u0000\",\"outputMsgType\":\"JSON\",\"guid\":\"" + guid + "\",\"responseCode\":\"200\"}";
                break;
        }
        return s;
    }

    /**
     * Sample row for imagelog: application, servicegroup, service, status, data, datastring, guid, header, headerstring.
     */
    public static class SampleData {
        public String application;
        public String servicegroup;
        public String service;
        public String status;
        public String data;
        public String datastring;
        public String guid;
        public String header;
        public String headerstring;
    }
}
