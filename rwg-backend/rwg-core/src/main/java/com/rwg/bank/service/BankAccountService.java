package com.rwg.bank.service;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.domain.BankAccountStatus;
import com.rwg.bank.dto.BankAccountRequest;
import com.rwg.bank.dto.BankAccountResponse;
import com.rwg.bank.repository.BankAccountRepository;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.identity.service.AuditTrailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nghiệp vụ liên kết tài khoản ngân hàng (chặng 2 Phase b):
 * - Số tài khoản mã hóa AES-256-GCM trước khi lưu; KHÔNG log plaintext.
 * - API/list chỉ trả maskedLast4; audit details KHÔNG chứa số TK đầy đủ.
 * - Mỗi user tối đa 1 tài khoản default (bắt buộc khi rút tiền).
 */
@Service
public class BankAccountService {

    private final BankAccountRepository repository;
    private final EncryptedStringConverter crypto;
    private final AuditTrailService audit;

    public BankAccountService(BankAccountRepository repository,
                              EncryptedStringConverter crypto,
                              AuditTrailService audit) {
        this.repository = repository;
        this.crypto = crypto;
        this.audit = audit;
    }

    @Transactional
    public BankAccountResponse add(UUID userId, BankAccountRequest request, String ip) {
        EncryptedStringConverter.CipherText cipher = crypto.encrypt(request.accountNumber());
        String maskedLast4 = request.accountNumber().substring(request.accountNumber().length() - 4);

        // Tài khoản ĐẦU TIÊN tự thành default; setDefault=true thì chiếm cờ default.
        boolean wantsDefault = Boolean.TRUE.equals(request.setDefault());
        boolean firstAccount = !repository.existsByUserIdAndStatus(userId, BankAccountStatus.ACTIVE);
        boolean makeDefault = wantsDefault || firstAccount;
        if (makeDefault) {
            repository.clearDefaultForUser(userId);
        }

        BankAccount saved = repository.save(BankAccount.create(
                userId, request.bankCode().trim(), cipher.ciphertextBase64(), cipher.ivBase64(),
                maskedLast4, request.holderName().trim(), makeDefault));

        // Audit CHỈ chứa masked — KHÔNG chứa số tài khoản đầy đủ.
        audit.record(userId, null, AuditTrailService.BANK_ACCOUNT_ADDED, "BANK_ACCOUNT",
                saved.getId().toString(),
                Map.of("bankCode", saved.getBankCode(), "maskedLast4", maskedLast4,
                        "isDefault", makeDefault), ip);
        return BankAccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> list(UUID userId) {
        return repository.findByUserIdAndStatusOrderByCreatedAtAsc(userId, BankAccountStatus.ACTIVE)
                .stream().map(BankAccountResponse::from).toList();
    }

    /** Xóa mềm (REMOVED) — giữ audit trail. Nếu đang là default thì user cần thêm TK khác để rút. */
    @Transactional
    public void remove(UUID userId, UUID bankAccountId, String ip) {
        BankAccount ba = repository.findFirstById(bankAccountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.bank_account"));
        ba.setStatus(BankAccountStatus.REMOVED);
        ba.setIsDefault(Boolean.FALSE);
        repository.save(ba);
        audit.record(userId, null, AuditTrailService.BANK_ACCOUNT_REMOVED, "BANK_ACCOUNT",
                ba.getId().toString(), Map.of("maskedLast4", ba.getMaskedLast4()), ip);
    }
}
