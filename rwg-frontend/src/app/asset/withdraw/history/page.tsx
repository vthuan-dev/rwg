"use client";

import React from "react";
import { PaymentOrderHistory } from "@/components/asset/PaymentOrderHistory";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Lịch sử rút tiền.
 *
 * `negative` bật để số tiền hiện dấu trừ: backend lưu độ lớn của lệnh (luôn dương), chiều
 * tiền nằm ở loại lệnh.
 */
export default function WithdrawHistoryPage() {
  const { t } = useTranslation();

  return (
    <PaymentOrderHistory
      backHref="/asset/withdraw"
      itemTitle={t("asset.withdraw")}
      negative
      title={t("asset.withdraw_history")}
      type="WITHDRAWAL"
    />
  );
}
