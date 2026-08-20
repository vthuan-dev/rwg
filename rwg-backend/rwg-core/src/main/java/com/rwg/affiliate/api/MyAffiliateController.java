package com.rwg.affiliate.api;

import com.rwg.affiliate.dto.MyAffiliateSummaryResponse;
import com.rwg.affiliate.dto.MyCommissionResponse;
import com.rwg.affiliate.dto.MyDownlineMemberResponse;
import com.rwg.affiliate.dto.ReferralCodeResponse;
import com.rwg.affiliate.service.MyAffiliateService;
import com.rwg.common.PageResponse;
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
 * API đại lý cho CHÍNH NGƯỜI CHƠI. Đây là controller của app player, KHÔNG nằm dưới
 * /api/v1/admin/** nên không bị loại khỏi rwg-user-app.
 *
 * Mọi endpoint chỉ đọc dữ liệu của người gọi: userId luôn lấy từ JWT subject, không
 * có tham số nào cho phép chỉ định người khác. Nhờ vậy không cần kiểm quyền riêng —
 * đường xem dữ liệu người khác đơn giản là không tồn tại.
 */
@RestController
@RequestMapping("/api/v1/affiliate/me")
@Tag(name = "Affiliate - Của tôi", description = "Mã giới thiệu, tuyến dưới và hoa hồng của chính người chơi")
@SecurityRequirement(name = "bearerAuth")
public class MyAffiliateController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MyAffiliateService service;

    public MyAffiliateController(MyAffiliateService service) {
        this.service = service;
    }

    @GetMapping("/code")
    @Operation(summary = "Mã giới thiệu của tôi (sinh tự động ở lần xem đầu tiên)")
    public ReferralCodeResponse myCode(@AuthenticationPrincipal Jwt jwt) {
        return service.myCode(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/summary")
    @Operation(summary = "Tổng quan: mã, số tuyến dưới, tổng hoa hồng đã nhận, % hiện hành")
    public MyAffiliateSummaryResponse mySummary(@AuthenticationPrincipal Jwt jwt) {
        return service.mySummary(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/downline")
    @Operation(summary = "Tuyến dưới của tôi theo cấp (1 hoặc 2); username được che")
    public PageResponse<MyDownlineMemberResponse> myDownline(@AuthenticationPrincipal Jwt jwt,
                                                            @RequestParam(defaultValue = "1") int level,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return service.myDownline(UUID.fromString(jwt.getSubject()), level, page,
                Math.min(size, MAX_PAGE_SIZE));
    }

    @GetMapping("/commissions")
    @Operation(summary = "Hoa hồng tôi đã nhận; mặc định 30 ngày gần nhất")
    public PageResponse<MyCommissionResponse> myCommissions(@AuthenticationPrincipal Jwt jwt,
                                                            @RequestParam(required = false) String from,
                                                            @RequestParam(required = false) String to,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return service.myCommissions(UUID.fromString(jwt.getSubject()), from, to, page,
                Math.min(size, MAX_PAGE_SIZE));
    }
}
