package com.logmng.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 암호화된 샘플 데이터 생성 유틸리티
 * 실제 암호화 기능을 사용하여 샘플 데이터 생성
 */
public class GenerateEncryptedSampleData {
    
    private final CryptoUtil cryptoUtil;
    
    public GenerateEncryptedSampleData(CryptoUtil cryptoUtil) {
        this.cryptoUtil = cryptoUtil;
    }
    
    /**
     * 암호화된 샘플 데이터 생성
     */
    public List<SampleData> generateSampleData() {
        List<SampleData> samples = new ArrayList<>();
        
        // Sample 1: input 상태
        SampleData sample1 = new SampleData();
        sample1.application = "LDP";
        sample1.servicegroup = "EduSG";
        sample1.service = "SE10002_select";
        sample1.status = "input";
        sample1.guid = "250315142429291DAOLCS0TT0S01090000045001";
        
        // data 필드 암호화
        String plainData1 = "{\"id\":\"1110\",\"name\":\"홍길동\",\"age\":30,\"email\":\"hong@example.com\",\"phone\":\"010-1234-5678\"}";
        sample1.data = cryptoUtil.encrypt(plainData1);
        
        // datastring (JSON 내부에 암호화된 값 포함)
        String encryptedP1 = cryptoUtil.encrypt("password123");
        sample1.datastring = String.format("{\"id\":\"1110\",\"name\":\"\",\"age\":0,\"r\":\"\",\"p\":\"[%s]\"}", encryptedP1);
        
        // header 필드 암호화
        String plainHeader1 = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045001\",\"sessionId\":\"session123\"}";
        sample1.header = cryptoUtil.encrypt(plainHeader1);
        
        // headerstring
        sample1.headerstring = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045001\"}";
        
        samples.add(sample1);
        
        // Sample 2: input 상태
        SampleData sample2 = new SampleData();
        sample2.application = "LDP";
        sample2.servicegroup = "EduSG";
        sample2.service = "SE10003_insert";
        sample2.status = "input";
        sample2.guid = "250315142429291DAOLCS0TT0S01090000045002";
        
        String plainData2 = "{\"id\":\"2220\",\"name\":\"김철수\",\"age\":25,\"email\":\"kim@example.com\",\"phone\":\"010-9876-5432\"}";
        sample2.data = cryptoUtil.encrypt(plainData2);
        
        String encryptedEmail2 = cryptoUtil.encrypt("kim@example.com");
        sample2.datastring = String.format("{\"id\":\"2220\",\"name\":\"홍길동\",\"age\":30,\"email\":\"[%s]\",\"phone\":\"010-1234-5678\"}", encryptedEmail2);
        
        String plainHeader2 = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045002\",\"sessionId\":\"session456\"}";
        sample2.header = cryptoUtil.encrypt(plainHeader2);
        
        String encryptedSession2 = cryptoUtil.encrypt("session456");
        sample2.headerstring = String.format("{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045002\",\"sessionId\":\"[%s]\"}", encryptedSession2);
        
        samples.add(sample2);
        
        // Sample 3: output 상태
        SampleData sample3 = new SampleData();
        sample3.application = "LDP";
        sample3.servicegroup = "EduSG";
        sample3.service = "SE10002_select";
        sample3.status = "output";
        sample3.guid = "250315142429291DAOLCS0TT0S01090000045003";
        
        String plainData3 = "{\"result\":\"success\",\"data\":[{\"id\":\"1110\",\"name\":\"홍길동\",\"age\":30}],\"count\":1}";
        sample3.data = cryptoUtil.encrypt(plainData3);
        
        String encryptedMessage3 = cryptoUtil.encrypt("데이터 조회 성공");
        sample3.datastring = String.format("{\"result\":\"success\",\"data\":[{\"id\":\"1110\",\"name\":\"김철수\",\"age\":25}],\"count\":1,\"message\":\"[%s]\"}", encryptedMessage3);
        
        String plainHeader3 = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045003\",\"responseCode\":\"200\"}";
        sample3.header = cryptoUtil.encrypt(plainHeader3);
        
        sample3.headerstring = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045003\",\"responseCode\":\"200\"}";
        
        samples.add(sample3);
        
        // Sample 4: output 상태
        SampleData sample4 = new SampleData();
        sample4.application = "LDP";
        sample4.servicegroup = "EduSG";
        sample4.service = "SE10003_insert";
        sample4.status = "output";
        sample4.guid = "250315142429291DAOLCS0TT0S01090000045004";
        
        String plainData4 = "{\"result\":\"success\",\"insertedId\":\"2220\",\"message\":\"데이터가 성공적으로 저장되었습니다\"}";
        sample4.data = cryptoUtil.encrypt(plainData4);
        
        String encryptedTimestamp4 = cryptoUtil.encrypt("2026-02-05T15:00:00");
        sample4.datastring = String.format("{\"result\":\"success\",\"insertedId\":\"2220\",\"message\":\"데이터가 성공적으로 저장되었습니다\",\"timestamp\":\"[%s]\"}", encryptedTimestamp4);
        
        String plainHeader4 = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045004\",\"responseCode\":\"201\"}";
        sample4.header = cryptoUtil.encrypt(plainHeader4);
        
        sample4.headerstring = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045004\",\"responseCode\":\"201\"}";
        
        samples.add(sample4);
        
        // Sample 5: error 상태
        SampleData sample5 = new SampleData();
        sample5.application = "LDP";
        sample5.servicegroup = "EduSG";
        sample5.service = "SE10002_select";
        sample5.status = "error";
        sample5.guid = "250315142429291DAOLCS0TT0S01090000045005";
        
        String plainData5 = "{\"error\":\"Database connection failed\",\"code\":\"DB_ERROR\",\"details\":\"Connection timeout after 30 seconds\"}";
        sample5.data = cryptoUtil.encrypt(plainData5);
        
        String encryptedDetails5 = cryptoUtil.encrypt("데이터베이스 연결 실패: 타임아웃");
        sample5.datastring = String.format("{\"error\":\"Database connection failed\",\"code\":\"DB_ERROR\",\"details\":\"[%s]\",\"timestamp\":\"2025-01-15T14:24:29\"}", encryptedDetails5);
        
        String plainHeader5 = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045005\",\"responseCode\":\"500\",\"errorCode\":\"DB_CONNECTION_ERROR\"}";
        sample5.header = cryptoUtil.encrypt(plainHeader5);
        
        sample5.headerstring = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045005\",\"responseCode\":\"500\",\"errorCode\":\"DB_CONNECTION_ERROR\"}";
        
        samples.add(sample5);
        
        // Sample 6: error 상태
        SampleData sample6 = new SampleData();
        sample6.application = "LDP";
        sample6.servicegroup = "EduSG";
        sample6.service = "SE10004_update";
        sample6.status = "error";
        sample6.guid = "250315142429291DAOLCS0TT0S01090000045006";
        
        String plainData6 = "{\"error\":\"Validation failed\",\"code\":\"VALIDATION_ERROR\",\"fields\":[\"name\",\"email\"]}";
        sample6.data = cryptoUtil.encrypt(plainData6);
        
        String encryptedMessage6 = cryptoUtil.encrypt("유효성 검증 실패: 필수 필드 누락");
        sample6.datastring = String.format("{\"error\":\"Validation failed\",\"code\":\"VALIDATION_ERROR\",\"fields\":[\"name\",\"email\"],\"message\":\"[%s]\"}", encryptedMessage6);
        
        String plainHeader6 = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045006\",\"responseCode\":\"400\",\"errorCode\":\"VALIDATION_ERROR\"}";
        sample6.header = cryptoUtil.encrypt(plainHeader6);
        
        sample6.headerstring = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045006\",\"responseCode\":\"400\",\"errorCode\":\"VALIDATION_ERROR\"}";
        
        samples.add(sample6);
        
        // Sample 7: 다른 application (input)
        SampleData sample7 = new SampleData();
        sample7.application = "SYSTEM_B";
        sample7.servicegroup = "Group1";
        sample7.service = "SERVICE_001";
        sample7.status = "input";
        sample7.guid = "250315142429291DAOLCS0TT0S01090000045007";
        
        String plainData7 = "{\"userId\":\"user123\",\"action\":\"login\",\"password\":\"encrypted_password\"}";
        sample7.data = cryptoUtil.encrypt(plainData7);
        
        String encryptedPassword7 = cryptoUtil.encrypt("mySecretPassword123");
        sample7.datastring = String.format("{\"userId\":\"user123\",\"action\":\"login\",\"password\":\"[%s]\"}", encryptedPassword7);
        
        String plainHeader7 = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045007\",\"ipAddress\":\"192.168.1.100\"}";
        sample7.header = cryptoUtil.encrypt(plainHeader7);
        
        sample7.headerstring = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045007\",\"ipAddress\":\"192.168.1.100\"}";
        
        samples.add(sample7);
        
        // Sample 8: 다른 application (output)
        SampleData sample8 = new SampleData();
        sample8.application = "SYSTEM_B";
        sample8.servicegroup = "Group1";
        sample8.service = "SERVICE_001";
        sample8.status = "output";
        sample8.guid = "250315142429291DAOLCS0TT0S01090000045008";
        
        String plainData8 = "{\"result\":\"success\",\"token\":\"jwt_token_here\",\"expiresIn\":3600}";
        sample8.data = cryptoUtil.encrypt(plainData8);
        
        String encryptedToken8 = cryptoUtil.encrypt("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJ1c2VyMTIzIn0");
        sample8.datastring = String.format("{\"result\":\"success\",\"token\":\"[%s]\",\"expiresIn\":3600}", encryptedToken8);
        
        String plainHeader8 = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045008\",\"responseCode\":\"200\"}";
        sample8.header = cryptoUtil.encrypt(plainHeader8);
        
        sample8.headerstring = "{\"flag\":\"\\u0000\",\"inputMsgType\":\"JSON\",\"outputMsgType\":\"JSON\",\"guid\":\"250315142429291DAOLCS0TT0S01090000045008\",\"responseCode\":\"200\"}";
        
        samples.add(sample8);
        
        return samples;
    }
    
    /**
     * 샘플 데이터 클래스
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





