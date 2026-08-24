package com.rwg.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng trong danh sách người dùng của khu quản trị.
 *
 * Vì sao KHÔNG thêm số dư vào {@link UserResponse}: DTO đó dùng chung cho các điểm cuối của
 * chính người chơi ({@code /auth/register}, {@code /users/me}, đổi mật khẩu...). Thêm số dư
 * vào đó buộc mọi lời gọi đó phải tra ví, và làm số dư xuất hiện ở những phản hồi không cần
 * đến nó.
 *
 * `balance` và `currency` là String vì backend dùng BigDecimal — chuyển sang số thực dấu
 * phẩy động sẽ làm tròn sai ở các số lẻ. Jackson tuần tự hoá BigDecimal thành SỐ JSON, nên
 * mọi trường tiền phải là String kèm {@code toPlainString()}.
 *
 * `balance` không bao giờ null: người dùng chưa có ví được trả về "0.00" thay vì null, để
 * phía hiển thị không phải phân biệt "chưa có ví" với "số dư bằng không" — hai thứ đó giống
 * nhau đối với người vận hành.
 */
public record AdminUserListItemResponse(
        UUID id,
        String username,
        String email,
        String role,
        String status,
        String kycLevel,
        boolean hasWithdrawalPassword,
        String locale,
        Instant createdAt,
        String balance,
        String currency
) {
}
