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
 *
 * `lastLoginAt` LÀ Instant CHỨ KHÔNG PHẢI String: quy ước "tiền phải là String" chỉ áp cho
 * BigDecimal, nơi Jackson tuần tự hoá thành số thực và làm tròn sai. Thời gian không có vấn
 * đề đó — Instant ra chuỗi ISO-8601 và {@code new Date(...)} phía giao diện đọc trực tiếp.
 *
 * `lastLoginAt` null khi tài khoản CHƯA TỪNG đăng nhập, và giá trị null này có ý nghĩa nên
 * không được thay bằng giá trị giả: "đăng ký rồi bỏ đó" khác hẳn "vừa đăng nhập xong" đối
 * với người đang đánh giá tài khoản.
 *
 * `online` và `lastSeenAt` LÀ THỨ KHÁC HẲN `lastLoginAt`, không phải bản chi tiết hơn của nó.
 * `lastLoginAt` chỉ được ghi MỘT LẦN lúc đăng nhập, nên nó không phân biệt được người đang
 * chơi lúc này với người đăng nhập rồi tắt máy ngay — hai trường hợp đó cho cùng một giá trị.
 * Hai trường mới trả lời câu "ngay lúc này người đó có ở đây không".
 *
 * `lastSeenAt` null nghĩa là KHÔNG RÕ (mốc đã hết hạn, hoặc Redis không sẵn sàng), KHÔNG
 * phải "đã rời đi từ lâu". Phía hiển thị phải lùi về `lastLoginAt` trong trường hợp đó.
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
        Instant lastLoginAt,
        Instant createdAt,
        String balance,
        String currency,
        boolean online,
        Instant lastSeenAt
) {
}
