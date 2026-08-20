package com.rwg.common.money;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value object tiền tệ chuẩn của toàn hệ thống (DECISIONS.md):
 * BigDecimal + scale 8 + RoundingMode.HALF_UP. CẤM float/double trong package này
 * (được enforce bằng ArchUnit).
 */
public record Money(BigDecimal amount) {

    public static final int SCALE = 8;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public static Money of(String value) {
        Objects.requireNonNull(value, "money string must not be null");
        return new Money(new BigDecimal(value));
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    /**
     * Chỉ nhận String hoặc BigDecimal — chặn float/double ngay từ API của Money.
     */
    public static Money of(Number value) {
        if (value instanceof BigDecimal bd) {
            return new Money(bd);
        }
        if (value instanceof Float || value instanceof Double) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Cấm dùng float/double cho tiền tệ - xem DECISIONS.md");
        }
        if (value instanceof Long || value instanceof Integer) {
            return new Money(BigDecimal.valueOf(value.longValue()));
        }
        throw new ApiException(ErrorCode.INVALID_REQUEST, "Kiểu tiền tệ không hỗ trợ: " + value.getClass());
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    /**
     * Tiền lời theo odds (M2): stake * odds, ví dụ $10 x 35 = $350 lời.
     */
    public Money profitAtOdds(BigDecimal odds) {
        return multiply(odds);
    }

    /**
     * Payout khi thắng theo quy ước M2: tiền lời theo odds + HOÀN NGUYÊN STAKE.
     * Ví dụ cược $10 thắng odds 35:1 -> nhận $360.
     */
    public Money winningPayoutAtOdds(BigDecimal odds) {
        return add(profitAtOdds(odds));
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNotNegative() {
        return amount.signum() >= 0;
    }

    /**
     * So sánh tiền: dùng compareTo, KHÔNG dùng equals (khác scale).
     */
    public int compareAmountTo(Money other) {
        return amount.compareTo(other.amount);
    }
}
