package com.rwg.report.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.config.ReportProperties;
import com.rwg.game.domain.Bet;
import com.rwg.game.repository.BetRepository;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.domain.PaymentType;
import com.rwg.payment.repository.PaymentOrderRepository;
import com.rwg.report.dto.LedgerGameLineResponse;
import com.rwg.report.dto.LedgerOverviewResponse;
import com.rwg.report.dto.LedgerPlayerRowResponse;
import com.rwg.report.dto.PlayerLedgerResponse;
import com.rwg.wallet.domain.Wallet;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.repository.WalletRepository;
import com.rwg.wallet.repository.WalletTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sổ sách một người chơi: dòng tiền vào/ra và thắng/thua theo từng game.
 *
 * MỤC ĐÍCH KHÁC HẲN {@code AuditTrailService}: nhật ký kiểm toán ghi <em>hành vi</em>
 * (ai làm gì, lúc nào) và cố tình không chứa số tiền. Service này trả lời câu hỏi
 * <em>bao nhiêu tiền</em>, lấy từ ba nguồn số thật: {@code bets},
 * {@code wallet_transactions}, {@code payment_orders}.
 *
 * BỐN TRUY VẤN, MỖI TRUY VẤN MỘT CÂU HỎI — không gộp thành một câu SQL lớn. Gộp lại
 * sẽ cần nhiều {@code LEFT JOIN} lên các bảng có lượng bản ghi rất khác nhau, và một
 * người chơi không có giao dịch nào ở một nguồn sẽ làm mất luôn dòng dữ liệu của các
 * nguồn còn lại. Bốn truy vấn nhỏ dễ đọc và dễ kiểm hơn.
 *
 * KHÔNG CÓ TRẠNG THÁI NÀO Ở CẤP THỂ HIỆN. Service là singleton của Spring, nên một
 * trường {@code BigDecimal} dùng để cộng dồn sẽ bị hai yêu cầu đồng thời ghi đè lên
 * nhau và trả về số của người chơi khác. Mọi giá trị trung gian nằm trong biến cục bộ
 * hoặc được trả về qua {@link GameBreakdown}.
 */
@Service
public class PlayerLedgerService {

    private final BetRepository betRepository;
    private final PaymentOrderRepository orderRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository txnRepository;
    private final UserRepository userRepository;
    private final ReportProperties properties;

    public PlayerLedgerService(BetRepository betRepository,
                               PaymentOrderRepository orderRepository,
                               WalletRepository walletRepository,
                               WalletTransactionRepository txnRepository,
                               UserRepository userRepository,
                               ReportProperties properties) {
        this.betRepository = betRepository;
        this.orderRepository = orderRepository;
        this.walletRepository = walletRepository;
        this.txnRepository = txnRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    /**
     * Sổ sách của một người chơi trong một tháng.
     *
     * @param month dạng {@code yyyy-MM}, ví dụ {@code 2026-08}. Null hoặc rỗng thì
     *     lấy tháng hiện tại theo múi giờ báo cáo.
     */
    @Transactional(readOnly = true)
    public PlayerLedgerResponse monthlyLedger(UUID userId, String month) {
        YearMonth ym = parseMonth(month, properties.zone());
        return ledger(userId, ym.atDay(1), ym.atEndOfMonth());
    }

    /**
     * Sổ sách của một người chơi trong khoảng ngày bất kỳ.
     *
     * {@code to} là ngày BAO GỒM, nên mốc trên thực tế là đầu ngày kế tiếp. Làm vậy
     * để admin nhập "1/8 đến 31/8" và nhận đúng cả ngày 31, thay vì phải nhập 1/9.
     */
    @Transactional(readOnly = true)
    public PlayerLedgerResponse ledger(UUID userId, LocalDate from, LocalDate to) {
        validateRange(from, to);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(),
                        Map.of("resource", "user"), "error.user.not_found"));

        ZoneId zone = properties.zone();
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        // Khoảng NỬA MỞ [from, to): hai kỳ liền nhau không đếm trùng bản ghi ở
        // đúng ranh giới, và không bỏ sót giao dịch lúc 23:59:59.999 ngày cuối.
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();

        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        GameBreakdown breakdown = buildGameLines(userId, fromInstant, toInstant);

        BigDecimal gateway = gatewayDeposit(userId, fromInstant, toInstant);
        BigDecimal credit = adminAdjustment(wallet, fromInstant, toInstant, true);

        return new PlayerLedgerResponse(
                userId.toString(),
                user.getUsername(),
                from.toString(),
                to.toString(),
                properties.timezone(),
                wallet != null ? wallet.getCurrency() : "USD",
                plain(balanceAt(wallet, fromInstant)),
                plain(balanceAt(wallet, toInstant)),
                plain(gateway.add(credit)),
                plain(gateway),
                plain(credit),
                plain(adminAdjustment(wallet, fromInstant, toInstant, false)),
                plain(settledWithdrawal(userId, fromInstant, toInstant)),
                breakdown.lines(),
                plain(breakdown.stake()),
                plain(breakdown.payout()),
                plain(breakdown.net()),
                plain(breakdown.pending()));
    }

    /**
     * Bảng tổng quan: một dòng mỗi người chơi CÓ HOẠT ĐỘNG trong kỳ.
     *
     * TRỘN BỐN NGUỒN Ở TẦNG ỨNG DỤNG, KHÔNG PHẢI BẠN MỘT CÂU SQL:
     * một người có thể nạp tiền mà không cược ván nào, hoặc chỉ được admin cộng
     * tiền mà không làm gì khác. Một câu SQL với bốn {@code LEFT JOIN} lên bốn bảng
     * tổng hợp sẽ nhân bản dòng (fan-out) và làm TỔNG TIỀN SAI — một lỗi âm thầm,
     * không báo gì, và chỉ lộ ra khi ai đó cộng tay để đối chiếu.
     *
     * PHÂN TRANG CŨNG Ở TẦNG ỨNG DỤNG vì lý do trên: không biết tập người chơi
     * cuối cùng gồm những ai cho đến khi đã hợp nhất xong cả bốn nguồn.
     *
     * ĐÁNH ĐỔI ĐƯỢC GHI NHẬN: cách này nạp toàn bộ dòng tổng hợp của kỳ vào bộ
     * nhớ trước khi cắt trang. Số dòng bằng số người hoạt động trong kỳ (không phải
     * số giao dịch), nên với một tháng thì đây là con số chấp nhận được. Nếu sau này
     * sàn lớn tới mức hàng trăm nghìn người hoạt động mỗi tháng, phải chuyển sang
     * một bảng tổng hợp được cập nhật dần thay vì tính lại mỗi lần mở trang.
     *
     * @param sort {@code net} lãi/lỗ tăng dần (người lỗ nặng nhất lên đầu),
     *     {@code stake} tiền cược giảm dần, {@code deposit} tiền vào giảm dần.
     *     Mặc định {@code stake}.
     */
    @Transactional(readOnly = true)
    public LedgerOverviewResponse overview(String month, String keyword, String sort,
                                           int page, int size) {
        ZoneId zone = properties.zone();
        YearMonth ym = parseMonth(month, zone);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();

        // LinkedHashMap để thứ tự gộp ổn định giữa hai lần gọi cùng tham số: với
        // HashMap, hai người có cùng giá trị sắp xếp có thể đảo chỗ nhau và người
        // vận hành thấy dòng "nhảy" khi chuyển trang.
        Map<UUID, Accumulator> byUser = new LinkedHashMap<>();

        for (BetRepository.PlayerAggregate row : betRepository.sumSettledByPlayer(fromInstant, toInstant)) {
            Accumulator acc = byUser.computeIfAbsent(row.getUserId(), k -> new Accumulator());
            acc.betCount = row.getBetCount();
            acc.stake = zeroIfNull(row.getTotalStake());
            // payout ĐÃ GỒM tiền gốc nên lãi thật phải trừ stake.
            acc.net = zeroIfNull(row.getTotalPayout()).subtract(acc.stake);
        }

        for (PaymentOrderRepository.PlayerAmount row
                : orderRepository.sumByPlayerTypeStatus(
                        PaymentType.DEPOSIT, PaymentStatus.SUCCESS, fromInstant, toInstant)) {
            byUser.computeIfAbsent(row.getUserId(), k -> new Accumulator())
                    .deposit = zeroIfNull(row.getTotal());
        }

        for (PaymentOrderRepository.PlayerAmount row
                : orderRepository.sumByPlayerTypeStatus(
                        PaymentType.WITHDRAWAL, PaymentStatus.SETTLED, fromInstant, toInstant)) {
            byUser.computeIfAbsent(row.getUserId(), k -> new Accumulator())
                    .withdrawal = zeroIfNull(row.getTotal());
        }

        for (WalletTransactionRepository.PlayerAdjustment row
                : txnRepository.sumAdjustmentsByPlayer(fromInstant, toInstant)) {
            Accumulator acc = byUser.computeIfAbsent(row.getUserId(), k -> new Accumulator());
            acc.adminCredit = zeroIfNull(row.getTotalCredit());
            acc.adminDebit = zeroIfNull(row.getTotalDebit());
        }

        // TỔNG CỦA TOÀN KỲ, tính TRƯỚC khi lọc và cắt trang: admin làm sổ cần con số
        // của cả kỳ, không phải tổng của 20 dòng đang xem.
        Totals totals = new Totals();
        for (Accumulator acc : byUser.values()) {
            totals.deposit = totals.deposit.add(acc.deposit);
            totals.adminCredit = totals.adminCredit.add(acc.adminCredit);
            totals.adminDebit = totals.adminDebit.add(acc.adminDebit);
            totals.withdrawal = totals.withdrawal.add(acc.withdrawal);
            totals.net = totals.net.add(acc.net);
        }

        if (byUser.isEmpty()) {
            return new LedgerOverviewResponse(from.toString(), to.toString(), properties.timezone(),
                    List.of(), page, size, 0L, 0,
                    plain(totals.deposit.add(totals.adminCredit)),
                    plain(totals.deposit), plain(totals.adminCredit), plain(totals.adminDebit),
                    plain(totals.withdrawal), plain(totals.net));
        }

        // MỘT truy vấn cho toàn bộ tên người dùng và MỘT cho toàn bộ ví, không phải
        // mỗi dòng một lượt — cách kia thành 2N+1 truy vấn cho một lần vẽ bảng.
        List<UUID> ids = List.copyOf(byUser.keySet());
        Map<UUID, User> userById = new HashMap<>();
        for (User u : userRepository.findAllById(ids)) {
            userById.put(u.getId(), u);
        }
        Map<UUID, Wallet> walletByUser = new HashMap<>();
        for (Wallet w : walletRepository.findByUserIdIn(ids)) {
            walletByUser.put(w.getUserId(), w);
        }

        String needle = keyword == null ? "" : keyword.trim().toLowerCase();
        List<LedgerPlayerRowResponse> all = new ArrayList<>();
        for (Map.Entry<UUID, Accumulator> e : byUser.entrySet()) {
            User user = userById.get(e.getKey());
            // TÀI KHOẢN ĐÃ BỊ XOÁ nhưng ledger còn dòng: bỏ qua thay vì hiện dòng
            // không tên, vì không có gì để bấm vào xem chi tiết.
            if (user == null) {
                continue;
            }
            if (!needle.isEmpty() && !user.getUsername().toLowerCase().contains(needle)) {
                continue;
            }

            Accumulator acc = e.getValue();
            Wallet wallet = walletByUser.get(e.getKey());
            all.add(new LedgerPlayerRowResponse(
                    e.getKey().toString(),
                    user.getUsername(),
                    wallet != null ? wallet.getCurrency() : "USD",
                    acc.betCount,
                    plain(acc.stake), plain(acc.net),
                    plain(acc.deposit.add(acc.adminCredit)),
                    plain(acc.deposit), plain(acc.adminCredit), plain(acc.adminDebit),
                    plain(acc.withdrawal),
                    plain(wallet != null ? wallet.getBalance() : BigDecimal.ZERO)));
        }

        all.sort(comparatorFor(sort));

        int safeSize = Math.max(1, size);
        int fromIdx = Math.min(Math.max(0, page) * safeSize, all.size());
        int toIdx = Math.min(fromIdx + safeSize, all.size());
        int totalPages = (int) Math.ceil((double) all.size() / safeSize);

        return new LedgerOverviewResponse(
                from.toString(), to.toString(), properties.timezone(),
                all.subList(fromIdx, toIdx),
                page, safeSize, all.size(), totalPages,
                plain(totals.deposit.add(totals.adminCredit)),
                    plain(totals.deposit), plain(totals.adminCredit), plain(totals.adminDebit),
                plain(totals.withdrawal), plain(totals.net));
    }

    /** Tích luỹ số của một người chơi khi trộn bốn nguồn. */
    private static final class Accumulator {
        long betCount;
        BigDecimal stake = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal deposit = BigDecimal.ZERO;
        BigDecimal adminCredit = BigDecimal.ZERO;
        BigDecimal adminDebit = BigDecimal.ZERO;
        BigDecimal withdrawal = BigDecimal.ZERO;
    }

    /** Tổng của toàn kỳ. */
    private static final class Totals {
        BigDecimal deposit = BigDecimal.ZERO;
        BigDecimal adminCredit = BigDecimal.ZERO;
        BigDecimal adminDebit = BigDecimal.ZERO;
        BigDecimal withdrawal = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
    }

    /**
     * Bộ so sánh cho cột sắp xếp.
     *
     * {@code net} TĂNG DẦN chứ không giảm dần: giá trị âm nhất là người lỗ nhiều
     * nhất, và đó mới là dòng admin muốn thấy đầu tiên khi soát sổ.
     *
     * Giá trị không hợp lệ rơi về {@code stake} thay vì báo lỗi: một tham số sai
     * không đáng làm cả trang báo cáo không mở được.
     */
    private Comparator<LedgerPlayerRowResponse> comparatorFor(String sort) {
        String key = sort == null ? "" : sort.trim().toLowerCase();
        return switch (key) {
            case "net" -> Comparator.comparing(r -> new BigDecimal(r.net()));
            case "deposit" -> Comparator.comparing(
                    (LedgerPlayerRowResponse r) -> new BigDecimal(r.deposit())).reversed();
            default -> Comparator.comparing(
                    (LedgerPlayerRowResponse r) -> new BigDecimal(r.stake())).reversed();
        };
    }

    // ------------------------------------------------------------------
    // Dòng tiền vào / ra
    // ------------------------------------------------------------------

    /**
     * Tiền người chơi TỰ NẠP qua cổng thanh toán, đã hoàn tất.
     *
     * TRẠNG THÁI HOÀN TẤT CỦA NẠP LÀ {@code SUCCESS}, của rút là {@code SETTLED} —
     * hai giá trị khác nhau cho hai chiều. Truyền sai sẽ ra tổng bằng 0 mà không có
     * lỗi nào; {@code AdminDashboardService} đã ghi lại đúng cái bẫy này.
     */
    private BigDecimal gatewayDeposit(UUID userId, Instant from, Instant to) {
        return zeroIfNull(orderRepository.sumAmountByUserTypeStatusInRange(
                userId, PaymentType.DEPOSIT, PaymentStatus.SUCCESS, from, to));
    }

    /** Tiền rút đã chi thành công. Rút hoàn tất là {@code SETTLED}, không phải {@code SUCCESS}. */
    private BigDecimal settledWithdrawal(UUID userId, Instant from, Instant to) {
        return zeroIfNull(orderRepository.sumAmountByUserTypeStatusInRange(
                userId, PaymentType.WITHDRAWAL, PaymentStatus.SETTLED, from, to));
    }

    /**
     * Tiền admin điều chỉnh thủ công.
     *
     * TÁCH KHỎI TIỀN NẠP QUA CỔNG: về kế toán đây là hai loại hoàn toàn khác nhau —
     * một là tiền thật vào hệ thống, một là tiền do admin tạo ra. Trên dữ liệu dev
     * thực tế, tiền admin cộng tay đang gấp gần 6 lần tiền nạp thật; gộp lại sẽ che
     * mất đúng điều mà sổ sách cần thấy nhất.
     *
     * Cộng và trừ cũng tách riêng vì đó là hai nghiệp vụ ngược nhau.
     *
     * @param credit true lấy chiều cộng, false lấy chiều trừ
     */
    private BigDecimal adminAdjustment(Wallet wallet, Instant from, Instant to, boolean credit) {
        if (wallet == null) {
            return BigDecimal.ZERO;
        }
        // wallet_transactions khoá theo wallet_id, KHÔNG phải user_id.
        return zeroIfNull(credit
                ? txnRepository.sumCreditInRange(wallet.getId(), WalletRefType.ADJUSTMENT, from, to)
                : txnRepository.sumDebitInRange(wallet.getId(), WalletRefType.ADJUSTMENT, from, to));
    }

    /**
     * Số dư ngay TRƯỚC thời điểm {@code at}.
     *
     * Dùng cho CẢ số dư đầu kỳ (truyền mốc đầu) và số dư cuối kỳ (truyền mốc cuối) —
     * cùng một phép tính, chỉ khác mốc.
     *
     * LẤY {@code balance_after} CỦA DÒNG LEDGER GẦN NHẤT, không cộng dồn toàn bộ lịch
     * sử: cộng dồn sẽ quét mọi dòng từ đầu đến mốc và chi phí tăng dần theo tuổi tài
     * khoản. {@code balance_after} đã là số dư tích luỹ tại thời điểm đó.
     *
     * KHÔNG DÙNG {@code wallet.balance} CHO SỐ DƯ CUỐI KỲ: đó là số dư HIỆN TẠI. Với
     * kỳ đã đóng (xem lại tháng trước) thì hai con số khác nhau, và lấy số dư hiện tại
     * sẽ làm phép đối chiếu cân đối không bao giờ khớp.
     *
     * Không có dòng nào trước mốc nghĩa là ví chưa từng có giao dịch, tức số dư 0.
     */
    private BigDecimal balanceAt(Wallet wallet, Instant at) {
        if (wallet == null) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> found = txnRepository.findBalanceBefore(
                wallet.getId(), at, PageRequest.of(0, 1));
        return found.isEmpty() ? BigDecimal.ZERO : found.get(0);
    }

    // ------------------------------------------------------------------
    // Thắng / thua theo game
    // ------------------------------------------------------------------

    /**
     * Kết quả tổng hợp theo game kèm các tổng cộng dồn.
     *
     * Trả về qua record thay vì ghi vào trường của service: service là singleton nên
     * trường dùng chung sẽ bị hai yêu cầu đồng thời ghi đè lên nhau.
     */
    private record GameBreakdown(
            List<LedgerGameLineResponse> lines,
            BigDecimal stake,
            BigDecimal payout,
            BigDecimal net,
            BigDecimal pending) {
    }

    private GameBreakdown buildGameLines(UUID userId, Instant from, Instant to) {
        Map<String, BigDecimal> pendingByGame = new HashMap<>();
        for (BetRepository.PendingAggregate row : betRepository.sumPendingStakeByGame(userId, from, to)) {
            pendingByGame.put(row.getGameType(), zeroIfNull(row.getPendingStake()));
        }

        List<LedgerGameLineResponse> lines = new ArrayList<>();
        BigDecimal sumStake = BigDecimal.ZERO;
        BigDecimal sumPayout = BigDecimal.ZERO;
        BigDecimal sumPending = BigDecimal.ZERO;

        for (BetRepository.GameAggregate row : betRepository.sumSettledByGame(userId, from, to)) {
            BigDecimal stake = zeroIfNull(row.getTotalStake());
            BigDecimal payout = zeroIfNull(row.getTotalPayout());
            // payout ĐÃ GỒM TIỀN GỐC (stake-inclusive) nên lãi thật phải trừ stake.
            // Coi payout là tiền lãi sẽ làm mọi con số phồng lên đúng bằng tổng cược.
            BigDecimal net = payout.subtract(stake);
            BigDecimal pending = Optional.ofNullable(pendingByGame.remove(row.getGameType()))
                    .orElse(BigDecimal.ZERO);

            lines.add(new LedgerGameLineResponse(
                    row.getGameType(), row.getBetCount(),
                    plain(stake), plain(payout), plain(net), plain(pending)));

            sumStake = sumStake.add(stake);
            sumPayout = sumPayout.add(payout);
            sumPending = sumPending.add(pending);
        }

        // GAME CHỈ CÓ CƯỢC TREO, CHƯA CÓ VÁN NÀO KẾT TOÁN: vòng lặp trên bỏ sót vì nó
        // đi theo danh sách game đã kết toán. Bỏ qua thì tiền treo biến mất khỏi báo
        // cáo và phép đối chiếu cân đối sẽ lệch đúng bằng số tiền đó.
        for (Map.Entry<String, BigDecimal> leftover : pendingByGame.entrySet()) {
            lines.add(new LedgerGameLineResponse(
                    leftover.getKey(), 0L,
                    plain(BigDecimal.ZERO), plain(BigDecimal.ZERO),
                    plain(BigDecimal.ZERO), plain(leftover.getValue())));
            sumPending = sumPending.add(leftover.getValue());
        }

        return new GameBreakdown(lines, sumStake, sumPayout,
                sumPayout.subtract(sumStake), sumPending);
    }

    /** Chi tiết từng ván của một người chơi tại một loại game trong kỳ (mức 2). */
    @Transactional(readOnly = true)
    public Page<Bet> betsForGame(UUID userId, String gameType, String month, Pageable pageable) {
        ZoneId zone = properties.zone();
        YearMonth ym = parseMonth(month, zone);
        Instant from = ym.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = ym.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant();
        return betRepository.findByUserAndGameTypeInRange(userId, gameType, from, to, pageable);
    }

    // ------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------

    private YearMonth parseMonth(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            return YearMonth.now(zone);
        }
        try {
            return YearMonth.parse(raw.trim());
        } catch (DateTimeParseException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "month"), "validation.report.month.invalid");
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "from"), "validation.report.range.invalid");
        }
        int max = properties.maxRangeDays();
        if (from.plusDays(max).isBefore(to)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "to", "maxDays", String.valueOf(max)),
                    "validation.report.range.too_wide");
        }
    }

    /**
     * Chuỗi thập phân thuần, KHÔNG ký hiệu khoa học.
     *
     * {@code BigDecimal.toString()} cho ra {@code "5E+2"} với một số giá trị, và chuỗi
     * đó vô nghĩa trong báo cáo tiền — cũng làm Excel hiểu sai khi mở tệp CSV.
     * {@code NotificationService.amountParams} đã ghi lại cùng lý do này.
     */
    private String plain(BigDecimal value) {
        return zeroIfNull(value).toPlainString();
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO);
    }
}
