package com.rwg.common.geo;

import com.rwg.config.GeoIpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Tra vị trí địa lý của một địa chỉ IP qua dịch vụ ngoài.
 *
 * <h2>KHÔNG BAO GIỜ NÉM NGOẠI LỆ RA NGOÀI</h2>
 * Mọi lỗi — hết hạn chờ, dịch vụ trả 500, hạn mức cạn, JSON sai dạng — đều trả về
 * {@link GeoLocation#unknown()}. Đây là thông tin phụ trợ hiển thị trên header chat; để
 * nó làm gãy việc gửi tin nhắn của người chơi là đánh đổi sai hoàn toàn. Lỗi được ghi ở
 * mức DEBUG chứ không WARN: dịch vụ miễn phí thỉnh thoảng chặn vì hạn mức là chuyện bình
 * thường, log WARN cho việc đó chỉ làm nhiễu log thật.
 *
 * <h2>DÙNG HttpClient CỦA JDK, KHÔNG THÊM THƯ VIỆN</h2>
 * {@code java.net.http.HttpClient} có sẵn từ Java 11 và đủ cho một lời gọi GET trả JSON.
 * Thêm RestClient của Spring hay OkHttp vào chỉ vì một endpoint là chi phí phụ thuộc
 * không cần thiết.
 *
 * Bean này là {@code @Service} nhưng nằm ở {@code com.rwg.common.geo} chứ không ở
 * {@code ..chat.service..}: tra IP không phải nghiệp vụ của chat, và các phần khác
 * (kiểm soát rủi ro, sổ audit) sẽ dùng lại được mà không phải phụ thuộc vào module chat.
 */
@Service
public class GeoIpLookupService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpLookupService.class);

    private final GeoIpProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeoIpLookupService(GeoIpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                // Hạn chờ KẾT NỐI riêng, ngắn hơn hạn chờ cả request: một máy chủ không
                // phản hồi bắt tay TCP thì chờ thêm cũng không có kết quả.
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Tra một IP.
     *
     * @return luôn khác null. {@link GeoLocation#unknown()} khi không tra được vì bất kỳ lý do gì.
     */
    public GeoLocation lookup(String ip) {
        if (!properties.enabled() || ip == null || ip.isBlank()) {
            return GeoLocation.unknown();
        }

        // IP nội bộ KHÔNG gọi ra ngoài. Ở môi trường phát triển mọi request đến từ
        // 127.0.0.1, và gọi dịch vụ ngoài cho địa chỉ đó vừa chắc chắn không ra gì vừa
        // đốt hạn mức của IP máy chủ.
        if (isNotPublic(ip)) {
            return GeoLocation.unknown();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.endpoint().replace("{ip}", ip)))
                    .timeout(properties.timeout())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.debug("geoip_lookup_non_200 ip={} status={}", ip, response.statusCode());
                return GeoLocation.unknown();
            }

            return parse(response.body());

        } catch (InterruptedException e) {
            // Trả lại cờ ngắt cho luồng: nuốt nó đi sẽ khiến việc tắt máy chủ (shutdown)
            // không dừng được luồng đang chờ.
            Thread.currentThread().interrupt();
            return GeoLocation.unknown();
        } catch (Exception e) {
            log.debug("geoip_lookup_failed ip={} error={}", ip, e.getMessage());
            return GeoLocation.unknown();
        }
    }

    /**
     * Đọc phản hồi của ip-api.com.
     *
     * Dịch vụ này trả HTTP 200 KÈM {@code "status":"fail"} khi không tra được, thay vì
     * một mã lỗi HTTP — nên phải kiểm trường đó, không thể chỉ dựa vào mã trạng thái.
     */
    private GeoLocation parse(String body) {
        JsonNode json = objectMapper.readTree(body);

        if (!"success".equals(text(json, "status"))) {
            return GeoLocation.unknown();
        }

        return new GeoLocation(
                text(json, "countryCode"),
                text(json, "country"),
                text(json, "regionName"),
                text(json, "city"),
                text(json, "isp"));
    }

    /**
     * Đọc một trường chuỗi, coi chuỗi rỗng như không có.
     *
     * Dịch vụ trả {@code ""} cho những trường nó không biết. Để nguyên chuỗi rỗng thì
     * giao diện sẽ vẽ ra dấu phẩy lơ lửng giữa hai phần trống.
     */
    private static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asString().trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * IP thuộc mạng nội bộ / không định tuyến được trên Internet.
     *
     * Gộp cả loopback, link-local và địa chỉ site-local (10.x, 172.16–31.x, 192.168.x)
     * vào một phép kiểm. Dùng {@link InetAddress} thay vì tự so chuỗi tiền tố: cách tự so
     * sẽ bỏ sót IPv6 và các dải ít gặp, và mỗi lần bổ sung lại phải sửa mã.
     */
    private static boolean isNotPublic(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()
                    || address.isMulticastAddress();
        } catch (UnknownHostException e) {
            // Không phân giải được nghĩa là chuỗi không phải một IP hợp lệ — không có gì
            // để tra.
            return true;
        }
    }

    /** Hạn chờ đang cấu hình — để chỗ khác ghi log/kiểm tra mà không đọc lại properties. */
    public Duration timeout() {
        return properties.timeout();
    }
}
