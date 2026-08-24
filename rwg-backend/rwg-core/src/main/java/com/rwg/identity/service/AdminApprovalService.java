package com.rwg.identity.service;

import com.rwg.common.PageResponse;
import com.rwg.identity.domain.AdminApprovalRequest;
import com.rwg.identity.domain.User;
import com.rwg.identity.dto.AdminApprovalResponse;
import com.rwg.identity.repository.AdminApprovalRequestRepository;
import com.rwg.identity.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Tra cứu lịch sử đề nghị phê duyệt thao tác admin — CHỈ ĐỌC.
 *
 * ===== QUY TRÌNH 4 MẮT ĐÃ BỎ =====
 * Trước đây lớp này là trung tâm của quy trình maker-checker: điều chỉnh ví vượt hạn
 * mức không thực thi ngay mà trở thành ĐỀ NGHỊ, phải có admin THỨ HAI phê duyệt thì
 * tiền mới chuyển. Quy trình đó đã bỏ theo yêu cầu vận hành — mọi khoản điều chỉnh
 * giờ thực thi ngay trong {@code AdminWalletService.adjust}.
 *
 * VÌ SAO LỚP NÀY CÒN TỒN TẠI: bảng {@code admin_approval_requests} là dấu vết của
 * những lần chuyển tiền đã qua phê duyệt trong quá khứ. Xoá bảng nghĩa là mất vĩnh
 * viễn phần "ai đã duyệt khoản này và vì sao" của các khoản đó. Nên bảng và endpoint
 * đọc được giữ lại, chỉ bỏ khả năng TẠO MỚI và QUYẾT ĐỊNH.
 *
 * KHÔNG còn phụ thuộc {@code WalletService}: lớp này không chạm tiền nữa.
 */
@Service
public class AdminApprovalService {

    private final AdminApprovalRequestRepository repository;
    private final UserRepository userRepository;

    public AdminApprovalService(AdminApprovalRequestRepository repository,
                                UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminApprovalResponse> search(String status, UUID makerId, int page, int size) {
        String statusFilter = status == null || status.isBlank()
                ? null : status.trim().toUpperCase();
        return PageResponse.from(
                repository.searchForAdmin(statusFilter, makerId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                this::toResponse);
    }

    private AdminApprovalResponse toResponse(AdminApprovalRequest request) {
        String targetUsername = userRepository.findById(request.getTargetUserId())
                .map(User::getUsername)
                .orElse("N/A");
        String makerUsername = userRepository.findById(request.getMakerId())
                .map(User::getUsername)
                .orElse("N/A");
        return AdminApprovalResponse.from(request, targetUsername, makerUsername);
    }
}
