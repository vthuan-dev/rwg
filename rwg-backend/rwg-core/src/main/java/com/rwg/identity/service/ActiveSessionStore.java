package com.rwg.identity.service;

import java.time.Duration;
import java.util.UUID;

/**
 * Phiên ĐANG HIỆU LỰC của mỗi người dùng — nền tảng cho quy tắc "một tài khoản, một phiên".
 *
 * VÌ SAO KHÔNG DÙNG {@link SessionRevocationStore} CHO VIỆC NÀY: store đó lưu một MỐC THỜI
 * GIAN với nghĩa "mọi token phát trước lúc này đều vô hiệu", và
 * {@code RevokedSessionValidator} còn cộng thêm biên lệch đồng hồ về phía TỪ CHỐI. Gọi nó
 * lúc đăng nhập sẽ vô hiệu hoá luôn token vừa phát cho thiết bị MỚI — chính người vừa đăng
 * nhập bị chặn. Ép một phiên cần phân biệt PHIÊN NÀO, không phải THỜI ĐIỂM NÀO.
 *
 * Định danh phiên dùng luôn {@code familyId} của chuỗi refresh rotation. Family đã có đúng
 * vòng đời cần thiết: sinh ra một lần mỗi lần đăng nhập, giữ nguyên qua mọi lần gia hạn.
 * Thêm một định danh thứ hai song song với nó là tạo hai khái niệm cho cùng một thứ, và
 * chúng sẽ lệch nhau ở đâu đó.
 */
public interface ActiveSessionStore {

    /**
     * Ghi nhận {@code sessionId} là phiên duy nhất còn hiệu lực của người này.
     *
     * Gọi khi đăng nhập (chốt phiên mới, đẩy phiên cũ ra) và ở mỗi lần gia hạn (giữ cho bản
     * ghi không hết hạn trước phiên mà nó đang bảo vệ).
     */
    void claim(UUID userId, String sessionId, Duration ttl);

    /**
     * Phiên hiện hành, hoặc {@code null} nếu chưa từng chốt / bản ghi đã hết hạn.
     *
     * {@code null} PHẢI được hiểu là CHO QUA, không phải chặn. Hai lý do:
     *
     * 1. Mọi access token đang lưu hành trước khi tính năng này ra đời đều không mang claim
     *    phiên. Nếu vắng dữ liệu mà chặn thì đúng giây triển khai là toàn bộ người đang chơi
     *    bị đăng xuất.
     * 2. Nhân sự quản trị và phiên hỗ trợ khách quên mật khẩu CỐ TÌNH không chốt phiên, nên
     *    khoá của họ không bao giờ tồn tại.
     */
    String current(UUID userId);
}
