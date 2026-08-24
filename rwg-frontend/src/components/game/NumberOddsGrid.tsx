"use client";

import React from "react";
import { useTranslation } from "@/context/LanguageContext";
import { formatMoney } from "@/lib/money";
import type { NumberOdds } from "@/lib/playerApi";

export interface NumberOddsGridProps {
  /** Tỷ lệ từng tổng, từ API. Rỗng ở bàn không phải Lucky 28. */
  numberOdds: NumberOdds[];
  /** Các tổng đang chọn, dạng chuỗi để khớp trường `selection` của backend. */
  selected: string[];
  onToggle: (sum: string) => void;
  /** Khoá cả lưới khi đã đóng cửa cược. */
  disabled: boolean;
}

/**
 * Lưới chọn tổng 0–27 cho tab Special Code.
 *
 * Bốn cột chứ không hai như lưới cược kết hợp: 28 ô xếp hai cột sẽ dài 14 hàng và người
 * chơi phải cuộn để thấy hết, trong khi cửa cược chỉ mở 45 giây.
 *
 * Mỗi ô hiện tổng ở trên và hệ số ở dưới. Hệ số chênh rất xa nhau (12 ở giữa dải, 999 ở
 * hai đầu) nên phải hiện ngay trên ô: người chơi cần biết mình đang nhắm ô nào có giá.
 *
 * Dùng `<button type="button">` vì lưới nằm trong `<form>` của thanh đặt cược — thiếu
 * `type` thì mặc định là `submit` và bấm chọn một số sẽ gửi luôn cả phiếu cược.
 */
export const NumberOddsGrid: React.FC<NumberOddsGridProps> = ({
  numberOdds,
  selected,
  onToggle,
  disabled,
}) => {
  const { t } = useTranslation();

  if (numberOdds.length === 0) {
    return (
      <p className="mt-4 p-8 text-center text-[0.875rem] text-white">
        {t("bet.special_code_options")}
      </p>
    );
  }

  return (
    <div className="mt-4 grid grid-cols-4 border-s border-t border-[#2E2E2E]">
      {numberOdds.map((item) => {
        const key = String(item.sum);
        const isSelected = selected.includes(key);
        return (
          <button
            aria-pressed={isSelected}
            className={[
              "flex flex-col items-center justify-center gap-y-1 py-2.5 transition-colors",
              "border-b border-e border-[#2E2E2E]",
              isSelected ? "bg-primary text-black" : "bg-[#0D0D0D] text-white",
              disabled ? "opacity-40" : "",
            ].join(" ")}
            disabled={disabled}
            key={key}
            onClick={() => onToggle(key)}
            type="button"
          >
            <span className="text-[1rem] font-bold tabular-nums">{item.sum}</span>
            <span className="text-[0.625rem] font-extralight tabular-nums">
              {formatMoney(item.multiplier)}
            </span>
          </button>
        );
      })}
    </div>
  );
};
