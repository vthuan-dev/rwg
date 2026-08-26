package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình tra vị trí địa lý từ IP (prefix rwg.geoip).
 *
 * <h2>VÌ SAO SUY TỪ IP, KHÔNG XIN QUYỀN VỊ TRÍ CỦA TRÌNH DUYỆT</h2>
 * Trình duyệt bắt buộc hỏi người dùng trước khi cho đọc toạ độ, và phần lớn khách bấm
 * Từ chối. Một tính năng chỉ chạy khi khách đồng ý thì gần như luôn rỗng — đúng lúc
 * nhân sự cần nó nhất. IP thì có sẵn trong mọi request, không cần ai cho phép.
 *
 * Đổi lại, độ chính xác thấp hơn hẳn: IP cho biết tỉnh/thành ở mức nhà mạng, và khách
 * dùng VPN sẽ hiện sai hoàn toàn. Đây là thông tin ĐỊNH HƯỚNG cho người trả lời hỗ trợ,
 * KHÔNG phải căn cứ để chặn giao dịch hay xác minh danh tính.
 *
 * <h2>DỊCH VỤ NGOÀI, KHÔNG PHẢI CƠ SỞ DỮ LIỆU NHÚNG</h2>
 * Cách còn lại là nhúng MaxMind GeoLite2 (~70MB) vào jar và tự cập nhật hàng tháng.
 * Với nhu cầu hiện tại — hiện một dòng chữ trên header chat — chi phí vận hành đó không
 * đáng: phải thêm bước tải cơ sở dữ liệu vào quy trình triển khai, và một cơ sở dữ liệu
 * quên cập nhật sẽ âm thầm trả kết quả sai.
 *
 * Mọi giá trị đều có mặc định: thiếu khối `rwg.geoip` trong application.yml thì tính năng
 * vẫn chạy được với dịch vụ mặc định.
 */
@ConfigurationProperties(prefix = "rwg.geoip")
public record GeoIpProperties(

        /**
         * Bật/tắt việc tra IP.
         *
         * Tắt đi thì luồng chat vẫn lưu IP nhưng KHÔNG gọi ra ngoài, và header chat chỉ
         * hiện IP trơn. Cần cái công tắc này để tắt nhanh khi dịch vụ ngoài sập hoặc khi
         * chạy ở môi trường không có internet ra ngoài, thay vì phải sửa mã.
         */
        boolean enabled,

        /**
         * Địa chỉ dịch vụ tra IP. Dấu {@code {ip}} sẽ được thay bằng IP cần tra.
         *
         * Mặc định dùng ip-api.com: không cần khoá API nên không có bí mật nào phải quản,
         * và trả về đủ quốc gia / tỉnh / thành phố / nhà mạng trong một lần gọi. Hạn mức
         * 45 lần/phút — lý do kết quả được LƯU vào cơ sở dữ liệu thay vì tra lại mỗi lần
         * mở luồng (xem migration V20260826_01).
         *
         * Để trong cấu hình thay vì viết thẳng trong mã: khi hạn mức không còn đủ, việc
         * chuyển sang dịch vụ khác hoặc bản trả phí chỉ là đổi một dòng yaml.
         */
        String endpoint,

        /**
         * Hạn chờ cho MỖI lần gọi dịch vụ ngoài.
         *
         * NGẮN CÓ CHỦ Ý (2 giây). Lời gọi này nằm trên đường người chơi gửi tin nhắn, nên
         * một dịch vụ ngoài phản hồi chậm sẽ làm chậm chính việc gửi tin. Thà bỏ phần vị
         * trí còn hơn để khách chờ.
         */
        Duration timeout,

        /**
         * Khoảng thời gian coi kết quả đã tra là còn dùng được.
         *
         * Cùng một IP thì vị trí không đổi trong ngày, nên tra lại chỉ đốt hạn mức. Chỉ
         * tra lại khi IP ĐỔI, hoặc khi kết quả cũ hơn khoảng này.
         */
        Duration cacheTtl
) {

    public GeoIpProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://ip-api.com/json/{ip}"
                    + "?fields=status,country,countryCode,regionName,city,isp";
        }
        if (timeout == null) timeout = Duration.ofSeconds(2);
        if (cacheTtl == null) cacheTtl = Duration.ofDays(1);
    }
}
