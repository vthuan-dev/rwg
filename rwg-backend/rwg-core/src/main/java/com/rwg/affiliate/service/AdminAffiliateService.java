package com.rwg.affiliate.service;

import com.rwg.affiliate.domain.CommissionSettings;
import com.rwg.affiliate.domain.UserRelation;
import com.rwg.affiliate.dto.CommissionRunResponse;
import com.rwg.affiliate.dto.CommissionRunSummaryResponse;
import com.rwg.affiliate.dto.CommissionSettingsResponse;
import com.rwg.affiliate.dto.DownlineMemberResponse;
import com.rwg.affiliate.dto.UpdateCommissionSettingsRequest;
import com.rwg.affiliate.repository.CommissionRunRepository;
import com.rwg.affiliate.repository.CommissionSettingsRepository;
import com.rwg.affiliate.repository.UserRelationRepository;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Nghiệp vụ quản trị hệ thống đại lý.
 *
 * Nằm dưới /api/v1/admin/** nên phân quyền đã được SecurityConfig enforce tập trung
 * bằng hasRole("ADMIN"); service chỉ lo quy tắc nghiệp vụ.
 */
@Service
public class AdminAffiliateService {

    /** Trần khoảng ngày mặc định khi admin không truyền — chặn quét toàn bảng. */
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final UserRelationRepository relationRepository;
    private final CommissionRunRepository runRepository;
    private final CommissionSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final CommissionJob commissionJob;
    private final AuditTrailService audit;

    public AdminAffiliateService(UserRelationRepository relationRepository,
                                 CommissionRunRepository runRepository,
                                 CommissionSettingsRepository settingsRepository,
                                 UserRepository userRepository,
                                 CommissionJob commissionJob,
                                 AuditTrailService audit) {
        this.relationRepository = relationRepository;
        this.runRepository = runRepository;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.commissionJob = commissionJob;
        this.audit = audit;
    }

    /** Tuyến dưới của một đại lý ở một cấp. */
    @Transactional(readOnly = true)
    public PageResponse<DownlineMemberResponse> downline(UUID agentId, int level, int page, int size) {
        if (level < 1 || level > UserRelation.MAX_LEVEL) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "level"), "validation.commission.level.invalid");
        }
        Page<UserRelation> relations = relationRepository.findByAncestorIdAndLevel(
                agentId, (short) level,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.from(relations, relation -> {
            // Username tra riêng: user_relations chỉ lưu khóa để tránh phụ thuộc
            // vòng giữa module affiliate và identity ở tầng entity.
            String username = userRepository.findById(relation.getDescendantId())
                    .map(User::getUsername)
                    .orElse(null);
            return new DownlineMemberResponse(relation.getDescendantId(), username,
                    relation.getLevel(), relation.getCreatedAt());
        });
    }

    /** Lịch sử chi hoa hồng; from/to dạng ISO yyyy-MM-dd, optional. */
    @Transactional(readOnly = true)
    public PageResponse<CommissionRunResponse> commissions(UUID agentId, String from, String to,
                                                          int page, int size) {
        LocalDate toDate = parseDateOr(to, LocalDate.now(ZoneOffset.UTC));
        LocalDate fromDate = parseDateOr(from, toDate.minusDays(DEFAULT_RANGE_DAYS));
        if (fromDate.isAfter(toDate)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "from"), "validation.commission.range.invalid");
        }
        return PageResponse.from(
                runRepository.searchForAdmin(agentId, fromDate, toDate,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "periodDate"))),
                CommissionRunResponse::from);
    }

    @Transactional(readOnly = true)
    public CommissionSettingsResponse settings() {
        CommissionSettings settings = requireSettings();
        return CommissionSettingsResponse.of(settings.getLevel1Rate(), settings.getLevel2Rate(),
                settings.getUpdatedAt(), settings.getUpdatedBy());
    }

    /**
     * Đổi % hoa hồng. Chỉ ảnh hưởng các đợt chi TỪ NAY: chứng từ cũ đã lưu rate
     * riêng nên số liệu quá khứ không bị viết lại.
     */
    @Transactional
    public CommissionSettingsResponse updateSettings(UpdateCommissionSettingsRequest request,
                                                     UUID adminId, String ip) {
        CommissionSettings settings = requireSettings();
        BigDecimal oldLevel1 = settings.getLevel1Rate();
        BigDecimal oldLevel2 = settings.getLevel2Rate();

        BigDecimal newLevel1 = new BigDecimal(request.level1Rate());
        BigDecimal newLevel2 = new BigDecimal(request.level2Rate());
        settings.update(newLevel1, newLevel2, adminId);
        settingsRepository.saveAndFlush(settings);

        audit.record(adminId, null, AuditTrailService.ADMIN_COMMISSION_RATE_CHANGED,
                "COMMISSION_SETTINGS", String.valueOf(CommissionSettings.SINGLETON_ID),
                Map.of("oldLevel1Rate", oldLevel1.toPlainString(),
                        "newLevel1Rate", newLevel1.toPlainString(),
                        "oldLevel2Rate", oldLevel2.toPlainString(),
                        "newLevel2Rate", newLevel2.toPlainString()), ip);

        return CommissionSettingsResponse.of(settings.getLevel1Rate(), settings.getLevel2Rate(),
                settings.getUpdatedAt(), settings.getUpdatedBy());
    }

    /**
     * Chạy lại đợt chi hoa hồng cho một ngày. An toàn bấm nhiều lần: ngày đã chốt
     * sẽ bị bỏ qua nhờ uq_commission_runs_agent_period_level.
     *
     * TỪ CHỐI ngày hôm nay và tương lai: ngày chưa kết thúc thì turnover chưa đủ,
     * chốt sớm sẽ khóa luôn ngày đó (UNIQUE) và phần cược sau đó không bao giờ
     * được tính — sai sót này không sửa được mà không xoá chứng từ bằng tay.
     */
    @Transactional
    public CommissionRunSummaryResponse triggerRun(String periodDate, UUID adminId, String ip) {
        LocalDate date = parseDateOrThrow(periodDate);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!date.isBefore(today)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "periodDate"), "validation.commission.period.not_finished");
        }
        CommissionJob.RunSummary summary = commissionJob.runForDate(date, adminId);
        return new CommissionRunSummaryResponse(
                summary.periodDate().toString(),
                summary.agentsProcessed(),
                summary.runsCreated(),
                summary.skipped(),
                summary.totalPaid().toPlainString());
    }

    private CommissionSettings requireSettings() {
        return settingsRepository.findById(CommissionSettings.SINGLETON_ID)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Thiếu cấu hình hoa hồng", Map.of(), "error.not_found.commission_settings"));
    }

    private LocalDate parseDateOr(String raw, LocalDate fallback) {
        return raw == null || raw.isBlank() ? fallback : parseDateOrThrow(raw);
    }

    private LocalDate parseDateOrThrow(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "periodDate"), "validation.commission.date.invalid");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "periodDate"), "validation.commission.date.invalid");
        }
    }

    /** Danh sách đại lý (dùng cho gợi ý ở UI admin). */
    @Transactional(readOnly = true)
    public List<UUID> agentIds() {
        return relationRepository.findAllAgentIds();
    }

    /** Tổng hoa hồng đã chi trong khoảng — dashboard dùng lại. */
    @Transactional(readOnly = true)
    public BigDecimal totalCommissionPaid(LocalDate from, LocalDate to) {
        return Optional.ofNullable(runRepository.sumAmountBetween(from, to)).orElse(BigDecimal.ZERO);
    }
}
