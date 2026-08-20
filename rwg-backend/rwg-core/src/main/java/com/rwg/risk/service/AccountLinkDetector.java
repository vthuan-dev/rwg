package com.rwg.risk.service;

import com.rwg.config.RiskProperties;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.risk.domain.AccountLink;
import com.rwg.risk.domain.AccountLinkType;
import com.rwg.risk.domain.AccountSignal;
import com.rwg.risk.repository.AccountLinkRepository;
import com.rwg.risk.repository.AccountSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ghi dấu vết lúc đăng ký và dò các tài khoản có khả năng cùng một người (chặng 7).
 *
 * ===== VÌ SAO CẦN =====
 * Hệ hoa hồng chặn được tự giới thiệu và vòng lặp A->B->A, nhưng không chặn được
 * cách trục lợi thật: một người tạo A, rồi tự tạo B/C/D đăng ký bằng mã của A, cược
 * bằng tiền của chính mình ở B/C/D rồi rút hoa hồng về A. Không ràng buộc nào bị vi
 * phạm và turnover của B là CƯỢC THẬT, nên job hoa hồng trả tiền bình thường.
 *
 * ===== GỌI ĐỒNG BỘ TRONG register(), KHÔNG PHẢI JOB QUÉT =====
 * register() đã có IP trong tay và tra một fingerprint chỉ là một lần lookup theo
 * index. Job quét định kỳ sẽ phải đọc lại toàn bảng để tìm thứ đã biết ngay lúc
 * đăng ký.
 *
 * ===== LỖI Ở ĐÂY KHÔNG ĐƯỢC LÀM HỎNG ĐĂNG KÝ =====
 * Cùng triết lý đã áp cho attachReferral: người dùng không nên bị chặn tạo tài khoản
 * vì hệ thống dò gian lận gặp sự cố. Mọi ngoại lệ đều bị bắt và chỉ ghi log.
 *
 * ===== GIỚI HẠN CẦN BIẾT =====
 * X-Device-Id do CLIENT gửi nên GIẢ MẠO ĐƯỢC (xoá localStorage là có id mới). Lớp
 * này nâng chi phí của kẻ farm thông thường và tạo hàng đợi cho người thật điều tra,
 * nhưng KHÔNG chống được kẻ có kỹ thuật. Muốn thế cần fingerprint phía client
 * (canvas/WebGL/font) hoặc dịch vụ chống gian lận thương mại.
 *
 * Quy ước bắt buộc: xem DECISIONS.md ở root repository.
 */
@Service
public class AccountLinkDetector {

    private static final Logger log = LoggerFactory.getLogger(AccountLinkDetector.class);

    private final AccountSignalRepository signalRepository;
    private final AccountLinkRepository linkRepository;
    private final AuditTrailService audit;
    private final RiskProperties properties;

    public AccountLinkDetector(AccountSignalRepository signalRepository,
                               AccountLinkRepository linkRepository,
                               AuditTrailService audit,
                               RiskProperties properties) {
        this.signalRepository = signalRepository;
        this.linkRepository = linkRepository;
        this.audit = audit;
        this.properties = properties;
    }

    /**
     * Ghi dấu vết cho user mới và dò liên kết. Gọi TRONG transaction đăng ký.
     *
     * @param deviceId giá trị header X-Device-Id, có thể null (client không bắt buộc gửi)
     * @param userAgent User-Agent, có thể null
     */
    @Transactional
    public void recordAndDetect(UUID userId, String ip, String deviceId, String userAgent) {
        if (!properties.enabled()) {
            return;
        }
        try {
            String fingerprint = sha256(deviceId);
            // registration_ip là NOT NULL. Ghi nhãn tường minh thay vì để insert nổ:
            // "không xác định" vẫn là thông tin có ích cho người điều tra.
            String safeIp = (ip == null || ip.isBlank()) ? "UNKNOWN" : ip.trim();
            signalRepository.saveAndFlush(
                    new AccountSignal(userId, safeIp, fingerprint, sha256(userAgent)));

            if (fingerprint != null) {
                detectSharedDevice(userId, fingerprint);
            }
            detectSharedIp(userId, safeIp);
        } catch (RuntimeException failure) {
            // KHÔNG để lỗi dò gian lận chặn việc tạo tài khoản.
            log.error("Ghi dấu vết/dò liên kết thất bại cho user {} (bỏ qua)", userId, failure);
        }
    }

    /** Trùng dấu vết thiết bị — tín hiệu mạnh, sẽ giữ hoa hồng ngay khi còn SUSPECTED. */
    private void detectSharedDevice(UUID newUserId, String fingerprint) {
        List<AccountSignal> others =
                signalRepository.findOthersByDeviceFingerprint(fingerprint, newUserId);
        for (AccountSignal other : others) {
            createLink(newUserId, other.getUserId(), AccountLinkType.SHARED_DEVICE,
                    Map.of("deviceFingerprint", fingerprint,
                            "matchedAccounts", String.valueOf(others.size())));
        }
    }

    /**
     * Chùm tài khoản trên cùng IP — CHỈ khi vượt ngưỡng và DƯỚI chặn trên.
     *
     * Không tạo liên kết khi mới 2-3 tài khoản trùng IP: đó chính là kịch bản giới
     * thiệu hợp pháp phổ biến nhất (cho bạn xem sàn ngay tại nhà, nó đăng ký trên
     * cùng wifi).
     *
     * Cũng KHÔNG tạo khi chùm quá lớn. Chùm 500 tài khoản trên một IP không phải một
     * người mở 500 tài khoản mà là NAT nhà mạng / wifi văn phòng / quán net — nối
     * chúng lại chỉ sinh hàng nghìn nghi vấn oan, và số dòng tăng theo O(n²) vì mỗi
     * lượt đăng ký nối với TẤT CẢ tài khoản trước đó trong chùm.
     */
    private void detectSharedIp(UUID newUserId, String ip) {
        if (isLoopback(ip)) {
            // Loopback KHÔNG nói gì về người dùng là ai. Quan trọng hơn: nếu reverse
            // proxy nằm cùng máy và thiếu header X-Forwarded-For thì RemoteIpValve
            // không thay được địa chỉ, và MỌI user sẽ mang cùng 127.0.0.1 — nối chúng
            // lại sẽ giữ tiền của toàn bộ đại lý trên sàn.
            return;
        }
        Instant since = Instant.now().minus(properties.ipWindow());
        List<AccountSignal> others = signalRepository.findOthersByIpSince(ip, newUserId, since);

        // +1 cho chính user mới: ngưỡng nói về TỔNG số tài khoản trong chùm.
        int clusterSize = others.size() + 1;
        if (clusterSize <= properties.ipThreshold()) {
            return;
        }
        if (clusterSize > properties.ipClusterCap()) {
            // Vẫn ghi vết để người điều tra biết chùm này tồn tại, chỉ không nối cặp.
            log.info("Chùm IP {} có {} tài khoản, vượt chặn trên {} -> không nối cặp",
                    ip, clusterSize, properties.ipClusterCap());
            return;
        }
        for (AccountSignal other : others) {
            createLink(newUserId, other.getUserId(), AccountLinkType.SHARED_IP,
                    Map.of("registrationIp", ip,
                            "clusterSize", String.valueOf(clusterSize),
                            "windowDays", String.valueOf(properties.ipWindow().toDays())));
        }
    }

    /**
     * IP không dùng được để dò chùm: loopback IPv4/IPv6, hoặc nhãn UNKNOWN.
     *
     * UNKNOWN phải bị loại vì nó KHÔNG phải một địa chỉ thật — nếu coi nó như IP bình
     * thường thì mọi user không xác định được IP sẽ bị nối thành một chùm.
     */
    private boolean isLoopback(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        String trimmed = ip.trim();
        return trimmed.equals("UNKNOWN")
                || trimmed.startsWith("127.")
                || trimmed.equals("::1")
                || trimmed.equals("0:0:0:0:0:0:0:1");
    }

    /**
     * Tạo liên kết nếu chưa có. AccountLink.of tự sắp xếp cặp nên gọi với thứ tự nào
     * cũng ra cùng một dòng; UNIQUE ở DB là chốt cuối.
     */
    private void createLink(UUID first, UUID second, AccountLinkType type,
                            Map<String, String> evidence) {
        AccountLink link = AccountLink.of(first, second, type, toJson(evidence));
        if (linkRepository.findByUserAIdAndUserBId(link.getUserAId(), link.getUserBId())
                .isPresent()) {
            // Đã có liên kết cho cặp này. KHÔNG ghi đè: liên kết cũ có thể đã được
            // người thật xem và kết luận, ghi đè sẽ xoá mất kết luận đó.
            return;
        }
        try {
            linkRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException race) {
            // Hai lượt đăng ký song song cùng tạo một cặp -> UNIQUE chặn, bỏ qua.
            log.debug("Liên kết {} <-> {} đã tồn tại (race)", first, second);
            return;
        }
        audit.record(first, null, AuditTrailService.RISK_ACCOUNT_LINK_DETECTED,
                "ACCOUNT_LINK", link.getId().toString(),
                Map.of("linkType", type.name(),
                        "userA", link.getUserAId().toString(),
                        "userB", link.getUserBId().toString()), null);
    }

    /** SHA-256 hex; null vào thì null ra (client không gửi header là chuyện thường). */
    private String sha256(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 không khả dụng", impossible);
        }
    }

    /** JSON thủ công cho map phẳng chuỗi-chuỗi — tránh phụ thuộc ObjectMapper ở đây. */
    private String toJson(Map<String, String> evidence) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : evidence.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(entry.getKey()).append("\":\"")
                    .append(entry.getValue().replace("\"", "\\\"")).append('"');
            first = false;
        }
        return sb.append('}').toString();
    }
}
