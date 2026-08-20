package com.rwg.risk;

import com.rwg.affiliate.domain.CommissionSettings;
import com.rwg.affiliate.repository.CommissionRunRepository;
import com.rwg.affiliate.repository.CommissionSettingsRepository;
import com.rwg.affiliate.service.CommissionJob;
import com.rwg.affiliate.service.ReferralService;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.BetType;
import com.rwg.game.repository.BetRepository;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.risk.domain.AccountLink;
import com.rwg.risk.domain.AccountLinkStatus;
import com.rwg.risk.domain.AccountLinkType;
import com.rwg.risk.repository.AccountLinkRepository;
import com.rwg.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chặn dòng hoa hồng cho tài khoản bị liên kết (chặng 7) — PHẦN CHẠM TIỀN.
 *
 * Mọi test ở đây khẳng định SỐ DƯ VÍ THỰC TẾ, không chỉ kiểm cờ hay mã HTTP: nếu
 * chỉ kiểm chứng từ thì một lỗi kiểu "đã credit rồi mới bỏ qua" vẫn lọt.
 *
 * Kịch bản trục lợi đang chặn: một người tạo A, tự tạo B đăng ký bằng mã của A, cược
 * bằng tiền của mình ở B rồi rút hoa hồng về A. Turnover của B là CƯỢC THẬT nên
 * không ràng buộc nào của hệ hoa hồng bị vi phạm.
 */
@SpringBootTest
@ActiveProfiles("test")
class CommissionAntiAbuseTest {

    /** Ngày đã kết thúc — job từ chối chốt hôm nay. */
    private static final LocalDate PERIOD = LocalDate.now(ZoneOffset.UTC).minusDays(1);

    @Autowired
    UserRepository userRepository;

    @Autowired
    BetRepository betRepository;

    @Autowired
    ReferralService referralService;

    @Autowired
    CommissionJob commissionJob;

    @Autowired
    CommissionRunRepository runRepository;

    @Autowired
    CommissionSettingsRepository settingsRepository;

    @Autowired
    AccountLinkRepository linkRepository;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    // ===== Helpers =====

    private User newUser(String prefix) {
        String name = prefix + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(
                new User(name, name + "@example.com", "hash-khong-dung-de-login"));
    }

    /** Nối tuyến dưới vào đại lý qua mã giới thiệu (đường thật, không ghi tay quan hệ). */
    private void attachDownline(User agent, User member) {
        String code = referralService.getOrCreateCode(agent.getId()).getCode();
        assertThat(referralService.attachReferral(member.getId(), code, null)).isTrue();
    }

    /** Cược SETTLED trong ngày PERIOD -> turnover hợp lệ. */
    private void settledBet(UUID userId, String stake) {
        Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), userId, BetType.RED, "RED",
                new BigDecimal(stake), "BET:" + UUID.randomUUID());
        bet.settle(BigDecimal.ZERO);
        betRepository.saveAndFlush(bet);
        // created_at là một nửa PK composite và do @PrePersist gán -> không có setter.
        // Dùng TransactionTemplate vì @Transactional trên method gọi nội bộ cùng class
        // không đi qua proxy Spring.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery(
                                "update bets set created_at = :ts where id = :id")
                        .setParameter("ts", PERIOD.atTime(12, 0).toInstant(ZoneOffset.UTC))
                        .setParameter("id", bet.getId().toString())
                        .executeUpdate());
    }

    /**
     * Tạo liên kết ở trạng thái mong muốn.
     *
     * Người xét duyệt phải là user THẬT: reviewed_by có khoá ngoại tới users, nên
     * UUID ngẫu nhiên sẽ bị DB chặn (đúng thiết kế — không cho ghi vết xét duyệt bởi
     * người không tồn tại).
     */
    private void link(User a, User b, AccountLinkType type, AccountLinkStatus status) {
        AccountLink created = AccountLink.of(a.getId(), b.getId(), type, "{\"test\":\"1\"}");
        if (status != AccountLinkStatus.SUSPECTED) {
            created.review(status, newUser("aa-reviewer").getId(), "test");
        }
        linkRepository.saveAndFlush(created);
    }

    private BigDecimal balanceOf(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> walletRepository.findBalanceById(wallet.getId()))
                .orElse(BigDecimal.ZERO);
    }

    private long runsOf(UUID agentId) {
        return runRepository.findAll().stream()
                .filter(run -> run.getAgentId().equals(agentId))
                .count();
    }

    private void setRates(String level1, String level2) {
        CommissionSettings settings = settingsRepository
                .findById(CommissionSettings.SINGLETON_ID).orElseThrow();
        settings.update(new BigDecimal(level1), new BigDecimal(level2), null);
        settingsRepository.saveAndFlush(settings);
    }

    // ===== Chặn =====

    @Test
    @DisplayName("Tuyến dưới trùng thiết bị -> KHÔNG chi hoa hồng và VÍ KHÔNG TĂNG")
    void linkedDownlineEarnsNoCommission() {
        setRates("0.010000", "0.000000");
        User agent = newUser("aa-agent");
        User self = newUser("aa-self");
        attachDownline(agent, self);
        settledBet(self.getId(), "1000");

        // Chính kịch bản trục lợi: A tự tạo B làm tuyến dưới của mình.
        link(agent, self, AccountLinkType.SHARED_DEVICE, AccountLinkStatus.SUSPECTED);

        commissionJob.runForDate(PERIOD, null);

        assertThat(runsOf(agent.getId())).isZero();
        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("CONFIRMED giữ tiền kể cả với loại tín hiệu yếu SHARED_IP")
    void confirmedBlocksEvenForWeakSignal() {
        setRates("0.010000", "0.000000");
        User agent = newUser("aa-conf");
        User self = newUser("aa-conf");
        attachDownline(agent, self);
        settledBet(self.getId(), "1000");

        link(agent, self, AccountLinkType.SHARED_IP, AccountLinkStatus.CONFIRMED);

        commissionJob.runForDate(PERIOD, null);

        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Liên kết MANUAL (người vận hành tự nối) giữ tiền ngay kỳ sau")
    void manualLinkBlocks() {
        setRates("0.010000", "0.000000");
        User agent = newUser("aa-man");
        User self = newUser("aa-man");
        attachDownline(agent, self);
        settledBet(self.getId(), "1000");

        link(agent, self, AccountLinkType.MANUAL, AccountLinkStatus.CONFIRMED);

        commissionJob.runForDate(PERIOD, null);

        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("0");
    }

    // ===== Không chặn oan =====

    @Test
    @DisplayName("SHARED_IP + SUSPECTED VẪN được chi (tín hiệu yếu không giữ tiền)")
    void sharedIpSuspectedStillPays() {
        setRates("0.010000", "0.000000");
        User agent = newUser("aa-ip");
        User member = newUser("aa-ip");
        attachDownline(agent, member);
        settledBet(member.getId(), "1000");

        // Trùng IP là kịch bản giới thiệu hợp pháp phổ biến nhất -> không được giữ tiền.
        link(agent, member, AccountLinkType.SHARED_IP, AccountLinkStatus.SUSPECTED);

        commissionJob.runForDate(PERIOD, null);

        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("10.00000000");
    }

    @Test
    @DisplayName("Liên kết đã CLEARED -> chi hoa hồng bình thường")
    void clearedLinkPaysNormally() {
        setRates("0.010000", "0.000000");
        User agent = newUser("aa-clear");
        User member = newUser("aa-clear");
        attachDownline(agent, member);
        settledBet(member.getId(), "1000");

        link(agent, member, AccountLinkType.SHARED_DEVICE, AccountLinkStatus.CLEARED);

        commissionJob.runForDate(PERIOD, null);

        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("10.00000000");
    }

    @Test
    @DisplayName("Không có liên kết -> chi bình thường (không hồi quy Phase 2)")
    void noLinkPaysNormally() {
        setRates("0.010000", "0.000000");
        User agent = newUser("aa-none");
        User member = newUser("aa-none");
        attachDownline(agent, member);
        settledBet(member.getId(), "1000");

        commissionJob.runForDate(PERIOD, null);

        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("10.00000000");
    }

    // ===== Chặn CHỌN LỌC — test khó nhất nhóm này =====

    @Test
    @DisplayName("Hai tuyến dưới, một bị liên kết -> chỉ tính turnover của người còn lại")
    void onlyLinkedDownlineIsExcluded() {
        setRates("0.010000", "0.000000");
        User agent = newUser("aa-mix");
        User honest = newUser("aa-honest");
        User self = newUser("aa-mixself");
        attachDownline(agent, honest);
        attachDownline(agent, self);

        settledBet(honest.getId(), "1000");
        settledBet(self.getId(), "5000");
        link(agent, self, AccountLinkType.SHARED_DEVICE, AccountLinkStatus.SUSPECTED);

        commissionJob.runForDate(PERIOD, null);

        // 1% của 1000 = 10. Nếu lọc sai thì ra 60 (tính cả 5000) hoặc 0 (loại cả chùm).
        // Kiểm ĐÚNG SỐ TIỀN, không chỉ "khác 0".
        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("10.00000000");

        // Chứng từ cũng phải ghi turnover ĐÃ LOẠI: nếu ghi 6000 mà chi 10 thì người
        // đối soát sau không hiểu vì sao amount != turnover * rate.
        BigDecimal turnover = runRepository.findAll().stream()
                .filter(run -> run.getAgentId().equals(agent.getId()))
                .findFirst().orElseThrow().getTurnover();
        assertThat(turnover).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Chặn chỉ áp cho ĐÚNG cặp bị liên kết, đại lý khác không bị ảnh hưởng")
    void blockingDoesNotLeakToOtherAgents() {
        setRates("0.010000", "0.000000");
        User blockedAgent = newUser("aa-b1");
        User blockedSelf = newUser("aa-b2");
        User cleanAgent = newUser("aa-c1");
        User cleanMember = newUser("aa-c2");

        attachDownline(blockedAgent, blockedSelf);
        attachDownline(cleanAgent, cleanMember);
        settledBet(blockedSelf.getId(), "1000");
        settledBet(cleanMember.getId(), "1000");
        link(blockedAgent, blockedSelf, AccountLinkType.SHARED_DEVICE,
                AccountLinkStatus.SUSPECTED);

        commissionJob.runForDate(PERIOD, null);

        assertThat(balanceOf(blockedAgent.getId())).isEqualByComparingTo("0");
        assertThat(balanceOf(cleanAgent.getId())).isEqualByComparingTo("10.00000000");
    }

    @Test
    @DisplayName("Liên kết với người NGOÀI tuyến dưới không ảnh hưởng hoa hồng")
    void linkToUnrelatedUserDoesNotBlock() {
        setRates("0.010000", "0.000000");
        User agent = newUser("aa-out");
        User member = newUser("aa-out");
        User stranger = newUser("aa-stranger");
        attachDownline(agent, member);
        settledBet(member.getId(), "1000");

        // Đại lý bị nối với một người KHÔNG nằm trong tuyến dưới -> không có dòng hoa
        // hồng nào tự trả cho chính mình, nên không được giữ tiền.
        link(agent, stranger, AccountLinkType.SHARED_DEVICE, AccountLinkStatus.CONFIRMED);

        commissionJob.runForDate(PERIOD, null);

        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("10.00000000");
    }

    @Test
    @DisplayName("Chặn cấp 2: tuyến dưới gián tiếp bị liên kết cũng không được tính")
    void level2LinkedDownlineExcluded() {
        setRates("0.000000", "0.010000");
        User top = newUser("aa-l2top");
        User middle = newUser("aa-l2mid");
        User bottom = newUser("aa-l2bot");
        attachDownline(top, middle);
        attachDownline(middle, bottom);
        settledBet(bottom.getId(), "1000");

        // bottom là tuyến dưới CẤP 2 của top; nối top <-> bottom.
        link(top, bottom, AccountLinkType.SHARED_DEVICE, AccountLinkStatus.SUSPECTED);

        commissionJob.runForDate(PERIOD, null);

        assertThat(balanceOf(top.getId())).isEqualByComparingTo("0");
    }
}
