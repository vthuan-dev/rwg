package com.rwg.bank;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.repository.BankAccountRepository;
import com.rwg.identity.domain.AuditLog;
import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra tích hợp phương thức nhận tiền (chỉ hỗ trợ tài khoản ngân hàng).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayoutMethodApiTest {

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAWAL_PASSWORD = "123456";
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
    UserRepository userRepository;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String prefix) throws Exception {
        String username = unique(prefix);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","withdrawalPassword":"%s"}
                                """.formatted(username, PASSWORD, WITHDRAWAL_PASSWORD)))
                .andExpect(status().isCreated());
        return username;
    }

    private String playerBearer(String username) throws Exception {
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

    private String staffBearer(UserRole role) throws Exception {
        String username = register("staff");
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(role);
        userRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String addBank(String bearer, String bankCode, String accNum, String holder) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"%s","accountNumber":"%s","holderName":"%s","withdrawalPassword":"%s"}
                                """.formatted(bankCode, accNum, holder, WITHDRAWAL_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("Người chơi liên kết tài khoản ngân hàng thành công: response chỉ có dạng đã che, DB lưu ciphertext, audit sạch")
    void addBankAccountWorksAndMasksAccountNumber() throws Exception {
        String bearer = playerBearer(register("addbank"));

        MvcResult created = mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"%s","holderName":"NGUYEN VAN A","withdrawalPassword":"%s"}
                                """.formatted(ACCOUNT_NUMBER, WITHDRAWAL_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedAccountNumber").value("****6789"))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andReturn();

        String body = created.getResponse().getContentAsString();
        assertThat(body).doesNotContain(ACCOUNT_NUMBER);

        String methodId = objectMapper.readTree(body).get("id").asText();

        BankAccount row = bankAccountRepository.findFirstById(UUID.fromString(methodId)).orElseThrow();
        assertThat(row.getAccountNumberCiphertext()).doesNotContain(ACCOUNT_NUMBER);
        assertThat(row.getMaskedLast4()).isEqualTo("6789");

        List<AuditLog> audits = auditLogRepository.findByAction(AuditTrailService.BANK_ACCOUNT_ADDED)
                .stream().filter(a -> methodId.equals(a.getTargetId())).toList();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getDetails()).doesNotContain(ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("ADMIN xem danh sách thấy dạng đã che, và xem số đầy đủ được ghi audit")
    void adminListsMaskedAndRevealIsAudited() throws Exception {
        String username = register("target");
        String playerBearer = playerBearer(username);
        String methodId = addBank(playerBearer, "VCB", ACCOUNT_NUMBER, "NGUYEN VAN TARGET");
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();

        String adminBearer = staffBearer(UserRole.ADMIN);

        // Danh sách: CHỈ dạng đã che.
        MvcResult listed = mockMvc.perform(get("/api/v1/admin/users/" + userId + "/payout-methods")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payoutType").value("BANK"))
                .andExpect(jsonPath("$[0].maskedAddress").value("****6789"))
                .andReturn();
        assertThat(listed.getResponse().getContentAsString()).doesNotContain(ACCOUNT_NUMBER);

        // Reveal: trả số đầy đủ.
        mockMvc.perform(post("/api/v1/admin/users/" + userId
                        + "/payout-methods/" + methodId + "/reveal")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullAddress").value(ACCOUNT_NUMBER));

        List<AuditLog> reveals = auditLogRepository
                .findByAction(AuditTrailService.ADMIN_PAYOUT_METHOD_REVEALED)
                .stream().filter(a -> methodId.equals(a.getTargetId())).toList();
        assertThat(reveals).hasSize(1);
        assertThat(reveals.get(0).getDetails()).doesNotContain(ACCOUNT_NUMBER);
        assertThat(reveals.get(0).getDetails()).contains(userId.toString());
    }

    @Test
    @DisplayName("SUPPORT xem được danh sách đã che nhưng bị chặn 403 khi xem số đầy đủ")
    void supportCanListButCannotReveal() throws Exception {
        String username = register("supporttarget");
        String methodId = addBank(playerBearer(username), "VCB", ACCOUNT_NUMBER, "NGUYEN VAN SUPPORT");
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();

        String supportBearer = staffBearer(UserRole.SUPPORT);

        mockMvc.perform(get("/api/v1/admin/users/" + userId + "/payout-methods")
                        .header("Authorization", supportBearer))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/" + userId
                        + "/payout-methods/" + methodId + "/reveal")
                        .header("Authorization", supportBearer))
                .andExpect(status().isForbidden());

        List<AuditLog> reveals = auditLogRepository
                .findByAction(AuditTrailService.ADMIN_PAYOUT_METHOD_REVEALED)
                .stream().filter(a -> methodId.equals(a.getTargetId())).toList();
        assertThat(reveals).isEmpty();
    }

    @Test
    @DisplayName("Reveal qua đường dẫn của user khác trả 404")
    void revealThroughAnotherUserPathIsNotFound() throws Exception {
        String ownerUsername = register("owner");
        String methodId = addBank(playerBearer(ownerUsername), "VCB", ACCOUNT_NUMBER, "NGUYEN VAN OWNER");

        String otherUsername = register("other");
        UUID otherUserId = userRepository.findByUsername(otherUsername).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/users/" + otherUserId
                        + "/payout-methods/" + methodId + "/reveal")
                        .header("Authorization", staffBearer(UserRole.ADMIN)))
                .andExpect(status().isNotFound());
    }
}
