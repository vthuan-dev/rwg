package com.rwg.payment.service;

import com.rwg.common.PageResponse;
import com.rwg.payment.domain.PaymentOrder;
import com.rwg.payment.domain.PaymentType;
import com.rwg.payment.dto.PaymentOrderResponse;
import com.rwg.payment.repository.PaymentOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Lịch sử lệnh nạp/rút của CHÍNH người chơi đang đăng nhập.
 *
 * VÌ SAO TÁCH KHỎI {@link AdminPaymentQueryService}: hai bên trả hai mức thông tin khác
 * nhau và phục vụ hai mục đích khác nhau. Khu quản trị cần tra soát chéo nên nhận được
 * cả {@code userId} và bộ lọc theo người dùng; người chơi chỉ được xem lệnh của mình.
 * Dùng chung một service rồi truyền cờ "có phải admin không" là cách dễ nhất để một
 * ngày nào đó rò dữ liệu người khác.
 *
 * Chỉ đọc, không tạo ví, không đổi trạng thái lệnh.
 */
@Service
public class MyPaymentOrderService {

    /**
     * Trần số dòng mỗi trang.
     *
     * Chặn ở tầng service chứ không tin tham số từ client: gửi {@code size=100000} sẽ
     * kéo toàn bộ lịch sử vào bộ nhớ trong một truy vấn.
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentOrderRepository orderRepository;

    public MyPaymentOrderService(PaymentOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Một trang lệnh nạp/rút của người chơi, mới nhất trước.
     *
     * {@code type} là TUỲ CHỌN: null trả cả hai loại lẫn nhau theo thời gian (màn lịch sử
     * chung), còn truyền vào thì chỉ trả loại đó (hai màn lịch sử nạp và rút riêng).
     *
     * Trả {@link PaymentOrderResponse} — DTO này CỐ TÌNH không chứa {@code providerTxnId}
     * (xem chú thích của nó: lộ mã đó cho phép dựng callback giả), nên an toàn để trả
     * thẳng cho người chơi mà không cần lọc thêm trường nào.
     */
    @Transactional(readOnly = true)
    public PageResponse<PaymentOrderResponse> list(UUID userId, PaymentType type, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        PageRequest pageable = PageRequest.of(safePage, safeSize);

        // Thứ tự sắp xếp nằm trong tên phương thức repository, KHÔNG truyền qua Sort:
        // client không được quyết định sắp theo cột nào.
        Page<PaymentOrder> orders = type == null
                ? orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : orderRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);

        return PageResponse.from(orders, PaymentOrderResponse::from);
    }
}
