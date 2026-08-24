package com.rwg.banner;

import com.rwg.banner.domain.Banner;
import com.rwg.banner.domain.BannerMediaType;
import com.rwg.banner.domain.BannerRepository;
import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.domain.UserStatus;
import com.rwg.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rwg.CoreTestApplication;

@SpringBootTest(classes = CoreTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBannerApiTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BannerRepository bannerRepository;

    /**
     * Byte đầu tệp MP4 hợp lệ: 4 byte độ dài box, rồi "ftyp", rồi brand.
     *
     * PHẢI LÀ BYTE THẬT chứ không phải chuỗi bất kỳ: {@code MediaStorageService} giờ
     * kiểm chữ ký nội dung, nên "dummy mp4 video bytes" bị từ chối — đúng như mong đợi.
     */
    private static final byte[] MP4_HEADER = {
            0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
            0x00, 0x00, 0x00, 0x00
    };

    /** Byte đầu tệp PNG hợp lệ: {@code 89 50 4E 47} = 0x89 + "PNG". */
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x00, 0x00, 0x00, 0x00
    };

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        bannerRepository.deleteAll();

        // Tạo hoặc lấy lại Admin user
        User admin = userRepository.findByUsername("admin-banner-user")
                .orElseGet(() -> {
                    User u = new User(
                            "admin-banner-user",
                            "admin-banner@rwg.com",
                            passwordEncoder.encode("Pass123!@#")
                    );
                    u.setRole(UserRole.ADMIN);
                    u.setStatus(UserStatus.ACTIVE);
                    return userRepository.save(u);
                });

        adminToken = loginAndGetToken("admin-banner-user", "Pass123!@#");
    }

    @AfterEach
    void tearDown() {
        bannerRepository.deleteAll();
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "identifier", username,
                "password", password
        ));
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    @DisplayName("Admin upload video MP4 banner thành công")
    void uploadVideoBannerSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "promo_hero.mp4",
                "video/mp4",
                MP4_HEADER
        );

        mockMvc.perform(multipart("/api/v1/admin/banners/upload")
                        .file(file)
                        .param("title", "Hero Video Casino 2026")
                        .param("linkUrl", "https://rwg.com/promo")
                        .param("sortOrder", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Hero Video Casino 2026"))
                .andExpect(jsonPath("$.mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.mediaUrl").value(org.hamcrest.Matchers.startsWith("/uploads/media/")))
                .andExpect(jsonPath("$.isActive").value(true));

        assertThat(bannerRepository.findAll()).hasSize(1);
        Banner b = bannerRepository.findAll().get(0);
        assertThat(b.getMediaType()).isEqualTo(BannerMediaType.VIDEO);
    }

    @Test
    @DisplayName("Admin upload ảnh PNG banner thành công")
    void uploadImageBannerSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "banner.png",
                "image/png",
                PNG_HEADER
        );

        mockMvc.perform(multipart("/api/v1/admin/banners/upload")
                        .file(file)
                        .param("title", "Ảnh Khuyến Mãi Nạp Đầu")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.title").value("Ảnh Khuyến Mãi Nạp Đầu"));

        assertThat(bannerRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Từ chối file không đúng định dạng (vd .exe)")
    void rejectInvalidFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malware.exe",
                "application/x-msdownload",
                "binary".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/admin/banners/upload")
                        .file(file)
                        .param("title", "Malicious File")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        assertThat(bannerRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Player xem danh sách banner active tại trang chủ")
    void playerGetsActiveBanners() throws Exception {
        Banner b1 = new Banner("Banner 1", BannerMediaType.VIDEO, "/uploads/media/v1.mp4", null, 1);
        Banner b2 = new Banner("Banner 2", BannerMediaType.IMAGE, "/uploads/media/i1.png", null, 2);
        b2.setActive(false); // Inactive
        bannerRepository.save(b1);
        bannerRepository.save(b2);

        // Player gọi API công khai /api/v1/banners/active
        mockMvc.perform(get("/api/v1/banners/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Banner 1"))
                .andExpect(jsonPath("$[0].mediaType").value("VIDEO"));
    }

    @Test
    @DisplayName("Admin cập nhật trạng thái và xoá banner")
    void adminToggleStatusAndDelete() throws Exception {
        Banner banner = new Banner("Banner Test", BannerMediaType.VIDEO, "/uploads/media/test.mp4", null, 0);
        banner = bannerRepository.save(banner);

        // Bật / tắt status
        mockMvc.perform(patch("/api/v1/admin/banners/" + banner.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        // Xoá banner
        mockMvc.perform(delete("/api/v1/admin/banners/" + banner.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(bannerRepository.findById(banner.getId())).isEmpty();
    }

    @Test
    @DisplayName("Từ chối tệp đặt tên .mp4 nhưng nội dung không phải video")
    void rejectFakeVideoContent() throws Exception {
        // ĐÂY LÀ LỖ HỎNG TRƯỚC KHI SỬA: đặt tên tệp thành .mp4 và khai
        // Content-Type video/mp4 là qua được, bất kể nội dung thật là gì. Cả hai thứ
        // đó đều do client khai nên không thể tin.
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.mp4",
                "video/mp4",
                "Đây hoàn toàn là văn bản, không phải video".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/admin/banners/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        assertThat(bannerRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Không truyền title thì tiêu đề lấy từ tên tệp")
    void titleFallsBackToFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hero-casino-2026.mp4",
                "video/mp4",
                MP4_HEADER
        );

        mockMvc.perform(multipart("/api/v1/admin/banners/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                // Tên tệp bỏ đuôi, không phải tên kèm đuôi.
                .andExpect(jsonPath("$.title").value("hero-casino-2026"));
    }

    @Test
    @DisplayName("Từ chối banner thứ 5 khi đã đủ trần 4")
    void rejectUploadBeyondMaxCount() throws Exception {
        // ĐẦY SẴN 4 banner bằng repository thay vì tải lên 4 lần: nhanh hơn và không
        // để lại 4 tệp thật trên đĩa của máy chạy test.
        for (int i = 1; i <= 4; i++) {
            bannerRepository.save(new Banner(
                    "Banner " + i, BannerMediaType.IMAGE, "/uploads/media/seed" + i + ".png", null, i));
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "thu-nam.png", "image/png", PNG_HEADER);

        mockMvc.perform(multipart("/api/v1/admin/banners/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        // VẪN ĐÚNG 4, không thành 5.
        assertThat(bannerRepository.findAll()).hasSize(4);
    }

    @Test
    @DisplayName("Trần đếm CẢ banner đang tắt")
    void maxCountIncludesInactiveBanners() throws Exception {
        // TẮT HẾT 4 banner rồi thử tải thêm: nếu trần chỉ đếm banner ACTIVE thì lần
        // tải này sẽ thành công, và ai cũng có thể tải vô hạn bằng cách tắt hết đi.
        for (int i = 1; i <= 4; i++) {
            Banner b = new Banner(
                    "Banner " + i, BannerMediaType.IMAGE, "/uploads/media/off" + i + ".png", null, i);
            b.setActive(false);
            bannerRepository.save(b);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "them-nua.png", "image/png", PNG_HEADER);

        mockMvc.perform(multipart("/api/v1/admin/banners/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        assertThat(bannerRepository.findAll()).hasSize(4);
    }

    @Test
    @DisplayName("Endpoint /limits trả đúng trần và số đang có")
    void limitsEndpointReturnsCurrentState() throws Exception {
        bannerRepository.save(new Banner(
                "Một banner", BannerMediaType.IMAGE, "/uploads/media/one.png", null, 1));

        mockMvc.perform(get("/api/v1/admin/banners/limits")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxCount").value(4))
                .andExpect(jsonPath("$.currentCount").value(1))
                .andExpect(jsonPath("$.maxImageBytes").value(10L * 1024 * 1024))
                .andExpect(jsonPath("$.maxVideoBytes").value(50L * 1024 * 1024));
    }
}
