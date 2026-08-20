package com.rwg.bank.domain;

/**
 * Trạng thái tài khoản ngân hàng liên kết (bank_accounts.status).
 * Xóa là SOFT DELETE (REMOVED) — giữ audit trail.
 */
public enum BankAccountStatus {
    ACTIVE, REMOVED
}
