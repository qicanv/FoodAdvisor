package com.foodadvisor.backend.service;

import com.foodadvisor.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Service
public class ApiKeyCryptoService {

    private static final String CIPHER_TRANSFORMATION =
            "AES/GCM/NoPadding";
    private static final String ENCRYPTED_VALUE_PREFIX = "gcm:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyCryptoService(
            @Value("${foodadvisor.crypto.model-key-secret:}")
            String secret
    ) {
        if (secret == null || secret.isBlank()) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MODEL_KEY_SECRET_MISSING",
                    "Model API key encryption secret is not configured"
            );
        }

        this.secretKeySpec = new SecretKeySpec(
                sha256(secret),
                "AES"
        );
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );
            byte[] cipherText = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );
            byte[] payload = ByteBuffer
                    .allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();

            return ENCRYPTED_VALUE_PREFIX
                    + Base64.getEncoder().encodeToString(payload);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "API_KEY_ENCRYPTION_FAILED",
                    "Failed to protect model API key"
            );
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null
                || !encryptedText.startsWith(ENCRYPTED_VALUE_PREFIX)) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "API_KEY_FORMAT_INVALID",
                    "Protected model API key format is invalid"
            );
        }

        try {
            byte[] payload = Base64.getDecoder().decode(
                    encryptedText.substring(ENCRYPTED_VALUE_PREFIX.length())
            );
            if (payload.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Missing encrypted payload");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKeySpec,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );

            return new String(cipher.doFinal(cipherText),
                    StandardCharsets.UTF_8
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "API_KEY_DECRYPTION_FAILED",
                    "Failed to read protected model API key"
            );
        }
    }

    public String mask(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }

        if (plainText.length() <= 8) {
            return "****";
        }

        return plainText.substring(0, 4)
                + "****"
                + plainText.substring(plainText.length() - 4);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MODEL_KEY_SECRET_INVALID",
                    "Model API key encryption secret is invalid"
            );
        }
    }
}
