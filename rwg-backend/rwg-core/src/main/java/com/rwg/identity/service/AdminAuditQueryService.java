package com.rwg.identity.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.identity.dto.AuditLogResponse;
import com.rwg.identity.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Tra cứu nhật ký hệ thống cho khu quản trị (chặng 3) — READ-ONLY tuyệt đối.
 * Bảng audit_log là append-only: service này KHÔNG có method ghi/sửa/xóa.
 *
 * Dùng chung bảng audit_log sẵn có (đã index actor_id / action / created_at) thay vì
 * tạo bảng admin_actions riêng — một dòng thời gian duy nhất cho mọi sự kiện giúp
 * điều tra sự cố không phải ghép 2 nguồn.
 */
@Service
public class AdminAuditQueryService {

    private static final int DEFAULT_RANGE_DAYS = 7;

    private final AuditLogRepository auditLogRepository;

    public AdminAuditQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Tra cứu nhật ký. Mọi filter optional; khoảng thời gian mặc định 7 ngày gần nhất
     * để không quét toàn bảng (audit_log tăng rất nhanh: mỗi login/giao dịch 1 dòng).
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(UUID actorId, String action, String targetId,
                                                 String fromDate, String toDate,
                                                 int page, int size) {
        String actionFilter = action == null || action.isBlank() ? null : action.trim().toUpperCase();
        String targetFilter = targetId == null || targetId.isBlank() ? null : targetId.trim();

        Instant to = toDate == null || toDate.isBlank()
                ? Instant.now()
                : parseDate(toDate, "toDate").plusSeconds(86400);
        Instant from = fromDate == null || fromDate.isBlank()
                ? to.minusSeconds(DEFAULT_RANGE_DAYS * 86400L)
                : parseDate(fromDate, "fromDate");
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    ErrorCode.INVALID_REQUEST.defaultMessage(), Map.of("field", "fromDate"));
        }

        return PageResponse.from(
                auditLogRepository.searchForAdmin(actorId, actionFilter, targetFilter, from, to,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                AuditLogResponse::from);
    }

    private Instant parseDate(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim()).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (RuntimeException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", field));
        }
    }
}
