"use client";

import React from "react";
import { Loader2 } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";
import { compareMoney, formatMoney, multiplyMoney } from "@/lib/money";

export interface BetSubmitBarProps {
  /** Tiền cược cho MỖI lựa chọn, dạng chuỗi. Rỗng nghĩa là chưa nhập. */
  stake: string;
  onStakeChange: (value: string) => void;
  /** Nhãn các lựa chọn đang chọn, để hiện ở dòng "Cược của tôi". */
  selectedLabels: string[];
  selectedCount: number;
  balance: string | null;
  currency: string;
  /** Hạn mức cược của bàn, dạng chuỗi. */
  minBet: string;
  maxBet: string;
  submitting: boolean;
  /** Đã đóng cửa cược. */
  closed: boolean;
  onSubmit: () => void;
}

/**
 * Thanh đặt cược cố định ở đáy màn hình.
 *
 * `fixed bottom-0` kèm `sm:max-w-[640px]`: khung người chơi rộng tối đa 640px, mà phần
 * tử `fixed` bám theo khung nhìn chứ không theo khung cha — thiếu giới hạn này thì trên
 * màn hình rộng thanh sẽ kéo dài hết chiều ngang trong khi nội dung nằm gọn ở giữa.
 *
 * `pb-[var(--safe-bottom)]` chừa chỗ cho thanh gesture của iPhone, không thì nút Đặt
 * cược nằm ngay dưới vạch kéo và rất dễ bấm nhầm sang thao tác hệ thống.
 */
export const BetSubmitBar: React.FC<BetSubmitBarProps> = ({
  stake,
  onStakeChange,
  selectedLabels,
  selectedCount,
  balance,
  currency,
  minBet,
  maxBet,
  submitting,
  closed,
  onSubmit,
}) => {
  const { t } = useTranslation();

  const total = stake === "" ? "0" : multiplyMoney(stake, selectedCount);

  // Kiểm ở trình duyệt chỉ để đỡ một lượt gọi chắc chắn thất bại, và để nói RÕ sai ở đâu:
  // server trả `VALIDATION_ERROR` chung cho cả ba trường hợp dưới đây (đã kiểm bằng lọi gọi
  // thật), nên nếu để server báo thì người chơi không biết phải sửa gì.
  // Server vẫn là nơi quyết định — xem ghi chú ở `placeBet`.
  const hasStake = stake !== "" && stake !== "0";
  const belowMin = hasStake && compareMoney(stake, minBet) < 0;
  const aboveMax = hasStake && compareMoney(stake, maxBet) > 0;
  const overBalance = balance != null && compareMoney(total, balance) > 0;

  const problem = belowMin
    ? t("bet.below_min_bet", { min: formatMoney(minBet), currency })
    : aboveMax
      ? t("bet.above_max_bet", { max: formatMoney(maxBet), currency })
      : overBalance
        ? t("bet.insufficient_balance")
        : null;

  const canSubmit =
    !submitting && !closed && selectedCount > 0 && hasStake && problem == null;

  return (
    <form
      className="fixed bottom-0 left-1/2 z-40 flex w-full -translate-x-1/2 flex-col bg-black p-5 pb-[calc(1.25rem+var(--safe-bottom))] sm:max-w-[640px]"
      onSubmit={(event) => {
        event.preventDefault();
        if (canSubmit) onSubmit();
      }}
    >
      <div className="mb-2 flex flex-col gap-y-2">
        <div className="flex items-center justify-between gap-x-3 leading-normal">
          <label className="text-[0.875rem] text-[#4A5056]" htmlFor="bet-stake">
            {t("bet.each_number_bet_amount")}
          </label>
          <span className="text-[0.6875rem] text-[#4A5056]">
            {t("bet.balance")} {currency}{" "}
            <span className="font-bold">
              {balance != null ? formatMoney(balance) : "—"}
            </span>
          </span>
        </div>

        <div className="relative flex items-center">
          <input
            aria-describedby="bet-total"
            // `inputMode="numeric"` mở bàn phím số trên di động mà vẫn là ô text, nên
            // giữ được chuỗi người dùng nhập nguyên vẹn. `type="number"` sẽ tự đổi giá
            // trị theo địa phương và cho phép nhập `e`, `+`, `-`.
            className="w-full border border-[#2E2E2E] bg-[#1D1D1D] py-3 pe-40 ps-4 text-[0.875rem] text-white outline-none focus:border-primary"
            disabled={closed}
            id="bet-stake"
            inputMode="numeric"
            onChange={(event) => {
              // Chỉ nhận chữ số. Backend nhận chuỗi thập phân, nhưng bản gốc cũng chặn
              // ở số nguyên và hạn mức cược của bàn là số nguyên.
              if (/^\d*$/.test(event.target.value)) {
                onStakeChange(event.target.value);
              }
            }}
            type="text"
            value={stake}
          />

          <div className="absolute end-5 flex items-center gap-x-5">
            <span className="text-[0.875rem] font-bold text-[#D0D5DA]">{currency}</span>
            <button
              className="bg-primary px-5 py-1 text-[0.75rem] font-bold text-white disabled:opacity-40"
              disabled={closed || balance == null || selectedCount === 0}
              onClick={() => {
                if (balance == null || selectedCount === 0) return;
                // Chia đều số dư cho các lựa chọn, làm tròn XUỐNG: làm tròn lên sẽ cho
                // tổng vượt số dư và server từ chối ngay.
                const whole = balance.split(".")[0] || "0";
                const perOption = Math.floor(Number(whole) / selectedCount);
                onStakeChange(String(Math.max(0, perOption)));
              }}
              type="button"
            >
              {t("bet.max")}
            </button>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-between gap-x-3">
        <span className="w-1/2 text-[0.6875rem] text-[#4a5056]">{t("bet.my_bet")}</span>
        <span className="text-end text-[0.6875rem] font-bold text-[#d0d5da]">
          {selectedLabels.length > 0 ? selectedLabels.join(", ") : "-"}
        </span>
      </div>

      <div className="flex items-center justify-between gap-x-3" id="bet-total">
        <span className="text-[0.6875rem] text-[#4a5056]">{t("bet.total_bet_amount")}</span>
        <span className="text-end text-[0.6875rem] font-bold text-[#d0d5da]">
          {formatMoney(total)} {currency}
        </span>
      </div>

      {problem ? (
        <p className="mt-1 text-[0.6875rem] font-bold text-[#ff4355]" role="alert">
          {problem}
        </p>
      ) : null}

      <button
        className="mt-3 w-full bg-primary py-3 text-[0.9375rem] font-bold text-white transition-opacity disabled:opacity-40"
        disabled={!canSubmit}
        type="submit"
      >
        {submitting ? (
          <span className="flex items-center justify-center">
            <Loader2 aria-hidden="true" className="mr-2 size-4 animate-spin" />
            {t("bet.submitting")}
          </span>
        ) : (
          t("bet.place_bet")
        )}
      </button>
    </form>
  );
};
