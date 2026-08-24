package com.rwg.bank.service;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.domain.BankAccountStatus;
import com.rwg.bank.dto.AdminBankAccountRequest;
import com.rwg.bank.dto.AdminPayoutMethodResponse;
import com.rwg.bank.dto.BankAccountResponse;
import com.rwg.bank.dto.RevealedPayoutAddressResponse;
import com.rwg.bank.repository.BankAccountRepository;
import com.rwg.identity.repository.UserRepository;
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
 * Nghiệp vụ phương thức nhận tiền cho khu quản trị: đọc, xem số đầy đủ, và THÊM/GỠ HỘ.
 *
 * Chỉ hỗ trợ BANK.
 *
 * VÌ SAO ADMIN PHẢI THÊM/GỠ ĐƯỢC: người chơi chỉ liên kết được MỘT tài khoản và
 * không tự gỡ được (xem {@code BankAccountService}). Nếu admin cũng không sửa được thì
 * ai gõ sai số tài khoản sẽ KẸT VĨNH VIỄN: tiền rút chạy vào số sai và cách duy nhất
 * để cứu là sửa tay trong database. Chặn mà không có đường thoát là làm hỏng.
 *
 * KHÔNG CÓ HÀM "SỬA": muốn đổi thì gỡ rồi thêm lại. Bản ghi cũ giữ trạng thái
 * {@code REMOVED} nên lịch sử đổi tài khoản còn nguyên. Sửa tại chỗ sẽ ghi đè số cũ và
 * mất dấu — đúng lúc cần điều tra thì không còn gì để xem.
 */
@Service
public class AdminPayoutMethodService {

    private final BankAccountRepository repository;
    private final EncryptedStringConverter crypto;
    private final AuditTrailService audit;
    private final PayoutAddressValidator validator;
    private final UserRepository userRepository;

    public AdminPayoutMethodService(BankAccountRepository repository,
                                    EncryptedStringConverter crypto,
                                    AuditTrailService audit,
                                    PayoutAddressValidator validator,
                                    UserRepository userRepository) {
        this.repository = repository;
        this.crypto = crypto;
        this.audit = audit;
        this.validator = validator;
        this.userRepository = userRepository;
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

    /**
     * Admin thêm tài khoản ngân hàng HỘ người chơi.
     *
     * KHÔNG KIỂM MẬT KHẨU RÚT TIỀN của người chơi — admin không biết và không được
     * biết mật khẩu đó. Thẩm quyền đến từ vai trò trong JWT ({@code SecurityConfig} giới
     * hạn route này cho ADMIN + FINANCE), và mọi lần dùng đều ghi audit mang {@code adminId}.
     *
     * VẪN CHẶN NẾU ĐÃ CÓ TÀI KHOẢN ĐANG HOẠT ĐỘNG: luật "mỗi người một tài khoản"
     * là luật của dự liệu, không phải của giao diện người chơi. Nếu để admin thêm cái
     * thứ hai thì {@code findFirstByUserIdAndIsDefaultTrueAndStatus} lúc rút tiền có hai
     * ứng viên và kết quả phụ thuộc thứ tự trả về của DB — tiền đi sai chỗ một cách
     * không xác định. Muốn đổi thì gỡ cái cũ trước.
     */
    @Transactional
    public BankAccountResponse addForUser(UUID userId, AdminBankAccountRequest request,
                                          UUID adminId, String ip) {
        // Kiểm user tồn tại TRƯỚC: bảng bank_accounts không có khoá ngoại sang users, nên
        // gõ sai một ký tự trong UUID sẽ tạo tài khoản ngân hàng mồ côi mà không báo gì.
        if (!userRepository.existsById(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND,
                    ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.user");
        }

        if (repository.existsByUserIdAndStatus(userId, BankAccountStatus.ACTIVE)) {
            throw new ApiException(ErrorCode.BANK_ACCOUNT_ALREADY_LINKED);
        }

        PayoutAddressValidator.Normalized normalized = validator.validate(request.accountNumber());
        EncryptedStringConverter.CipherText cipher = crypto.encrypt(normalized.address());

        // Dọn cờ default còn sót trên bản ghi đã gỡ trước khi đặt cờ mới.
        repository.clearDefaultForUser(userId);

        BankAccount saved = repository.save(BankAccount.createBank(
                userId, request.bankCode().trim(),
                cipher.ciphertextBase64(), cipher.ivBase64(),
                normalized.maskedLast4(), request.holderName().trim(), true));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("payoutType", "BANK");
        details.put("targetUserId", userId.toString());
        details.put("maskedLast4", normalized.maskedLast4());
        details.put("bankCode", saved.getBankCode());
        details.put("byAdmin", true);
        details.put("reason", request.reason().trim());

        audit.record(adminId, null, AuditTrailService.BANK_ACCOUNT_ADDED,
                "BANK_ACCOUNT", saved.getId().toString(), details, ip);

        return BankAccountResponse.from(saved);
    }

    /**
     * Admin gỡ tài khoản ngân hàng của người chơi (xóa mềm).
     *
     * Lọc theo {@code userId} chứ không chỉ tra theo {@code methodId}: thiếu bước này thì
     * một {@code methodId} của người khác vẫn gỡ được qua URL của bất kỳ user nào.
     */
    @Transactional
    public void removeForUser(UUID userId, UUID methodId, String reason, UUID adminId, String ip) {
        BankAccount ba = repository.findFirstById(methodId)
                .filter(m -> m.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.bank_account"));

        ba.setStatus(BankAccountStatus.REMOVED);
        ba.setIsDefault(Boolean.FALSE);
        repository.save(ba);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("maskedLast4", ba.getMaskedLast4());
        details.put("targetUserId", userId.toString());
        details.put("byAdmin", true);
        details.put("reason", reason == null ? "" : reason.trim());

        audit.record(adminId, null, AuditTrailService.BANK_ACCOUNT_REMOVED,
                "BANK_ACCOUNT", ba.getId().toString(), details, ip);
    }
}
