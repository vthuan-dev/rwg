package com.rwg.chat.service;

import com.rwg.chat.domain.ChatConversation;
import com.rwg.chat.repository.ChatConversationRepository;
import com.rwg.common.geo.GeoIpLookupService;
import com.rwg.common.geo.GeoLocation;
import com.rwg.config.GeoIpProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Ghi nhận IP của người chơi vào luồng chat và tra vị trí địa lý tương ứng.
 *
 * <h2>VÌ SAO LÀ MỘT BEAN RIÊNG, KHÔNG NHÉT VÀO {@link ChatService}</h2>
 * Việc này gọi HTTP ra ngoài. Mọi phương thức nghiệp vụ của {@code ChatService} đều
 * {@code @Transactional}, nên đặt lời gọi đó vào trong sẽ GIỮ MỘT CONNECTION của pool
 * trong suốt thời gian chờ mạng — tới 2 giây mỗi lần. Với pool 20 connection thì vài chục
 * người chơi gửi tin cùng lúc là đủ làm nghẽn toàn bộ phần còn lại của hệ thống, y hệt
 * vấn đề đã tránh ở {@code ChatService.uploadAttachment}.
 *
 * Bean này CỐ TÌNH KHÔNG có {@code @Transactional}: mỗi lời gọi repository tự chạy trong
 * giao dịch riêng của nó, nên lời gọi HTTP nằm giữa hai lần đó KHÔNG giữ connection nào.
 *
 * Vì vậy tầng api gọi bean này SAU khi phương thức nghiệp vụ đã trả về — xem
 * {@code ChatController}.
 *
 * <h2>KHÔNG BAO GIỜ LÀM GÃY LUỒNG GỌI</h2>
 * Vị trí địa lý là thông tin phụ trợ cho người trả lời hỗ trợ. Một lỗi ở đây không được
 * làm người chơi gửi tin thất bại, nên mọi ngoại lệ bị chặn lại tại chỗ.
 * {@link GeoIpLookupService} đã tự nuốt lỗi mạng; phần {@code try/catch} ở đây lo những
 * lỗi còn lại (mất kết nối cơ sở dữ liệu, tranh chấp ghi đồng thời).
 */
@Service
public class ChatGeoService {

    private final ChatConversationRepository conversationRepository;
    private final GeoIpLookupService lookupService;
    private final GeoIpProperties properties;

    public ChatGeoService(ChatConversationRepository conversationRepository,
                          GeoIpLookupService lookupService,
                          GeoIpProperties properties) {
        this.conversationRepository = conversationRepository;
        this.lookupService = lookupService;
        this.properties = properties;
    }

    /**
     * Cập nhật IP và (nếu cần) vị trí địa lý cho luồng của một người chơi.
     *
     * KHÔNG tạo luồng nếu chưa có: hàm này chỉ bổ sung thông tin cho luồng đã tồn tại.
     * Người chơi chưa từng mở khung chat thì cũng chẳng có gì để hiện ở khu quản trị.
     */
    public void track(UUID userId, String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }

        try {
            ChatConversation conversation = conversationRepository.findByUserId(userId).orElse(null);
            if (conversation == null) {
                return;
            }

            Instant now = Instant.now();
            boolean needLookup = conversation.recordIp(ip, now, properties.cacheTtl());

            if (!needLookup) {
                // IP không đổi và kết quả cũ vẫn còn hạn: chỉ lưu lại IP, KHÔNG gọi ra
                // ngoài. Đây là nhánh chạy ở gần như mọi tin nhắn — người chơi giữ nguyên
                // một IP suốt cả đoạn hội thoại.
                conversationRepository.save(conversation);
                return;
            }

            // Lời gọi mạng nằm NGOÀI mọi giao dịch — xem chú thích ở đầu lớp.
            GeoLocation geo = lookupService.lookup(ip);

            conversation.applyGeo(ip, geo.countryCode(), geo.countryName(),
                    geo.region(), geo.city(), geo.isp(), now);
            conversationRepository.save(conversation);

        } catch (RuntimeException e) {
            // Nuốt có chủ ý. Người chơi vừa gửi tin thành công; để một lỗi ở bước phụ trợ
            // biến thành lỗi 500 sẽ khiến giao diện báo gửi thất bại cho một tin ĐÃ được lưu.
        }
    }
}
