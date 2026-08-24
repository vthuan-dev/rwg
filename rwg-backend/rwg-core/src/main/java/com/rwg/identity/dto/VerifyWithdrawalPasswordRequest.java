package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Kiểm mật khẩu rút tiền mà KHÔNG tạo lệnh rút.
 *
 * Dùng cho trang rút tiền: giao diện kiểm ngầm trong lúc người chơi gõ để chỉ bật nút gửi
 * lệnh khi mật khẩu đã đúng, thay vì để họ bấm gửi rồi mới biết sai — mỗi lần sai như vậy
 * tiêu một lượt trong bộ đếm chống dò và quá ngưỡng là bị khóa 15 phút.
 *
 * KHÔNG có ràng buộc {@code @Size}: đây là thao tác kiểm, mật khẩu người dùng gõ vào có thể
 * dài bất kỳ. Áp giới hạn độ dài ở đây sẽ trả lỗi 400 khác hẳn 401, biến endpoint thành
 * công cụ dò ĐỘ DÀI mật khẩu thật.
 */
public record VerifyWithdrawalPasswordRequest(
        @NotBlank(message = "{validation.withdrawal.withdrawal_password.not_blank}")
        String withdrawalPassword
) {
}
