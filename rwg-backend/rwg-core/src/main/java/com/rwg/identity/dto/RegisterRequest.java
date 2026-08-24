package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu đăng ký tài khoản PLAYER.
 *
 * KHÔNG có trường email: form đăng ký của khu người chơi chỉ gồm tên đăng nhập,
 * mật khẩu, nhập lại mật khẩu và mật khẩu rút tiền. Cột users.email vẫn tồn tại
 * (nullable) để phục vụ các tài khoản cũ và ô tìm kiếm ở khu quản trị, nhưng tài
 * khoản tạo qua API này luôn có email NULL.
 */
public record RegisterRequest(
        @NotBlank(message = "{validation.register.username.not_blank}")
        @Size(min = 3, max = 32, message = "{validation.register.username.size}")
        @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "{validation.register.username.pattern}")
        String username,

        @NotBlank(message = "{validation.register.password.not_blank}")
        @Size(min = 8, max = 72, message = "{validation.register.password.size}")
        String password,

        /**
         * Mật khẩu rút tiền — TÙY CHỌN khi đăng ký.
         *
         * Có ở đây để form đăng ký một bước đặt được luôn, thay vì buộc người dùng
         * đăng nhập rồi vào phần cài đặt đặt thêm một lần nữa. Bỏ trống vẫn đăng ký
         * được; khi đó user đặt sau qua POST /users/me/withdrawal-password.
         *
         * ĐÚNG 6 CHỮ SỐ: đây là mã PIN nhập trên bàn phím số ở điện thoại, không
         * phải mật khẩu tự do. Ràng buộc này khớp với thông báo "6 chữ số (0-9)"
         * hiển thị trên form.
         */
        @Pattern(regexp = "^$|^\\d{6}$",
                message = "{validation.register.withdrawal_password.pattern}")
        String withdrawalPassword,

        /**
         * Mã giới thiệu — TÙY CHỌN (null/rỗng đều hợp lệ).
         *
         * Mã sai KHÔNG làm đăng ký thất bại: người dùng không nên bị chặn tạo tài
         * khoản vì gõ sai mã của người khác. Mọi lần bỏ qua đều được ghi audit
         * (xem ReferralService.attachReferral).
         */
        @Size(max = 16, message = "{validation.register.referral_code.size}")
        @Pattern(regexp = "^$|^[A-Za-z0-9]{4,16}$",
                message = "{validation.register.referral_code.pattern}")
        String referralCode
) {
}
