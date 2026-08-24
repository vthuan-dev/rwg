package com.rwg.common;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi chuẩn của toàn hệ thống. Response lỗi luôn có dạng
 * {code, message, details, traceId} (xem {@link GlobalExceptionHandler}).
 */
public enum ErrorCode {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),

    // 400 - wallet / payment (chặng 2 Phase b)
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "Số dư không đủ"),
    WITHDRAWAL_PASSWORD_NOT_SET(HttpStatus.BAD_REQUEST, "Chưa đặt mật khẩu rút tiền"),
    BANK_ACCOUNT_REQUIRED(HttpStatus.BAD_REQUEST, "Cần có tài khoản ngân hàng mặc định"),
    WITHDRAWAL_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Vượt hạn mức rút tiền"),
    /**
     * Sai MẬT KHẨU RÚT TIỀN (mật khẩu cấp hai), KHÔNG phải sai đăng nhập.
     *
     * VÌ SAO 400 CHỨ KHÔNG 401 — ĐÂY TẮNG LÀ MỘT LỖI THẬT ĐÃ GÂY HẬU QUẢ:
     * trước đây chỗ này dùng {@link #INVALID_CREDENTIALS} (401). Frontend hiểu 401 là
     * "phiên hết hạn" — {@code authedRequest} đi xoay vòng token và trang đẩy người
     * dùng sang {@code /login}. Kết quả: gõ sai mật khẩu rút tiền thì BỊ ĐĂNG XUẤT.
     *
     * 401 có nghĩa "tôi không biết bạn là ai" — nói về danh tính của phiên. Nhưng sai
     * mật khẩu cấp hai thì phiên VẪN HỢP LỆ hoàn toàn: token còn tốt, người dùng vẫn
     * đạng đăng nhập. Frontend không sai — nó phản ứng đúng theo nghĩa của 401.
     *
     * CŨNG KHÔNG DÙNG 403: 403 nghĩa "bạn không có quyền làm việc này", nhưng người
     * chơi CÓ quyền, chỉ là gõ sai. 400 "dữ liệu gửi lên không hợp lệ" mới đúng.
     */
    WITHDRAWAL_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "Mật khẩu rút tiền không đúng"),

    // 400 - admin backoffice (chặng 3)
    ADMIN_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Thao tác này bắt buộc có lý do"),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "Chuyển trạng thái không hợp lệ"),
    CANNOT_MODIFY_SELF(HttpStatus.BAD_REQUEST, "Không thể tự thay đổi tài khoản của chính mình"),

    // 400 - siết an toàn vận hành khu quản trị (chặng 5)
    /** Quy trình 4 mắt: người tạo đề nghị KHÔNG được tự phê duyệt. */
    CANNOT_APPROVE_OWN_REQUEST(HttpStatus.BAD_REQUEST, "Không thể tự phê duyệt đề nghị của chính mình"),
    /** Vượt trần số tiền mỗi lần hoặc trần tổng mỗi ngày của một admin. */
    ADMIN_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Vượt hạn mức thao tác của admin"),
    /** Đề nghị đã được duyệt/từ chối trước đó (chống bấm hai lần). */
    APPROVAL_ALREADY_DECIDED(HttpStatus.BAD_REQUEST, "Đề nghị đã được xử lý trước đó"),

    // 400 / 404 - game (chặng 2 Phase c)
    GAME_TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bàn chơi"),
    ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy vòng chơi"),
    ROUND_BETTING_CLOSED(HttpStatus.BAD_REQUEST, "Vòng đã đóng cược"),
    INVALID_BET_SELECTION(HttpStatus.BAD_REQUEST, "Cửa cược không hợp lệ"),

    // 401
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Sai thông tin đăng nhập"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa xác thực hoặc token không hợp lệ"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã hết hạn"),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền truy cập"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "Tài khoản đang bị khóa hoặc chưa kích hoạt"),

    // 404 / 409
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu đã tồn tại"),
    /**
     * Người chơi đã có một tài khoản ngân hàng đang hoạt động.
     *
     * MỖI NGƯỜI CHỈ MỘT TÀI KHOẢN: để đổi thì phải qua CSKH. Đổi được số tài khoản
     * nhận tiền là chuyển được toàn bộ tiền rút sang chỗ khác, nên thao tác này cần có
     * người thật xác nhận thay vì để tự làm trong phiên có thể đã bị chiếm.
     */
    BANK_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT,
            "Đã có tài khoản ngân hàng, liên hệ CSKH để đổi"),
    /** Người chơi KHÔNG được tự gỡ tài khoản nhận tiền — phải qua CSKH. */
    BANK_ACCOUNT_REMOVE_FORBIDDEN(HttpStatus.CONFLICT,
            "Không thể tự gỡ tài khoản, liên hệ CSKH"),

    // 423 / 429
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Tài khoản tạm khóa do đăng nhập sai quá nhiều lần"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu, vui lòng thử lại sau"),
    CAPTCHA_REQUIRED(HttpStatus.TOO_MANY_REQUESTS, "Cần xác thực captcha trước khi tiếp tục"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /**
     * Key i18n trong bundle messages (vd error.INVALID_CREDENTIALS).
     * {@link GlobalExceptionHandler} resolve qua MessageSource theo locale request;
     * không có key thì fallback về {@link #defaultMessage()}.
     */
    public String messageKey() {
        return "error." + name();
    }
}
