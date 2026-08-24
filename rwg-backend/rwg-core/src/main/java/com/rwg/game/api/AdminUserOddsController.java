package com.rwg.game.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.game.dto.SetUserOddsRequest;
import com.rwg.game.dto.UserOddsResponse;
import com.rwg.game.service.AdminUserOddsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API quản trị tỷ lệ cược riêng theo người chơi.
 *
 * Phân quyền theo route trong SecurityConfig: ĐỌC mở cho mọi nhân sự quản trị (xem tỷ lệ
 * không thay đổi gì), GHI chỉ ADMIN.
 *
 * Lý do chỉ ADMIN được ghi: nâng tỷ lệ của một tài khoản rồi để tài khoản đó cược và thắng
 * là một đường rút tiền không đi qua sổ điều chỉnh ví nào.
 */
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/game-odds")
@Tag(name = "Admin - User Odds",
        description = "Tỷ lệ cược riêng theo người chơi ở từng bàn - ghi yêu cầu ROLE_ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserOddsController {

    private final AdminUserOddsService service;

    public AdminUserOddsController(AdminUserOddsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Bảng tỷ lệ của người chơi ở mọi bàn, kèm mức chung để đối chiếu")
    public UserOddsResponse odds(@PathVariable UUID userId) {
        return service.oddsOf(userId);
    }

    /**
     * Đặt hoặc đổi tỷ lệ.
     *
     * PUT chứ không POST: cùng một (người chơi, bàn, loại cược) gửi hai lần cho cùng một
     * kết quả, nên phép toán này là ghi-đè chứ không phải tạo-mới.
     */
    @PutMapping
    @Operation(summary = "Đặt tỷ lệ riêng cho một loại cược (bắt buộc có lý do)")
    public ResponseEntity<Void> setOdds(@PathVariable UUID userId,
                                        @AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody SetUserOddsRequest request,
                                        HttpServletRequest http) {
        service.setOdds(userId, UUID.fromString(jwt.getSubject()), request,
                ClientAddresses.clientIp(http));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tableId}/{betType}")
    @Operation(summary = "Xoá tỷ lệ riêng, đưa người chơi về mức chung")
    public ResponseEntity<Void> removeOdds(@PathVariable UUID userId,
                                           @PathVariable UUID tableId,
                                           @PathVariable String betType,
                                           @AuthenticationPrincipal Jwt jwt,
                                           HttpServletRequest http) {
        service.removeOdds(userId, UUID.fromString(jwt.getSubject()), tableId, betType,
                ClientAddresses.clientIp(http));
        return ResponseEntity.noContent().build();
    }
}
