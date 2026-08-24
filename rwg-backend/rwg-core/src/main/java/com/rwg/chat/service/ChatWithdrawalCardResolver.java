package com.rwg.chat.service;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.repository.BankAccountRepository;
import com.rwg.chat.dto.ChatWithdrawalCardResponse;
import com.rwg.identity.domain.AuditLog;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.payment.domain.PaymentOrder;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.repository.PaymentOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dựng dữ liệu cho thẻ duyệt lệnh rút hiển thị trong luồng chat quản trị.
 *
 * TÁCH KHỎI {@link AdminChatService} vì lớp đó đã đủ dài và việc này cần bốn repository
 * mà nghiệp vụ chat không dùng đến ở bất kỳ đâu khác.
 *
 * MỌI THỨ NẠP THEO LÔ. Một trang lịch sử chat có thể chứa nhiều thẻ; tra từng thẻ nghĩa
 * là mỗi thẻ thành ba lượt gọi DB (lệnh, tài khoản ngân hàng, nhật ký) trên màn hình được
 * mở lại mỗi lần nhân sự chuyển luồng. Đây là cùng lý do với {@code usernamesFor} trong
 * {@code AdminChatService} và {@code loadDecisions} trong {@code AdminPaymentQueryService}.
 *
 * ĐỌC TRẠNG THÁI TỪ BẢNG LỆNH, không từ tin nhắn: tin nhắn chỉ giữ mã lệnh. Nhờ vậy một
 * lệnh được duyệt ở trang "Duyệt Nạp & Rút Tiền" thì thẻ trong chat cũng đổi theo, thay vì
 * hiện "chờ duyệt" vĩnh viễn và dụ người vận hành bấm duyệt lần thứ hai.
 */
@Service
public class ChatWithdrawalCardResolver {

    private static final Logger log = LoggerFactory.getLogger(ChatWithdrawalCardResolver.class);

    /**
     * Hai hành động ghi lại quyết định của nhân sự trên một lệnh rút.
     *
     * Giống {@code AdminPaymentQueryService.DECISION_ACTIONS}: bảng lệnh chỉ lưu trạng thái
     * cuối chứ không lưu ai đã bấm và vì sao, nên thông tin đó phải lấy từ nhật ký — nơi nó
     * không sửa được, vì audit_log là append-only.
     */
    private static final Set<String> DECISION_ACTIONS = Set.of(
            AuditTrailService.WITHDRAWAL_APPROVED,
            AuditTrailService.WITHDRAWAL_REJECTED);

    private final PaymentOrderRepository orderRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ChatWithdrawalCardResolver(PaymentOrderRepository orderRepository,
                                      BankAccountRepository bankAccountRepository,
                                      AuditLogRepository auditLogRepository,
                                      UserRepository userRepository,
                                      ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Thẻ cho một tập mã lệnh, khoá theo mã.
     *
     * Mã không tìm thấy sẽ KHÔNG xuất hiện trong map trả về; nơi gọi để trường thẻ null và
     * giao diện vẽ tin đó như một dòng hệ thống thường. Ném lỗi ở đây sẽ khiến một bản ghi
     * lệnh bị dọn đi làm sập cả lịch sử hội thoại.
     */
    public Map<UUID, ChatWithdrawalCardResponse> cardsFor(Set<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }

        List<PaymentOrder> orders = orderRepository.findByIdIn(orderIds);
        if (orders.isEmpty()) {
            return Map.of();
        }

        Map<UUID, BankAccount> banks = loadBankAccounts(orders);
        Map<String, Decision> decisions = loadDecisions(orders);

        return orders.stream().collect(Collectors.toMap(
                PaymentOrder::getId,
                order -> toCard(order, banks, decisions)));
    }

    /**
     * Tài khoản nhận tiền của các lệnh, nạp một lượt.
     *
     * Lấy theo {@code bankAccountId} CỦA CHÍNH LỆNH, không phải tài khoản đang đặt mặc định
     * của người chơi: họ có thể đổi mặc định sau khi gửi lệnh, và hiện theo mặc định sẽ làm
     * nhân sự chuyển tiền đi sai chỗ.
     *
     * Lệnh có {@code bankAccountId} null vẫn hợp lệ (lệnh cũ tạo trước khi hệ thống bắt buộc
     * chọn tài khoản), nên phải lọc null trước khi truy vấn.
     */
    private Map<UUID, BankAccount> loadBankAccounts(List<PaymentOrder> orders) {
        Set<UUID> bankIds = orders.stream()
                .map(PaymentOrder::getBankAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (bankIds.isEmpty()) {
            return Map.of();
        }
        return bankAccountRepository.findByIdIn(bankIds).stream()
                .collect(Collectors.toMap(BankAccount::getId, Function.identity()));
    }

    /** Quyết định của nhân sự trên một lệnh, trích từ một dòng nhật ký. */
    private record Decision(String adminUsername, String note) {
    }

    /**
     * Nhật ký duyệt/từ chối của các lệnh, khoá theo mã lệnh.
     *
     * Lệnh còn PENDING chắc chắn chưa có quyết định nên không đưa vào mệnh đề IN — truy vấn
     * chỉ hỏi những mã thật sự có khả năng khớp.
     */
    private Map<String, Decision> loadDecisions(List<PaymentOrder> orders) {
        Set<String> decidedIds = orders.stream()
                .filter(order -> order.getStatus() != PaymentStatus.PENDING)
                .map(order -> order.getId().toString())
                .collect(Collectors.toCollection(HashSet::new));
        if (decidedIds.isEmpty()) {
            return Map.of();
        }

        List<AuditLog> logs = auditLogRepository.findByActionInAndTargetIdIn(DECISION_ACTIONS, decidedIds);
        if (logs.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> adminNames = loadActorUsernames(logs);

        return logs.stream().collect(Collectors.toMap(
                AuditLog::getTargetId,
                entry -> new Decision(resolveActorName(entry, adminNames), extractNote(entry.getDetails())),
                // Một lệnh về nguyên tắc chỉ được quyết định một lần, nhưng nhật ký là
                // append-only nên nếu có hai dòng (sự cố ghi lặp) thì giữ dòng MỚI NHẤT —
                // đó là trạng thái thực tế cuối cùng.
                (older, newer) -> newer));
    }

    /**
     * Tên nhân sự đứng sau mỗi dòng nhật ký.
     *
     * Cần truy vấn riêng vì các dòng cũ có {@code actor_username} null — cột đó chỉ được ghi
     * từ một chặng sau. Không suy ra được tên thì để trống, còn hơn hiện UUID trần.
     */
    private Map<UUID, String> loadActorUsernames(List<AuditLog> logs) {
        Set<UUID> actorIds = logs.stream()
                .filter(entry -> entry.getActorUsername() == null || entry.getActorUsername().isBlank())
                .map(AuditLog::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private String resolveActorName(AuditLog entry, Map<UUID, String> adminNames) {
        if (entry.getActorUsername() != null && !entry.getActorUsername().isBlank()) {
            return entry.getActorUsername();
        }
        return entry.getActorId() == null ? null : adminNames.get(entry.getActorId());
    }

    /**
     * Lý do quyết định, đọc từ chuỗi JSON trong cột details.
     *
     * Không parse được thì trả null thay vì ném lỗi: một dòng nhật ký dị dạng không được phép
     * làm sập cả luồng hội thoại.
     */
    private String extractNote(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(details);
            // H2's JSON column returns the value as a JSON-encoded string literal
            // (outer-quoted) when Hibernate reads it via @JdbcTypeCode(LONGVARCHAR).
            // Unwrap one level when we get a TextNode instead of an ObjectNode.
            if (root != null && root.isTextual()) {
                root = objectMapper.readTree(root.asString());
            }
            if (root == null || !root.isObject()) {
                log.warn("[extractNote] not a JSON object: {}", details);
                return null;
            }
            JsonNode node = root.path("note");
            String val = node.asString();
            return (val != null && !val.isBlank()) ? val : null;
        } catch (Exception malformed) {
            log.warn("[extractNote] Cannot parse audit details: {}", details, malformed);
            return null;
        }
    }


    private ChatWithdrawalCardResponse toCard(PaymentOrder order,
                                              Map<UUID, BankAccount> banks,
                                              Map<String, Decision> decisions) {
        BankAccount bank = order.getBankAccountId() == null
                ? null : banks.get(order.getBankAccountId());
        Decision decision = decisions.get(order.getId().toString());
        return new ChatWithdrawalCardResponse(
                order.getId().toString(),
                order.getAmount().toPlainString(),
                order.getCurrency(),
                order.getStatus().name(),
                bank == null ? null : bank.getBankCode(),
                // CHỈ 4 số cuối. Số đầy đủ chỉ lộ qua endpoint reveal riêng, nơi mỗi lần gọi
                // ghi một dòng nhật ký — trả số đầy đủ ở đây sẽ vô hiệu hoá dấu vết đó.
                bank == null ? null : bank.getMaskedLast4(),
                bank == null ? null : bank.getHolderName(),
                order.getCreatedAt(),
                decision == null ? null : decision.adminUsername(),
                decision == null ? null : decision.note());
    }
}
