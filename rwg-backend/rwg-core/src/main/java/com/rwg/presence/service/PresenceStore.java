package com.rwg.presence.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Nơi lưu mốc hoạt động cuối của từng người chơi.
 *
 * <h2>VÌ SAO LÀ MỘT MỐC THỜI GIAN, KHÔNG PHẢI MỘT CỜ ONLINE</h2>
 * Cờ bật/tắt đòi phải có ai đó báo "tôi đã rời đi". Trên web thì không có tín hiệu đó:
 * điện thoại mất sóng, người dùng gập laptop, tab bị hệ điều hành thu hồi — không frame
 * nào được gửi. Cờ sẽ mắc kẹt ở trạng thái bật.
 *
 * Một mốc thời gian được làm mới liên tục thì không cần ai báo gì: im lặng đủ lâu là tự
 * thành offline. Nó cũng TỰ LÀNH sau khi triển khai lại — tiến trình chết thì không còn
 * ai làm mới, mọi mốc tự cũ đi. Với cờ thì mọi người đang bật sẽ mắc kẹt vĩnh viễn vì
 * không còn ai gửi tín hiệu rời đi cho họ.
 *
 * Cùng một mốc trả lời được cả hai câu hỏi: còn mới thì là "đang online", đã cũ thì
 * chính là "thấy lần cuối lúc nào". Hai khoá riêng cho hai câu đó có thể mâu thuẫn nhau.
 *
 * <h2>KHÔNG BAO GIỜ ĐƯỢC NÉM NGOẠI LỆ</h2>
 * Đây là dữ liệu trang trí. Lỗi ở tầng lưu trữ phải biểu hiện thành "không rõ trạng
 * thái", KHÔNG được làm sập bảng danh sách của khu quản trị hay chặn request của người
 * chơi. Mọi bản hiện thực phải tự nuốt lỗi.
 */
public interface PresenceStore {

    /**
     * Đánh dấu người chơi vừa có hoạt động.
     *
     * Gọi trên đường đi của request thật, nên phải rẻ và không bao giờ chặn lâu.
     */
    void touch(UUID userId);

    /**
     * Mốc hoạt động cuối của một nhóm người chơi.
     *
     * NHẬN CẢ NHÓM chứ không phải từng người: bảng danh sách vẽ 20 dòng một lần, và một
     * lời gọi mạng cho mỗi dòng sẽ thành 20 lượt khứ hồi tới Redis cho một lần xem bảng.
     *
     * Khoá không có mặt thì KHÔNG xuất hiện trong map trả về — người gọi phân biệt được
     * "chưa từng thấy" với "thấy lần cuối lúc nào đó".
     */
    Map<UUID, Instant> lastSeen(Collection<UUID> userIds);
}
