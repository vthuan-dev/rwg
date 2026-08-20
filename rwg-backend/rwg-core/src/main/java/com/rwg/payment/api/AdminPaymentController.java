package com.rwg.payment.api;

import com.rwg.common.PageResponse;
import com.rwg.payment.dto.PaymentOrderResponse;
import com.rwg.payment.service.AdminPaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * API tra soát lệnh nạp/rút cho khu quản trị (READ-ONLY).
 * Duyệt/từ chối lệnh rút nằm ở {@link AdminWithdrawalController}.
 * /api/v1/admin/** yêu cầu ROLE_ADMIN (enforce tập trung trong SecurityConfig).
 *
 * fromDate/toDate dạng yyyy-MM-dd hiểu theo UTC. Không truyền -> 30 ngày gần nhất.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin - Payments", description = "Tra soát lệnh nạp/rút - yêu cầu ROLE_ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminPaymentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminPaymentQueryService queryService;

    public AdminPaymentController(AdminPaymentQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/deposits")
    @Operation(summary = "Danh sách lệnh nạp tiền, filter status/userId/khoảng ngày (UTC)")
    public PageResponse<PaymentOrderResponse> deposits(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) UUID userId,
                                                       @RequestParam(required = false) String fromDate,
                                                       @RequestParam(required = false) String toDate,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return queryService.searchDeposits(status, userId, fromDate, toDate, page, Math.min(size, MAX_PAGE_SIZE));
    }

    @GetMapping("/withdrawals")
    @Operation(summary = "Danh sách lệnh rút tiền, filter status/userId/khoảng ngày (UTC)")
    public PageResponse<PaymentOrderResponse> withdrawals(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) UUID userId,
                                                          @RequestParam(required = false) String fromDate,
                                                          @RequestParam(required = false) String toDate,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return queryService.searchWithdrawals(status, userId, fromDate, toDate, page, Math.min(size, MAX_PAGE_SIZE));
    }

    @GetMapping("/withdrawals/pending-count")
    @Operation(summary = "Số lệnh rút đang chờ duyệt (badge dashboard)")
    public Map<String, Long> pendingWithdrawalCount() {
        return Map.of("pendingWithdrawals", queryService.countPendingWithdrawals());
    }
}
