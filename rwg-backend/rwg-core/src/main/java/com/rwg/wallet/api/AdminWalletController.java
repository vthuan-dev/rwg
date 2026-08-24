package com.rwg.wallet.api;

import com.rwg.common.PageResponse;
import com.rwg.common.web.ClientAddresses;
import com.rwg.wallet.dto.AdjustWalletRequest;
import com.rwg.wallet.dto.WalletAdjustmentResponse;
import com.rwg.wallet.dto.WalletResponse;
import com.rwg.wallet.dto.WalletTransactionResponse;
import com.rwg.wallet.service.AdminWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API quản trị ví: xem ví/ledger của user và điều chỉnh số dư THỦ CÔNG.
 *
 * Phân quyền theo route trong SecurityConfig: xem ví/ledger mở cho mọi nhân sự quản
 * trị, nhưng ĐIỀU CHỈNH SỐ DƯ chỉ ADMIN và FINANCE (SUPPORT không chạm tiền).
 *
 * Điều chỉnh thủ công chỉ nhận DELTA (CREDIT/DEBIT) kèm lý do bắt buộc — không có
 * endpoint set số dư tuyệt đối, để ledger luôn tái dựng lại được số dư.
 */
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/wallet")
@Tag(name = "Admin - Wallet", description = "Xem ví, lịch sử ledger, cộng/trừ tiền thủ công - yêu cầu ROLE_ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminWalletController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminWalletService adminWalletService;

    public AdminWalletController(AdminWalletService adminWalletService) {
        this.adminWalletService = adminWalletService;
    }

    @GetMapping
    @Operation(summary = "Xem ví của user (ví chưa tồn tại -> số dư 0)")
    public WalletResponse wallet(@PathVariable UUID userId) {
        return adminWalletService.walletOf(userId);
    }

    @GetMapping("/transactions")
    @Operation(summary = "Lịch sử biến động số dư; refType optional (vd ADJUSTMENT, COMMISSION, BET)")
    public PageResponse<WalletTransactionResponse> transactions(@PathVariable UUID userId,
                                                                @RequestParam(required = false) String refType,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return adminWalletService.ledgerOf(userId, refType, page, Math.min(size, MAX_PAGE_SIZE));
    }

    /**
     * Cộng/trừ tiền thủ công. Luôn trả 200 OK — tiền chuyển ngay trong request này.
     *
     * TRƯỚC ĐÂY có thêm nhánh 202 Accepted cho khoản vượt hạn mức, khi đó chỉ tạo đề
     * nghị chờ admin thứ hai duyệt. Quy trình 4 mắt đã bỏ nên chỉ còn một đường thoát.
     */
    @PostMapping("/adjust")
    @Operation(summary = "Cộng/trừ tiền thủ công (bắt buộc có lý do). "
            + "Thực thi ngay với mọi số tiền và sinh 1 dòng ledger ADJUSTMENT.")
    public WalletAdjustmentResponse adjust(@PathVariable UUID userId,
                                           @Valid @RequestBody AdjustWalletRequest request,
                                           @AuthenticationPrincipal Jwt jwt,
                                           HttpServletRequest httpRequest) {
        return adminWalletService.adjust(
                userId, request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }
}
