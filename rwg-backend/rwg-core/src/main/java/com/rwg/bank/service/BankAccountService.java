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
 *
 * MỖI NGƯỜI CHỈ MỘT TÀI KHOẢN, VÀ KHÔNG TỰ GỠ ĐƯỢC:
 * Sau khi liên kết tài khoản đầu tiên, người chơi không thêm được cái thứ hai và
 * không gỡ được cái đang có. Muốn đổi thì liên hệ CSKH để admin làm hộ qua
 * {@code AdminPayoutMethodService}.
 *
 * LÝ DO: đổi được số tài khoản nhận tiền là chuyển được toàn bộ tiền rút của nạn
 * nhân sang chỗ khác. Đây là thao tác nhạy cảm nhất sau khi ai đó chiếm được phiên
 * đăng nhập đang mở. Để một người thật xác nhận qua chat an toàn hơn là tin vào
 * mật khẩu cấp hai — mật khẩu cũng có thể bị lấy cùng lúc với phiên.
 *
 * ENDPOINT {@code DELETE} VẪN GIỮ, giờ luôn trả 409. Gỡ hẳn route sẽ làm client cũ
 * nhận 404 và không hiểu vì sao.
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

        // 1) MỖI NGƯỜI MỘT TÀI KHOẢN.
        //
        // THỨ TỰ QUAN TRỌNG — KIỂM MẬT KHẨU TRƯỚC, KIỂM ĐÃ-CÓ-TÀI-KHOẢN SAU:
        // nếu đảo lại, bất kỳ ai chiếm được phiên cũng dò được nạn nhân đã liên kết
        // tài khoản hay chưa mà KHÔNG cần biết mật khẩu rút tiền — hai thông báo lỗi
        // khác nhau là một kênh rò rỉ thông tin.
        if (repository.existsByUserIdAndStatus(userId, BankAccountStatus.ACTIVE)) {
            throw new ApiException(ErrorCode.BANK_ACCOUNT_ALREADY_LINKED);
        }

        // Kiểm + chuẩn hoá TRƯỚC khi mã hoá.
        PayoutAddressValidator.Normalized normalized =
                validator.validate(request.accountNumber());

        EncryptedStringConverter.CipherText cipher = crypto.encrypt(normalized.address());

        // Tài khoản duy nhất thì LUÔN là default — trường {@code setDefault} của request
        // giờ không còn ý nghĩa. Vẫn gọi clearDefaultForUser để dọn cờ còn sót trên
        // các bản ghi đã gỡ: một bản ghi REMOVED mà vẫn isDefault=true sẽ làm
        // findFirstByUserIdAndIsDefaultTrueAndStatus trả sai tài khoản khi rút tiền.
        repository.clearDefaultForUser(userId);

        BankAccount saved = repository.save(BankAccount.createBank(
                userId, request.bankCode().trim(),
                cipher.ciphertextBase64(), cipher.ivBase64(),
                normalized.maskedLast4(), request.holderName().trim(), true));

        audit.record(userId, null, AuditTrailService.BANK_ACCOUNT_ADDED, "BANK_ACCOUNT",
                saved.getId().toString(), addDetails(saved, normalized.maskedLast4()), ip);
        return BankAccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> list(UUID userId) {
        return repository.findByUserIdAndStatusOrderByCreatedAtAsc(userId, BankAccountStatus.ACTIVE)
                .stream().map(BankAccountResponse::from).toList();
    }

    /**
     * Người chơi TỰ gỡ tài khoản — LUÔN BỊ CHẬN.
     *
     * CHẮN Ở TẦNG SERVICE, KHÔNG CHỈ ẨN NÚT: ẩn nút trên giao diện là để người dùng
     * khỏi bối rối, không phải là biện pháp bảo vệ. Ai mở DevTools cũng gọi thẳng
     * được {@code DELETE /api/v1/wallet/me/bank-accounts/{id}}.
     *
     * Admin gỡ hộ qua {@code AdminPayoutMethodService.removeForUser}, dùng chung
     * {@link #softRemove} bên dưới.
     */
    @Transactional
    public void remove(UUID userId, UUID bankAccountId, String ip) {
        throw new ApiException(ErrorCode.BANK_ACCOUNT_REMOVE_FORBIDDEN);
    }

    /**
     * Xóa mềm (REMOVED) — giữ audit trail. DÙNG CHUNG cho luồng admin.
     *
     * @param actorId ai thực hiện — chính chủ hay admin. Phải ghi đúng vào audit để
     *     tra được ai đã đổi tài khoản nhận tiền của ai.
     */
    @Transactional
    public void softRemove(UUID userId, UUID bankAccountId, UUID actorId, String ip) {
        BankAccount ba = repository.findFirstById(bankAccountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.bank_account"));
        ba.setStatus(BankAccountStatus.REMOVED);
        ba.setIsDefault(Boolean.FALSE);
        repository.save(ba);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("maskedLast4", ba.getMaskedLast4());
        details.put("targetUserId", userId.toString());
        details.put("byAdmin", !actorId.equals(userId));
        audit.record(actorId, null, AuditTrailService.BANK_ACCOUNT_REMOVED, "BANK_ACCOUNT",
                ba.getId().toString(), details, ip);
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
            // 400, KHÔNG 401 — xem javadoc của WITHDRAWAL_PASSWORD_MISMATCH. Dùng 401
            // ở đây làm frontend tưởng phiên hết hạn và đăng xuất người dùng.
            throw new ApiException(ErrorCode.WITHDRAWAL_PASSWORD_MISMATCH);
        }

        loginRateLimiter.reset(ip, limitKey);
    }
}
