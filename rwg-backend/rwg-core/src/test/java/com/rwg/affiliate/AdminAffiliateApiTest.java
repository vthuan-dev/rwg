package com.rwg.affiliate;

import com.rwg.affiliate.repository.CommissionSettingsRepository;
import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng API quản trị đại lý + dashboard (Phase 2) qua HTTP.
 *
 * Trọng tâm: phân quyền (player KHÔNG được xem/sửa), audit khi đổi % hoa hồng, và
 * chặn chạy job cho ngày chưa kết thúc (nếu cho phép, ngày đó bị chốt sớm với
 * turnover thiếu và KHÔNG sửa được vì UNIQUE đã khóa).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAffiliateApiTest {

    private static final String PASSWORD = "MatKhau@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    CommissionSettingsRepository settingsRepository;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s"}
                                """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated());
        return login(username);
    }

    private JsonNode login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Phong ADMIN rồi login LẠI: token cũ phát hành trước khi có role không mang claim ROLE_ADMIN. */
    private String adminBearer() throws Exception {
        String username = unique("admaff");
        registerAndLogin(username);
        User admin = userRepository.findByUsername(username).orElseThrow();
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        return "Bearer " + login(username).get("accessToken").asText();
    }

    private String playerBearer() throws Exception {
        return "Bearer " + registerAndLogin(unique("plaff")).get("accessToken").asText();
    }

    private String yesterday() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
    }

    // ===== Phân quyền =====

    @Test
    @DisplayName("Chưa đăng nhập -> 401 trên toàn bộ khu đại lý và dashboard")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/affiliate/config"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/affiliate/commissions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Player -> 403, KHÔNG xem được và KHÔNG đổi được % hoa hồng")
    void playerIsForbidden() throws Exception {
        String player = playerBearer();

        mockMvc.perform(get("/api/v1/admin/affiliate/config").header("Authorization", player))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/dashboard/summary").header("Authorization", player))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/affiliate/config")
                        .header("Authorization", player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level1Rate":"0.5","level2Rate":"0.5"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/affiliate/commissions/run")
                        .header("Authorization", player)
                        .param("periodDate", yesterday()))
                .andExpect(status().isForbidden());
    }

    // ===== Cấu hình % hoa hồng =====

    @Test
    @DisplayName("Admin xem được % hoa hồng mặc định từ migration")
    void adminReadsDefaultRates() throws Exception {
        mockMvc.perform(get("/api/v1/admin/affiliate/config")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level1Rate").exists())
                .andExpect(jsonPath("$.level2Rate").exists());
    }

    @Test
    @DisplayName("Đổi % hoa hồng thành công và ĐƯỢC GHI AUDIT kèm giá trị cũ/mới")
    void updatingRatesIsAudited() throws Exception {
        String admin = adminBearer();
        long before = auditLogRepository.countByAction(
                AuditTrailService.ADMIN_COMMISSION_RATE_CHANGED);

        mockMvc.perform(patch("/api/v1/admin/affiliate/config")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level1Rate":"0.012500","level2Rate":"0.003000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level1Rate").value("0.012500"))
                .andExpect(jsonPath("$.level2Rate").value("0.003000"));

        assertThat(auditLogRepository.countByAction(
                AuditTrailService.ADMIN_COMMISSION_RATE_CHANGED)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("% hoa hồng ngoài khoảng 0..1 hoặc sai định dạng bị chặn")
    void invalidRatesAreRejected() throws Exception {
        String admin = adminBearer();

        // > 1
        mockMvc.perform(patch("/api/v1/admin/affiliate/config")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level1Rate":"1.5","level2Rate":"0.002"}
                                """))
                .andExpect(status().isBadRequest());
        // âm
        mockMvc.perform(patch("/api/v1/admin/affiliate/config")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level1Rate":"-0.01","level2Rate":"0.002"}
                                """))
                .andExpect(status().isBadRequest());
        // không phải số
        mockMvc.perform(patch("/api/v1/admin/affiliate/config")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level1Rate":"abc","level2Rate":"0.002"}
                                """))
                .andExpect(status().isBadRequest());
        // quá nhiều chữ số thập phân so với DECIMAL(9,6)
        mockMvc.perform(patch("/api/v1/admin/affiliate/config")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level1Rate":"0.0000001","level2Rate":"0.002"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ===== Chạy job thủ công =====

    @Test
    @DisplayName("Chặn chạy job cho HÔM NAY và ngày TƯƠNG LAI (ngày chưa kết thúc)")
    void cannotRunForUnfinishedDay() throws Exception {
        String admin = adminBearer();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(post("/api/v1/admin/affiliate/commissions/run")
                        .header("Authorization", admin)
                        .param("periodDate", today.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/affiliate/commissions/run")
                        .header("Authorization", admin)
                        .param("periodDate", today.plusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Ngày sai định dạng bị chặn")
    void invalidDateIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/affiliate/commissions/run")
                        .header("Authorization", adminBearer())
                        .param("periodDate", "20-08-2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Chạy job tay cho ngày đã kết thúc trả về tổng kết, an toàn bấm nhiều lần")
    void manualRunIsIdempotent() throws Exception {
        String admin = adminBearer();

        mockMvc.perform(post("/api/v1/admin/affiliate/commissions/run")
                        .header("Authorization", admin)
                        .param("periodDate", yesterday()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDate").value(yesterday()))
                .andExpect(jsonPath("$.runsCreated").exists());

        // Bấm lần hai KHÔNG lỗi (idempotent).
        mockMvc.perform(post("/api/v1/admin/affiliate/commissions/run")
                        .header("Authorization", admin)
                        .param("periodDate", yesterday()))
                .andExpect(status().isOk());
    }

    // ===== Truy vấn =====

    @Test
    @DisplayName("Lịch sử hoa hồng trả cấu trúc phân trang, chặn size quá lớn")
    void commissionHistoryIsPaged() throws Exception {
        mockMvc.perform(get("/api/v1/admin/affiliate/commissions")
                        .header("Authorization", adminBearer())
                        .param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("Cấp tuyến dưới ngoài 1..2 bị chặn")
    void invalidDownlineLevelIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/affiliate/users/{id}/downline", UUID.randomUUID())
                        .header("Authorization", adminBearer())
                        .param("level", "3"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Khoảng ngày ngược (from sau to) bị chặn")
    void reversedRangeIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/affiliate/commissions")
                        .header("Authorization", adminBearer())
                        .param("from", "2026-08-20")
                        .param("to", "2026-08-01"))
                .andExpect(status().isBadRequest());
    }

    // ===== Dashboard =====

    @Test
    @DisplayName("Dashboard trả đủ số liệu tổng hợp")
    void dashboardReturnsSummary() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeposits").exists())
                .andExpect(jsonPath("$.totalWithdrawals").exists())
                .andExpect(jsonPath("$.totalTurnover").exists())
                .andExpect(jsonPath("$.totalCommissionPaid").exists())
                .andExpect(jsonPath("$.newUsers").exists())
                .andExpect(jsonPath("$.pendingWithdrawals").exists());
    }

    @Test
    @DisplayName("Dashboard chặn khoảng ngày quá rộng (bảo vệ DB)")
    void dashboardRejectsTooWideRange() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", adminBearer())
                        .param("from", "2000-01-01")
                        .param("to", "2026-12-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Dashboard chặn khoảng ngày ngược và ngày sai định dạng")
    void dashboardRejectsInvalidRange() throws Exception {
        String admin = adminBearer();

        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", admin)
                        .param("from", "2026-08-20")
                        .param("to", "2026-08-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", admin)
                        .param("from", "hom-qua"))
                .andExpect(status().isBadRequest());
    }
}
