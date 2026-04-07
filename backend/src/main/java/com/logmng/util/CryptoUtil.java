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

/**
 * AES 암호화/복호화 유틸리티.
 * <p>
 * 로그 페이로드 복호화는 {@link #decryptLogPayload(String, LogPayloadCryptoVariant)} 를 사용한다.
 * ProObject 호환 형식(PBKDF2 키 + Base64(SALT+IV+密文), java_fw_imglog 는 선택적 {@code E002} 접두)을 먼저 시도하고,
 * 실패 시 레거시 {@code ivHex:encryptedHex}(UTF-8 바이트 키) 형식을 시도한다.
 * </p>
 */
@Component
public class CryptoUtil {

    private static final Logger log = LoggerFactory.getLogger(CryptoUtil.class);
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String TRANSFORMATION = "AES";
    private static final int IV_SIZE = 16;

    /**
     * DB 로그 타입별 페이로드 복호화 규칙(ProObject 변형).
     */
    public enum LogPayloadCryptoVariant {
        /** java_fw_imglog: Base64 전에 선택적 {@code E002} 제거. */
        IMAGE_LOG,
        /** pb_feplog: 동일 알고리즘, {@code E002} 제거 없음. */
        PB_FEP
    }

    @Value("${app.security.encryption-key}")
    private String encryptionKey;

    @Value("${app.decryption.enabled:true}")
    private boolean decryptionEnabled;

    @Value("${app.decryption.failure-handling:fallback}")
    private String failureHandling;

    /** Lazily derived AES key for ProObject-compatible crypto (PBKDF2; expensive). */
    private volatile SecretKeySpec proObjectAesKeySpec;

    private SecretKeySpec getOrCreateProObjectAesKey() throws Exception {
        if (encryptionKey == null) {
            return null;
        }
        SecretKeySpec local = proObjectAesKeySpec;
        if (local == null) {
            synchronized (this) {
                local = proObjectAesKeySpec;
                if (local == null) {
                    proObjectAesKeySpec = ProObjectAesCipher.deriveAesKeyFromPassword(encryptionKey);
                    local = proObjectAesKeySpec;
                }
            }
        }
        return local;
    }

    /**
     * 레거시 AES256 암호화 (IV:암호문 hex, 키는 UTF-8 바이트).
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_SIZE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            SecretKeySpec keySpec = new SecretKeySpec(
                    encryptionKey.getBytes(StandardCharsets.UTF_8),
                    TRANSFORMATION);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            String ivHex = bytesToHex(iv);
            String encryptedHex = bytesToHex(encrypted);

            return ivHex + ":" + encryptedHex;

        } catch (Exception e) {
            log.error("암호화 중 오류 발생", e);
            throw new RuntimeException("암호화 실패", e);
        }
    }

    /**
     * ProObject 호환 암호화 — java_fw_imglog 샘플/검증용 ({@code E002} + Base64).
     */
    public String encryptImageLogPayload(String plainText) {
        try {
            SecretKeySpec key = getOrCreateProObjectAesKey();
            if (key == null) {
                throw new IllegalStateException("encryption key is not configured");
            }
            return ProObjectAesCipher.encrypt(plainText, key, true);
        } catch (Exception e) {
            log.error("암호화 중 오류 발생 (ProObject ImageLog)", e);
            throw new RuntimeException("암호화 실패", e);
        }
    }

    /**
     * ProObject 호환 암호화 — pb_feplog 샘플/검증용 (Base64만, {@code E002} 없음).
     */
    public String encryptPbFepPayload(String plainText) {
        try {
            SecretKeySpec key = getOrCreateProObjectAesKey();
            if (key == null) {
                throw new IllegalStateException("encryption key is not configured");
            }
            return ProObjectAesCipher.encrypt(plainText, key, false);
        } catch (Exception e) {
            log.error("암호화 중 오류 발생 (ProObject PB FEP)", e);
            throw new RuntimeException("암호화 실패", e);
        }
    }

    /**
     * 레거시 AES256 복호화 ({@code ivHex:encryptedHex} 만).
     */
    public String decrypt(String encryptedText) {
        return decrypt(encryptedText, false);
    }

    /**
     * 레거시 AES256 복호화.
     */
    public String decrypt(String encryptedText, boolean force) {
        if (!decryptionEnabled && !force) {
            log.debug("🔒 복호화가 비활성화되어 있습니다");
            throw new RuntimeException("복호화가 비활성화되어 있습니다");
        }

        try {
            String legacy = tryDecryptLegacy(encryptedText);
            if (legacy != null) {
                log.debug("🔓 복호화 성공 (legacy)");
                return legacy;
            }
            throw new IllegalArgumentException("잘못된 암호화 형식");
        } catch (Exception e) {
            log.error("복호화 중 오류 발생", e);
            return handleDecryptFailure(e, encryptedText, false);
        }
    }

    /**
     * 로그 페이로드 복호화: ProObject 호환을 먼저 시도한 뒤 레거시 형식으로 폴백.
     */
    public String decryptLogPayload(String encryptedText, LogPayloadCryptoVariant variant) {
        return decryptLogPayload(encryptedText, variant, false);
    }

    /**
     * 로그 페이로드 복호화 ({@code force} 시 복호화 비활성화 무시).
     * <p>
     * {@code app.decryption.failure-handling=fallback} 이라도 이 메서드는 실패 시 ciphertext를 반환하지 않고 예외를 던진다
     * (UI/API가 암호문을 평문으로 오인하지 않도록).
     * </p>
     */
    public String decryptLogPayload(String encryptedText, LogPayloadCryptoVariant variant, boolean force) {
        if (!decryptionEnabled && !force) {
            log.debug("🔒 복호화가 비활성화되어 있습니다");
            throw new RuntimeException("복호화가 비활성화되어 있습니다");
        }

        try {
            String pro = tryDecryptProObject(encryptedText, variant);
            if (pro != null) {
                log.debug("🔓 복호화 성공 (ProObject)");
                return pro;
            }
            String legacy = tryDecryptLegacy(encryptedText);
            if (legacy != null) {
                log.debug("🔓 복호화 성공 (legacy)");
                return legacy;
            }
            throw new IllegalArgumentException("잘못된 암호화 형식");
        } catch (Exception e) {
            log.error("복호화 중 오류 발생", e);
            return handleDecryptFailure(e, encryptedText, true);
        }
    }

    /**
     * @param logPayloadDecrypt {@code true} for {@link #decryptLogPayload(String, LogPayloadCryptoVariant, boolean)} —
     *                          never return ciphertext on failure when mode would be {@code fallback}, so APIs/UI do not
     *                          treat undecryptable blobs as plaintext. Legacy {@link #decrypt(String, boolean)} uses {@code false}.
     */
    private String handleDecryptFailure(Exception e, String encryptedText, boolean logPayloadDecrypt) {
        String mode = (failureHandling == null || failureHandling.isEmpty()) ? "fallback" : failureHandling;
        if (logPayloadDecrypt && "fallback".equals(mode)) {
            log.warn("로그 페이로드 복호화 실패: failure-handling=fallback 이어도 ciphertext를 평문으로 반환하지 않습니다");
            throw new RuntimeException("로그 페이로드 복호화 실패", e);
        }
        switch (mode) {
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

    private String tryDecryptProObject(String encryptedText, LogPayloadCryptoVariant variant) {
        try {
            SecretKeySpec key = getOrCreateProObjectAesKey();
            if (key == null) {
                return null;
            }
            boolean stripE002 = (variant == LogPayloadCryptoVariant.IMAGE_LOG);
            return ProObjectAesCipher.decrypt(encryptedText, key, stripE002);
        } catch (Exception e) {
            log.debug("ProObject 복호화 시도 실패: {}", e.getMessage());
            return null;
        }
    }

    private String tryDecryptLegacy(String encryptedText) {
        try {
            String[] parts = encryptedText.split(":");
            if (parts.length != 2) {
                return null;
            }
            if (!parts[0].matches("[0-9a-fA-F]+") || !parts[1].matches("[0-9a-fA-F]+")) {
                return null;
            }
            if (parts[0].length() % 2 != 0 || parts[1].length() % 2 != 0) {
                return null;
            }

            byte[] iv = hexToBytes(parts[0]);
            byte[] encrypted = hexToBytes(parts[1]);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            SecretKeySpec keySpec = new SecretKeySpec(
                    encryptionKey.getBytes(StandardCharsets.UTF_8),
                    TRANSFORMATION);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("레거시 복호화 시도 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 조건부 복호화 (키워드 검색 시)
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
     */
    public String generateRandomData() {
        int minSize = 1500;
        int maxSize = 2500;
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
     * 암호화된 랜덤 데이터 생성 (레거시 형식)
     */
    public String generateEncryptedData() {
        String randomData = generateRandomData();
        return encrypt(randomData);
    }

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
