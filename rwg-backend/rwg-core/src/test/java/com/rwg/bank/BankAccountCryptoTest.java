package com.rwg.bank;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.repository.BankAccountRepository;
import com.rwg.bank.service.EncryptedStringConverter;
import com.rwg.config.CryptoProperties;
import com.rwg.identity.domain.AuditLog;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.service.AuditTrailService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test mã hóa số tài khoản ngân hàng (chặng 2 Phase b):
 * - round-trip encrypt/decrypt; SAI KHÓA phải bị từ chối (GCM tag).
 * - API chỉ trả masked; DB lưu ciphertext KHÔNG chứa plaintext.
 * - audit details KHÔNG chứa số tài khoản đầy đủ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankAccountCryptoTest {

    private static final String ACCOUNT_NUMBER = "0123456789";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    BankAccountRepository bankAccountRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    EncryptedStringConverter converter;

    private static final String PASSWORD = "MatKhau@12345";

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    // ===== unit: converter =====

    @Test
    void roundTripEncryptDecrypt() {
        EncryptedStringConverter local = new EncryptedStringConverter(new CryptoProperties("unit-key-1"));
        EncryptedStringConverter.CipherText cipher = local.encrypt(ACCOUNT_NUMBER);
        assertThat(cipher.ciphertextBase64()).doesNotContain(ACCOUNT_NUMBER);
        assertThat(local.decrypt(cipher.ciphertextBase64(), cipher.ivBase64())).isEqualTo(ACCOUNT_NUMBER);
    }

    @Test
    void wrongKeyDecryptionIsRejected() {
        EncryptedStringConverter enc = new EncryptedStringConverter(new CryptoProperties("unit-key-A"));
        EncryptedStringConverter wrongKey = new EncryptedStringConverter(new CryptoProperties("unit-key-B"));
        EncryptedStringConverter.CipherText cipher = enc.encrypt(ACCOUNT_NUMBER);
        assertThatThrownBy(() -> wrongKey.decrypt(cipher.ciphertextBase64(), cipher.ivBase64()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingKeyFailsFast() {
        assertThatThrownBy(() -> new EncryptedStringConverter(new CryptoProperties(null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EncryptedStringConverter(new CryptoProperties("  ")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ===== api: masked only, DB ciphertext, audit sạch =====

    private String registerLoginBearer(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s"}
                                """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    void apiReturnsMaskedOnlyDbAndAuditStayClean() throws Exception {
        String bearer = registerLoginBearer(unique("bankmask"));

        MvcResult created = mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"%s","holderName":"NGUYEN VAN A"}
                                """.formatted(ACCOUNT_NUMBER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedAccountNumber").value("****6789"))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andReturn();
        // Response KHÔNG chứa số tài khoản đầy đủ.
        assertThat(created.getResponse().getContentAsString()).doesNotContain(ACCOUNT_NUMBER);

        String bankId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // GET danh sách: chỉ masked, không plaintext.
        MvcResult listed = mockMvc.perform(get("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maskedAccountNumber").value("****6789"))
                .andReturn();
        assertThat(listed.getResponse().getContentAsString()).doesNotContain(ACCOUNT_NUMBER);

        // DB: ciphertext/iv KHÔNG chứa plaintext; decrypt bằng bean converter phải ra đúng số.
        List<BankAccount> rows = bankAccountRepository.findAll().stream()
                .filter(b -> b.getId().toString().equals(bankId)).toList();
        assertThat(rows).hasSize(1);
        BankAccount row = rows.get(0);
        assertThat(row.getAccountNumberCiphertext()).doesNotContain(ACCOUNT_NUMBER);
        assertThat(row.getMaskedLast4()).isEqualTo("6789");
        assertThat(converter.decrypt(row.getAccountNumberCiphertext(), row.getAccountNumberIv()))
                .isEqualTo(ACCOUNT_NUMBER);

        // Audit details KHÔNG chứa số tài khoản đầy đủ.
        List<AuditLog> audits = auditLogRepository.findByAction(AuditTrailService.BANK_ACCOUNT_ADDED);
        assertThat(audits).isNotEmpty();
        for (AuditLog log : audits) {
            if (log.getTargetId() != null && log.getTargetId().equals(bankId)) {
                assertThat(log.getDetails()).doesNotContain(ACCOUNT_NUMBER);
            }
        }

        // DELETE soft-delete -> danh sách rỗng.
        mockMvc.perform(delete("/api/v1/wallet/me/bank-accounts/" + bankId)
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidAccountNumberReturns400() throws Exception {
        String bearer = registerLoginBearer(unique("bankbad"));
        mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"12a45","holderName":"NGUYEN VAN A"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
