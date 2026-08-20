package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình phát hiện đa tài khoản (prefix rwg.risk).
 *
 * @param detectionEnabled tắt được để gỡ lỗi/chạy migrate dữ liệu. Mặc định BẬT.
 * @param sameIpAccountThreshold số tài khoản trên cùng IP trong cửa sổ thì mới coi
 *        là đáng nghi. Mặc định 3: gia đình 2-4 người mở tài khoản rải rác vài tháng
 *        sẽ không vào hàng đợi, còn kẻ mở 20 tài khoản một buổi thì bị.
 * @param sameIpWindow cửa sổ thời gian đếm theo IP. Mặc định 7 ngày. PHẢI có cửa sổ:
 *        nếu đếm toàn bộ lịch sử thì một IP quán net dùng suốt hai năm sẽ tích đủ
 *        tài khoản để mọi người đăng ký sau đó đều bị nghi oan.
 * @param sameIpClusterCap CHẶN TRÊN của chùm IP. Mặc định 20.
 *        <p>Hai lý do, cả hai đều quan trọng:
 *        <ul>
 *        <li><b>Tín hiệu vô giá trị:</b> chùm 500 tài khoản trên một IP không phải một
 *        người mở 500 tài khoản, mà là NAT nhà mạng / wifi văn phòng / quán net. Nối
 *        chúng lại chỉ tạo hàng nghìn nghi vấn oan rồi giữ tiền của người thật.</li>
 *        <li><b>Số dòng bùng nổ:</b> mỗi lượt đăng ký nối với TẤT CẢ tài khoản trước
 *        đó trong chùm, nên số dòng tăng theo O(n²). Một IP có 1000 lượt đăng ký trong
 *        cửa sổ sẽ sinh gần 500 nghìn dòng.</li>
 *        </ul>
 *        Vượt chặn trên thì KHÔNG nối cặp nào; vẫn ghi audit để còn dấu vết điều tra.
 */
@ConfigurationProperties(prefix = "rwg.risk")
public record RiskProperties(
        Boolean detectionEnabled,
        Integer sameIpAccountThreshold,
        Duration sameIpWindow,
        Integer sameIpClusterCap
) {

    /** Mặc định an toàn khi thiếu cấu hình — không để null làm sập lúc dò. */
    public boolean enabled() {
        return detectionEnabled == null || detectionEnabled;
    }

    public int ipThreshold() {
        return sameIpAccountThreshold == null ? 3 : sameIpAccountThreshold;
    }

    public Duration ipWindow() {
        return sameIpWindow == null ? Duration.ofDays(7) : sameIpWindow;
    }

    public int ipClusterCap() {
        return sameIpClusterCap == null ? 20 : sameIpClusterCap;
    }
}
