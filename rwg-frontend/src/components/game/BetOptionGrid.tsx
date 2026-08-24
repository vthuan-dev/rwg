"use client";

import React from "react";
import { useTranslation } from "@/context/LanguageContext";
import { formatMoney } from "@/lib/money";
import type { BetOption } from "@/lib/betOptions";

export interface BetOptionGridProps {
  options: BetOption[];
  /** Các `betType` đang được chọn. */
  selected: string[];
  onToggle: (betType: string) => void;
  /** Khoá cả lưới khi đã đóng cửa cược. */
  disabled: boolean;
}

/**
 * Lưới lựa chọn cược, hai cột như trang gốc.
 *
 * Viền dựng bằng `border-r` ở ô cột trái và `border-b` ở mọi ô — cách này cho đường kẻ
 * mảnh 1px không bị nhân đôi ở chỗ hai ô gặp nhau, khác với việc cho mỗi ô một viền
 * đầy đủ.
 *
 * Dùng `<button type="button">` chứ không `<div onClick>`: các ô này nằm trong `<form>`
 * của thanh đặt cược, mà `<button>` không ghi rõ `type` thì mặc định là `submit` và bấm
 * chọn một lựa chọn sẽ gửi luôn cả phiếu cược.
 */
export const BetOptionGrid: React.FC<BetOptionGridProps> = ({
  options,
  selected,
  onToggle,
  disabled,
}) => {
  const { t } = useTranslation();

  if (options.length === 0) {
    return (
      <p className="mt-4 p-8 text-center text-[0.875rem] text-white">
        {t("bet.no_options")}
      </p>
    );
  }

  return (
    <div className="mt-4 grid grid-cols-2 border border-[#2E2E2E]">
      {options.map((option, index) => {
        const isSelected = selected.includes(option.betType);
        return (
          <button
            aria-pressed={isSelected}
            className={[
              "relative flex flex-col items-center justify-center gap-y-5 py-2 transition-colors",
              index % 2 === 0 ? "border-r" : "",
              "border-b border-[#2E2E2E]",
              isSelected ? "bg-primary text-black" : "bg-[#0D0D0D] text-white",
              disabled ? "opacity-40" : "",
            ].join(" ")}
            disabled={disabled}
            key={option.betType}
            onClick={() => onToggle(option.betType)}
            type="button"
          >
            {/* Tỷ lệ riêng: đánh dấu bằng một chấm nhỏ ở góc, đủ để người chơi nhận ra
                con số khác thường mà không phá bố cục hai dòng của ô. Chấm là hình khối
                nên trình đọc màn hình không đọc được — nhãn ẩn phía dưới lo phần đó. */}
            {option.personalized ? (
              <span
                aria-hidden="true"
                className={`absolute end-1.5 top-1.5 size-1.5 rounded-full ${
                  isSelected ? "bg-black/50" : "bg-primary"
                }`}
              />
            ) : null}

            <span className="text-center text-[0.9375rem] font-bold">
              {t(option.labelKey)}
            </span>
            <span className="text-center text-[0.75rem] font-extralight leading-[1.125rem]">
              {formatMoney(option.multiplier)}
            </span>

            {/* Cửa có hoa hồng: nói rõ hệ số ĐÃ trừ hoa hồng. Không có dòng này thì người
                chơi thấy 1.95 ở cửa Nhà băng trong khi các cửa 1:1 khác hiện 2, và không
                có gì giải thích khoảng chênh. Đặt tuyệt đối để không đẩy hai dòng trên
                lệch khỏi các ô cùng lưới. */}
            {option.commissionRate ? (
              <span
                className={`absolute bottom-0.5 text-[0.5625rem] leading-none ${
                  isSelected ? "text-black/60" : "text-white/45"
                }`}
              >
                {t("bet.commission_note", {
                  percent: String(Number(option.commissionRate) * 100),
                })}
              </span>
            ) : null}

            {option.personalized ? (
              <span className="sr-only">{t("bet.personalized_odds")}</span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
};
