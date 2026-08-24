package com.rwg.bank.api;

import com.rwg.bank.dto.BankAccountRequest;
import com.rwg.bank.dto.BankAccountResponse;
import com.rwg.bank.service.BankAccountService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API liên kết tài khoản ngân hàng (yêu cầu JWT hợp lệ).
 * Response CHỈ chứa số TK đã mask — KHÔNG lộ plaintext.
 */
@RestController
@RequestMapping("/api/v1/wallet/me/bank-accounts")
@Tag(name = "Bank Accounts", description = "Liên kết tài khoản ngân hàng (mã hóa AES-256-GCM)")
@SecurityRequirement(name = "bearerAuth")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    public BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm tài khoản ngân hàng — CHỈ ĐƯỢC MỘT. "
            + "Đã có tài khoản thì trả 409, phải liên hệ CSKH để đổi")
    public BankAccountResponse add(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody BankAccountRequest request,
                                   HttpServletRequest httpRequest) {
        return bankAccountService.add(UUID.fromString(jwt.getSubject()), request,
                ClientAddresses.clientIp(httpRequest));
    }

    @GetMapping
    @Operation(summary = "Danh sách tài khoản ngân hàng (chỉ masked)")
    public List<BankAccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return bankAccountService.list(UUID.fromString(jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "LUÔN trả 409 — người chơi không tự gỡ được tài khoản nhận tiền. "
            + "Giữ route để client cũ nhận lỗi có nghĩa thay vì 404")
    public void remove(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID id,
                       HttpServletRequest httpRequest) {
        bankAccountService.remove(UUID.fromString(jwt.getSubject()), id,
                ClientAddresses.clientIp(httpRequest));
    }
}
