package com.rwg.payment.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.payment.dto.PaymentOrderResponse;
import com.rwg.payment.dto.WithdrawalDecisionRequest;
import com.rwg.payment.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API admin duyệt/từ chối lệnh rút tiền. /api/v1/admin/** yêu cầu ROLE_ADMIN
 * (enforce tập trung trong SecurityConfig).
 *
 * Cả hai thao tác BẮT BUỘC kèm lý do — xem {@link WithdrawalDecisionRequest} để biết vì sao.
 */
@RestController
@RequestMapping("/api/v1/admin/withdrawals")
@Tag(name = "Admin", description = "Duyệt/từ chối lệnh rút tiền - yêu cầu ROLE_ADMIN")
public class AdminWithdrawalController {

    private final WithdrawalService withdrawalService;

    public AdminWithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Duyệt lệnh rút: PENDING -> SETTLED (không double-credit). Bắt buộc kèm lý do")
    public PaymentOrderResponse approve(@PathVariable UUID id,
                                        @Valid @RequestBody WithdrawalDecisionRequest request,
                                        @AuthenticationPrincipal Jwt jwt,
                                        HttpServletRequest httpRequest) {
        return withdrawalService.approve(id, UUID.fromString(jwt.getSubject()),
                request.note().trim(), ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối lệnh rút: hoàn tiền credit(REFUND) + VOIDED. Bắt buộc kèm lý do")
    public PaymentOrderResponse reject(@PathVariable UUID id,
                                       @Valid @RequestBody WithdrawalDecisionRequest request,
                                       @AuthenticationPrincipal Jwt jwt,
                                       HttpServletRequest httpRequest) {
        return withdrawalService.reject(id, UUID.fromString(jwt.getSubject()),
                request.note().trim(), ClientAddresses.clientIp(httpRequest));
    }
}
