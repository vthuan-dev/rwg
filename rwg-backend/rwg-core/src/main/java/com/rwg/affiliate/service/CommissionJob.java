package com.rwg.affiliate.service;

import com.rwg.affiliate.domain.CommissionRun;
import com.rwg.affiliate.domain.CommissionSettings;
import com.rwg.affiliate.domain.UserRelation;
import com.rwg.affiliate.repository.CommissionRunRepository;
import com.rwg.affiliate.repository.CommissionSettingsRepository;
import com.rwg.affiliate.repository.UserRelationRepository;
import com.rwg.common.money.Money;
import com.rwg.game.repository.BetRepository;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.risk.service.CommissionRiskGuard;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chi hoa hồng đại lý theo NGÀY (Phase 2).
 *
 * ===== AN TOÀN TIỀN — ĐỌC TRƯỚC KHI SỬA =====
 * Service này TỰ ĐỘNG CỘNG TIỀN THẬT vào ví đại lý. Có 3 lớp chống chi trùng:
 *
 * 1. {@code uq_commission_runs_agent_period_level} (DB): mỗi đại lý chỉ có 1 chứng
 *    từ cho mỗi (ngày, cấp). Hai job song song -> chỉ 1 insert thắng.
 * 2. {@code idempotencyKey = COMMISSION:{agentId}:{date}:L{level}} truyền vào
 *    {@link WalletService#credit}: tận dụng bảng wallet_ledger_guard (PK thuần)
 *    nên dù chứng từ có bị xoá tay thì tiền vẫn không cộng lần hai.
 * 3. Chỉ chốt NGÀY ĐÃ KẾT THÚC (mặc định hôm qua theo UTC) — không chốt ngày đang
 *    chạy để tránh cộng thiếu rồi phải bù.
 *
 * MỖI ĐẠI LÝ MỘT TRANSACTION RIÊNG (REQUIRES_NEW trong {@link #payOneAgentLevel}):
 * một đại lý lỗi thì các đại lý còn lại vẫn được chi. Nếu gói tất cả vào 1
 * transaction, một lỗi nhỏ sẽ rollback toàn bộ đợt chi và ngày đó không ai nhận.
 *
 * Turnover CHỈ tính cược SETTLED (xem BetRepository.sumSettledTurnoverByUsers).
 *
 * ===== CHẶN ĐA TÀI KHOẢN (chặng 7) =====
 * Tuyến dưới bị xác định là CHÍNH đại lý sẽ bị loại khỏi cơ sở tính hoa hồng.
 * Turnover của họ là cược thật, nhưng trả hoa hồng cho nó là trả tiền cho chính
 * người đã cược — thiệt hại thuần, không phải chi phí marketing.
 */
@Service
public class CommissionJob {

    private static final Logger log = LoggerFactory.getLogger(CommissionJob.class);

    private final UserRelationRepository relationRepository;
    private final CommissionRunRepository runRepository;
    private final CommissionSettingsRepository settingsRepository;
    private final BetRepository betRepository;
    private final WalletService walletService;
    private final AuditTrailService audit;
    /**
     * Cổng chặn đa tài khoản — TÙY CHỌN qua ObjectProvider: module affiliate KHÔNG
     * phụ thuộc cứng vào risk (cùng tiền lệ AuthService dùng ObjectProvider cho
     * ReferralService). App nào không quét com.rwg.risk thì job chạy như cũ.
     */
    private final ObjectProvider<CommissionRiskGuard> riskGuardProvider;

    public CommissionJob(UserRelationRepository relationRepository,
                         CommissionRunRepository runRepository,
                         CommissionSettingsRepository settingsRepository,
                         BetRepository betRepository,
                         WalletService walletService,
                         AuditTrailService audit,
                         ObjectProvider<CommissionRiskGuard> riskGuardProvider) {
        this.relationRepository = relationRepository;
        this.runRepository = runRepository;
        this.settingsRepository = settingsRepository;
        this.betRepository = betRepository;
        this.walletService = walletService;
        this.audit = audit;
        this.riskGuardProvider = riskGuardProvider;
    }

    /** Kết quả một đợt chi — trả về cho API admin và để log/kiểm thử. */
    public record RunSummary(LocalDate periodDate, int agentsProcessed, int runsCreated,
                             int skipped, BigDecimal totalPaid) {
    }

    /**
     * Chốt hoa hồng cho một ngày (UTC). An toàn khi gọi lại nhiều lần cho cùng
     * ngày: lần sau sẽ bỏ qua toàn bộ vì đã có chứng từ.
     */
    public RunSummary runForDate(LocalDate periodDate, UUID triggeredByAdminId) {
        CommissionSettings settings = settings();
        Instant from = periodDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = periodDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<UUID> agentIds = relationRepository.findAllAgentIds();
        int runsCreated = 0;
        int skipped = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (UUID agentId : agentIds) {
            for (int level = 1; level <= UserRelation.MAX_LEVEL; level++) {
                BigDecimal rate = settings.rateForLevel(level);
                if (rate.signum() <= 0) {
                    continue; // cấp này bị tắt (rate = 0)
                }
                List<UUID> descendants = relationRepository.findDescendantIds(agentId, (short) level);
                if (descendants.isEmpty()) {
                    continue;
                }
                // Loại tuyến dưới bị xác định là CHÍNH đại lý. Phải lọc Ở ĐÂY, trước khi
                // cộng turnover — nếu chỉ trừ tiền ở bước cuối thì chứng từ vẫn ghi
                // turnover gồm cả phần bị loại, khiến người đối soát sau không hiểu
                // vì sao amount không bằng turnover * rate.
                CommissionRiskGuard riskGuard = riskGuardProvider.getIfAvailable();
                if (riskGuard != null) {
                    descendants = riskGuard.excludeLinked(agentId, descendants);
                    if (descendants.isEmpty()) {
                        continue;
                    }
                }
                BigDecimal turnover = totalTurnover(descendants, from, to);
                CommissionCalculator.Result result =
                        CommissionCalculator.calculate(agentId, level, turnover, rate);
                if (!result.payable()) {
                    continue;
                }
                try {
                    boolean paid = payOneAgentLevel(result, periodDate);
                    if (paid) {
                        runsCreated++;
                        totalPaid = totalPaid.add(result.amount());
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException failure) {
                    // Một đại lý lỗi KHÔNG được làm chết cả đợt chi.
                    skipped++;
                    log.error("Chi hoa hồng thất bại: agent={} level={} date={}",
                            agentId, level, periodDate, failure);
                }
            }
        }

        log.info("Đợt hoa hồng {}: {} đại lý, {} chứng từ, {} bỏ qua, tổng chi {}",
                periodDate, agentIds.size(), runsCreated, skipped, totalPaid.toPlainString());

        if (triggeredByAdminId != null) {
            audit.record(triggeredByAdminId, null, AuditTrailService.ADMIN_COMMISSION_RUN_TRIGGERED,
                    "COMMISSION_RUN", periodDate.toString(),
                    Map.of("runsCreated", String.valueOf(runsCreated),
                            "skipped", String.valueOf(skipped),
                            "totalPaid", totalPaid.toPlainString()), null);
        }
        return new RunSummary(periodDate, agentIds.size(), runsCreated, skipped, totalPaid);
    }

    /**
     * Chi cho MỘT (đại lý, cấp) trong transaction RIÊNG.
     *
     * Thứ tự bắt buộc: ghi chứng từ TRƯỚC (để UNIQUE chặn ngay nếu đã chi), rồi
     * mới credit ví. Nếu credit lỗi thì cả hai rollback cùng nhau — không bao giờ
     * có chứng từ mà không có tiền, hoặc có tiền mà không có chứng từ.
     *
     * @return false nếu đã chi trước đó (idempotent no-op)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean payOneAgentLevel(CommissionCalculator.Result result, LocalDate periodDate) {
        short level = (short) result.level();
        // Fast-path; chốt thật vẫn là UNIQUE ở DB bên dưới.
        if (runRepository.existsByAgentIdAndPeriodDateAndLevel(result.agentId(), periodDate, level)) {
            return false;
        }
        String idempotencyKey =
                CommissionRun.idempotencyKeyFor(result.agentId(), periodDate, result.level());
        try {
            runRepository.saveAndFlush(new CommissionRun(
                    result.agentId(), periodDate, result.level(),
                    result.turnover(), result.rate(), result.amount(), idempotencyKey));
        } catch (DataIntegrityViolationException alreadyPaid) {
            // Thua race với job/instance khác -> đã có người chi, không chi nữa.
            return false;
        }

        walletService.credit(result.agentId(), Money.of(result.amount()),
                WalletRefType.COMMISSION, periodDate.toString(), idempotencyKey);

        audit.record(result.agentId(), null, AuditTrailService.COMMISSION_PAID,
                "COMMISSION_RUN", periodDate.toString(),
                Map.of("level", String.valueOf(result.level()),
                        "turnover", result.turnover().toPlainString(),
                        "rate", result.rate().toPlainString(),
                        "amount", result.amount().toPlainString()), null);
        return true;
    }

    /** Tổng turnover của toàn bộ tuyến dưới trong khoảng. */
    private BigDecimal totalTurnover(List<UUID> descendantIds, Instant from, Instant to) {
        Map<UUID, BigDecimal> perUser = new HashMap<>();
        for (Object[] row : betRepository.sumSettledTurnoverByUsers(descendantIds, from, to)) {
            perUser.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return perUser.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CommissionSettings settings() {
        return settingsRepository.findById(CommissionSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Thiếu dòng commission_settings id=1 (xem migration V20260820_09)"));
    }
}
