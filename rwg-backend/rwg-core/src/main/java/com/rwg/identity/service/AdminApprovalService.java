package com.rwg.identity.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.common.money.Money;
import com.rwg.identity.domain.AdminApprovalRequest;
import com.rwg.identity.dto.AdminApprovalResponse;
import com.rwg.identity.repository.AdminApprovalRequestRepository;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.service.WalletService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Quy trình 4 mắt (maker-checker) cho thao tác admin vượt hạn mức.
 *
 * ===== VÌ SAO CẦN =====
 * Trước đây mọi admin vừa cộng được tiền vào ví vừa tự duyệt được lệnh rút, nên một
 * người có thể chuyển tiền ra khỏi sàn trong 2 request. Audit log chỉ ghi vết SAU KHI
 * mất tiền. Service này biến thao tác vượt ngưỡng thành ĐỀ NGHỊ: tiền chỉ chuyển khi
 * có admin THỨ HAI phê duyệt.
 *
 * ===== BA CHỐT AN TOÀN =====
 * 1. Người tạo KHÔNG được tự duyệt — kiểm ở service (để trả mã lỗi i18n rõ ràng) và
 *    chốt lại bằng CHECK constraint chk_admin_approval_maker_ne_checker ở DB.
 * 2. Chuyển trạng thái bằng UPDATE ĐIỀU KIỆN NGUYÊN TỬ (decideIfPending trả affected
 *    rows). Hai admin bấm duyệt đồng thời -> chỉ 1 thắng, không chi tiền hai lần.
 *    KHÔNG dùng mẫu kiểm-tra-rồi-ghi vì có khe race giữa hai bước.
 * 3. Tiền được chuyển qua {@link WalletService} với idempotencyKey lưu sẵn trong đề
 *    nghị -> tận dụng wallet_ledger_guard, nên kể cả gọi lại cũng không cộng hai lần.
 *
 * Thứ tự bắt buộc: transition trạng thái TRƯỚC, chuyển tiền SAU, cùng transaction.
 * Nếu chuyển tiền lỗi thì cả hai rollback — đề nghị trở lại PENDING, không có cảnh
 * "đã duyệt nhưng chưa có tiền".
 */
@Service
public class AdminApprovalService {

    private final AdminApprovalRequestRepository repository;
    private final WalletService walletService;
    private final AuditTrailService audit;

    public AdminApprovalService(AdminApprovalRequestRepository repository,
                                WalletService walletService,
                                AuditTrailService audit) {
        this.repository = repository;
        this.walletService = walletService;
        this.audit = audit;
    }

    /**
     * Tạo đề nghị chờ duyệt. KHÔNG chạm tiền ở bước này.
     * Gọi từ {@code AdminWalletService.adjust} khi số tiền vượt trần mỗi lần.
     */
    @Transactional
    public AdminApprovalResponse createWalletAdjustment(UUID targetUserId, String direction,
                                                       BigDecimal amount, String reason,
                                                       UUID makerId, String ip) {
        AdminApprovalRequest request = new AdminApprovalRequest(
                AdminApprovalRequest.TYPE_WALLET_ADJUSTMENT, targetUserId, direction, amount,
                reason, makerId, "ADJUST:" + UUID.randomUUID());
        repository.saveAndFlush(request);

        audit.record(makerId, null, AuditTrailService.ADMIN_APPROVAL_REQUESTED,
                "APPROVAL_REQUEST", request.getId().toString(),
                Map.of("targetUserId", targetUserId.toString(),
                        "direction", direction,
                        "amount", amount.toPlainString(),
                        "reason", reason), ip);
        return AdminApprovalResponse.from(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminApprovalResponse> search(String status, UUID makerId, int page, int size) {
        String statusFilter = status == null || status.isBlank()
                ? null : status.trim().toUpperCase();
        return PageResponse.from(
                repository.searchForAdmin(statusFilter, makerId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                AdminApprovalResponse::from);
    }

    /**
     * Phê duyệt và THỰC THI chuyển tiền.
     *
     * @param checkerId admin phê duyệt — phải KHÁC người tạo đề nghị
     */
    @Transactional
    public AdminApprovalResponse approve(UUID requestId, UUID checkerId, String note, String ip) {
        AdminApprovalRequest request = requirePending(requestId, checkerId);

        int updated = repository.decideIfPending(requestId, AdminApprovalRequest.STATUS_APPROVED,
                checkerId, note, Instant.now());
        if (updated == 0) {
            // Admin khác vừa xử lý xong trong khe thời gian giữa hai lệnh.
            throw new ApiException(ErrorCode.APPROVAL_ALREADY_DECIDED,
                    ErrorCode.APPROVAL_ALREADY_DECIDED.defaultMessage(), null,
                    "error.approval.already_decided");
        }

        Money amount = Money.of(request.getAmount());
        // Ghi adminId (maker) vào refId để trần hạn mức ngày tính được đúng người
        // khởi tạo thao tác — người chịu trách nhiệm về khoản tiền này.
        String refId = request.getMakerId().toString();
        Money balanceAfter = "CREDIT".equals(request.getDirection())
                ? walletService.credit(request.getTargetUserId(), amount,
                        WalletRefType.ADJUSTMENT, refId, request.getIdempotencyKey())
                : walletService.debit(request.getTargetUserId(), amount,
                        WalletRefType.ADJUSTMENT, refId, request.getIdempotencyKey());

        audit.record(checkerId, null, AuditTrailService.ADMIN_APPROVAL_APPROVED,
                "APPROVAL_REQUEST", requestId.toString(),
                Map.of("makerId", request.getMakerId().toString(),
                        "targetUserId", request.getTargetUserId().toString(),
                        "direction", request.getDirection(),
                        "amount", request.getAmount().toPlainString(),
                        "balanceAfter", balanceAfter.amount().toPlainString()), ip);

        // ĐỒNG BỘ lại entity trong bộ nhớ với giá trị vừa UPDATE.
        //
        // BẮT BUỘC: decideIfPending là bulk UPDATE nên KHÔNG cập nhật entity đang
        // được quản lý; findById sau đó trả BẢN CACHE vẫn ghi PENDING, khiến client
        // thấy "chờ duyệt" cho khoản ĐÃ chi tiền.
        //
        // CỐ TÌNH không dùng clearAutomatically = true để đồng bộ: EntityManager.clear()
        // giữa luồng tiền chính là nguyên nhân đã làm MẤT dòng ledger ở lỗi trước đây.
        request.decide(AdminApprovalRequest.STATUS_APPROVED, checkerId, note);
        return AdminApprovalResponse.from(request);
    }

    /** Từ chối đề nghị — KHÔNG chuyển tiền. */
    @Transactional
    public AdminApprovalResponse reject(UUID requestId, UUID checkerId, String note, String ip) {
        AdminApprovalRequest request = requirePending(requestId, checkerId);

        int updated = repository.decideIfPending(requestId, AdminApprovalRequest.STATUS_REJECTED,
                checkerId, note, Instant.now());
        if (updated == 0) {
            throw new ApiException(ErrorCode.APPROVAL_ALREADY_DECIDED,
                    ErrorCode.APPROVAL_ALREADY_DECIDED.defaultMessage(), null,
                    "error.approval.already_decided");
        }

        audit.record(checkerId, null, AuditTrailService.ADMIN_APPROVAL_REJECTED,
                "APPROVAL_REQUEST", requestId.toString(),
                Map.of("makerId", request.getMakerId().toString(),
                        "targetUserId", request.getTargetUserId().toString(),
                        "amount", request.getAmount().toPlainString(),
                        "note", note == null ? "" : note), ip);

        // Đồng bộ entity sau bulk UPDATE — xem giải thích ở approve().
        request.decide(AdminApprovalRequest.STATUS_REJECTED, checkerId, note);
        return AdminApprovalResponse.from(request);
    }

    // ===== helpers =====

    /**
     * Đề nghị phải tồn tại, còn PENDING, và checker phải KHÁC maker.
     *
     * Kiểm maker != checker Ở ĐÂY để trả 400 kèm message i18n; CHECK constraint của DB
     * là lớp chốt cuối, không phải lớp báo lỗi cho người dùng.
     */
    private AdminApprovalRequest requirePending(UUID requestId, UUID checkerId) {
        AdminApprovalRequest request = repository.findById(requestId)
                .orElseThrow(this::notFound);
        if (!request.isPending()) {
            throw new ApiException(ErrorCode.APPROVAL_ALREADY_DECIDED,
                    ErrorCode.APPROVAL_ALREADY_DECIDED.defaultMessage(), null,
                    "error.approval.already_decided");
        }
        if (request.getMakerId().equals(checkerId)) {
            throw new ApiException(ErrorCode.CANNOT_APPROVE_OWN_REQUEST,
                    ErrorCode.CANNOT_APPROVE_OWN_REQUEST.defaultMessage(), null,
                    "error.approval.cannot_approve_own");
        }
        return request;
    }

    private ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(),
                null, "error.not_found.approval_request");
    }
}
