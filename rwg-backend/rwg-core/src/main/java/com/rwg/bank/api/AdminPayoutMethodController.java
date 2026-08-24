package com.rwg.bank.api;

import com.rwg.bank.dto.AdminBankAccountRequest;
import com.rwg.bank.dto.AdminPayoutMethodResponse;
import com.rwg.bank.dto.BankAccountResponse;
import com.rwg.bank.dto.RevealedPayoutAddressResponse;
import com.rwg.bank.service.AdminPayoutMethodService;
import com.rwg.common.web.ClientAddresses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API quản trị phương thức nhận tiền của người chơi (tài khoản ngân hàng + ví USDT).
 *
 * Phân quyền theo route trong SecurityConfig:
 *   - GET danh sách (đã che): mọi vai trò quản trị, qua rule chung /api/v1/admin/**.
 *   - POST reveal (số đầy đủ): CHỈ ADMIN và FINANCE — cùng nhóm được phép chạm tiền.
 *     SUPPORT và RISK xem được danh sách nhưng KHÔNG xem được số đầy đủ.
 *   - POST thêm / DELETE gỡ: CHỈ ADMIN và FINANCE.
 *
 *     KHÔNG CHO SUPPORT dù họ là người trực chat và nhận yêu cầu đổi tài khoản từ
 *     khách: đổi được số tài khoản nhận tiền là chuyển được tiền của người khác vào
 *     tài khoản mình. Đây là quyền nặng hơn cả xem số đầy đủ. SUPPORT chuyển yêu cầu
 *     lên ADMIN/FINANCE.
 *
 * Controller này bị loại khỏi rwg-user-app (xem RwgApplication.excludeFilters).
 */
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/payout-methods")
@Tag(name = "Admin - Payout Methods",
        description = "Tài khoản ngân hàng & ví USDT của người chơi - xem số đầy đủ có ghi nhật ký")
@SecurityRequirement(name = "bearerAuth")
public class AdminPayoutMethodController {

    private final AdminPayoutMethodService service;

    public AdminPayoutMethodController(AdminPayoutMethodService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sách phương thức nhận tiền (CHỈ đã che), gồm cả bản ghi đã gỡ")
    public List<AdminPayoutMethodResponse> list(@PathVariable UUID userId) {
        return service.listForUser(userId);
    }

    /**
     * Xem số tài khoản / địa chỉ ví ĐẦY ĐỦ.
     *
     * VÌ SAO POST CHỨ KHÔNG GET, dù đây là thao tác đọc:
     *   1. Có tác dụng phụ thật — ghi một dòng audit mỗi lần gọi. GET theo quy ước là
     *      không tác dụng phụ, và các tầng trung gian được phép lặp lại/prefetch GET.
     *   2. Giữ giá trị nhạy cảm ra khỏi URL. Response của GET có thể bị cache, và URL
     *      thì nằm trong lịch sử duyệt web, log proxy và access log của server.
     */
    @PostMapping("/{methodId}/reveal")
    @Operation(summary = "Giải mã số đầy đủ để chuyển tiền. CHỈ ADMIN/FINANCE. "
            + "Mỗi lần gọi ghi audit ADMIN_PAYOUT_METHOD_REVEALED")
    public RevealedPayoutAddressResponse reveal(@PathVariable UUID userId,
                                                @PathVariable UUID methodId,
                                                @AuthenticationPrincipal Jwt jwt,
                                                HttpServletRequest httpRequest) {
        return service.reveal(userId, methodId, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    /**
     * Admin thêm tài khoản ngân hàng hộ người chơi.
     *
     * CẦN THIẾT VÌ người chơi chỉ liên kết được MỘT tài khoản và không tự gỡ được.
     * Không có endpoint này thì ai gõ sai số tài khoản sẽ kẹt vĩnh viễn.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm tài khoản ngân hàng hộ người chơi. CHỈ ADMIN/FINANCE. "
            + "Ghi audit BANK_ACCOUNT_ADDED kèm adminId và lý do")
    public BankAccountResponse add(@PathVariable UUID userId,
                                   @Valid @RequestBody AdminBankAccountRequest request,
                                   @AuthenticationPrincipal Jwt jwt,
                                   HttpServletRequest httpRequest) {
        return service.addForUser(userId, request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    /**
     * Admin gỡ tài khoản ngân hàng của người chơi (xóa mềm, giữ audit).
     *
     * {@code reason} qua query param chứ không qua body: DELETE có body là hợp lệ về mặt
     * đặc tả nhưng nhiều tầng trung gian và client cắt bỏ nó — lý do sẽ biến mất khỏi
     * nhật ký mà không báo gì.
     */
    @DeleteMapping("/{methodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Gỡ tài khoản ngân hàng của người chơi. CHỈ ADMIN/FINANCE. "
            + "Ghi audit BANK_ACCOUNT_REMOVED kèm adminId")
    public void remove(@PathVariable UUID userId,
                       @PathVariable UUID methodId,
                       @RequestParam(required = false) String reason,
                       @AuthenticationPrincipal Jwt jwt,
                       HttpServletRequest httpRequest) {
        service.removeForUser(userId, methodId, reason, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }
}
