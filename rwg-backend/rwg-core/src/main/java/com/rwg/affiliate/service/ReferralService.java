package com.rwg.affiliate.service;

import com.rwg.affiliate.domain.ReferralCode;
import com.rwg.affiliate.domain.UserRelation;
import com.rwg.affiliate.repository.ReferralCodeRepository;
import com.rwg.affiliate.repository.UserRelationRepository;
import com.rwg.identity.service.AuditTrailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Nghiệp vụ mã giới thiệu và cây quan hệ đại lý (Phase 2).
 *
 * Quan hệ lưu PHẲNG tối đa 2 cấp: khi B đăng ký bằng mã của A, sinh
 * (A -> B, level 1) và nếu A cũng có tuyến trên X thì thêm (X -> B, level 2).
 * Không có cấp 3 — đúng giới hạn đặc tả để job hoa hồng không cần đệ quy.
 *
 * CÁC TRƯỜNG HỢP BỊ TỪ CHỐI (quan trọng, tránh trục lợi / treo job):
 * - Tự giới thiệu chính mình.
 * - Người giới thiệu đang là tuyến DƯỚI của người được giới thiệu (tạo vòng lặp:
 *   hai bên thành tuyến trên của nhau -> tự trả hoa hồng chéo).
 * - User đã có tuyến trên (uq_user_relations_descendant_level chặn ở DB).
 *
 * TRIẾT LÝ XỬ LÝ LỖI: mã giới thiệu sai KHÔNG làm đăng ký thất bại — người dùng
 * không nên bị chặn tạo tài khoản vì gõ sai mã của người khác. Nhưng mọi lần bỏ
 * qua đều được audit để điều tra được về sau.
 */
@Service
public class ReferralService {

    /** Bộ ký tự bỏ các ký tự dễ đọc lẫn (0/O, 1/I/L) để user đọc mã qua điện thoại không sai. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int MAX_GENERATE_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    private final ReferralCodeRepository codeRepository;
    private final UserRelationRepository relationRepository;
    private final AuditTrailService audit;
    private final SecureRandom random = new SecureRandom();

    public ReferralService(ReferralCodeRepository codeRepository,
                           UserRelationRepository relationRepository,
                           AuditTrailService audit) {
        this.codeRepository = codeRepository;
        this.relationRepository = relationRepository;
        this.audit = audit;
    }

    /**
     * Lấy mã giới thiệu của user, sinh mới nếu chưa có (lazy, idempotent).
     * Thua race UNIQUE -> đọc lại mã do luồng kia vừa tạo.
     */
    @Transactional
    public ReferralCode getOrCreateCode(UUID userId) {
        Optional<ReferralCode> existing = codeRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (codeRepository.existsByCode(code)) {
                continue;
            }
            try {
                return codeRepository.saveAndFlush(new ReferralCode(code, userId));
            } catch (DataIntegrityViolationException race) {
                // Trùng code hoặc user đã có mã do luồng khác tạo -> thử lại/đọc lại.
                Optional<ReferralCode> created = codeRepository.findByUserId(userId);
                if (created.isPresent()) {
                    return created.get();
                }
            }
        }
        throw new IllegalStateException("Không sinh được mã giới thiệu duy nhất sau "
                + MAX_GENERATE_ATTEMPTS + " lần thử");
    }

    /**
     * Gắn quan hệ giới thiệu cho user mới đăng ký. Gọi TRONG transaction đăng ký.
     *
     * @param rawCode mã người dùng nhập (có thể null/rỗng/sai — đều không chặn đăng ký)
     * @return true nếu đã gắn được quan hệ
     */
    @Transactional
    public boolean attachReferral(UUID newUserId, String rawCode, String ip) {
        if (rawCode == null || rawCode.isBlank()) {
            return false;
        }
        String code = rawCode.trim().toUpperCase();

        Optional<ReferralCode> found = codeRepository.findById(code);
        if (found.isEmpty()) {
            auditSkip(newUserId, code, "code_not_found", ip);
            return false;
        }
        UUID referrerId = found.get().getUserId();

        if (referrerId.equals(newUserId)) {
            auditSkip(newUserId, code, "self_referral", ip);
            return false;
        }
        // Chặn vòng lặp: người giới thiệu đang là tuyến dưới của user mới.
        if (relationRepository.existsByAncestorIdAndDescendantId(newUserId, referrerId)) {
            auditSkip(newUserId, code, "cycle_detected", ip);
            return false;
        }
        if (!relationRepository.findByDescendantId(newUserId).isEmpty()) {
            auditSkip(newUserId, code, "already_has_upline", ip);
            return false;
        }

        try {
            relationRepository.saveAndFlush(new UserRelation(referrerId, newUserId, 1));

            // Cấp 2: tuyến trên cấp 1 của người giới thiệu (nếu có).
            relationRepository.findByDescendantId(referrerId).stream()
                    .filter(r -> r.getLevel() == 1)
                    .findFirst()
                    .ifPresent(upline -> {
                        if (!upline.getAncestorId().equals(newUserId)) {
                            relationRepository.saveAndFlush(
                                    new UserRelation(upline.getAncestorId(), newUserId, 2));
                        }
                    });
        } catch (DataIntegrityViolationException duplicate) {
            // Luồng khác đã gắn tuyến trên cho user này -> coi như xong, không chặn đăng ký.
            log.warn("Quan hệ giới thiệu đã tồn tại cho user {} (bỏ qua)", newUserId);
            return false;
        }

        audit.record(newUserId, null, AuditTrailService.REFERRAL_ATTACHED,
                "USER", newUserId.toString(),
                Map.of("referrerId", referrerId.toString(), "code", code), ip);
        return true;
    }

    /** Tuyến trên của user (dùng cho màn chi tiết admin). */
    @Transactional(readOnly = true)
    public List<UserRelation> uplineOf(UUID userId) {
        return relationRepository.findByDescendantId(userId);
    }

    private void auditSkip(UUID newUserId, String code, String reason, String ip) {
        audit.record(newUserId, null, AuditTrailService.REFERRAL_SKIPPED,
                "USER", newUserId.toString(),
                Map.of("code", code, "reason", reason), ip);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }
}
