"use client";

import React from "react";
import { Loader2 } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Lớp phủ chặn tương tác khi đang gửi cược hoặc khi đang chờ ván mới.
 *
 * `role="alertdialog"` kèm `aria-modal`: đây là lớp phủ chặn thao tác, nên trình đọc màn
 * hình phải thông báo ngay chứ không để người dùng bấm vào những thứ đã bị vô hiệu.
 *
 * KHÔNG bắt phím Escape để đóng: lớp phủ này phản ánh trạng thái thật (đang gửi tiền,
 * hoặc ván chưa bắt đầu), đóng nó đi không làm trạng thái đó biến mất — chỉ khiến người
 * chơi tưởng có thể tương tác được.
 */
export const BetBlockingOverlay: React.FC<{
  /** `next_match` hiện thêm dòng "Đang chuẩn bị ván tiếp theo". */
  variant?: "submitting" | "next_match";
}> = ({ variant = "submitting" }) => {
  const { t } = useTranslation();

  return (
    <div
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70"
      role="alertdialog"
    >
      <div className="flex flex-col items-center gap-y-2">
        <Loader2 aria-hidden="true" className="size-6 animate-spin text-primary" />
        <p className="text-center text-[0.75rem] font-bold leading-normal text-primary">
          {t("bet.please_wait")}
        </p>
        {variant === "next_match" ? (
          <p className="text-center text-[0.75rem] font-bold leading-normal text-primary">
            {t("bet.preparing_next_match")}
          </p>
        ) : null}
      </div>
    </div>
  );
};
