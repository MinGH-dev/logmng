package com.logmng.util;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProObject + 레거시 폴백 복호화 및 {@link CryptoUtil} 암호화 헬퍼.
 */
class CryptoUtilLogPayloadDecryptTest {

    @Test
    void decryptLogPayload_proObjectImageLog_roundTrip() {
        CryptoUtil u = newCryptoUtil("01234567890123456789012345678901");
        String plain = "{\"a\":1}";
        String enc = u.encryptImageLogPayload(plain);
        assertThat(enc).startsWith(ProObjectAesCipher.E002);
        assertThat(u.decryptLogPayload(enc, CryptoUtil.LogPayloadCryptoVariant.IMAGE_LOG)).isEqualTo(plain);
    }

    /** Same default as {@code app.security.encryption-key} in application.yml (dev). */
    @Test
    void decryptLogPayload_applicationYmlDefaultDevKey_roundTrip() {
        CryptoUtil u = newCryptoUtilWithMode("12345678901234567890123456789012", "fallback");
        String plain = "{\"id\":\"1110\",\"name\":\"홍길동\"}";
        String enc = u.encryptImageLogPayload(plain);
        assertThat(u.decryptLogPayload(enc, CryptoUtil.LogPayloadCryptoVariant.IMAGE_LOG)).isEqualTo(plain);
    }

    @Test
    void decryptLogPayload_proObjectPbFep_roundTrip() {
        CryptoUtil u = newCryptoUtil("01234567890123456789012345678901");
        String plain = "pb payload";
        String enc = u.encryptPbFepPayload(plain);
        assertThat(enc).doesNotStartWith(ProObjectAesCipher.E002);
        assertThat(u.decryptLogPayload(enc, CryptoUtil.LogPayloadCryptoVariant.PB_FEP)).isEqualTo(plain);
    }

    @Test
    void decryptLogPayload_legacyIvHex_afterProObjectFails() {
        CryptoUtil u = newCryptoUtil("test-key-32-bytes-long!!!!!!!!!!");
        String plain = "legacy row";
        String legacy = u.encrypt(plain);
        assertThat(legacy).contains(":");
        assertThat(u.decryptLogPayload(legacy, CryptoUtil.LogPayloadCryptoVariant.IMAGE_LOG)).isEqualTo(plain);
        assertThat(u.decryptLogPayload(legacy, CryptoUtil.LogPayloadCryptoVariant.PB_FEP)).isEqualTo(plain);
    }

    /**
     * With global {@code failure-handling=fallback}, log payload decrypt must not pass through ciphertext (E002+Base64).
     */
    @Test
    void decryptLogPayload_wrongKey_fallbackMode_throws() {
        CryptoUtil enc = newCryptoUtilWithMode("12345678901234567890123456789012", "fallback");
        String encStr = enc.encryptImageLogPayload("{\"x\":1}");
        CryptoUtil dec = newCryptoUtilWithMode("wrong-key-32-bytes-long!!!!!!!!!!", "fallback");
        assertThatThrownBy(() -> dec.decryptLogPayload(encStr, CryptoUtil.LogPayloadCryptoVariant.IMAGE_LOG))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("로그 페이로드 복호화");
    }

    private static CryptoUtil newCryptoUtil(String key) {
        return newCryptoUtilWithMode(key, "error");
    }

    private static CryptoUtil newCryptoUtilWithMode(String key, String failureHandling) {
        CryptoUtil u = new CryptoUtil();
        ReflectionTestUtils.setField(u, "encryptionKey", key);
        ReflectionTestUtils.setField(u, "decryptionEnabled", true);
        ReflectionTestUtils.setField(u, "failureHandling", failureHandling);
        return u;
    }
}
