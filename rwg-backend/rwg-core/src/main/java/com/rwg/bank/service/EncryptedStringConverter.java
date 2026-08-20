package com.rwg.bank.service;

import com.rwg.config.CryptoProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Mã hóa số tài khoản ngân hàng bằng JCE AES-256-GCM (KHÔNG thêm dependency).
 * - Khóa AES-256 (32 byte) dẫn xuất SHA-256 từ chuỗi cấu hình rwg.crypto.bank-enc-key.
 * - Mỗi lần encrypt sinh IV 12 byte MỚI (SecureRandom) — GCM cấm tái sử dụng IV.
 * - FAIL-FAST khi thiếu khóa (mọi profile trừ dev — xem CryptoProperties).
 * - KHÔNG BAO GIỜ log plaintext (kể cả trong exception message).
 */
@Component
public class EncryptedStringConverter {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    /** Kết quả encrypt: ciphertext + IV, cả hai base64 (map 2 cột DB riêng biệt). */
    public record CipherText(String ciphertextBase64, String ivBase64) {
    }

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public EncryptedStringConverter(CryptoProperties properties) {
        String raw = properties == null ? null : properties.bankEncKey();
        if (raw == null || raw.isBlank()) {
            // Fail-fast: thiếu RWG_BANK_ENC_KEY thì KHÔNG khởi động (giống pattern JWT_SECRET).
            throw new IllegalStateException(
                    "rwg.crypto.bank-enc-key (env RWG_BANK_ENC_KEY) is required to start the application");
        }
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to derive bank encryption key", e);
        }
    }

    /** Mã hóa plaintext -> (ciphertext, iv) base64. KHÔNG log plaintext. */
    public CipherText encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new CipherText(Base64.getEncoder().encodeToString(encrypted),
                    Base64.getEncoder().encodeToString(iv));
        } catch (GeneralSecurityException e) {
            // Không đưa plaintext vào message.
            throw new IllegalStateException("Bank account encryption failed", e);
        }
    }

    /** Giải mã; sai khóa/sai IV/ciphertext hỏng -> AEADBadTagException bị bọc thành IllegalStateException. */
    public String decrypt(String ciphertextBase64, String ivBase64) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(ivBase64)));
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Bank account decryption failed", e);
        }
    }
}
