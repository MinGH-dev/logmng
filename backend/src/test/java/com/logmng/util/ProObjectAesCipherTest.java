package com.logmng.util;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProObjectAesCipherTest {

    @Test
    void roundTrip_imageLog_e002Prefix_matchesReferenceLayout() throws Exception {
        String password = "01234567890123456789012345678901";
        SecretKeySpec key = ProObjectAesCipher.deriveAesKeyFromPassword(password);
        String plain = "{\"k\":\"v\",\"n\":1}";

        String enc = ProObjectAesCipher.encrypt(plain, key, true);
        assertThat(enc).startsWith(ProObjectAesCipher.E002);
        assertThat(ProObjectAesCipher.decrypt(enc, key, true)).isEqualTo(plain);
    }

    @Test
    void roundTrip_pbFep_noPrefix() throws Exception {
        String password = "my-pbkdf2-password";
        SecretKeySpec key = ProObjectAesCipher.deriveAesKeyFromPassword(password);
        String plain = "request body";

        String enc = ProObjectAesCipher.encrypt(plain, key, false);
        assertThat(enc).doesNotStartWith(ProObjectAesCipher.E002);
        assertThat(ProObjectAesCipher.decrypt(enc, key, false)).isEqualTo(plain);
    }

    @Test
    void decrypt_imageLog_without_e002_still_decrypts() throws Exception {
        String password = "01234567890123456789012345678901";
        SecretKeySpec key = ProObjectAesCipher.deriveAesKeyFromPassword(password);
        String encNoPrefix = ProObjectAesCipher.encrypt("x", key, false);
        assertThat(ProObjectAesCipher.decrypt(encNoPrefix, key, true)).isEqualTo("x");
    }

    @Test
    void saltOnWire_mustMatchKdfSalt() {
        assertThatThrownBy(() -> ProObjectAesCipher.decrypt(
                java.util.Base64.getEncoder().encodeToString(new byte[48]),
                new SecretKeySpec(new byte[32], "AES"),
                false))
                .isInstanceOf(Exception.class);
    }
}
