package com.rwg.risk.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.risk.domain.AccountLink;
import com.rwg.risk.domain.AccountLinkStatus;
import com.rwg.risk.domain.AccountLinkType;
import com.rwg.risk.domain.AccountSignal;
import com.rwg.risk.dto.AccountLinkResponse;
import com.rwg.risk.dto.CreateAccountLinkRequest;
import com.rwg.risk.dto.ReviewAccountLinkRequest;
import com.rwg.risk.dto.UserRiskProfileResponse;
import com.rwg.risk.repository.AccountLinkRepository;
import com.rwg.risk.repository.AccountSignalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Khu quản trị risk: xem hàng đợi liên kết, kết luận, và tự nối tay (chặng 7).
 *
 * Vai trò RISK được GHI dữ liệu ở đây — trước chặng này họ chỉ đọc báo cáo. Phù hợp
 * vì đánh giá gian lận đúng là việc của họ, và các thao tác này KHÔNG chuyển một đồng
 * nào nên không thuộc nhóm cần quy trình 4 mắt.
 *
 * Quy ước bắt buộc: xem DECISIONS.md ở root repository.
 */
@Service
public class AdminRiskService {

    private final AccountLinkRepository linkRepository;
    private final AccountSignalRepository signalRepository;
    private final UserRepository userRepository;
    private final AuditTrailService audit;

    public AdminRiskService(AccountLinkRepository linkRepository,
                            AccountSignalRepository signalRepository,
                            UserRepository userRepository,
                            AuditTrailService audit) {
        this.linkRepository = linkRepository;
        this.signalRepository = signalRepository;
        this.userRepository = userRepository;
        this.audit = audit;
    }

    /** Hàng đợi liên kết; lọc theo trạng thái nếu truyền. */
    @Transactional(readOnly = true)
    public PageResponse<AccountLinkResponse> listLinks(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AccountLink> links = (status == null || status.isBlank())
                ? linkRepository.findAll(pageable)
                : linkRepository.findByStatus(parseStatus(status), pageable);

        // Nạp username theo LÔ: nếu tra từng dòng thì một trang 20 liên kết sinh 40
        // truy vấn users.
        Map<UUID, String> usernames = loadUsernames(links.getContent());
        return PageResponse.from(links, link -> AccountLinkResponse.from(link,
                usernames.get(link.getUserAId()), usernames.get(link.getUserBId())));
    }

    /** Hồ sơ risk của một user: dấu vết đăng ký + toàn bộ liên kết. */
    @Transactional(readOnly = true)
    public UserRiskProfileResponse userProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        Optional<AccountSignal> signal = signalRepository.findById(userId);
        List<AccountLink> links = linkRepository.findAllForUser(userId);
        Map<UUID, String> usernames = loadUsernames(links);

        return new UserRiskProfileResponse(
                userId.toString(),
                user.getUsername(),
                signal.map(AccountSignal::getRegistrationIp).orElse(null),
                signal.map(s -> s.getDeviceFingerprint() != null).orElse(false),
                signal.map(AccountSignal::getCreatedAt).orElse(null),
                links.stream().anyMatch(AccountLink::blocksCommission),
                links.stream()
                        .map(link -> AccountLinkResponse.from(link,
                                usernames.get(link.getUserAId()),
                                usernames.get(link.getUserBId())))
                        .toList());
    }

    /** Kết luận về một liên kết. */
    @Transactional
    public AccountLinkResponse review(UUID linkId, ReviewAccountLinkRequest request,
                                      UUID adminId, String ip) {
        AccountLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), Map.of(),
                        "error.not_found.account_link"));

        AccountLinkStatus decision = parseStatus(request.status());
        AccountLinkStatus previous = link.getStatus();
        if (previous == decision) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    ErrorCode.INVALID_STATUS_TRANSITION.defaultMessage(),
                    Map.of("status", decision.name()),
                    "error.invalid_status_transition.link_same_status");
        }

        link.review(decision, adminId, request.note());
        linkRepository.saveAndFlush(link);

        audit.record(adminId, null, AuditTrailService.RISK_ACCOUNT_LINK_REVIEWED,
                "ACCOUNT_LINK", linkId.toString(),
                Map.of("oldStatus", previous.name(),
                        "newStatus", decision.name(),
                        "userA", link.getUserAId().toString(),
                        "userB", link.getUserBId().toString(),
                        "note", request.note()), ip);

        Map<UUID, String> usernames = loadUsernames(List.of(link));
        return AccountLinkResponse.from(link,
                usernames.get(link.getUserAId()), usernames.get(link.getUserBId()));
    }

    /**
     * Nối tay hai tài khoản. Tạo với {@code MANUAL} + {@code CONFIRMED} luôn: người
     * vận hành đã điều tra rồi mới nối, không cần khâu xem lại — nên nó giữ tiền ngay
     * từ kỳ hoa hồng kế tiếp.
     */
    @Transactional
    public AccountLinkResponse createManual(CreateAccountLinkRequest request,
                                            UUID adminId, String ip) {
        UUID userA = parseUuid(request.userAId(), "userAId");
        UUID userB = parseUuid(request.userBId(), "userBId");

        requireUserExists(userA);
        requireUserExists(userB);

        AccountLink link;
        try {
            link = AccountLink.of(userA, userB, AccountLinkType.MANUAL,
                    "{\"source\":\"MANUAL\"}");
        } catch (IllegalArgumentException sameUser) {
            // AccountLink.of chặn tự liên kết chính mình; đổi sang lỗi API có i18n.
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "userBId"), "validation.risk.link.self");
        }

        // Cặp đã có liên kết -> không tạo thêm dòng thứ hai (UNIQUE ở DB cũng chặn),
        // và KHÔNG ghi đè kết luận cũ của người khác.
        if (linkRepository.findByUserAIdAndUserBId(link.getUserAId(), link.getUserBId())
                .isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    ErrorCode.CONFLICT.defaultMessage(), Map.of(),
                    "error.conflict.account_link");
        }

        link.review(AccountLinkStatus.CONFIRMED, adminId, request.note());
        linkRepository.saveAndFlush(link);

        audit.record(adminId, null, AuditTrailService.RISK_ACCOUNT_LINK_CREATED,
                "ACCOUNT_LINK", link.getId().toString(),
                Map.of("userA", link.getUserAId().toString(),
                        "userB", link.getUserBId().toString(),
                        "note", request.note()), ip);

        Map<UUID, String> usernames = loadUsernames(List.of(link));
        return AccountLinkResponse.from(link,
                usernames.get(link.getUserAId()), usernames.get(link.getUserBId()));
    }

    private void requireUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    /** Nạp username cho mọi user xuất hiện trong danh sách liên kết, một truy vấn. */
    private Map<UUID, String> loadUsernames(List<AccountLink> links) {
        Map<UUID, String> result = new HashMap<>();
        if (links.isEmpty()) {
            return result;
        }
        List<UUID> ids = links.stream()
                .flatMap(link -> List.of(link.getUserAId(), link.getUserBId()).stream())
                .distinct()
                .toList();
        for (User user : userRepository.findAllById(ids)) {
            result.put(user.getId(), user.getUsername());
        }
        return result;
    }

    private AccountLinkStatus parseStatus(String raw) {
        try {
            return AccountLinkStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", "status"), "validation.risk.status.invalid");
        }
    }

    private UUID parseUuid(String raw, String field) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    Map.of("field", field), "validation.risk.uuid.invalid");
        }
    }
}
