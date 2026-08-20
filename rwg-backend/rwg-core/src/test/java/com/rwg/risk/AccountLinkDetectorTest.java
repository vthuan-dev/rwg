package com.rwg.risk;

import com.rwg.identity.repository.UserRepository;
import com.rwg.risk.domain.AccountLink;
import com.rwg.risk.domain.AccountLinkStatus;
import com.rwg.risk.domain.AccountLinkType;
import com.rwg.risk.repository.AccountLinkRepository;
import com.rwg.risk.repository.AccountSignalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dò liên kết tài khoản lúc đăng ký (chặng 7).
 *
 * Nhóm test QUAN TRỌNG NHẤT ở đây là "không dò oan": trùng IP dưới ngưỡng phải
 * KHÔNG sinh liên kết, vì kịch bản giới thiệu hợp pháp phổ biến nhất chính là hai
 * người cùng wifi. Nếu dò oan thì hệ thống sẽ giữ tiền của đại lý thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountLinkDetectorTest {

    private static final String PASSWORD = "MatKhau@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountLinkRepository linkRepository;

    @Autowired
    AccountSignalRepository signalRepository;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Đăng ký với IP và device tùy ý. MockMvc cho phép đặt remoteAddr trực tiếp. */
    private UUID register(String username, String ip, String deviceId) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","email":"%s@example.com","password":"%s"}
                        """.formatted(username, username, PASSWORD));
        if (ip != null) {
            builder = builder.with(request -> {
                request.setRemoteAddr(ip);
                return request;
            });
        }
        if (deviceId != null) {
            builder = builder.header("X-Device-Id", deviceId);
        }
        mockMvc.perform(builder).andExpect(status().isCreated());
        return userRepository.findByUsername(username).orElseThrow().getId();
    }

    private List<AccountLink> linksOf(UUID userId) {
        return linkRepository.findAllForUser(userId);
    }

    /** IP riêng cho mỗi test để không nhiễm chùm IP của test khác. */
    private String freshIp() {
        int a = 10 + (int) (Math.random() * 200);
        int b = 1 + (int) (Math.random() * 250);
        int c = 1 + (int) (Math.random() * 250);
        return "203." + a + "." + b + "." + c;
    }

    // ===== Trùng thiết bị =====

    @Test
    @DisplayName("Hai tài khoản cùng X-Device-Id -> sinh liên kết SHARED_DEVICE")
    void sameDeviceCreatesLink() throws Exception {
        String device = "device-" + UUID.randomUUID();
        UUID first = register(unique("dev"), freshIp(), device);
        UUID second = register(unique("dev"), freshIp(), device);

        List<AccountLink> links = linksOf(first);
        assertThat(links).hasSize(1);
        AccountLink link = links.get(0);
        assertThat(link.getLinkType()).isEqualTo(AccountLinkType.SHARED_DEVICE);
        assertThat(link.getStatus()).isEqualTo(AccountLinkStatus.SUSPECTED);
        assertThat(link.otherThan(first)).isEqualTo(second);
    }

    @Test
    @DisplayName("Cặp lưu ĐÃ SẮP XẾP: đăng ký thứ tự nào cũng ra đúng một dòng")
    void pairIsStoredOrdered() throws Exception {
        String device = "device-" + UUID.randomUUID();
        UUID first = register(unique("ord"), freshIp(), device);
        UUID second = register(unique("ord"), freshIp(), device);

        List<AccountLink> links = linksOf(first);
        assertThat(links).hasSize(1);
        AccountLink link = links.get(0);
        // Nếu không sắp xếp thì (A,B) và (B,A) là hai dòng khác nhau -> UNIQUE vô hiệu.
        assertThat(link.getUserAId().toString())
                .isLessThan(link.getUserBId().toString());
        assertThat(linksOf(second)).hasSize(1);
    }

    @Test
    @DisplayName("Tài khoản thứ ba cùng thiết bị -> liên kết với CẢ HAI tài khoản trước")
    void thirdAccountLinksToBothPrevious() throws Exception {
        String device = "device-" + UUID.randomUUID();
        register(unique("three"), freshIp(), device);
        register(unique("three"), freshIp(), device);
        UUID third = register(unique("three"), freshIp(), device);

        // Chỉ nối với người đầu tiên thì chùm bị chia mảnh và job hoa hồng sẽ bỏ sót.
        assertThat(linksOf(third)).hasSize(2);
    }

    @Test
    @DisplayName("Client KHÔNG gửi X-Device-Id -> vẫn đăng ký được, chỉ thiếu tín hiệu")
    void missingDeviceHeaderIsAccepted() throws Exception {
        UUID first = register(unique("nodev"), freshIp(), null);
        UUID second = register(unique("nodev"), freshIp(), null);

        // Nếu coi "cùng null" là trùng thiết bị thì MỌI client cũ sẽ bị nối vào nhau.
        assertThat(linksOf(first)).isEmpty();
        assertThat(linksOf(second)).isEmpty();
        assertThat(signalRepository.findById(first).orElseThrow().getDeviceFingerprint()).isNull();
    }

    @Test
    @DisplayName("Dấu vết thiết bị được HASH, không lưu thô")
    void deviceIdIsHashed() throws Exception {
        String device = "device-plaintext-" + UUID.randomUUID();
        UUID userId = register(unique("hash"), freshIp(), device);

        String stored = signalRepository.findById(userId).orElseThrow().getDeviceFingerprint();
        assertThat(stored).isNotNull().isNotEqualTo(device).hasSize(64);
    }

    // ===== Chùm IP — nhóm chống dò oan =====

    @Test
    @DisplayName("Hai tài khoản cùng IP (DƯỚI ngưỡng) -> KHÔNG sinh liên kết")
    void sameIpUnderThresholdCreatesNoLink() throws Exception {
        String ip = freshIp();
        UUID first = register(unique("fam"), ip, null);
        UUID second = register(unique("fam"), ip, null);

        // Đây chính là kịch bản giới thiệu hợp pháp phổ biến nhất: cho bạn xem sàn
        // ngay tại nhà, nó đăng ký trên cùng wifi. Dò oan ở đây = giữ tiền oan.
        assertThat(linksOf(first)).isEmpty();
        assertThat(linksOf(second)).isEmpty();
    }

    @Test
    @DisplayName("Ba tài khoản cùng IP (ĐÚNG ngưỡng) -> vẫn KHÔNG sinh liên kết")
    void sameIpAtThresholdCreatesNoLink() throws Exception {
        String ip = freshIp();
        UUID first = register(unique("fam3"), ip, null);
        register(unique("fam3"), ip, null);
        register(unique("fam3"), ip, null);

        // Ngưỡng mặc định là 3: gia đình 3 người vẫn phải sạch.
        assertThat(linksOf(first)).isEmpty();
    }

    @Test
    @DisplayName("Bốn tài khoản cùng IP (VƯỢT ngưỡng) -> sinh liên kết SHARED_IP")
    void sameIpOverThresholdCreatesLink() throws Exception {
        String ip = freshIp();
        register(unique("farm"), ip, null);
        register(unique("farm"), ip, null);
        register(unique("farm"), ip, null);
        UUID fourth = register(unique("farm"), ip, null);

        List<AccountLink> links = linksOf(fourth);
        assertThat(links).isNotEmpty();
        assertThat(links).allMatch(l -> l.getLinkType() == AccountLinkType.SHARED_IP);
    }

    @Test
    @DisplayName("Liên kết SHARED_IP + SUSPECTED KHÔNG giữ hoa hồng (tín hiệu yếu)")
    void sharedIpSuspectedDoesNotBlockCommission() throws Exception {
        String ip = freshIp();
        register(unique("weak"), ip, null);
        register(unique("weak"), ip, null);
        register(unique("weak"), ip, null);
        UUID fourth = register(unique("weak"), ip, null);

        assertThat(linksOf(fourth)).isNotEmpty();
        assertThat(linksOf(fourth)).noneMatch(AccountLink::blocksCommission);
    }

    @Test
    @DisplayName("IP loopback KHÔNG bao giờ sinh chùm, dù bao nhiêu tài khoản")
    void loopbackNeverClusters() throws Exception {
        // Nếu reverse proxy cùng máy mà thiếu X-Forwarded-For thì MỌI user sẽ mang
        // 127.0.0.1 — nối chúng lại sẽ giữ tiền của toàn bộ đại lý trên sàn.
        register(unique("loop"), "127.0.0.1", null);
        register(unique("loop"), "127.0.0.1", null);
        register(unique("loop"), "127.0.0.1", null);
        register(unique("loop"), "127.0.0.1", null);
        UUID fifth = register(unique("loop"), "127.0.0.1", null);

        assertThat(linksOf(fifth)).isEmpty();
    }

    @Test
    @DisplayName("Liên kết SHARED_DEVICE + SUSPECTED thì GIỮ hoa hồng (tín hiệu mạnh)")
    void sharedDeviceSuspectedBlocksCommission() throws Exception {
        String device = "device-" + UUID.randomUUID();
        register(unique("strong"), freshIp(), device);
        UUID second = register(unique("strong"), freshIp(), device);

        assertThat(linksOf(second)).allMatch(AccountLink::blocksCommission);
    }

    // ===== Bất biến của domain =====

    @Test
    @DisplayName("Tự liên kết chính mình bị chặn ngay trong domain")
    void selfLinkRejectedInDomain() {
        UUID id = UUID.randomUUID();
        assertThat(catchThrowable(() ->
                AccountLink.of(id, id, AccountLinkType.MANUAL, "{}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Không thể đưa liên kết về lại SUSPECTED")
    void cannotRevertToSuspected() {
        AccountLink link = AccountLink.of(UUID.randomUUID(), UUID.randomUUID(),
                AccountLinkType.SHARED_DEVICE, "{}");
        assertThat(catchThrowable(() ->
                link.review(AccountLinkStatus.SUSPECTED, UUID.randomUUID(), "thu revert")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CLEARED thì thôi giữ hoa hồng")
    void clearedStopsBlocking() {
        AccountLink link = AccountLink.of(UUID.randomUUID(), UUID.randomUUID(),
                AccountLinkType.SHARED_DEVICE, "{}");
        assertThat(link.blocksCommission()).isTrue();

        link.review(AccountLinkStatus.CLEARED, UUID.randomUUID(), "hai nguoi khac nhau that");
        assertThat(link.blocksCommission()).isFalse();
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable caught) {
            return caught;
        }
    }
}
