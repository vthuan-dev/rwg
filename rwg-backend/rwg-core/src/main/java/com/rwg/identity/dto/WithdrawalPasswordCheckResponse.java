package com.rwg.identity.dto;

/**
 * Kết quả kiểm mật khẩu rút tiền.
 *
 * Trả 200 kèm {@code valid=false} khi mật khẩu SAI, không trả 401: giao diện cần phân biệt
 * "mật khẩu sai" (chỉ tắt đèn nút gửi lệnh) với "lỗi mạng / hết lượt thử" (phải hiện cảnh
 * báo khác hẳn). Nếu sai cũng trả 401 thì lớp gọi API chung sẽ hiểu là phiên hết hạn và
 * đẩy người chơi về trang đăng nhập ngay giữa lúc họ đang gõ.
 *
 * @param valid mật khẩu có khớp hash đã đặt hay không
 * @param attemptsRemaining số lần còn được thử trước khi bị khóa tạm — để giao diện cảnh
 *                          báo TRƯỚC khi khóa xảy ra, không phải sau
 */
public record WithdrawalPasswordCheckResponse(
        boolean valid,
        long attemptsRemaining
) {
}
