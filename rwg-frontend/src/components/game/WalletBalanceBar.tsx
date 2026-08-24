"use client";

import React from "react";
import { useTranslation } from "@/context/LanguageContext";
import { formatMoney } from "@/lib/money";

/**
 * Dải số dư ví trên cùng trang đặt cược.
 *
 * `balance` là chuỗi thập phân từ backend, không phải số — xem `formatMoney`.
 */
export const WalletBalanceBar: React.FC<{
  balance: string | null;
  currency: string;
}> = ({ balance, currency }) => {
  const { t } = useTranslation();

  return (
    <div className="bg-[#1D1D1D] px-5 py-4 shadow-[0_2px_5px_0_rgba(0,0,0,0.1)]">
      <div className="flex items-center justify-between gap-x-3">
        <span className="text-[0.8125rem] text-[#D8D8D8]">{t("bet.wallet_balance")}</span>
        <span className="text-center text-[1rem] font-bold leading-[1.0625rem] text-primary">
          {currency} {balance != null ? formatMoney(balance) : "—"}
        </span>
      </div>
    </div>
  );
};
