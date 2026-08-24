package com.rwg.report;

import com.rwg.CoreTestApplication;
import com.rwg.report.dto.PlayerLedgerResponse;
import com.rwg.report.service.PlayerLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm sổ sách người chơi.
 *
 * PHÉP KIỂM QUAN TRỌNG NHẤT LÀ {@link #ledgerBalancesOut()}: đối chiếu cân đối. Từng
 * con số riêng lẻ có thể trông hợp lý mà tổng thể vẫn sai — chỉ đẳng thức cân đối mới
 * phát hiện được kiểu lỗi đó.
 *
 * VÌ SAO DÙNG {@link JdbcTemplate} CHÈN DỮ LIỆU THAY VÌ REPOSITORY: mọi con số trong
 * báo cáo phụ thuộc vào {@code created_at}, mà các entity KHÔNG cho đặt trường đó —
 * {@code Bet.onCreate} và {@code PaymentOrder.onCreate} tự gán {@code Instant.now()}
 * trong {@code @PrePersist}, {@code PaymentOrder} chỉ có static factory, và
 * {@code WalletTransaction} để trường private không setter. Chèn hàng thô là cách duy
 * nhất đặt được mốc thời gian chính xác, và cũng phản ánh đúng những gì có trong DB.
 */
@SpringBootTest(classes = CoreTestApplication.class)
@ActiveProfiles("test")
class PlayerLedgerServiceTest {

    /** Bàn KL28 có sẵn từ migration seed. */
    private static final String KL28_TABLE = "33333333-4444-5555-6666-777777777777";
    /** Bàn Baccarat có sẵn từ migration seed — để kiểm việc nhóm theo game. */
    private static final String BACCARAT_TABLE = "22222222-3333-4444-5555-666666666666";

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private PlayerLedgerService service;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;
    private UUID walletId;

    /** Giữa kỳ — mốc cho mọi giao dịch, để không lẫn sang kỳ khác. */
    private Instant midPeriod;
    private LocalDate from;
    private LocalDate to;

    @BeforeEach
    void setUp() {
        userId = createPlayer();
        walletId = createWallet(userId);

        // KỲ BÁO CÁO LÀ THÁNG TRƯỚC, không phải tháng này: tháng này còn đang chạy nên
        // bộ lập lịch vòng chơi (đang bật trong test) có thể chèn thêm bản ghi.
        LocalDate lastMonth = LocalDate.now(REPORT_ZONE).minusMonths(1);
        from = lastMonth.withDayOfMonth(1);
        to = from.plusMonths(1).minusDays(1);
        midPeriod = from.plusDays(14).atStartOfDay(REPORT_ZONE).toInstant();
    }

    @Test
    @DisplayName("Thắng thua tách đúng theo từng game, lãi lỗ trừ tiền gốc")
    void splitsWinLossByGame() {
        // KL28: cược 100 thắng 198 -> lãi 98. Cược 50 thua -> lỗ 50. Ròng +48.
        saveBet(KL28_TABLE, "KL28_BIG", "100", "198", "SETTLED", midPeriod);
        saveBet(KL28_TABLE, "KL28_SMALL", "50", "0", "SETTLED", midPeriod);
        // Baccarat: cược 80 thua sạch -> lỗ 80.
        saveBet(BACCARAT_TABLE, "PLAYER", "80", "0", "SETTLED", midPeriod);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(r.games()).hasSize(2);

        PlayerLedgerResponse.GameLine kl28 = lineOf(r, "KL28");
        assertThat(kl28.betCount()).isEqualTo(2);
        assertThat(new BigDecimal(kl28.stake())).isEqualByComparingTo("150");
        assertThat(new BigDecimal(kl28.payout())).isEqualByComparingTo("198");
        // LÃI THẬT = payout - stake. payout ĐÃ GỒM tiền gốc nên coi 198 là lãi sẽ sai.
        assertThat(new BigDecimal(kl28.net())).isEqualByComparingTo("48");

        assertThat(new BigDecimal(lineOf(r, "BACCARAT").net())).isEqualByComparingTo("-80");

        assertThat(new BigDecimal(r.totalStake())).isEqualByComparingTo("230");
        assertThat(new BigDecimal(r.totalNet())).isEqualByComparingTo("-32");
    }

    @Test
    @DisplayName("Cược VOIDED không tính vào lãi lỗ")
    void voidedBetsExcluded() {
        saveBet(KL28_TABLE, "KL28_BIG", "100", "198", "SETTLED", midPeriod);
        // Cược VOIDED đã được hoàn tiền nên không phải cược thật. Tính vào sẽ làm
        // phồng doanh số và sai lãi lỗ.
        saveBet(KL28_TABLE, "KL28_BIG", "9999", "0", "VOIDED", midPeriod);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(lineOf(r, "KL28").betCount()).isEqualTo(1);
        assertThat(new BigDecimal(r.totalStake())).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Cược PENDING vào cột Đang treo, KHÔNG vào lãi lỗ")
    void pendingBetsGoToOwnColumn() {
        saveBet(KL28_TABLE, "KL28_BIG", "100", "198", "SETTLED", midPeriod);
        saveBet(KL28_TABLE, "KL28_SMALL", "70", "0", "PENDING", midPeriod);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        PlayerLedgerResponse.GameLine kl28 = lineOf(r, "KL28");
        // Chỉ đếm ván đã kết toán.
        assertThat(kl28.betCount()).isEqualTo(1);
        assertThat(new BigDecimal(kl28.net())).isEqualByComparingTo("98");
        assertThat(new BigDecimal(kl28.pendingStake())).isEqualByComparingTo("70");
        assertThat(new BigDecimal(r.totalPending())).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("Game CHỈ có cược treo vẫn hiện ra, không bị bỏ sót")
    void gameWithOnlyPendingBetsStillAppears() {
        // Nhánh dễ sót nhất: vòng lặp chính đi theo danh sách game ĐÃ KẾT TOÁN, nên
        // game chỉ có cược treo sẽ biến mất và tổng tiền treo bị thiếu.
        saveBet(KL28_TABLE, "KL28_BIG", "100", "198", "SETTLED", midPeriod);
        saveBet(BACCARAT_TABLE, "PLAYER", "60", "0", "PENDING", midPeriod);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(r.games()).hasSize(2);
        PlayerLedgerResponse.GameLine baccarat = lineOf(r, "BACCARAT");
        assertThat(baccarat.betCount()).isZero();
        assertThat(new BigDecimal(baccarat.pendingStake())).isEqualByComparingTo("60");
        assertThat(new BigDecimal(r.totalPending())).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("Nạp qua cổng và admin cộng tay TÁCH RIÊNG, không gộp")
    void separatesGatewayDepositFromAdminCredit() {
        saveOrder("DEPOSIT", "SUCCESS", "500", midPeriod);
        // Lệnh nạp thất bại KHÔNG được tính: tiền chưa vào.
        saveOrder("DEPOSIT", "FAILED", "9999", midPeriod);
        saveOrder("WITHDRAWAL", "SETTLED", "200", midPeriod);
        // Lệnh rút còn chờ duyệt KHÔNG được tính: tiền chưa ra khỏi sàn.
        saveOrder("WITHDRAWAL", "PENDING", "8888", midPeriod);

        saveTxn("ADJUSTMENT", "1000", "0", "1500", midPeriod);
        saveTxn("ADJUSTMENT", "0", "300", "1200", midPeriod);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(new BigDecimal(r.depositViaGateway())).isEqualByComparingTo("500");
        assertThat(new BigDecimal(r.adminCredit())).isEqualByComparingTo("1000");
        assertThat(new BigDecimal(r.adminDebit())).isEqualByComparingTo("300");
        assertThat(new BigDecimal(r.withdrawalSettled())).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("Nạp SETTLED không tính là đã nạp; rút SUCCESS không tính là đã rút")
    void completionStatusDiffersBetweenDepositAndWithdrawal() {
        // BẪY ĐÃ ĐƯỢC GHI LẠI TRONG AdminDashboardService: nạp hoàn tất là SUCCESS,
        // rút hoàn tất là SETTLED. Dùng sai sẽ ra tổng 0 mà KHÔNG có lỗi nào.
        saveOrder("DEPOSIT", "SETTLED", "777", midPeriod);
        saveOrder("WITHDRAWAL", "SUCCESS", "666", midPeriod);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(new BigDecimal(r.depositViaGateway())).isEqualByComparingTo("0");
        assertThat(new BigDecimal(r.withdrawalSettled())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Chỉ tính điều chỉnh của ĐÚNG ví này, không lẫn ví người khác")
    void adjustmentsAreScopedToThisWallet() {
        saveTxn("ADJUSTMENT", "1000", "0", "1000", midPeriod);

        // Ví của một người chơi khác, cùng kỳ. wallet_transactions khoá theo wallet_id
        // chứ không user_id, nên thiếu điều kiện lọc là số của hai người trộn vào nhau.
        //
        // PHẢI TẠO NGƯỜI DÙNG THẬT: bảng wallets có khoá ngoại fk_wallets_user trỏ tới
        // users(id), nên một user_id bất kỳ sẽ bị từ chối.
        UUID otherWallet = createWallet(createPlayer());
        jdbc.update("insert into wallet_transactions (id, wallet_id, debit, credit, "
                        + "balance_after, ref_type, ref_id, idempotency_key, status, created_at) "
                        + "values (?, ?, 0, 5555, 5555, 'ADJUSTMENT', ?, ?, 'SETTLED', ?)",
                UUID.randomUUID().toString(), otherWallet.toString(),
                UUID.randomUUID().toString(), "OTHER-" + UUID.randomUUID(), ts(midPeriod));

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(new BigDecimal(r.adminCredit())).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("ĐỐI CHIẾU CÂN ĐỐI: số dư cuối = đầu + nạp + cộng − trừ − rút + lãi lỗ")
    void ledgerBalancesOut() {
        // Chuỗi giao dịch có số dư liên tục, đúng như hệ thống thật sẽ ghi. Bắt đầu 0.
        saveTxn("DEPOSIT", "500", "0", "500", midPeriod);
        saveTxn("ADJUSTMENT", "1000", "0", "1500", midPeriod.plusSeconds(1));
        saveTxn("ADJUSTMENT", "0", "300", "1200", midPeriod.plusSeconds(2));
        saveTxn("BET", "0", "100", "1100", midPeriod.plusSeconds(3));
        saveTxn("WIN", "198", "0", "1298", midPeriod.plusSeconds(4));
        saveTxn("WITHDRAWAL", "0", "200", "1098", midPeriod.plusSeconds(5));

        saveOrder("DEPOSIT", "SUCCESS", "500", midPeriod);
        saveOrder("WITHDRAWAL", "SETTLED", "200", midPeriod);
        saveBet(KL28_TABLE, "KL28_BIG", "100", "198", "SETTLED", midPeriod);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        BigDecimal expected = new BigDecimal(r.openingBalance())
                .add(new BigDecimal(r.depositViaGateway()))
                .add(new BigDecimal(r.adminCredit()))
                .subtract(new BigDecimal(r.adminDebit()))
                .subtract(new BigDecimal(r.withdrawalSettled()))
                .add(new BigDecimal(r.totalNet()));

        // ĐÂY LÀ PHÉP KIỂM QUAN TRỌNG NHẤT. Đẳng thức này sai nghĩa là báo cáo sai ở
        // đâu đó, dù từng con số riêng lẻ trông hoàn toàn hợp lý.
        assertThat(new BigDecimal(r.closingBalance())).isEqualByComparingTo(expected);
        assertThat(new BigDecimal(r.closingBalance())).isEqualByComparingTo("1098");
    }

    @Test
    @DisplayName("Số dư đầu kỳ lấy từ giao dịch TRƯỚC kỳ, không phải số dư hiện tại")
    void openingBalanceComesFromBeforePeriod() {
        // Một giao dịch ở kỳ trước để lại số dư 400.
        saveTxn("DEPOSIT", "400", "0", "400", midPeriod.minusSeconds(60L * 60 * 24 * 40));
        saveTxn("ADJUSTMENT", "100", "0", "500", midPeriod);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(new BigDecimal(r.openingBalance())).isEqualByComparingTo("400");
        assertThat(new BigDecimal(r.closingBalance())).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("Giao dịch ngoài kỳ KHÔNG bị tính vào")
    void excludesTransactionsOutsidePeriod() {
        saveBet(KL28_TABLE, "KL28_BIG", "100", "198", "SETTLED", midPeriod);
        // Một ván ở kỳ trước kỳ đang xem.
        saveBet(KL28_TABLE, "KL28_BIG", "9999", "0", "SETTLED",
                midPeriod.minusSeconds(60L * 60 * 24 * 60));

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(new BigDecimal(r.totalStake())).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Ván lúc 00:30 ngày đầu kỳ giờ Việt Nam thuộc ĐÚNG kỳ đó")
    void timezoneBoundaryUsesReportZone() {
        // 00:30 giờ Việt Nam ngày đầu kỳ = 17:30 giờ UTC ngày HÔM TRƯỚC. Nếu báo cáo cắt
        // kỳ theo UTC thì ván này bị đẩy sang kỳ trước và admin thấy số không khớp với
        // những gì họ quan sát trong ngày.
        Instant justAfterMidnight = from.atStartOfDay(REPORT_ZONE).plusSeconds(1800).toInstant();
        saveBet(KL28_TABLE, "KL28_BIG", "123", "0", "SETTLED", justAfterMidnight);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(new BigDecimal(r.totalStake())).isEqualByComparingTo("123");
        assertThat(r.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    @DisplayName("Ván lúc 23:30 ngày cuối kỳ giờ Việt Nam vẫn thuộc kỳ đó")
    void lastDayOfPeriodIsInclusive() {
        // `to` là ngày BAO GỒM. Cắt sai thì cả ngày cuối tháng biến mất khỏi báo cáo.
        Instant lateLastDay = to.atStartOfDay(REPORT_ZONE).plusSeconds(84600).toInstant();
        saveBet(KL28_TABLE, "KL28_BIG", "456", "0", "SETTLED", lateLastDay);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(new BigDecimal(r.totalStake())).isEqualByComparingTo("456");
    }

    @Test
    @DisplayName("Người chơi không có giao dịch nào trả về toàn số 0, không lỗi")
    void emptyPlayerReturnsZeros() {
        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(r.games()).isEmpty();
        assertThat(new BigDecimal(r.totalNet())).isEqualByComparingTo("0");
        assertThat(new BigDecimal(r.openingBalance())).isEqualByComparingTo("0");
        assertThat(new BigDecimal(r.closingBalance())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Số tiền trả về dạng thập phân thuần, không ký hiệu khoa học")
    void amountsAreNotInScientificNotation() {
        // BigDecimal("5E+2").toString() cho "5E+2" — vô nghĩa trong báo cáo tiền và
        // làm Excel hiểu sai khi mở CSV.
        saveTxn("ADJUSTMENT", "500", "0", "500", midPeriod);

        PlayerLedgerResponse r = service.ledger(userId, from, to);

        assertThat(r.adminCredit()).doesNotContain("E").doesNotContain("e");
        assertThat(r.totalNet()).doesNotContain("E").doesNotContain("e");
    }

    // ------------------------------------------------------------------
    // Tiện ích dựng dữ liệu
    // ------------------------------------------------------------------

    private PlayerLedgerResponse.GameLine lineOf(PlayerLedgerResponse r, String gameType) {
        return r.games().stream()
                .filter(g -> g.gameType().equals(gameType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không thấy dòng game " + gameType));
    }

    /** Tạo một người chơi tối thiểu, trả về id. */
    private UUID createPlayer() {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("insert into users (id, username, email, password_hash, role, status, "
                        + "kyc_level, locale, created_at, updated_at) "
                        + "values (?, ?, ?, ?, 'PLAYER', 'ACTIVE', 'NONE', 'vi', ?, ?)",
                id.toString(), "ledger-" + suffix, "ledger-" + suffix + "@rwg.com",
                "$2a$10$notarealhash", now(), now());
        return id;
    }

    /** Tạo ví cho một người chơi đã tồn tại, trả về id ví. */
    private UUID createWallet(UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into wallets (id, user_id, balance, currency, version, "
                        + "created_at, updated_at) values (?, ?, 0, 'USD', 0, ?, ?)",
                id.toString(), ownerId.toString(), now(), now());
        return id;
    }

    private void saveBet(String tableId, String betType, String stake, String payout,
                         String status, Instant createdAt) {
        jdbc.update("insert into bets (id, round_id, table_id, user_id, bet_type, selection, "
                        + "stake, status, payout, idempotency_key, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, '', ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), tableId,
                userId.toString(), betType, new BigDecimal(stake), status,
                new BigDecimal(payout), "BET-" + UUID.randomUUID(),
                ts(createdAt), ts(createdAt));
    }

    private void saveOrder(String type, String status, String amount, Instant createdAt) {
        jdbc.update("insert into payment_orders (id, user_id, provider, type, amount, currency, "
                        + "status, idempotency_key, created_at, updated_at) "
                        + "values (?, ?, 'TEST', ?, ?, 'USD', ?, ?, ?, ?)",
                UUID.randomUUID().toString(), userId.toString(), type,
                new BigDecimal(amount), status, "ORD-" + UUID.randomUUID(),
                ts(createdAt), ts(createdAt));
    }

    private void saveTxn(String refType, String credit, String debit,
                         String balanceAfter, Instant createdAt) {
        jdbc.update("insert into wallet_transactions (id, wallet_id, debit, credit, "
                        + "balance_after, ref_type, ref_id, idempotency_key, status, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, 'SETTLED', ?)",
                UUID.randomUUID().toString(), walletId.toString(),
                new BigDecimal(debit), new BigDecimal(credit), new BigDecimal(balanceAfter),
                refType, UUID.randomUUID().toString(), "TXN-" + UUID.randomUUID(),
                ts(createdAt));
    }

    /**
     * Đổi {@link Instant} sang {@link Timestamp} theo múi giờ MẶC ĐỊNH của JVM.
     *
     * Cột {@code DATETIME(6)} không lưu múi giờ, và driver JDBC quy đổi
     * {@code Instant} → {@code Timestamp} theo múi giờ JVM. Dùng
     * {@code Timestamp.from(instant)} trực tiếp cũng cho kết quả tương đương vì driver
     * đọc lại bằng chính quy tắc đó — điều quan trọng là GHI và ĐỌC dùng cùng một phép
     * quy đổi, và Hibernate ở tầng service cũng dùng đúng phép này.
     */
    private Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }
}
