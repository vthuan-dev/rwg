package com.rwg.wallet.api;

import com.rwg.common.PageResponse;
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

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
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
}
