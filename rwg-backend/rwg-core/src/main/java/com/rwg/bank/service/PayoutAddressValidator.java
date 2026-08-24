package com.rwg.bank.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Kiểm tra và chuẩn hoá số tài khoản ngân hàng.
 *
 * Chỉ hỗ trợ BANK.
 */
@Component
public class PayoutAddressValidator {

    /** Số tài khoản ngân hàng: 6-32 chữ số. */
    private static final Pattern BANK_ACCOUNT = Pattern.compile("^\\d{6,32}$");

    public record Normalized(
            String address,
            String maskedLast4
    ) {
    }

    /**
     * Kiểm tra và chuẩn hoá. Ném {@link ApiException} nếu không hợp lệ.
     */
    public Normalized validate(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            throw invalid("accountNumber", "validation.bank.account_number.not_blank");
        }
        String address = rawAddress.trim();
        if (!BANK_ACCOUNT.matcher(address).matches()) {
            throw invalid("accountNumber", "validation.bank.account_number.invalid");
        }
        return new Normalized(address, last4(address));
    }

    private String last4(String value) {
        return value.substring(value.length() - 4);
    }

    private ApiException invalid(String field, String messageKey) {
        return new ApiException(ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                Map.of("field", field), messageKey);
    }
}
