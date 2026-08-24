package com.rwg.bank.service;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.domain.BankAccountStatus;
import com.rwg.bank.dto.BankAccountRequest;
import com.rwg.bank.dto.BankAccountResponse;
import com.rwg.bank.repository.BankAccountRepository;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.identity.service.LoginRateLimiter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nghiệp vụ liên kết phương thức nhận tiền (chỉ hỗ trợ tài khoản ngân hàng):
 * - Số tài khoản mã hóa AES-256-GCM trước khi lưu; KHÔNG log plaintext.
 * - API của người chơi chỉ trả phần đã che; audit details KHÔNG chứa bản đầy đủ.
 * - Mỗi user tối đa 1 phương thức default (bắt buộc khi rút tiền).
 */
@Service
public class BankAccountService {

    private static final String WITHDRAWAL_RATE_LIMIT_PREFIX = "withdrawal:";

    private final BankAccountRepository repository;
    private final EncryptedStringConverter crypto;
    private final PayoutAddressValidator validator;
    private final AuditTrailService audit;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;

    public BankAccountService(BankAccountRepository repository,
                              EncryptedStringConverter crypto,
                              PayoutAddressValidator validator,
                              AuditTrailService audit,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              LoginRateLimiter loginRateLimiter) {
        this.repository = repository;
        this.crypto = crypto;
        this.validator = validator;
        this.audit = audit;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Transactional
    public BankAccountResponse add(UUID userId, BankAccountRequest request, String ip) {
        // 0) XÁC NHẬN MẬT KHẨU RÚT TIỀN — phải làm TRƯỚC mọi việc khác.
        requireWithdrawalPassword(userId, request.withdrawalPassword(), ip);

        // Kiểm + chuẩn hoá TRƯỚC khi mã hoá.
        PayoutAddressValidator.Normalized normalized =
                validator.validate(request.accountNumber());

        EncryptedStringConverter.CipherText cipher = crypto.encrypt(normalized.address());

        // Phương thức ĐẦU TIÊN tự thành default; setDefault=true thì chiếm cờ default.
        boolean wantsDefault = Boolean.TRUE.equals(request.setDefault());
        boolean firstAccount = !repository.existsByUserIdAndStatus(userId, BankAccountStatus.ACTIVE);
        boolean makeDefault = wantsDefault || firstAccount;
        if (makeDefault) {
            repository.clearDefaultForUser(userId);
        }

        BankAccount saved = repository.save(BankAccount.createBank(
                userId, request.bankCode().trim(),
                cipher.ciphertextBase64(), cipher.ivBase64(),
                normalized.maskedLast4(), request.holderName().trim(), makeDefault));

        audit.record(userId, null, AuditTrailService.BANK_ACCOUNT_ADDED, "BANK_ACCOUNT",
                saved.getId().toString(), addDetails(saved, normalized.maskedLast4()), ip);
        return BankAccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> list(UUID userId) {
        return repository.findByUserIdAndStatusOrderByCreatedAtAsc(userId, BankAccountStatus.ACTIVE)
                .stream().map(BankAccountResponse::from).toList();
    }

    /** Xóa mềm (REMOVED) — giữ audit trail. */
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

    // ===== helpers =====

    private Map<String, Object> addDetails(BankAccount saved, String maskedLast4) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("payoutType", "BANK");
        details.put("maskedLast4", maskedLast4);
        details.put("isDefault", Boolean.TRUE.equals(saved.getIsDefault()));
        details.put("bankCode", saved.getBankCode());
        return details;
    }

    /**
     * Xác nhận mật khẩu rút tiền trước khi cho liên kết phương thức nhận tiền.
     */
    private void requireWithdrawalPassword(UUID userId, String rawPassword, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.user"));

        String limitKey = WITHDRAWAL_RATE_LIMIT_PREFIX + userId;
        LoginRateLimiter.AttemptResult pre = loginRateLimiter.checkBeforeAttempt(ip, limitKey);
        if (pre.locked()) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    ErrorCode.RATE_LIMITED.defaultMessage(),
                    Map.of("retryAfterSeconds", pre.retryAfterSeconds()));
        }

        if (user.getWithdrawalPasswordHash() == null) {
            throw new ApiException(ErrorCode.WITHDRAWAL_PASSWORD_NOT_SET);
        }

        if (!passwordEncoder.matches(rawPassword, user.getWithdrawalPasswordHash())) {
            LoginRateLimiter.AttemptResult after = loginRateLimiter.recordFailure(ip, limitKey);
            if (after.locked()) {
                throw new ApiException(ErrorCode.RATE_LIMITED,
                        ErrorCode.RATE_LIMITED.defaultMessage(),
                        Map.of("retryAfterSeconds", after.retryAfterSeconds()));
            }
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    ErrorCode.INVALID_CREDENTIALS.defaultMessage(),
                    null, "error.invalid_credentials.withdrawal_password_mismatch");
        }

        loginRateLimiter.reset(ip, limitKey);
    }
}
