package com.rwg.affiliate.service;

import com.rwg.affiliate.dto.DashboardSummaryResponse;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.game.repository.BetRepository;
import com.rwg.identity.domain.UserStatus;
import com.rwg.identity.repository.UserRepository;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.domain.PaymentType;
import com.rwg.payment.repository.PaymentOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * Số liệu tổng hợp cho dashboard quản trị.
 *
 * TRẠNG THÁI HOÀN TẤT KHÁC NHAU GIỮA NẠP VÀ RÚT (dễ nhầm):
 * - Nạp hoàn tất  = {@link PaymentStatus#SUCCESS}
 * - Rút hoàn tất  = {@link PaymentStatus#SETTLED}
 * Dùng sai sẽ ra tổng bằng 0 mà không có lỗi nào — không được đoán.
 *
 * Mọi khoảng thời gian là NỬA MỞ [from, to) để ngày biên không bị đếm hai lần
 * khi admin xem hai khoảng liền nhau.
 */
@Service
public class AdminDashboardService {

    /** Trần khoảng truy vấn — chặn quét toàn bảng làm nặng DB. */
    private static final int MAX_RANGE_DAYS = 366;
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final PaymentOrderRepository orderRepository;
    private final BetRepository betRepository;
    private final UserRepository userRepository;
    private final AdminAffiliateService affiliateService;

    public AdminDashboardService(PaymentOrderRepository orderRepository,
                                 BetRepository betRepository,
                                 UserRepository userRepository,
                                 AdminAffiliateService affiliateService) {
        this.orderRepository = orderRepository;
        this.betRepository = betRepository;
        this.userRepository = userRepository;
        this.affiliateService = affiliateService;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(String from, String to) {
        LocalDate toDate = parseOr(to, LocalDate.now(ZoneOffset.UTC));
        LocalDate fromDate = parseOr(from, toDate.minusDays(DEFAULT_RANGE_DAYS));
        validateRange(fromDate, toDate);

        Instant fromInstant = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        // to là NGÀY BAO GỒM -> mốc trên là đầu ngày kế tiếp (nửa mở).
        Instant toInstant = toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        BigDecimal deposits = zeroIfNull(orderRepository.sumAmountByTypeAndStatus(
                PaymentType.DEPOSIT, PaymentStatus.SUCCESS, fromInstant, toInstant));
        BigDecimal withdrawals = zeroIfNull(orderRepository.sumAmountByTypeAndStatus(
                PaymentType.WITHDRAWAL, PaymentStatus.SETTLED, fromInstant, toInstant));
        BigDecimal turnover = zeroIfNull(
                betRepository.sumSettledTurnover(fromInstant, toInstant));
        BigDecimal commission = affiliateService.totalCommissionPaid(fromDate, toDate);

        return new DashboardSummaryResponse(
                fromDate.toString(),
                toDate.toString(),
                deposits.toPlainString(),
                withdrawals.toPlainString(),
                turnover.toPlainString(),
                commission.toPlainString(),
                userRepository.countRegisteredBetween(fromInstant, toInstant),
                orderRepository.countByTypeAndStatus(PaymentType.WITHDRAWAL, PaymentStatus.PENDING),
                userRepository.count(),
                userRepository.countByStatus(UserStatus.LOCKED),
                userRepository.countByStatus(UserStatus.BANNED));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "from"), "validation.dashboard.range.invalid");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "to", "maxDays", String.valueOf(MAX_RANGE_DAYS)),
                    "validation.dashboard.range.too_wide");
        }
    }

    private LocalDate parseOr(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "date"), "validation.dashboard.date.invalid");
        }
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO);
    }
}
