package com.rwg.affiliate.dto;

/** Số liệu tổng hợp cho dashboard admin trong một khoảng ngày. */
public record DashboardSummaryResponse(
        String from,
        String to,
        String totalDeposits,
        String totalWithdrawals,
        String totalTurnover,
        String totalCommissionPaid,
        long newUsers,
        long pendingWithdrawals,
        long totalUsers,
        long lockedUsers,
        long bannedUsers
) {
}
