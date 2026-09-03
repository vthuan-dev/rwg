package com.rwg.presence.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Làm mới mốc hoạt động cho mọi người chơi đang giữ một phiên WebSocket.
 *
 * <h2>VÌ SAO CẦN, KHI ĐÃ CÓ INTERCEPTOR TRÊN REQUEST REST</h2>
 * Một người chơi ngồi trên trang game có thể không gọi REST nào trong hàng chục phút: kết
 * quả từng vòng tới qua WebSocket. Chỉ dựa vào request HTTP thì họ hiện thành đã rời đi
 * trong khi đang ngồi xem.
 *
 * Ngược lại, ai bị chặn WebSocket vẫn duyệt web bình thường và chỉ có interceptor bắt được.
 * Hai nguồn bịt đúng lỗ hổng của nhau.
 *
 * <h2>VÌ SAO QUÉT ĐỊNH KỲ CHỨ KHÔNG BẮT SỰ KIỆN KẾT NỐI/NGẮT KẾT NỐI</h2>
 * Sự kiện ngắt kết nối không đáng tin: mất sóng, gập laptop, hệ điều hành thu hồi tab — đều
 * không sinh frame nào. Nhưng lý do nặng hơn là KHẢ NĂNG TỰ LÀNH: nếu đánh dấu online theo
 * sự kiện, một lần triển khai lại tiến trình này sẽ để mọi người đang được đánh dấu mắc kẹt
 * ở trạng thái online VĨNH VIỄN, vì không còn ai gửi sự kiện ngắt cho họ. Quét định kỳ thì
 * tiến trình chết là không còn ai làm mới, mọi mốc tự cũ đi.
 *
 * <h2>CHỈ CHẠY Ở APP NGƯỜI CHƠI</h2>
 * Bị loại khỏi app quản trị (xem {@code AdminApplication.excludeFilters}). Ở đó
 * {@code SimpUserRegistry} chỉ chứa nhân sự, vì {@code WebSocketProperties.audience} chặn
 * token PLAYER mở phiên trên broker quản trị.
 */
@Component
@EnableScheduling
public class WebSocketPresenceRefresher {

    private static final Logger log = LoggerFactory.getLogger(WebSocketPresenceRefresher.class);

    private final PresenceStore presenceStore;

    /**
     * null khi app không bật WebSocket (context test tối giản).
     *
     * Theo đúng cách {@code GameEventRelay} và {@code ChatEventPublisher} khai báo phụ
     * thuộc hạ tầng có thể vắng mặt: bắt buộc phải có sẽ làm context test không khởi động.
     */
    private final SimpUserRegistry userRegistry;

    public WebSocketPresenceRefresher(PresenceStore presenceStore,
                                      @Autowired(required = false) SimpUserRegistry userRegistry) {
        this.presenceStore = presenceStore;
        this.userRegistry = userRegistry;
    }

    @Scheduled(fixedDelayString = "${rwg.presence.refresh-interval:PT30S}")
    public void refreshConnectedPlayers() {
        if (userRegistry == null) {
            return;
        }

        try {
            userRegistry.getUsers().forEach(user -> {
                // Tên principal là userId dạng chuỗi — xem
                // WsAuthChannelInterceptor.UserIdPrincipal.getName().
                try {
                    presenceStore.touch(UUID.fromString(user.getName()));
                } catch (IllegalArgumentException notAUuid) {
                    // Principal không phải UUID: không thể xảy ra với luồng xác thực hiện
                    // tại. Bỏ qua người này thay vì dừng cả vòng lặp.
                }
            });
        } catch (RuntimeException unexpected) {
            // Việc định kỳ KHÔNG được ném ra ngoài: Spring sẽ ghi stack trace mỗi 30 giây
            // và làm log không đọc được, trong khi đây chỉ là dữ liệu trang trí.
            log.debug("Không làm mới được mốc hoạt động qua WebSocket: {}", unexpected.getMessage());
        }
    }
}
