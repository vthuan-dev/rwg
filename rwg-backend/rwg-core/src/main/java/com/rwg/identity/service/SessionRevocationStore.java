package com.rwg.identity.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Mốc thời gian thu hồi phiên của từng người dùng.
 *
 * VÌ SAO CẦN: access token là JWT KHÔNG TRẠNG THÁI — server xác thực bằng chữ ký và hạn
 * dùng, không tra cứu gì. Thu hồi refresh token (xem {@link RefreshTokenStore}) chỉ chặn
 * việc GIA HẠN phiên; access token đã phát vẫn dùng được cho tới khi hết hạn, và
 * `rwg.security.access-token-ttl` là 15 phút.
 *
 * Hệ quả trước khi có lớp này: một tài khoản vừa bị khóa vì nghi gian lận vẫn gọi API bình
 * thường tới 15 phút — đủ để đặt thêm rất nhiều vòng cược.
 *
 * VÌ SAO LƯU MỘC THỜI GIAN, không lưu danh sách token đã thu hồi: danh sách phình theo số
 * phiên và phải tự dọn, còn một mốc thì cố định ĐÚNG MỘT khoá cho mỗi người dù họ có bao
 * nhiêu thiết bị. Mọi token phát TRƯỚC mốc đều bị từ chối, nên không cần biết chúng là
 * những token nào.
 *
 * TTL của bản ghi đặt bằng đúng `access-token-ttl`: sau khoảng đó mọi token phát trước mốc
 * đều tự hết hạn theo trường `exp` của chính nó, nên mốc không còn tác dụng và tự biến mất.
 * Không có TTL thì đây là một tập chỉ lớn lên mãi.
 */
public interface SessionRevocationStore {

    /**
     * Ghi nhận: mọi token của người này phát TRƯỚC thời điểm hiện tại đều không còn hiệu lực.
     *
     * Gọi khi tài khoản rời trạng thái ACTIVE, hoặc khi đổi quyền — hai trường hợp mà nội
     * dung claim trong token cũ đã lệch với cơ sở dữ liệu.
     */
    void revokeBefore(UUID userId);

    /**
     * Mốc thu hồi của một người, hoặc {@code null} nếu không có.
     *
     * Trả {@code null} chứ không {@code Instant.EPOCH}: "không có mốc" và "có mốc từ năm
     * 1970" đọc ra giống nhau nhưng ý nghĩa khác nhau, và chỗ gọi cần phân biệt để bỏ qua
     * hẳn phép so sánh trong trường hợp thường gặp.
     */
    Instant revokedBefore(UUID userId);
}
