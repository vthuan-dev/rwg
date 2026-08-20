package com.rwg.identity.dto;

import jakarta.validation.constraints.Size;

/** Quyết định của người phê duyệt (checker). Ghi chú tùy chọn, dùng khi từ chối. */
public record DecideApprovalRequest(
        @Size(max = 255, message = "{validation.admin.approval.note.size}")
        String note
) {
}
