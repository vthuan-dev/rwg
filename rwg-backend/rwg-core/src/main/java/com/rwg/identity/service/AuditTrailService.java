package com.rwg.identity.service;

import com.rwg.identity.domain.AuditLog;
import com.rwg.identity.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Ghi audit trail APPEND-ONLY: mọi sự kiện đăng nhập/đăng ký/đổi mật khẩu.
 * KHÔNG ghi mật khẩu thô hay hash vào details.
 * Chạy transaction REQUIRES_NEW: audit của sự kiện THẤT BẠI (login sai, bị khóa...)
 * vẫn được ghi lại dù transaction nghiệp vụ bên ngoài bị rollback.
 */
@Service
public class AuditTrailService {

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGIN_LOCKED = "LOGIN_LOCKED";
    public static final String REFRESH_TOKEN_ROTATED = "REFRESH_TOKEN_ROTATED";
    public static final String REFRESH_TOKEN_REUSE = "REFRESH_TOKEN_REUSE";
    public static final String LOGOUT = "LOGOUT";
    public static final String WITHDRAWAL_PASSWORD_SET = "WITHDRAWAL_PASSWORD_SET";
    public static final String USER_LOCALE_UPDATED = "USER_LOCALE_UPDATED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    // Chặng 2 Phase b: nghiệp vụ tiền / thanh toán / ngân hàng.
    public static final String WALLET_DEBIT = "WALLET_DEBIT";
    public static final String WALLET_CREDIT = "WALLET_CREDIT";
    public static final String DEPOSIT_COMPLETED = "DEPOSIT_COMPLETED";
    public static final String WITHDRAWAL_REQUESTED = "WITHDRAWAL_REQUESTED";
    public static final String WITHDRAWAL_APPROVED = "WITHDRAWAL_APPROVED";
    public static final String WITHDRAWAL_REJECTED = "WITHDRAWAL_REJECTED";
    public static final String BANK_ACCOUNT_ADDED = "BANK_ACCOUNT_ADDED";
    public static final String BANK_ACCOUNT_REMOVED = "BANK_ACCOUNT_REMOVED";

    // Chặng 3: thao tác quản trị (admin backoffice). Tiền tố ADMIN_ để tra cứu
    // nhật ký admin chỉ cần filter theo action LIKE 'ADMIN_%'.
    public static final String ADMIN_USER_STATUS_CHANGED = "ADMIN_USER_STATUS_CHANGED";
    public static final String ADMIN_USER_ROLE_CHANGED = "ADMIN_USER_ROLE_CHANGED";
    public static final String ADMIN_KYC_UPDATED = "ADMIN_KYC_UPDATED";
    public static final String ADMIN_WITHDRAWAL_PASSWORD_RESET = "ADMIN_WITHDRAWAL_PASSWORD_RESET";
    public static final String ADMIN_WALLET_ADJUSTED = "ADMIN_WALLET_ADJUSTED";

    // Chặng 3 Phase 2: giới thiệu & hoa hồng đại lý.
    public static final String REFERRAL_ATTACHED = "REFERRAL_ATTACHED";
    /** Mã giới thiệu bị bỏ qua (sai mã / tự giới thiệu / vòng lặp) — không chặn đăng ký. */
    public static final String REFERRAL_SKIPPED = "REFERRAL_SKIPPED";
    public static final String COMMISSION_PAID = "COMMISSION_PAID";
    public static final String ADMIN_COMMISSION_RATE_CHANGED = "ADMIN_COMMISSION_RATE_CHANGED";
    public static final String ADMIN_COMMISSION_RUN_TRIGGERED = "ADMIN_COMMISSION_RUN_TRIGGERED";


    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditTrailService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String actorUsername, String action,
                       String targetType, String targetId,
                       Map<String, Object> details, String ipAddress) {
        String json = null;
        if (details != null && !details.isEmpty()) {
            try {
                json = objectMapper.writeValueAsString(details);
            } catch (JacksonException e) {
                // Không để audit thất bại làm hỏng nghiệp vụ; ghi chuỗi thô an toàn.
                log.warn("Không serialize được audit details cho action={}", action, e);
                json = "{}";
            }
        }
        repository.save(new AuditLog(actorId, actorUsername, action, targetType, targetId, json, ipAddress));
    }
}
