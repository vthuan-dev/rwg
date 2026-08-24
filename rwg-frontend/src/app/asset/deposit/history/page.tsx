"use client";

import React from "react";
import { PaymentOrderHistory } from "@/components/asset/PaymentOrderHistory";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Lịch sử nạp tiền.
 *
 * Đích quay lại là `/asset/deposit` chứ không `/profile`: người dùng vào đây từ trang nạp
 * tiền, đưa họ về trang hồ sơ sẽ mất mạch thao tác đang làm.
 */
export default function DepositHistoryPage() {
  const { t } = useTranslation();

  return (
    <PaymentOrderHistory
      backHref="/asset/deposit"
      itemTitle={t("asset.deposit")}
      negative={false}
      title={t("asset.deposit_history")}
      type="DEPOSIT"
    />
  );
}
