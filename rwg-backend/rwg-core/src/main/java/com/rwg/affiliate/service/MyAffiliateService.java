package com.rwg.affiliate.service;

import com.rwg.affiliate.domain.CommissionRun;
import com.rwg.affiliate.domain.CommissionSettings;
import com.rwg.affiliate.domain.UserRelation;
import com.rwg.affiliate.dto.MyAffiliateSummaryResponse;
import com.rwg.affiliate.dto.MyCommissionResponse;
import com.rwg.affiliate.dto.MyDownlineMemberResponse;
import com.rwg.affiliate.dto.ReferralCodeResponse;
import com.rwg.affiliate.repository.CommissionRunRepository;
import com.rwg.affiliate.repository.CommissionSettingsRepository;
import com.rwg.affiliate.repository.UserRelationRepository;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API đại lý dành cho CHÍNH NGƯỜI CHƠI (chặng 6).
 *
 * VÌ SAO CẦN: Phase 2 đã dựng đủ mã giới thiệu, cây quan hệ 2 cấp và job chi hoa
 * hồng, nhưng chỉ làm API cho admin. Người chơi KHÔNG có cách nào lấy mã giới thiệu
 * của mình, nên trên thực tế không ai giới thiệu được ai — toàn bộ hệ hoa hồng chờ
 * dữ liệu không bao giờ phát sinh. Lớp này mở đường đó ra.
 *
 * NGUYÊN TẮC RIÊNG TƯ (khác hẳn phiên bản admin, đừng "hợp nhất" hai lớp làm một):
 * - Mọi truy vấn đều khoá theo userId lấy từ JWT, KHÔNG nhận id từ tham số. Nhờ vậy
 *   không tồn tại đường để người này xem dữ liệu người khác.
 * - Username tuyến dưới bị CHE, không trả userId, không trả turnover. Đại lý cần biết
 *   mình có bao nhiêu tuyến dưới và nhận bao nhiêu tiền, không cần danh tính và mức
 *   cược của người khác.
 *
 * Quy ước bắt buộc: xem DECISIONS.md ở root repository.
 */
@Service
public class MyAffiliateService {

    /** Trần khoảng ngày mặc định khi người chơi không truyền — chặn quét toàn bảng. */
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final ReferralService referralService;
    private final UserRelationRepository relationRepository;
    private final CommissionRunRepository runRepository;
    private final CommissionSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    public MyAffiliateService(ReferralService referralService,
                              UserRelationRepository relationRepository,
                              CommissionRunRepository runRepository,
                              CommissionSettingsRepository settingsRepository,
                              UserRepository userRepository) {
        this.referralService = referralService;
        this.relationRepository = relationRepository;
        this.runRepository = runRepository;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
    }

    /**
     * Mã giới thiệu của tôi. Sinh lazy ở lần gọi đầu — không sinh sẵn cho toàn bộ
     * user lúc đăng ký vì phần lớn người chơi không bao giờ làm đại lý.
     */
    @Transactional
    public ReferralCodeResponse myCode(UUID userId) {
        return ReferralCodeResponse.of(referralService.getOrCreateCode(userId).getCode());
    }

    /** Tổng quan: mã, số tuyến dưới mỗi cấp, tổng hoa hồng đã nhận, % hiện hành. */
    @Transactional
    public MyAffiliateSummaryResponse mySummary(UUID userId) {
        String code = referralService.getOrCreateCode(userId).getCode();
        CommissionSettings settings = requireSettings();

        // Tổng hoa hồng TỪ ĐẦU: cộng trên chứng từ commission_runs thay vì lọc ledger
        // theo refType, vì chứng từ là nguồn sự thật của nghiệp vụ hoa hồng.
        BigDecimal earned = Optional.ofNullable(
                runRepository.sumAmountByAgent(userId)).orElse(BigDecimal.ZERO);

        return new MyAffiliateSummaryResponse(
                code,
                relationRepository.countByAncestorIdAndLevel(userId, (short) 1),
                relationRepository.countByAncestorIdAndLevel(userId, (short) 2),
                earned.toPlainString(),
                settings.getLevel1Rate().toPlainString(),
                settings.getLevel2Rate().toPlainString());
    }

    /** Tuyến dưới của tôi ở một cấp — username bị che. */
    @Transactional(readOnly = true)
    public PageResponse<MyDownlineMemberResponse> myDownline(UUID userId, int level,
                                                            int page, int size) {
        if (level < 1 || level > UserRelation.MAX_LEVEL) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "level"), "validation.commission.level.invalid");
        }
        Page<UserRelation> relations = relationRepository.findByAncestorIdAndLevel(
                userId, (short) level,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.from(relations, relation -> {
            String username = userRepository.findById(relation.getDescendantId())
                    .map(User::getUsername)
                    .orElse(null);
            return new MyDownlineMemberResponse(
                    MyDownlineMemberResponse.mask(username),
                    relation.getLevel(),
                    relation.getCreatedAt());
        });
    }

    /** Hoa hồng của tôi; mặc định 30 ngày gần nhất. */
    @Transactional(readOnly = true)
    public PageResponse<MyCommissionResponse> myCommissions(UUID userId, String from, String to,
                                                           int page, int size) {
        LocalDate toDate = parseDateOr(to, LocalDate.now(ZoneOffset.UTC));
        LocalDate fromDate = parseDateOr(from, toDate.minusDays(DEFAULT_RANGE_DAYS));
        if (fromDate.isAfter(toDate)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "from"), "validation.commission.range.invalid");
        }
        // agentId LUÔN là userId từ JWT -> không có đường xem hoa hồng người khác.
        Page<CommissionRun> runs = runRepository.searchForAdmin(userId, fromDate, toDate,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "periodDate")));
        return PageResponse.from(runs, MyCommissionResponse::from);
    }

    private CommissionSettings requireSettings() {
        return settingsRepository.findById(CommissionSettings.SINGLETON_ID)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Thiếu cấu hình hoa hồng", Map.of(), "error.not_found.commission_settings"));
    }

    private LocalDate parseDateOr(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "from"), "validation.commission.date.invalid");
        }
    }
}
