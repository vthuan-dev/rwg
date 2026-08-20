package com.rwg.payment.api;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.web.ClientAddresses;
import com.rwg.config.PaymentProperties;
import com.rwg.payment.dto.DepositRequest;
import com.rwg.payment.dto.PaymentCallbackRequest;
import com.rwg.payment.dto.PaymentOrderResponse;
import com.rwg.payment.service.DepositService;
import com.rwg.payment.service.WithdrawalService;
import com.rwg.payment.dto.WithdrawalRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * API nạp/rút tiền (chặng 2 Phase b).
 * - POST /api/v1/wallet/deposits: tạo lệnh nạp (min $10 / max $50,000).
 * - POST /api/v1/wallet/withdrawals: tạo lệnh rút (cần mật khẩu rút tiền + bank default).
 * - POST /api/v1/payments/callback: webhook provider — permitAll ở SecurityConfig
 *   nhưng BẮT BUỘC header shared-secret X-Callback-Secret khớp
 *   rwg.payment.callback-secret (fix review M4); thiếu/sai -> 401.
 */
@RestController
@Tag(name = "Payments", description = "Nạp/rút tiền qua cổng thanh toán")
public class PaymentController {

    /** Header shared-secret provider phải gửi kèm webhook callback. */
    public static final String CALLBACK_SECRET_HEADER = "X-Callback-Secret";

    private final DepositService depositService;
    private final WithdrawalService withdrawalService;
    private final PaymentProperties paymentProperties;

    public PaymentController(DepositService depositService,
                             WithdrawalService withdrawalService,
                             PaymentProperties paymentProperties) {
        this.depositService = depositService;
        this.withdrawalService = withdrawalService;
        this.paymentProperties = paymentProperties;
        // Fail-fast khi khởi động (pattern giống rwg.crypto.bank-enc-key):
        // callback là endpoint công khai, KHÔNG THỂ chạy nếu thiếu secret.
        if (paymentProperties.callbackSecret() == null || paymentProperties.callbackSecret().isBlank()) {
            throw new IllegalStateException(
                    "rwg.payment.callback-secret (env RWG_PAYMENT_CALLBACK_SECRET) is required");
        }
    }

    @PostMapping("/api/v1/wallet/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo lệnh nạp tiền (min $10 / max $50,000)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public PaymentOrderResponse deposit(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody DepositRequest request) {
        return depositService.deposit(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/api/v1/wallet/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo lệnh rút tiền (debit ví ngay, chờ admin duyệt)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public PaymentOrderResponse withdraw(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody WithdrawalRequest request,
                                         HttpServletRequest httpRequest) {
        return withdrawalService.request(UUID.fromString(jwt.getSubject()), request,
                ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/api/v1/payments/callback")
    @Operation(summary = "Webhook provider thanh toán (yêu cầu header X-Callback-Secret)")
    public PaymentOrderResponse callback(
            @RequestHeader(value = CALLBACK_SECRET_HEADER, required = false) String callbackSecret,
            @Valid @RequestBody PaymentCallbackRequest request) {
        verifyCallbackSecret(callbackSecret);
        return depositService.handleCallback(request);
    }

    /**
     * Xác thực shared-secret của webhook (fix review M4): thiếu hoặc SAI -> 401.
     * So sánh constant-time chống timing attack.
     */
    private void verifyCallbackSecret(String provided) {
        if (provided == null || provided.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        byte[] expected = paymentProperties.callbackSecret().getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
    }
}
