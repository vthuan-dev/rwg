package com.rwg.affiliate;

import com.rwg.affiliate.domain.CommissionRun;
import com.rwg.affiliate.domain.CommissionSettings;
import com.rwg.affiliate.repository.CommissionRunRepository;
import com.rwg.affiliate.repository.CommissionSettingsRepository;
import com.rwg.affiliate.repository.ReferralCodeRepository;
import com.rwg.affiliate.repository.UserRelationRepository;
import com.rwg.affiliate.service.CommissionJob;
import com.rwg.affiliate.service.ReferralService;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.BetType;
import com.rwg.game.repository.BetRepository;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.repository.WalletRepository;
import com.rwg.wallet.repository.WalletTransactionRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm chứng nghiệp vụ hoa hồng đại lý (Phase 2) ở tầng service + DB thật (H2).
 *
 * TEST QUAN TRỌNG NHẤT: {@link #runningJobTwiceNeverPaysTwice()} — job chi tiền thật
 * nên phải chứng minh chạy lại KHÔNG cộng tiền lần hai. Đây là bất biến chống thất
 * thoát tiền, không phải test hình thức.
 */
@SpringBootTest
@ActiveProfiles("test")
class CommissionJobTest {

    /** Ngày đã kết thúc để chốt hoa hồng (không dùng hôm nay). */
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
    UserRelationRepository relationRepository;

    @Autowired
    CommissionRunRepository runRepository;

    @Autowired
    CommissionSettingsRepository settingsRepository;

    @Autowired
    ReferralCodeRepository codeRepository;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    WalletTransactionRepository transactionRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    // ===== Helpers =====

    private User newUser(String prefix) {
        String name = prefix + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new User(name, null, "hash-khong-dung-de-login"));
    }

    /** Đặt cược ĐÃ SETTLED trong ngày PERIOD (turnover hợp lệ). */
    private void settledBet(UUID userId, String stake) {
        placeBet(userId, stake, true);
    }

    /** Đặt cược bị HOÀN trong ngày PERIOD (KHÔNG được tính turnover). */
    private void voidedBet(UUID userId, String stake) {
        placeBet(userId, stake, false);
    }

    private void placeBet(UUID userId, String stake, boolean settled) {
        Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), userId, BetType.RED, "RED",
                new BigDecimal(stake), "BET:" + UUID.randomUUID());
        if (settled) {
            bet.settle(BigDecimal.ZERO);
        } else {
            bet.markVoided();
        }
        betRepository.saveAndFlush(bet);
        moveBetIntoPeriod(bet.getId());
    }

    /**
     * Đẩy created_at của bet về giữa ngày PERIOD.
     *
     * Dùng native UPDATE ngay trong test vì created_at là một nửa PK composite và
     * được @PrePersist gán — entity KHÔNG có setter (đúng thiết kế append-only).
     * CỐ TÌNH không thêm method test-only vào BetRepository để không làm bẩn API
     * production chỉ vì nhu cầu của test.
     *
     * Dùng TransactionTemplate chứ KHÔNG @Transactional: annotation trên method được
     * gọi NỘI BỘ trong cùng class không đi qua proxy Spring nên không có hiệu lực
     * (bulk update sẽ báo "No active transaction").
     */
    private void moveBetIntoPeriod(UUID betId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery(
                                "update bets set created_at = :ts where id = :id")
                        .setParameter("ts", PERIOD.atTime(12, 0).toInstant(ZoneOffset.UTC))
                        .setParameter("id", betId.toString())
                        .executeUpdate());
    }

    private BigDecimal balanceOf(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> walletRepository.findBalanceById(wallet.getId()))
                .orElse(BigDecimal.ZERO);
    }

    private long commissionLedgerRows(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> transactionRepository.countByWalletIdAndRefType(
                        wallet.getId(), WalletRefType.COMMISSION))
                .orElse(0L);
    }

    /** Số chứng từ hoa hồng của RIÊNG một đại lý (không đếm đại lý của test khác). */
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

    // ===== Tests =====

    @Test
    @DisplayName("Đăng ký bằng mã giới thiệu tạo quan hệ 2 cấp đúng chiều")
    void referralBuildsTwoLevelChain() {
        User top = newUser("top");
        User middle = newUser("mid");
        User bottom = newUser("bot");

        String topCode = referralService.getOrCreateCode(top.getId()).getCode();
        assertThat(referralService.attachReferral(middle.getId(), topCode, null)).isTrue();

        String middleCode = referralService.getOrCreateCode(middle.getId()).getCode();
        assertThat(referralService.attachReferral(bottom.getId(), middleCode, null)).isTrue();

        // bottom: cấp 1 = middle, cấp 2 = top.
        assertThat(relationRepository.findDescendantIds(middle.getId(), (short) 1))
                .containsExactly(bottom.getId());
        assertThat(relationRepository.findDescendantIds(top.getId(), (short) 2))
                .containsExactly(bottom.getId());
        // KHÔNG có cấp 3: top không phải tuyến trên cấp 1 của bottom.
        assertThat(relationRepository.findDescendantIds(top.getId(), (short) 1))
                .containsExactly(middle.getId());
    }

    @Test
    @DisplayName("Tự giới thiệu chính mình bị từ chối")
    void selfReferralRejected() {
        User user = newUser("self");
        String code = referralService.getOrCreateCode(user.getId()).getCode();

        assertThat(referralService.attachReferral(user.getId(), code, null)).isFalse();
        assertThat(relationRepository.findByDescendantId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("Vòng lặp A->B->A bị từ chối (nếu không chặn, job sẽ trả hoa hồng chéo)")
    void referralCycleRejected() {
        User a = newUser("cyca");
        User b = newUser("cycb");

        String codeA = referralService.getOrCreateCode(a.getId()).getCode();
        assertThat(referralService.attachReferral(b.getId(), codeA, null)).isTrue();

        // Giờ thử cho A nhận B làm tuyến trên -> tạo vòng lặp.
        String codeB = referralService.getOrCreateCode(b.getId()).getCode();
        assertThat(referralService.attachReferral(a.getId(), codeB, null)).isFalse();
        assertThat(relationRepository.findByDescendantId(a.getId())).isEmpty();
    }

    @Test
    @DisplayName("Mã giới thiệu sai bị bỏ qua, không tạo quan hệ")
    void unknownCodeIsIgnored() {
        User user = newUser("unk");

        assertThat(referralService.attachReferral(user.getId(), "KHONGTONTAI", null)).isFalse();
        assertThat(referralService.attachReferral(user.getId(), null, null)).isFalse();
        assertThat(referralService.attachReferral(user.getId(), "  ", null)).isFalse();
        assertThat(relationRepository.findByDescendantId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("Mã giới thiệu là duy nhất và ổn định (gọi lại trả cùng mã)")
    void codeIsStableAndUnique() {
        User user = newUser("code");

        String first = referralService.getOrCreateCode(user.getId()).getCode();
        String second = referralService.getOrCreateCode(user.getId()).getCode();

        assertThat(second).isEqualTo(first);
        assertThat(codeRepository.findByUserId(user.getId())).isPresent();
    }

    @Test
    @DisplayName("Chi hoa hồng đúng công thức turnover × rate cho cả 2 cấp")
    void paysCommissionForBothLevels() {
        setRates("0.010000", "0.005000");
        User top = newUser("ptop");
        User middle = newUser("pmid");
        User bottom = newUser("pbot");

        referralService.attachReferral(middle.getId(),
                referralService.getOrCreateCode(top.getId()).getCode(), null);
        referralService.attachReferral(bottom.getId(),
                referralService.getOrCreateCode(middle.getId()).getCode(), null);

        settledBet(bottom.getId(), "1000");

        commissionJob.runForDate(PERIOD, null);

        // middle là tuyến trên cấp 1 của bottom -> 1000 × 1% = 10
        assertThat(balanceOf(middle.getId())).isEqualByComparingTo("10");
        // top là tuyến trên cấp 2 của bottom -> 1000 × 0.5% = 5
        assertThat(balanceOf(top.getId())).isEqualByComparingTo("5");
        // bottom không có tuyến dưới -> không nhận gì
        assertThat(balanceOf(bottom.getId())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("CHẠY JOB HAI LẦN KHÔNG BAO GIỜ CHI TIỀN HAI LẦN")
    void runningJobTwiceNeverPaysTwice() {
        setRates("0.010000", "0.000000");
        User agent = newUser("dupag");
        User player = newUser("duppl");

        referralService.attachReferral(player.getId(),
                referralService.getOrCreateCode(agent.getId()).getCode(), null);
        settledBet(player.getId(), "2000");

        commissionJob.runForDate(PERIOD, null);
        BigDecimal afterFirst = balanceOf(agent.getId());
        long runsAfterFirst = runsOf(agent.getId());

        commissionJob.runForDate(PERIOD, null);
        BigDecimal afterSecond = balanceOf(agent.getId());

        // 2000 × 1% = 20 sau lần chạy đầu.
        assertThat(afterFirst).isEqualByComparingTo("20");
        assertThat(runsAfterFirst).isEqualTo(1);

        // Lần 2: KHÔNG cộng thêm tiền, KHÔNG thêm chứng từ, KHÔNG thêm dòng ledger.
        //
        // Khẳng định theo TỪNG ĐẠI LÝ, không dùng RunSummary.runsCreated: các test
        // trong class này dùng chung một DB nên con số tổng còn đếm cả đại lý do test
        // khác tạo — job quét toàn bộ đại lý là hành vi ĐÚNG.
        assertThat(afterSecond).isEqualByComparingTo(afterFirst);
        assertThat(runsOf(agent.getId())).isEqualTo(1);
        assertThat(commissionLedgerRows(agent.getId())).isEqualTo(1);
        assertThat(runRepository.existsByAgentIdAndPeriodDateAndLevel(
                agent.getId(), PERIOD, (short) 1)).isTrue();
    }

    @Test
    @DisplayName("Cược HOÀN (VOIDED) không tính turnover")
    void voidedBetsAreExcludedFromTurnover() {
        setRates("0.010000", "0.000000");
        User agent = newUser("voiag");
        User player = newUser("voipl");

        referralService.attachReferral(player.getId(),
                referralService.getOrCreateCode(agent.getId()).getCode(), null);
        voidedBet(player.getId(), "5000");

        commissionJob.runForDate(PERIOD, null);

        // Chỉ có cược VOIDED -> turnover 0 -> không chi đồng nào.
        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("0");
        assertThat(runRepository.existsByAgentIdAndPeriodDateAndLevel(
                agent.getId(), PERIOD, (short) 1)).isFalse();
    }

    @Test
    @DisplayName("Ngày khác kỳ chốt không bị tính vào hoa hồng")
    void betsOutsidePeriodAreExcluded() {
        setRates("0.010000", "0.000000");
        User agent = newUser("outag");
        User player = newUser("outpl");

        referralService.attachReferral(player.getId(),
                referralService.getOrCreateCode(agent.getId()).getCode(), null);
        settledBet(player.getId(), "1000");

        // Chốt cho ngày TRƯỚC kỳ có cược -> không có turnover.
        commissionJob.runForDate(PERIOD.minusDays(5), null);

        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Tổng ledger COMMISSION khớp số dư sau khi job chi tiền")
    void ledgerMatchesBalanceAfterCommission() {
        setRates("0.020000", "0.000000");
        User agent = newUser("ledag");
        User player = newUser("ledpl");

        referralService.attachReferral(player.getId(),
                referralService.getOrCreateCode(agent.getId()).getCode(), null);
        settledBet(player.getId(), "500");
        settledBet(player.getId(), "500");

        commissionJob.runForDate(PERIOD, null);

        // turnover = 1000 -> 1000 × 2% = 20, ghi ĐÚNG 1 dòng ledger gộp.
        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("20");
        assertThat(commissionLedgerRows(agent.getId())).isEqualTo(1);

        List<CommissionRun> runs = runRepository.findAll().stream()
                .filter(run -> run.getAgentId().equals(agent.getId()))
                .toList();
        assertThat(runs).hasSize(1);
        assertThat(runs.getFirst().getTurnover()).isEqualByComparingTo("1000");
        assertThat(runs.getFirst().getAmount()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("Rate 0 (cấp bị tắt) thì cấp đó không chi")
    void zeroRateLevelIsSkipped() {
        setRates("0.000000", "0.000000");
        User agent = newUser("zeroag");
        User player = newUser("zeropl");

        referralService.attachReferral(player.getId(),
                referralService.getOrCreateCode(agent.getId()).getCode(), null);
        settledBet(player.getId(), "10000");

        CommissionJob.RunSummary summary = commissionJob.runForDate(PERIOD, null);

        // Cả 2 cấp đều rate 0 -> đại lý NÀY không có chứng từ và không nhận tiền.
        // (Không assert summary.runsCreated() vì các test dùng chung DB nên con số
        // tổng còn tính cả đại lý do test khác tạo.)
        assertThat(summary).isNotNull();
        assertThat(runsOf(agent.getId())).isZero();
        assertThat(balanceOf(agent.getId())).isEqualByComparingTo("0");
    }
}
