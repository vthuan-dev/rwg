package com.rwg.identity.api;

import com.rwg.common.PageResponse;
import com.rwg.identity.dto.AdminApprovalResponse;
import com.rwg.identity.service.AdminApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lịch sử đề nghị phê duyệt thao tác admin — CHỈ ĐỌC.
 *
 * QUY TRÌNH 4 MẮT ĐÃ BỎ: điều chỉnh ví giờ thực thi ngay với mọi số tiền, không tạo
 * đề nghị chờ duyệt nữa. Hai endpoint {@code POST /{id}/approve} và {@code /{id}/reject}
 * đã xoá cùng với nghiệp vụ đó.
 *
 * Endpoint GET còn lại để tra cứu các đề nghị ĐÃ PHÁT SINH TRƯỚC ĐÂY — xoá nó nghĩa là
 * mất phần "ai đã duyệt khoản này" của những lần chuyển tiền trong quá khứ.
 */
@RestController
@RequestMapping("/api/v1/admin/approvals")
@Tag(name = "Admin - Approvals",
        description = "Lịch sử đề nghị phê duyệt (chỉ đọc) - quy trình 4 mắt đã bỏ")
@SecurityRequirement(name = "bearerAuth")
public class AdminApprovalController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminApprovalService approvalService;

    public AdminApprovalController(AdminApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    @Operation(summary = "Lịch sử đề nghị; status optional (PENDING/APPROVED/REJECTED)")
    public PageResponse<AdminApprovalResponse> list(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) UUID makerId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return approvalService.search(status, makerId, page, Math.min(size, MAX_PAGE_SIZE));
    }
}
