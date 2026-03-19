package com.logmng.util;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sample data seed script for imagelog.
 * Runs on application startup. Strategy: insert sample rows only when the imagelog table
 * is empty; never delete existing rows so that restarts preserve past data.
 * Uses the ImageLog datasource (B), or primary when dev fallback is active.
 */
@Component
public class GenerateSampleDataScript implements CommandLineRunner {

    private final CryptoUtil cryptoUtil;
    private final JdbcTemplate imagelogJdbcTemplate;

    public GenerateSampleDataScript(CryptoUtil cryptoUtil,
                                    @Qualifier("imagelogJdbcTemplate") JdbcTemplate imagelogJdbcTemplate) {
        this.cryptoUtil = cryptoUtil;
        this.imagelogJdbcTemplate = imagelogJdbcTemplate;
    }

    @Override
    public void run(String... args) {
        Long count = imagelogJdbcTemplate.queryForObject("SELECT COUNT(*) FROM imagelog", Long.class);
        if (count != null && count > 0) {
            System.out.println("imagelog already has " + count + " row(s); skipping sample seed (preserve existing data).");
            return;
        }

        System.out.println("imagelog is empty; inserting sample data...");
        GenerateEncryptedSampleData generator = new GenerateEncryptedSampleData(cryptoUtil);
        List<GenerateEncryptedSampleData.SampleData> samples = generator.generateSampleData();

        long nowMs = System.currentTimeMillis();
        for (int i = 0; i < samples.size(); i++) {
            GenerateEncryptedSampleData.SampleData sample = samples.get(i);
            int hoursAgo = i + 1;
            long insertTime = nowMs - (hoursAgo * 3600L * 1000);
            String sql = "INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            imagelogJdbcTemplate.update(sql,
                    sample.application,
                    sample.servicegroup,
                    sample.service,
                    sample.status,
                    sample.data,
                    sample.datastring,
                    sample.guid,
                    sample.header,
                    sample.headerstring,
                    insertTime);
        }

        System.out.println("Sample imagelog data inserted: " + samples.size() + " rows.");
    }
}
