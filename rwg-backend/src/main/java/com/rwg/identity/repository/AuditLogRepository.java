package com.rwg.identity.repository;

import com.rwg.identity.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Append-only: chỉ dùng save() (INSERT). Không cập nhật/xóa bản ghi audit.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByAction(String action);

    /** Tra cứu theo action (phục vụ kiểm thử/đối soát — KHÔNG sửa/xóa). */
    List<AuditLog> findByAction(String action);
}
