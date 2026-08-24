package com.rwg.wallet.api;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.payment.domain.PaymentType;
import com.rwg.payment.dto.PaymentOrderResponse;
import com.rwg.payment.service.MyPaymentOrderService;
import com.rwg.wallet.dto.WalletResponse;
import com.rwg.wallet.dto.WalletTransactionResponse;
import com.rwg.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * API ví cá nhân (yêu cầu JWT hợp lệ).
 */
@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Wallet", description = "Số dư và lịch sử giao dịch ví")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;
    private final MyPaymentOrderService myPaymentOrderService;

    public WalletController(WalletService walletService,
                            MyPaymentOrderService myPaymentOrderService) {
        this.walletService = walletService;
        this.myPaymentOrderService = myPaymentOrderService;
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy ví + số dư hiện tại của user (tạo lazy nếu chưa có)")
    public WalletResponse me(@AuthenticationPrincipal Jwt jwt) {
        return walletService.getWallet(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/me/transactions")
    @Operation(summary = "Lịch sử giao dịch ví phân trang (mới nhất trước)")
    public PageResponse<WalletTransactionResponse> transactions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return walletService.listTransactions(UUID.fromString(jwt.getSubject()), page, Math.min(size, 100));
    }

    /**
     * Lịch sử LỆNH nạp/rút, khác với {@code /me/transactions} ở trên.
     *
     * Hai danh sách này KHÔNG trùng nhau và đều cần thiết:
     * - {@code /me/transactions} là sổ cái ví: mọi bút toán gồm cả cược, thắng, hoa hồng.
     * - {@code /me/orders} là các lệnh nạp/rút kèm TRẠNG THÁI DUYỆT. Một lệnh rút đang
     *   chờ admin duyệt chỉ thấy được ở đây; sổ cái chỉ có dòng trừ tiền mà không cho
     *   biết lệnh đó đã được duyệt, bị từ chối, hay vẫn treo.
     *
     * {@code type} tuỳ chọn: "DEPOSIT" hoặc "WITHDRAWAL"; bỏ trống thì trả cả hai.
     */
    @GetMapping("/me/orders")
    @Operation(summary = "Lịch sử lệnh nạp/rút của chính mình (kèm trạng thái duyệt)")
    public PageResponse<PaymentOrderResponse> orders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return myPaymentOrderService.list(UUID.fromString(jwt.getSubject()),
                parseType(type), page, size);
    }

    /**
     * Đổi tham số {@code type} thành enum, null nếu không truyền.
     *
     * TỪ CHỐI giá trị lạ bằng 400 thay vì coi như không lọc: gõ sai "DEPOSITS" mà server
     * âm thầm trả CẢ lệnh rút thì màn hình lịch sử nạp hiện lẫn lệnh rút, và người viết
     * client không có cách nào biết mình gõ sai.
     */
    private static PaymentType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PaymentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "type"));
        }
    }
}
