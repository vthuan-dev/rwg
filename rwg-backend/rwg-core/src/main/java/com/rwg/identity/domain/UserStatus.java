package com.rwg.identity.domain;

/**
 * Trạng thái tài khoản. CHỈ ACTIVE được đăng nhập (xem AuthService.login/refresh).
 *
 * Vòng đời hợp lệ (enforce trong AdminUserService):
 * - ACTIVE  <-> LOCKED : khóa tạm thời khi đang điều tra, admin mở lại được.
 * - ACTIVE/LOCKED -> BANNED : cấm vĩnh viễn do gian lận, KHÔNG mở lại qua API.
 * - ACTIVE/LOCKED -> CLOSED : đóng tài khoản (soft-delete; KHÔNG hard delete vì
 *   wallets có FK ON DELETE RESTRICT và ledger là nguồn sự thật tài chính).
 */
public enum UserStatus {
    ACTIVE,
    LOCKED,
    BANNED,
    CLOSED
}
