package com.rwg.identity.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cập nhật hồ sơ cá nhân: họ tên, quốc gia, số điện thoại.
 *
 * CẢ BA TRƯỜNG ĐỀU CHO PHÉP RỖNG, và đây là chủ ý chứ không phải thiếu kiểm tra:
 *
 * 1. Người dùng có thể chỉ muốn sửa một ô. Bắt buộc cả ba nghĩa là muốn đổi số điện
 *    thoại thì phải gõ lại cả họ tên.
 * 2. Gửi ô trống là cách XOÁ giá trị đã lưu — một hành vi hợp lệ. Dùng {@code @NotBlank}
 *    sẽ khiến người dùng không bao giờ xoá được thông tin họ đã khai.
 *
 * Vì vậy mỗi regex đều có nhánh {@code ^$} thay vì dựa vào {@code @NotBlank}.
 *
 * KHÔNG có ô email: form đăng ký của khu người chơi không thu email và cột
 * {@code users.email} có ràng buộc unique, nên cho sửa email tự do ở đây sẽ mở ra
 * đường chiếm email của tài khoản khác. Đổi email cần luồng xác thực riêng.
 */
public record UpdateProfileRequest(

        /**
         * Họ và tên. Không kiểm định dạng ngoài độ dài: tên người có dấu, có dấu nháy
         * (O'Brien), có gạch ngang, và mỗi nền văn hoá một quy ước — mọi biểu thức chính
         * quy "tên hợp lệ" đều sẽ từ chối tên thật của một ai đó.
         */
        @Size(max = 100, message = "{validation.profile.full_name.size}")
        String fullName,

        /**
         * Mã quốc gia ISO 3166-1 alpha-2, chữ IN HOA, ví dụ "VN".
         *
         * Chỉ nhận đúng hai chữ cái in hoa: client gửi tên nước ("Vietnam") hay mã ba
         * chữ ("VNM") đều bị từ chối ngay thay vì lưu vào cột CHAR(2) rồi bị cắt cụt
         * thành "Vi" hoặc "VN" một cách âm thầm.
         */
        @Pattern(regexp = "^$|^[A-Z]{2}$", message = "{validation.profile.country_code.pattern}")
        String countryCode,

        /**
         * Số điện thoại. Cho phép chữ số, dấu cộng đầu, dấu cách và gạch ngang.
         *
         * KHÔNG kiểm theo từng quốc gia: luật số điện thoại đổi liên tục khi nhà mạng mở
         * đầu số mới, và một bảng luật lỗi thời sẽ từ chối số thật của người dùng. Đây là
         * số để liên lạc, không phải khoá định danh.
         */
        @Pattern(regexp = "^$|^[0-9+\\-\\s]{6,20}$", message = "{validation.profile.phone.pattern}")
        String phone
) {
}
