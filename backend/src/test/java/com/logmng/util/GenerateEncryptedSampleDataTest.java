package com.logmng.util;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GenerateEncryptedSampleData (TC-01, TC-03, TC-04).
 * Requirement: docs/requirements/20260318-image-log-sample-data-preserve.md
 */
class GenerateEncryptedSampleDataTest {

    @Test
    void generateSampleData_returnsApproximately100Rows() {
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "01234567890123456789012345678901"); // 32 bytes for AES
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);

        GenerateEncryptedSampleData generator = new GenerateEncryptedSampleData(cryptoUtil);
        List<GenerateEncryptedSampleData.SampleData> samples = generator.generateSampleData();

        assertThat(samples).hasSize(GenerateEncryptedSampleData.TARGET_TOTAL);
        assertThat(samples.size()).isBetween(95, 105);
    }

    @Test
    void generateSampleData_includesRowsWithoutEncryptedData() {
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "01234567890123456789012345678901"); // 32 bytes for AES
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);

        GenerateEncryptedSampleData generator = new GenerateEncryptedSampleData(cryptoUtil);
        List<GenerateEncryptedSampleData.SampleData> samples = generator.generateSampleData();

        boolean hasPlain = samples.stream().anyMatch(s ->
                (s.data != null && s.data.contains("plain-"))
                        || (s.datastring != null && s.datastring.contains("\"plain\""))
        );
        assertThat(hasPlain).as("At least one row without encrypted (plain) data/datastring").isTrue();
    }

    @Test
    void generateSampleData_includesRowsWithEncryptedOrBracketContent() {
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "01234567890123456789012345678901"); // 32 bytes for AES
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);

        GenerateEncryptedSampleData generator = new GenerateEncryptedSampleData(cryptoUtil);
        List<GenerateEncryptedSampleData.SampleData> samples = generator.generateSampleData();

        boolean hasEncrypted = samples.stream().anyMatch(s ->
                (s.datastring != null && s.datastring.contains("[\"") && s.datastring.contains("\"]"))
                        || (s.data != null && s.data.length() > 200) // encrypted payload tends to be longer
        );
        assertThat(hasEncrypted).as("At least one row with encrypted or bracket-wrapped content").isTrue();
    }

    @Test
    void generateSampleData_plainCountMatchesConstant() {
        CryptoUtil cryptoUtil = new CryptoUtil();
        ReflectionTestUtils.setField(cryptoUtil, "encryptionKey", "01234567890123456789012345678901"); // 32 bytes for AES
        ReflectionTestUtils.setField(cryptoUtil, "decryptionEnabled", true);

        GenerateEncryptedSampleData generator = new GenerateEncryptedSampleData(cryptoUtil);
        List<GenerateEncryptedSampleData.SampleData> samples = generator.generateSampleData();

        long plainCount = samples.stream().filter(s -> s.data != null && s.data.contains("plain-")).count();
        assertThat(plainCount).isEqualTo(GenerateEncryptedSampleData.NON_ENCRYPTED_COUNT);
    }
}
