package com.logmng.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.util.Base64;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * ProObject-style AES/CBC/PKCS5Padding using PBKDF2-HMAC-SHA1 key derivation.
 * Matches {@code docs/enc/AESEncryptor.doc} (salt, iterations, wire format).
 */
public final class ProObjectAesCipher {

    private static final String PBKDF2 = "PBKDF2WithHmacSHA1";
    private static final String AES_CBC = "AES/CBC/PKCS5Padding";
    private static final int PBKDF2_ITERATIONS = 70_000;
    private static final int AES_KEY_BITS = 256;

    /** Same as {@code SALTS} in the reference AESEncryptor. */
    public static final byte[] SALTS = new byte[]{
            38, -115, 102, -89, 53, -88, 26, -127, 111, -70, -39, -6, 54, 22, 37, 1
    };

    private ProObjectAesCipher() {
    }

    /**
     * Derives an AES key from the password string (char-based PBKDF2), as in the reference class.
     */
    public static SecretKeySpec deriveAesKeyFromPassword(String password)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (password == null) {
            throw new IllegalArgumentException("encryption password is null");
        }
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), SALTS, PBKDF2_ITERATIONS, AES_KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2);
        SecretKey secret = factory.generateSecret(spec);
        return new SecretKeySpec(secret.getEncoded(), "AES");
    }

    /**
     * Encrypts plaintext to Base64(SALT_16 + IV_16 + ciphertext), optionally prefixed with {@code E002}.
     */
    public static String encrypt(String plainText, SecretKeySpec aesKey, boolean prefixE002) throws Exception {
        if (plainText == null) {
            throw new IllegalArgumentException("plainText is null");
        }
        Cipher cipher = Cipher.getInstance(AES_CBC);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        AlgorithmParameters params = cipher.getParameters();
        byte[] ivBytes = params.getParameterSpec(IvParameterSpec.class).getIV();
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] buffer = new byte[SALTS.length + ivBytes.length + encryptedBytes.length];
        System.arraycopy(SALTS, 0, buffer, 0, SALTS.length);
        System.arraycopy(ivBytes, 0, buffer, SALTS.length, ivBytes.length);
        System.arraycopy(encryptedBytes, 0, buffer, SALTS.length + ivBytes.length, encryptedBytes.length);
        String b64 = Base64.getEncoder().encodeToString(buffer);
        return prefixE002 ? (E002 + b64) : b64;
    }

    /**
     * Decrypts ProObject wire format. When {@code stripE002IfPresent} is true, strips a leading {@code E002} before Base64 decode.
     */
    public static String decrypt(String encryptedText, SecretKeySpec aesKey, boolean stripE002IfPresent) throws Exception {
        if (encryptedText == null || encryptedText.isEmpty()) {
            throw new IllegalArgumentException("encrypted text is null or empty");
        }
        String working = encryptedText;
        if (stripE002IfPresent && working.startsWith(E002)) {
            working = working.substring(E002.length());
        }
        byte[] all = Base64.getDecoder().decode(working.getBytes(StandardCharsets.US_ASCII));
        int blockSize = 16;
        if (all.length < SALTS.length + blockSize + 1) {
            throw new IllegalArgumentException("decoded payload too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(all);
        byte[] saltFromWire = new byte[SALTS.length];
        buffer.get(saltFromWire);
        if (!java.util.Arrays.equals(saltFromWire, SALTS)) {
            throw new IllegalArgumentException("salt mismatch");
        }
        byte[] ivBytes = new byte[blockSize];
        buffer.get(ivBytes);
        byte[] encryptedBytes = new byte[buffer.remaining()];
        buffer.get(encryptedBytes);
        Cipher cipher = Cipher.getInstance(AES_CBC);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(ivBytes));
        byte[] decrypted = cipher.doFinal(encryptedBytes);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    static final String E002 = "E002";
}
