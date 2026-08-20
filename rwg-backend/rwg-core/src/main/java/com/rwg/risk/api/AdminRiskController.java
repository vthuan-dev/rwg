package com.rwg.risk.api;

import com.rwg.common.PageResponse;
import com.rwg.common.web.ClientAddresses;
import com.rwg.risk.dto.AccountLinkResponse;
import com.rwg.risk.dto.CreateAccountLinkRequest;
import com.rwg.risk.dto.ReviewAccountLinkRequest;
import com.rwg.risk.dto.UserRiskProfileResponse;
import com.rwg.risk.service.AdminRiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API quản trị risk (chống đa tài khoản). Phân quyền enforce tập trung ở SecurityConfig:
 * ADMIN + RISK. Controller này bị loại khỏi rwg-user-app.
 */
@RestController
@RequestMapping("/api/v1/admin/risk")
@Tag(name = "Admin - Risk", description = "Liên kết tài khoản nghi cùng một người")
@SecurityRequirement(name = "bearerAuth")
public class AdminRiskController {

    private final AdminRiskService service;

    public AdminRiskController(AdminRiskService service) {
        this.service = service;
    }

    @GetMapping("/links")
    @Operation(summary = "Hàng đợi liên kết tài khoản; lọc theo status nếu truyền")
    public PageResponse<AccountLinkResponse> links(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return service.listLinks(status, page, size);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Hồ sơ risk của một user: dấu vết đăng ký + toàn bộ liên kết")
    public UserRiskProfileResponse userProfile(@PathVariable("id") UUID userId) {
        return service.userProfile(userId);
    }

    @PatchMapping("/links/{id}")
    @Operation(summary = "Kết luận về liên kết. CLEARED chỉ có hiệu lực cho kỳ hoa hồng TƯƠNG LAI")
    public AccountLinkResponse review(@PathVariable("id") UUID linkId,
                                      @Valid @RequestBody ReviewAccountLinkRequest request,
                                      @AuthenticationPrincipal Jwt jwt,
                                      HttpServletRequest httpRequest) {
        return service.review(linkId, request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/links")
    @Operation(summary = "Nối tay hai tài khoản. Giữ hoa hồng ngay từ kỳ kế tiếp")
    public AccountLinkResponse createManual(@Valid @RequestBody CreateAccountLinkRequest request,
                                            @AuthenticationPrincipal Jwt jwt,
                                            HttpServletRequest httpRequest) {
        return service.createManual(request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }
}
