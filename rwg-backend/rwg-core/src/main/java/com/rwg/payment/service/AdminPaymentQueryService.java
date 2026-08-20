package com.rwg.payment.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.domain.PaymentType;
import com.rwg.payment.dto.PaymentOrderResponse;
import com.rwg.payment.repository.PaymentOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Tra soát lệnh nạp/rút cho khu quản trị (chặng 3) — READ-ONLY.
 * Việc duyệt/từ chối lệnh rút vẫn nằm ở {@link WithdrawalService} (đã có transition
 * nguyên tử + hoàn tiền idempotent), service này KHÔNG lặp lại logic đó.
 *
 * Khoảng thời gian mặc định 30 ngày gần nhất nếu client không truyền — tránh quét
 * toàn bảng payment_orders khi bảng lớn.
 */
@Service
public class AdminPaymentQueryService {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private final PaymentOrderRepository orderRepository;

    public AdminPaymentQueryService(PaymentOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** Danh sách lệnh nạp tiền, filter theo trạng thái / user / khoảng ngày (UTC). */
    @Transactional(readOnly = true)
    public PageResponse<PaymentOrderResponse> searchDeposits(String status, UUID userId,
                                                             String fromDate, String toDate,
                                                             int page, int size) {
        return search(PaymentType.DEPOSIT, status, userId, fromDate, toDate, page, size);
    }

    /** Danh sách lệnh rút tiền, filter theo trạng thái / user / khoảng ngày (UTC). */
    @Transactional(readOnly = true)
    public PageResponse<PaymentOrderResponse> searchWithdrawals(String status, UUID userId,
                                                                String fromDate, String toDate,
                                                                int page, int size) {
        return search(PaymentType.WITHDRAWAL, status, userId, fromDate, toDate, page, size);
    }

    /** Số lệnh rút đang chờ duyệt — badge cảnh báo trên dashboard admin. */
    @Transactional(readOnly = true)
    public long countPendingWithdrawals() {
        return orderRepository.countByTypeAndStatus(PaymentType.WITHDRAWAL, PaymentStatus.PENDING);
    }

    private PageResponse<PaymentOrderResponse> search(PaymentType type, String status, UUID userId,
                                                      String fromDate, String toDate,
                                                      int page, int size) {
        PaymentStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
        Instant to = toDate == null || toDate.isBlank()
                ? Instant.now()
                : parseDate(toDate, "toDate").plusSeconds(86400); // nửa mở: bao trọn ngày kết thúc
        Instant from = fromDate == null || fromDate.isBlank()
                ? to.minusSeconds(DEFAULT_RANGE_DAYS * 86400L)
                : parseDate(fromDate, "fromDate");
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    ErrorCode.INVALID_REQUEST.defaultMessage(), Map.of("field", "fromDate"));
        }
        return PageResponse.from(
                orderRepository.searchForAdmin(type, statusFilter, userId, from, to,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                PaymentOrderResponse::from);
    }

    private PaymentStatus parseStatus(String raw) {
        try {
            return PaymentStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "status"));
        }
    }

    /** Ngày dạng ISO yyyy-MM-dd, hiểu theo UTC (khớp mốc hạn mức rút ngày). */
    private Instant parseDate(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim()).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (RuntimeException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", field));
        }
    }
}
