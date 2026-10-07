package com.scanly.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for storing sensitive credentials.
 *
 * Key derivation:
 *   - Key comes from scanly.crypto.secret in application.properties (env var SCANLY_CRYPTO_SECRET)
 *   - Key must be exactly 32 bytes when decoded from Base64 (256-bit AES key)
 *
 * Format of stored ciphertext (Base64-encoded):
 *   IV (12 bytes) | TAG (16 bytes appended by GCM) | ciphertext
 *   All concatenated, then Base64-encoded.
 */
@Service
@Slf4j
public class CryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE_BYTES  = 12;  // 96-bit IV recommended for GCM
    private static final int TAG_SIZE_BITS  = 128; // 128-bit authentication tag

    private final SecretKey secretKey;

    public CryptoService(@Value("${scanly.crypto.secret}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                "scanly.crypto.secret must decode to exactly 32 bytes (256-bit AES key). " +
                "Generate one with: openssl rand -base64 32");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("CryptoService initialized with AES-256-GCM");
    }

    /**
     * Encrypts a plaintext string.
     * @return Base64-encoded string: IV (12 bytes) + GCM ciphertext+tag
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_SIZE_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_SIZE_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // Prepend IV to ciphertext, then Base64
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a string previously encrypted by {@link #encrypt(String)}.
     * @param base64Encrypted Base64-encoded IV + ciphertext
     */
    public String decrypt(String base64Encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(base64Encrypted);

            byte[] iv = new byte[IV_SIZE_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_SIZE_BYTES];

            System.arraycopy(combined, 0, iv, 0, IV_SIZE_BYTES);
            System.arraycopy(combined, IV_SIZE_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_SIZE_BITS, iv));

            return new String(cipher.doFinal(ciphertext), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /** Utility: generate a new random 256-bit key, printed as Base64. For setup only. */
    public static String generateKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256, new SecureRandom());
        return Base64.getEncoder().encodeToString(kg.generateKey().getEncoded());
    }
}
