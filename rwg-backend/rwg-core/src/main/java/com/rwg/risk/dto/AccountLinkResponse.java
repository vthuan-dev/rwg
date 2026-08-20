package com.rwg.risk.dto;

import com.rwg.risk.domain.AccountLink;

import java.time.Instant;

/**
 * Liên kết tài khoản trả cho khu quản trị.
 *
 * KHÁC hẳn phía người chơi: ở đây trả username ĐẦY ĐỦ và userId, vì người vận hành
 * phải tra cứu được tài khoản để điều tra. Người chơi không bao giờ thấy DTO này.
 *
 * Có {@code blocksCommission} để màn hình quản trị nói được ngay liên kết này đang
 * giữ tiền hay chỉ nằm chờ xem — nếu để người vận hành tự suy từ (type, status) thì
 * họ sẽ đoán sai và tưởng đã chặn trong khi chưa.
 */
public record AccountLinkResponse(
        String id,
        String userAId,
        String userAUsername,
        String userBId,
        String userBUsername,
        String linkType,
        String status,
        boolean blocksCommission,
        String evidence,
        String reviewedBy,
        Instant reviewedAt,
        String note,
        Instant createdAt
) {

    public static AccountLinkResponse from(AccountLink link, String usernameA, String usernameB) {
        return new AccountLinkResponse(
                link.getId().toString(),
                link.getUserAId().toString(),
                usernameA,
                link.getUserBId().toString(),
                usernameB,
                link.getLinkType().name(),
                link.getStatus().name(),
                link.blocksCommission(),
                link.getEvidence(),
                link.getReviewedBy() == null ? null : link.getReviewedBy().toString(),
                link.getReviewedAt(),
                link.getNote(),
                link.getCreatedAt());
    }
}
