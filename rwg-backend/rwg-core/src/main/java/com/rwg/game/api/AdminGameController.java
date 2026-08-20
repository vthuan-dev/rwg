package com.rwg.game.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.game.dto.GameTableResponse;
import com.rwg.game.dto.UpdateTableLimitsRequest;
import com.rwg.game.dto.UpdateTableStatusRequest;
import com.rwg.game.service.AdminGameService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API quản trị bàn chơi. Phân quyền enforce tập trung ở SecurityConfig.
 * Controller này bị loại khỏi rwg-user-app (xem RwgApplication.excludeFilters).
 */
@RestController
@RequestMapping("/api/v1/admin/games")
@Tag(name = "Admin - Game", description = "Bật/tắt bàn và hạn mức cược")
@SecurityRequirement(name = "bearerAuth")
public class AdminGameController {

    private final AdminGameService service;

    public AdminGameController(AdminGameService service) {
        this.service = service;
    }

    @GetMapping("/tables")
    @Operation(summary = "Toàn bộ bàn chơi, GỒM CẢ bàn đã tắt")
    public List<GameTableResponse> tables() {
        return service.listAllTables();
    }

    @PatchMapping("/tables/{id}/status")
    @Operation(summary = "Bật/tắt bàn. Bàn tắt sẽ dừng ở cuối vòng đang chạy, không hủy vòng giữa dòng")
    public GameTableResponse changeStatus(@PathVariable("id") UUID tableId,
                                         @Valid @RequestBody UpdateTableStatusRequest request,
                                         @AuthenticationPrincipal Jwt jwt,
                                         HttpServletRequest httpRequest) {
        return service.changeStatus(tableId, request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    @PatchMapping("/tables/{id}/limits")
    @Operation(summary = "Đổi hạn mức cược. Chỉ áp cho cược mới; cược đã đặt không bị ảnh hưởng")
    public GameTableResponse updateLimits(@PathVariable("id") UUID tableId,
                                          @Valid @RequestBody UpdateTableLimitsRequest request,
                                          @AuthenticationPrincipal Jwt jwt,
                                          HttpServletRequest httpRequest) {
        return service.updateLimits(tableId, request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }
}
