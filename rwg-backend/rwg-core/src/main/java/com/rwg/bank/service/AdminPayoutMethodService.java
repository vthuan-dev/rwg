package com.rwg.bank.service;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.dto.AdminPayoutMethodResponse;
import com.rwg.bank.dto.RevealedPayoutAddressResponse;
import com.rwg.bank.repository.BankAccountRepository;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.identity.service.AuditTrailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nghiệp vụ đọc phương thức nhận tiền cho khu quản trị.
 *
 * Chỉ hỗ trợ BANK.
 */
@Service
public class AdminPayoutMethodService {

    private final BankAccountRepository repository;
    private final EncryptedStringConverter crypto;
    private final AuditTrailService audit;

    public AdminPayoutMethodService(BankAccountRepository repository,
                                    EncryptedStringConverter crypto,
                                    AuditTrailService audit) {
        this.repository = repository;
        this.crypto = crypto;
        this.audit = audit;
    }

    /**
     * Danh sách phương thức của một user — CHỈ phần đã che.
     */
    @Transactional(readOnly = true)
    public List<AdminPayoutMethodResponse> listForUser(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(AdminPayoutMethodResponse::from).toList();
    }

    /**
     * Giải mã số tài khoản đầy đủ.
     */
    @Transactional
    public RevealedPayoutAddressResponse reveal(UUID userId, UUID methodId, UUID adminId, String ip) {
        BankAccount method = repository.findFirstById(methodId)
                .filter(m -> m.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.bank_account"));

        Instant revealedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("payoutType", "BANK");
        details.put("targetUserId", userId.toString());
        details.put("maskedLast4", method.getMaskedLast4());

        audit.record(adminId, null, AuditTrailService.ADMIN_PAYOUT_METHOD_REVEALED,
                "BANK_ACCOUNT", method.getId().toString(), details, ip);

        String fullAddress = crypto.decrypt(
                method.getAccountNumberCiphertext(), method.getAccountNumberIv());

        return new RevealedPayoutAddressResponse(
                method.getId().toString(),
                "BANK",
                fullAddress,
                method.getBankCode(),
                method.getHolderName(),
                revealedAt);
    }
}
