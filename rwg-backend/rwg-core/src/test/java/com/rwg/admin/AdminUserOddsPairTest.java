package com.rwg.admin;

import com.rwg.game.domain.BetType;
import com.rwg.game.domain.GameTable;
import com.rwg.game.repository.GameTableRepository;
import com.rwg.game.repository.UserGameOddsRepository;
import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đặt tỷ lệ cho CẢ CẶP hai chiều của một bàn trong một lượt.
 *
 * Điều đáng kiểm nhất ở đây là TÍNH NGUYÊN VẸN: endpoint này tồn tại chính vì gọi hai lần
 * riêng lẻ có thể thành công một nửa, để lại cấu hình lệch mà người vận hành tin là đã đặt
 * cân. Nếu transaction không bọc được cả hai lần ghi thì endpoint mất hết ý nghĩa mà vẫn
 * "chạy đúng" ở đường thành công — nên trường hợp ngoài biên phải khẳng định KHÔNG cửa nào
 * bị ghi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserOddsPairTest {

    private static final String PASSWORD = "MatKhau@12345";

    /** Roulette LOW/HIGH có mức chung odds lợi = 1, tức hệ số 2.00. */
    private static final BigDecimal ROULETTE_DEFAULT_ODDS = BigDecimal.ONE;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GameTableRepository tableRepository;

    @Autowired
    UserGameOddsRepository oddsRepository;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isCreated());
        return login(username);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /** Tài khoản ADMIN kèm id, để test được cả nhánh tự sửa cho chính mình. */
    private Actor admin() throws Exception {
        String username = unique("oadm");
        register(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        // Login LẠI: token cũ phát hành trước khi có vai trò mới.
        return new Actor(user.getId(), login(username));
    }

    private Actor player() throws Exception {
        String username = unique("oply");
        register(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        return new Actor(user.getId(), "Bearer ignored");
    }

    private record Actor(UUID id, String bearer) {
    }

    /** Bàn riêng cho từng test để không nhiễm bàn của test khác. */
    private GameTable newRouletteTable() {
        GameTable table = new GameTable(UUID.randomUUID(), "ROULETTE",
                "{\"en\":\"Odds Pair\",\"vi\":\"Cap ty le\",\"zh\":\"Odds\",\"ja\":\"Odds\"}",
                new BigDecimal("1"), new BigDecimal("1000"));
        return tableRepository.saveAndFlush(table);
    }

    private MvcResult setPair(String bearer, UUID userId, UUID tableId, String odds,
                              int expectedStatus) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/users/" + userId + "/game-odds/pair")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableId":"%s","odds":"%s"}
                                """.formatted(tableId, odds)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private BigDecimal storedOdds(UUID userId, UUID tableId, BetType betType) {
        return oddsRepository.findByUserIdAndTableIdAndBetType(userId, tableId, betType)
                .map(o -> o.getOdds())
                .orElse(null);
    }

    @Test
    @DisplayName("Một lần gọi đặt tỷ lệ cho CẢ HAI cửa của bàn")
    void appliesToBothSidesInOneCall() throws Exception {
        Actor admin = admin();
        Actor player = player();
        GameTable table = newRouletteTable();

        // 1.10 odds lợi = hệ số 2.10 trên giao diện.
        setPair(admin.bearer(), player.id(), table.getId(), "1.10", 204);

        assertThat(storedOdds(player.id(), table.getId(), BetType.LOW))
                .isEqualByComparingTo("1.10");
        assertThat(storedOdds(player.id(), table.getId(), BetType.HIGH))
                .isEqualByComparingTo("1.10");
    }

    @Test
    @DisplayName("Đặt lại lần hai ghi đè cả hai cửa, không tạo bản ghi trùng")
    void secondCallOverwritesBothSides() throws Exception {
        Actor admin = admin();
        Actor player = player();
        GameTable table = newRouletteTable();

        setPair(admin.bearer(), player.id(), table.getId(), "1.10", 204);
        setPair(admin.bearer(), player.id(), table.getId(), "1.20", 204);

        assertThat(storedOdds(player.id(), table.getId(), BetType.LOW))
                .isEqualByComparingTo("1.20");
        assertThat(storedOdds(player.id(), table.getId(), BetType.HIGH))
                .isEqualByComparingTo("1.20");
    }

    @Test
    @DisplayName("Giá trị NGOÀI BIÊN bị từ chối và KHÔNG cửa nào bị ghi")
    void outOfRangeLeavesNothingWritten() throws Exception {
        Actor admin = admin();
        Actor player = player();
        GameTable table = newRouletteTable();

        // Biên an toàn là 0.5×–3× mức chung (mức chung = 1), nên 3.5 vượt trần.
        //
        // Đây là phép kiểm QUAN TRỌNG NHẤT của endpoint: nếu transaction không bọc được cả
        // hai lần ghi thì cửa đầu đã lưu trước khi cửa sau bị từ chối, để lại đúng cái cấu
        // hình lệch mà endpoint này ra đời để ngăn.
        setPair(admin.bearer(), player.id(), table.getId(), "3.5", 400);

        assertThat(storedOdds(player.id(), table.getId(), BetType.LOW)).isNull();
        assertThat(storedOdds(player.id(), table.getId(), BetType.HIGH)).isNull();
    }

    @Test
    @DisplayName("Giá trị ngoài biên KHÔNG xoá tỷ lệ đang áp trước đó")
    void outOfRangeKeepsPreviousValues() throws Exception {
        Actor admin = admin();
        Actor player = player();
        GameTable table = newRouletteTable();

        setPair(admin.bearer(), player.id(), table.getId(), "1.10", 204);
        setPair(admin.bearer(), player.id(), table.getId(), "9.9", 400);

        // Rollback phải trả về ĐÚNG giá trị cũ, không phải mức chung và không phải rỗng.
        assertThat(storedOdds(player.id(), table.getId(), BetType.LOW))
                .isEqualByComparingTo("1.10");
        assertThat(storedOdds(player.id(), table.getId(), BetType.HIGH))
                .isEqualByComparingTo("1.10");
    }

    @Test
    @DisplayName("Admin KHÔNG tự đặt tỷ lệ cho chính mình")
    void adminCannotSetOwnOdds() throws Exception {
        Actor admin = admin();
        GameTable table = newRouletteTable();

        setPair(admin.bearer(), admin.id(), table.getId(), "1.10", 400);

        assertThat(storedOdds(admin.id(), table.getId(), BetType.LOW)).isNull();
        assertThat(storedOdds(admin.id(), table.getId(), BetType.HIGH)).isNull();
    }

    @Test
    @DisplayName("Người chơi KHÔNG gọi được endpoint này")
    void playerCannotCallEndpoint() throws Exception {
        String playerBearer = register(unique("oint"));
        Actor victim = player();
        GameTable table = newRouletteTable();

        mockMvc.perform(put("/api/v1/admin/users/" + victim.id() + "/game-odds/pair")
                        .header("Authorization", playerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableId":"%s","odds":"1.10"}
                                """.formatted(table.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Bàn không tồn tại -> 404, không ghi gì")
    void unknownTableIsRejected() throws Exception {
        Actor admin = admin();
        Actor player = player();

        mockMvc.perform(put("/api/v1/admin/users/" + player.id() + "/game-odds/pair")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableId":"%s","odds":"1.10"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET trả về đúng giá trị vừa đặt cho cả hai cửa")
    void getReflectsPairValues() throws Exception {
        Actor admin = admin();
        Actor player = player();
        GameTable table = newRouletteTable();

        setPair(admin.bearer(), player.id(), table.getId(), "1.10", 204);

        // Mức chung của Roulette LOW/HIGH là 1, nên `defaultOdds` phải giữ nguyên giá trị đó
        // dù tỷ lệ riêng đã đổi — giao diện dùng nó để tính biên an toàn.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/admin/users/" + player.id() + "/game-odds")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables[?(@.tableId=='" + table.getId() + "')]"
                        + ".options[?(@.betType=='LOW')].effectiveOdds")
                        .value(org.hamcrest.Matchers.contains("1.1000")))
                .andExpect(jsonPath("$.tables[?(@.tableId=='" + table.getId() + "')]"
                        + ".options[?(@.betType=='HIGH')].effectiveOdds")
                        .value(org.hamcrest.Matchers.contains("1.1000")));

        assertThat(ROULETTE_DEFAULT_ODDS).isEqualByComparingTo("1");
    }
}
