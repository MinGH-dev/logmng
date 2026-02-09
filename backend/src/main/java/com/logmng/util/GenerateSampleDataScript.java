package com.logmng.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 샘플 데이터 생성 스크립트
 * 애플리케이션 시작 시 실행되어 암호화된 샘플 데이터를 생성
 */
@Component
public class GenerateSampleDataScript implements CommandLineRunner {
    
    @Autowired
    private CryptoUtil cryptoUtil;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public void run(String... args) {
        System.out.println("암호화된 샘플 데이터 생성 중...");
        
        // 기존 데이터 삭제
        jdbcTemplate.update("DELETE FROM imagelog");
        System.out.println("기존 데이터 삭제 완료");
        
        GenerateEncryptedSampleData generator = new GenerateEncryptedSampleData(cryptoUtil);
        List<GenerateEncryptedSampleData.SampleData> samples = generator.generateSampleData();
        
        // 샘플 데이터 삽입
        for (int i = 0; i < samples.size(); i++) {
            GenerateEncryptedSampleData.SampleData sample = samples.get(i);
            int hoursAgo = i + 1;
            
            // PostgreSQL에서 INTERVAL을 문자열로 전달
            String sql = "INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, EXTRACT(EPOCH FROM NOW() - CAST(? AS INTERVAL)) * 1000)";
            
            jdbcTemplate.update(sql,
                sample.application,
                sample.servicegroup,
                sample.service,
                sample.status,
                sample.data,
                sample.datastring,
                sample.guid,
                sample.header,
                sample.headerstring,
                hoursAgo + " hours"
            );
        }
        
        System.out.println("✅ 암호화된 샘플 데이터 생성 완료: " + samples.size() + "건");
    }
}

