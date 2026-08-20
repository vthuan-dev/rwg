package com.rwg.risk.dto;

import java.time.Instant;
import java.util.List;

/**
 * Hồ sơ risk của một user cho khu quản trị (GET /api/v1/admin/risk/users/{id}).
 *
 * Gộp dấu vết đăng ký + toàn bộ liên kết vào MỘT response để người vận hành không
 * phải gọi ba API rồi tự ghép — lúc điều tra họ cần thấy cả bức tranh cùng lúc.
 *
 * @param commissionBlocked có liên kết nào đang giữ hoa hồng của user này không
 */
public record UserRiskProfileResponse(
        String userId,
        String username,
        String registrationIp,
        boolean hasDeviceFingerprint,
        Instant signalRecordedAt,
        boolean commissionBlocked,
        List<AccountLinkResponse> links
) {
}
