package com.rwg.payment.api;

import com.rwg.common.PageResponse;
import com.rwg.payment.dto.AdminWithdrawalRowResponse;
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

    /**
     * Danh sách lệnh rút cho bảng duyệt.
     *
     * Trả kèm tên người chơi và tài khoản nhận tiền (CHỈ 4 số cuối) để người vận hành nhận
     * diện được dòng nào là của ai. Số tài khoản đầy đủ nằm ở endpoint reveal riêng, mỗi lần
     * gọi có ghi nhật ký.
     */
    @GetMapping("/withdrawals")
    @Operation(summary = "Danh sách lệnh rút tiền kèm tên người chơi và tài khoản nhận (đã che), "
            + "filter status/userId/khoảng ngày (UTC)")
    public PageResponse<AdminWithdrawalRowResponse> withdrawals(@RequestParam(required = false) String status,
                                                                @RequestParam(required = false) UUID userId,
                                                                @RequestParam(required = false) String fromDate,
                                                                @RequestParam(required = false) String toDate,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return queryService.searchWithdrawals(status, userId, fromDate, toDate, page, Math.min(size, MAX_PAGE_SIZE));
    }

    /**
     * LỊCH SỬ lệnh rút — chỉ lệnh đã duyệt (SETTLED) hoặc đã từ chối (VOIDED).
     *
     * Trả kèm ai đã quyết định, lý do và thời điểm — đọc từ nhật ký hệ thống. Đây là thứ trang
     * hàng chờ không thể trả lời: sau khi xử lý xong, lệnh biến mất khỏi hàng chờ và không còn
     * đường nào truy ra ai đã bấm duyệt.
     *
     * Đường dẫn này KHÔNG chồng lấn {@code /withdrawals/{orderId}/approve} của
     * {@link AdminWithdrawalController} vì đó là POST và có thêm một đoạn đường dẫn phía sau.
     */
    @GetMapping("/withdrawals/history")
    @Operation(summary = "Lịch sử lệnh rút đã xử lý (SETTLED/VOIDED) kèm người quyết định và lý do")
    public PageResponse<AdminWithdrawalRowResponse> withdrawalHistory(@RequestParam(required = false) String status,
                                                                      @RequestParam(required = false) UUID userId,
                                                                      @RequestParam(required = false) String fromDate,
                                                                      @RequestParam(required = false) String toDate,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return queryService.searchWithdrawalHistory(status, userId, fromDate, toDate,
                page, Math.min(size, MAX_PAGE_SIZE));
    }

    @GetMapping("/withdrawals/pending-count")
    @Operation(summary = "Số lệnh rút đang chờ duyệt (badge dashboard)")
    public Map<String, Long> pendingWithdrawalCount() {
        return Map.of("pendingWithdrawals", queryService.countPendingWithdrawals());
    }
}
