package com.logmng.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES256 암호화/복호화 유틸리티
 */
@Component
public class CryptoUtil {
    
    private static final Logger log = LoggerFactory.getLogger(CryptoUtil.class);
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String TRANSFORMATION = "AES";
    private static final int IV_SIZE = 16;
    
    @Value("${app.security.encryption-key}")
    private String encryptionKey;
    
    @Value("${app.decryption.enabled:true}")
    private boolean decryptionEnabled;
    
    @Value("${app.decryption.failure-handling:fallback}")
    private String failureHandling;
    
    /**
     * AES256 암호화
     * 
     * @param plainText 평문
     * @return 암호화된 텍스트 (IV:암호문 형식)
     */
    public String encrypt(String plainText) {
        try {
            // IV 생성
            byte[] iv = new byte[IV_SIZE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // 키 생성
            SecretKeySpec keySpec = new SecretKeySpec(
                encryptionKey.getBytes(StandardCharsets.UTF_8), 
                TRANSFORMATION
            );
            
            // 암호화
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // IV와 암호문을 결합하여 반환 (Hex 형식)
            String ivHex = bytesToHex(iv);
            String encryptedHex = bytesToHex(encrypted);
            
            return ivHex + ":" + encryptedHex;
            
        } catch (Exception e) {
            log.error("암호화 중 오류 발생", e);
            throw new RuntimeException("암호화 실패", e);
        }
    }
    
    /**
     * AES256 복호화
     * 
     * @param encryptedText 암호화된 텍스트 (IV:암호문 형식)
     * @return 복호화된 평문
     */
    public String decrypt(String encryptedText) {
        return decrypt(encryptedText, false);
    }
    
    /**
     * AES256 복호화 (강제 옵션)
     * 
     * @param encryptedText 암호화된 텍스트
     * @param force 강제 복호화 여부
     * @return 복호화된 평문
     */
    public String decrypt(String encryptedText, boolean force) {
        // 복호화가 비활성화되어 있고, 강제가 아닌 경우
        if (!decryptionEnabled && !force) {
            log.debug("🔒 복호화가 비활성화되어 있습니다");
            throw new RuntimeException("복호화가 비활성화되어 있습니다");
        }
        
        try {
            // IV와 암호문 분리
            String[] parts = encryptedText.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("잘못된 암호화 형식");
            }
            
            byte[] iv = hexToBytes(parts[0]);
            byte[] encrypted = hexToBytes(parts[1]);
            
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // 키 생성
            SecretKeySpec keySpec = new SecretKeySpec(
                encryptionKey.getBytes(StandardCharsets.UTF_8), 
                TRANSFORMATION
            );
            
            // 복호화
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);
            
            log.debug("🔓 복호화 성공");
            return new String(decrypted, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("복호화 중 오류 발생", e);
            
            // 실패 처리 방식에 따른 동작
            switch (failureHandling) {
                case "skip":
                    throw new RuntimeException("복호화 실패 - 건너뜀", e);
                case "fallback":
                    log.warn("복호화 실패, 원본 데이터 반환");
                    return encryptedText;
                case "error":
                default:
                    throw new RuntimeException("복호화 실패", e);
            }
        }
    }
    
    /**
     * 조건부 복호화 (키워드 검색 시)
     * 
     * @param encryptedText 암호화된 텍스트
     * @param context 컨텍스트 (search, keyword_search 등)
     * @return 복호화된 평문 또는 null
     */
    public String conditionalDecrypt(String encryptedText, String context) {
        try {
            return decrypt(encryptedText);
        } catch (Exception e) {
            log.debug("조건부 복호화 실패: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 랜덤 데이터 생성 (평균 2KB)
     * 
     * @return 랜덤 문자열
     */
    public String generateRandomData() {
        int minSize = 1500; // 1.5KB
        int maxSize = 2500; // 2.5KB
        int size = minSize + new SecureRandom().nextInt(maxSize - minSize + 1);
        
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?";
        StringBuilder result = new StringBuilder();
        SecureRandom random = new SecureRandom();
        
        for (int i = 0; i < size; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return result.toString();
    }
    
    /**
     * 암호화된 랜덤 데이터 생성
     * 
     * @return 암호화된 랜덤 문자열
     */
    public String generateEncryptedData() {
        String randomData = generateRandomData();
        return encrypt(randomData);
    }
    
    // Hex 변환 유틸리티
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}





