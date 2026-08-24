package com.rwg.payment.service;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.repository.BankAccountRepository;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.identity.domain.AuditLog;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.payment.domain.PaymentOrder;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.domain.PaymentType;
import com.rwg.payment.dto.AdminWithdrawalRowResponse;
import com.rwg.payment.dto.PaymentOrderResponse;
import com.rwg.payment.repository.PaymentOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tra soát lệnh nạp/rút cho khu quản trị (chặng 3) — READ-ONLY.
 * Việc duyệt/từ chối lệnh rút vẫn nằm ở {@link WithdrawalService} (đã có transition
 * nguyên tử + hoàn tiền idempotent), service này KHÔNG lặp lại logic đó.
 *
 * Khoảng thời gian mặc định 30 ngày gần nhất nếu client không truyền — tránh quét
 * toàn bảng payment_orders khi bảng lớn.
 */
@Service
public class AdminPaymentQueryService {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private static final Logger log = LoggerFactory.getLogger(AdminPaymentQueryService.class);

    /** Hai hành động ghi lại quyết định của admin trên một lệnh rút. */
    private static final Set<String> DECISION_ACTIONS = Set.of(
            AuditTrailService.WITHDRAWAL_APPROVED,
            AuditTrailService.WITHDRAWAL_REJECTED);

    private final PaymentOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AdminPaymentQueryService(PaymentOrderRepository orderRepository,
                                    UserRepository userRepository,
                                    BankAccountRepository bankAccountRepository,
                                    AuditLogRepository auditLogRepository,
                                    ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /** Danh sách lệnh nạp tiền, filter theo trạng thái / user / khoảng ngày (UTC). */
    @Transactional(readOnly = true)
    public PageResponse<PaymentOrderResponse> searchDeposits(String status, UUID userId,
                                                             String fromDate, String toDate,
                                                             int page, int size) {
        return search(PaymentType.DEPOSIT, status, userId, fromDate, toDate, page, size);
    }

    /**
     * Danh sách lệnh rút cho khu quản trị — dùng cho CẢ hàng chờ duyệt và lịch sử.
     *
     * Trả {@link AdminWithdrawalRowResponse} thay vì {@link PaymentOrderResponse}: người vận
     * hành cần tên người chơi và tài khoản nhận tiền để nhận diện, còn DTO kia chỉ có
     * {@code bankAccountId} dạng UUID trần — không đọc được bằng mắt.
     *
     * Tài khoản ngân hàng lấy theo {@code bankAccountId} CỦA CHÍNH LỆNH RÚT, không phải tài
     * khoản đang đặt mặc định của người chơi. Người chơi có thể đổi tài khoản mặc định sau khi
     * gửi lệnh; lấy theo mặc định sẽ hiện sai số và admin chuyển tiền đi sai chỗ.
     *
     * Với lệnh ĐÃ quyết định, đính kèm ai duyệt/từ chối, lý do và thời điểm — đọc từ
     * {@code audit_log} vì bảng lệnh không lưu những thông tin đó.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminWithdrawalRowResponse> searchWithdrawals(String status, UUID userId,
                                                                     String fromDate, String toDate,
                                                                     int page, int size) {
        Page<PaymentOrder> orders = searchOrders(
                PaymentType.WITHDRAWAL, status, userId, fromDate, toDate, page, size);

        // Nạp THEO LÔ: một trang 20 dòng mà tra từng dòng là 60 lượt gọi DB thêm.
        Map<UUID, String> usernames = loadUsernames(orders.getContent());
        Map<UUID, BankAccount> banks = loadBankAccounts(orders.getContent());
        Map<String, Decision> decisions = loadDecisions(orders.getContent());

        return PageResponse.from(orders, order -> toRow(order, usernames, banks, decisions));
    }

    /**
     * LỊCH SỬ lệnh rút — chỉ những lệnh ĐÃ kết thúc (đã duyệt hoặc đã từ chối).
     *
     * VÌ SAO TÁCH KHỎI {@link #searchWithdrawals}: hàng chờ và lịch sử phục vụ hai câu hỏi khác
     * nhau. Hàng chờ trả lời "tôi phải xử lý gì bây giờ", lịch sử trả lời "chuyện gì đã xảy ra và
     * ai chịu trách nhiệm". Gộp vào một bảng thì các lệnh chờ bị chôn giữa hàng trăm lệnh cũ và
     * người vận hành bỏ sót việc cần làm.
     *
     * Trả về CẢ SETTLED và VOIDED trong một truy vấn xếp theo thời gian: nếu tách hai lần gọi
     * rồi trộn ở tầng trên thì phân trang sai — trang 1 của mỗi bên ghép lại không phải trang 1
     * của tập hợp chung.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminWithdrawalRowResponse> searchWithdrawalHistory(String status, UUID userId,
                                                                           String fromDate, String toDate,
                                                                           int page, int size) {
        Set<PaymentStatus> statuses = resolveHistoryStatuses(status);

        Page<PaymentOrder> orders = searchOrdersByStatuses(
                PaymentType.WITHDRAWAL, statuses, userId, fromDate, toDate, page, size);

        Map<UUID, String> usernames = loadUsernames(orders.getContent());
        Map<UUID, BankAccount> banks = loadBankAccounts(orders.getContent());
        Map<String, Decision> decisions = loadDecisions(orders.getContent());

        return PageResponse.from(orders, order -> toRow(order, usernames, banks, decisions));
    }

    /**
     * Chuyển filter trạng thái của client thành tập trạng thái để truy vấn.
     *
     * Không truyền gì thì lấy cả hai trạng thái kết thúc. Truyền PENDING thì TỪ CHỐI thay vì
     * lặng lẽ trả rỗng: một lệnh chờ duyệt không thuộc lịch sử, và trả rỗng cho một tham số vô
     * nghĩa khiến người dùng tưởng không có dữ liệu thay vì biết mình gọi sai.
     */
    private Set<PaymentStatus> resolveHistoryStatuses(String status) {
        if (status == null || status.isBlank()) {
            return Set.of(PaymentStatus.SETTLED, PaymentStatus.VOIDED);
        }
        PaymentStatus parsed = parseStatus(status);
        if (parsed != PaymentStatus.SETTLED && parsed != PaymentStatus.VOIDED) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "status"));
        }
        return Set.of(parsed);
    }

    /** Số lệnh rút đang chờ duyệt — badge cảnh báo trên dashboard admin. */
    @Transactional(readOnly = true)
    public long countPendingWithdrawals() {
        return orderRepository.countByTypeAndStatus(PaymentType.WITHDRAWAL, PaymentStatus.PENDING);
    }

    private PageResponse<PaymentOrderResponse> search(PaymentType type, String status, UUID userId,
                                                      String fromDate, String toDate,
                                                      int page, int size) {
        return PageResponse.from(
                searchOrders(type, status, userId, fromDate, toDate, page, size),
                PaymentOrderResponse::from);
    }

    /**
     * Dựng và chạy truy vấn tra soát, trả entity thô.
     *
     * Tách khỏi {@link #search} để luồng lệnh rút dùng lại được toàn bộ phần phân tích ngày
     * tháng và phân trang mà vẫn tự quyết định cách ghép DTO của mình.
     */
    private Page<PaymentOrder> searchOrders(PaymentType type, String status, UUID userId,
                                            String fromDate, String toDate,
                                            int page, int size) {
        PaymentStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
        Range range = resolveRange(fromDate, toDate);
        return orderRepository.searchForAdmin(type, statusFilter, userId, range.from(), range.to(),
                pageableByNewest(page, size));
    }

    /** Như {@link #searchOrders} nhưng lọc theo một TẬP trạng thái — dùng cho trang lịch sử. */
    private Page<PaymentOrder> searchOrdersByStatuses(PaymentType type, Set<PaymentStatus> statuses,
                                                      UUID userId, String fromDate, String toDate,
                                                      int page, int size) {
        Range range = resolveRange(fromDate, toDate);
        return orderRepository.searchForAdminByStatuses(type, statuses, userId, range.from(), range.to(),
                pageableByNewest(page, size));
    }

    /** Khoảng thời gian nửa mở [from, to). */
    private record Range(Instant from, Instant to) {
    }

    /**
     * Phân tích khoảng ngày của client.
     *
     * Tách ra hàm riêng vì cả hai biến thể truy vấn đều cần đúng logic này. Nhân đôi nó là mở
     * đường cho hai màn hình cùng bộ lọc ngày lại trả kết quả lệch nhau ở biên.
     */
    private Range resolveRange(String fromDate, String toDate) {
        Instant to = toDate == null || toDate.isBlank()
                ? Instant.now()
                : parseDate(toDate, "toDate").plusSeconds(86400); // nửa mở: bao trọn ngày kết thúc
        Instant from = fromDate == null || fromDate.isBlank()
                ? to.minusSeconds(DEFAULT_RANGE_DAYS * 86400L)
                : parseDate(fromDate, "fromDate");
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    ErrorCode.INVALID_REQUEST.defaultMessage(), Map.of("field", "fromDate"));
        }
        return new Range(from, to);
    }

    private PageRequest pageableByNewest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** Tên đăng nhập của những người chơi xuất hiện trong trang, nạp một lượt. */
    private Map<UUID, String> loadUsernames(List<PaymentOrder> orders) {
        Set<UUID> userIds = orders.stream()
                .map(PaymentOrder::getUserId)
                .collect(Collectors.toCollection(HashSet::new));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    /**
     * Tài khoản ngân hàng của những lệnh trong trang, nạp một lượt.
     *
     * Lệnh có {@code bankAccountId} null vẫn hợp lệ (lệnh cũ tạo trước khi bắt buộc chọn tài
     * khoản), nên phải lọc null trước khi truy vấn.
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

    /**
     * Quyết định của admin trên một lệnh rút, trích từ một dòng nhật ký.
     *
     * {@code note} có thể null với lệnh cũ được quyết định trước khi hệ thống bắt buộc nhập lý do.
     */
    private record Decision(String adminUsername, String note, Instant at) {
    }

    /**
     * Nhật ký duyệt/từ chối của những lệnh trong trang, khoá theo mã lệnh.
     *
     * VÌ SAO ĐỌC TỪ audit_log: bảng {@code payment_orders} chỉ lưu trạng thái cuối, không lưu ai
     * đã quyết định hay vì sao. Thông tin đó chỉ tồn tại trong nhật ký — nơi nó không sửa được,
     * vì audit_log là append-only còn một cột trong bảng lệnh thì có thể bị ghi đè.
     *
     * Lệnh còn PENDING không có dòng nhật ký nào nên không xuất hiện trong map; nơi gọi trả null
     * cho ba trường quyết định.
     */
    private Map<String, Decision> loadDecisions(List<PaymentOrder> orders) {
        Set<String> orderIds = orders.stream()
                // Lệnh chờ duyệt chắc chắn chưa có quyết định — không đưa vào mệnh đề IN để
                // truy vấn chỉ hỏi những mã thật sự có khả năng khớp.
                .filter(order -> order.getStatus() != PaymentStatus.PENDING)
                .map(order -> order.getId().toString())
                .collect(Collectors.toCollection(HashSet::new));
        if (orderIds.isEmpty()) {
            return Map.of();
        }

        List<AuditLog> logs = auditLogRepository.findByActionInAndTargetIdIn(DECISION_ACTIONS, orderIds);
        if (logs.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> adminNames = loadActorUsernames(logs);

        return logs.stream().collect(Collectors.toMap(
                AuditLog::getTargetId,
                entry -> new Decision(
                        resolveActorName(entry, adminNames),
                        extractNote(entry.getDetails()),
                        entry.getCreatedAt()),
                // Một lệnh về nguyên tắc chỉ được quyết định một lần, nhưng nhật ký là append-only
                // nên nếu có hai dòng (ví dụ do sự cố ghi lặp) thì giữ dòng MỚI NHẤT — đó là
                // trạng thái thực tế cuối cùng.
                (older, newer) -> Comparator
                        .comparing(Decision::at, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .compare(newer, older) >= 0 ? newer : older));
    }

    /**
     * Tên admin đứng sau mỗi dòng nhật ký.
     *
     * Cần truy vấn riêng vì các dòng nhật ký cũ có {@code actor_username} null — cột này chỉ
     * được ghi từ một chặng sau. Không suy ra được tên thì thà để trống còn hơn hiện UUID trần.
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
                .collect(Collectors.toMap(User::getId, User::getUsername));
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
     * details là JSON thô do {@code AuditTrailService} ghi. Không parse được thì trả null thay
     * vì ném lỗi: một dòng nhật ký dị dạng không được phép làm sập cả trang lịch sử rút tiền.
     */
    private String extractNote(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(details);
            // H2 (used in tests) wraps JSON column values in an extra string literal.
            // Unwrap if we get a TextNode instead of an ObjectNode.
            if (root != null && root.isTextual()) {
                root = objectMapper.readTree(root.asString());
            }
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode node = root.path("note");
            String val = node.asText();
            return (val != null && !val.isBlank()) ? val : null;
        } catch (Exception malformed) {
            log.warn("Cannot parse audit details for withdrawal decision", malformed);
            return null;
        }
    }

    /**
     * Ghép một dòng bảng lệnh rút.
     *
     * Username thiếu thì trả null chứ không trả chuỗi {@code "N/A"}: quyết định hiển thị gì khi
     * thiếu dữ liệu thuộc về giao diện, còn API nhồi chuỗi hiển thị vào thì client không phân
     * biệt được người chơi tên "N/A" với dữ liệu bị thiếu.
     */
    private AdminWithdrawalRowResponse toRow(PaymentOrder order,
                                             Map<UUID, String> usernames,
                                             Map<UUID, BankAccount> banks,
                                             Map<String, Decision> decisions) {
        BankAccount bank = order.getBankAccountId() == null
                ? null : banks.get(order.getBankAccountId());
        Decision decision = decisions.get(order.getId().toString());
        return new AdminWithdrawalRowResponse(
                order.getId().toString(),
                order.getUserId().toString(),
                usernames.get(order.getUserId()),
                order.getAmount().toPlainString(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getBankAccountId() == null ? null : order.getBankAccountId().toString(),
                bank == null ? null : bank.getBankCode(),
                bank == null ? null : bank.getMaskedLast4(),
                bank == null ? null : bank.getHolderName(),
                order.getCreatedAt(),
                decision == null ? null : decision.adminUsername(),
                decision == null ? null : decision.note(),
                decision == null ? null : decision.at());
    }

    private PaymentStatus parseStatus(String raw) {
        try {
            return PaymentStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "status"));
        }
    }

    /** Ngày dạng ISO yyyy-MM-dd, hiểu theo UTC (khớp mốc hạn mức rút ngày). */
    private Instant parseDate(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim()).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (RuntimeException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", field));
        }
    }
}
