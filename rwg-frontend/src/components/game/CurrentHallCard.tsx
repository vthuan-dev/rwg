"use client";

import React from "react";
import { useTranslation } from "@/context/LanguageContext";
import type { GameRound, GameTable } from "@/lib/playerApi";
import { DrawResultBalls, resultNumbers } from "@/components/games/DrawResultBalls";

/** Bảng màu nhãn hạng, giống trang `/games` và trang gốc. */
const TIER_COLORS = ["#ff4355", "#ffc443", "#43d0ff", "#f943ff"] as const;
const TIER_LABEL_KEYS = [
  "draw.tier_premium",
  "draw.tier_gold",
  "draw.tier_platinum",
  "draw.tier_diamond",
] as const;

/** Số ô kết quả theo loại bàn — xem `resultNumbers` để biết vì sao khác nhau. */
function slotsForGameType(gameType: string): number {
  if (gameType === "ROULETTE") return 1;
  if (gameType === "BACCARAT") return 2;
  return 3;
}

/** Định dạng `DD-MM-YYYY h:mm A` như trang gốc. */
function formatMatchTime(iso: string | null): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "-";

  const pad = (n: number) => String(n).padStart(2, "0");
  const h24 = d.getHours();
  const meridiem = h24 < 12 ? "AM" : "PM";
  const h12 = h24 % 12 === 0 ? 12 : h24 % 12;

  return `${pad(d.getDate())}-${pad(d.getMonth() + 1)}-${d.getFullYear()} ${h12}:${pad(
    d.getMinutes()
  )} ${meridiem}`;
}

export interface CurrentHallCardProps {
  table: GameTable;
  /** Ván ĐÃ XONG gần nhất — nguồn của các số và kết quả hiển thị. */
  lastRound: GameRound | null;
  /**
   * Thứ tự bàn trong danh sách, quyết định ảnh nền và hạng.
   *
   * Hạn chế đã biết: bàn không có cột hạng riêng, nên đổi thứ tự danh sách là nhãn hạng và
   * ảnh nền của mọi bàn đổi theo.
   */
  index: number;
  /**
   * Độ dài một vòng, tính bằng giây, lấy từ `roundSeconds` của API.
   *
   * Tính bằng GIÂY chứ không phút: cấu hình thực tế là 63 giây, quy đổi sang phút sẽ
   * hiện "1 phút" cho một vòng dài hơn một phút.
   */
  roundSeconds: number;
}

/**
 * Thẻ sảnh trên trang đặt cược.
 *
 * Khác thẻ ở trang `/games`: hiện kết quả của ván ĐÃ XONG (không phải ván đang chạy),
 * có thêm dòng thời lượng, và không có vòng đếm ngược — đồng hồ nằm ở khối bên dưới.
 */
export const CurrentHallCard: React.FC<CurrentHallCardProps> = ({
  table,
  lastRound,
  index,
  roundSeconds,
}) => {
  const { t, locale } = useTranslation();

  const tierColor = TIER_COLORS[index % TIER_COLORS.length];
  const tierLabelKey = TIER_LABEL_KEYS[index % TIER_LABEL_KEYS.length];
  const roomNumber = (index % 4) + 1;

  const tableName = table.nameI18n[locale] ?? table.nameI18n.en ?? table.gameType;
  const numbers = resultNumbers(lastRound);
  const sum = lastRound?.kl28Sum ?? null;

  /**
   * Nhãn thời lượng vòng.
   *
   * Hiện theo phút CHỈ khi chia hết, còn lại theo giây. Cấu hình hiện tại là 63 giây, làm
   * tròn thành "1 phút" là nói sai với người chơi đang canh giờ đặt cược.
   */
  const durationLabel =
    roundSeconds > 0 && roundSeconds % 60 === 0
      ? t("bet.x_minutes", { title: String(roundSeconds / 60) })
      : t("bet.x_seconds", { title: String(roundSeconds) });

  return (
    <div
      className={`relative bg-[#1D1D1D] shadow-[0_2px_5px_0_rgba(0,0,0,0.1)] px-5 py-4 flex flex-col bg-room-${roomNumber}`}
    >
      <div className="flex items-center justify-between gap-x-2">
        <h2 className="text-[0.875rem] font-bold leading-[1rem] text-[#D8D8D8]">
          {tableName}
        </h2>
        <span
          className="shrink-0 rounded-full border bg-black px-2.5 text-[0.8125rem] font-bold leading-normal"
          style={{ borderColor: tierColor, color: tierColor }}
        >
          {t(tierLabelKey)}
        </span>
      </div>

      <div className="mt-2.5 flex flex-row items-center gap-x-1">
        <i aria-hidden="true" className="icon-icon6 text-primary" />
        <span className="text-[0.75rem] font-bold leading-[1rem] text-[#d8d8d8]">
          {durationLabel}
        </span>
      </div>

      <div className="mt-2.5 mb-3">
        <DrawResultBalls numbers={numbers} slots={slotsForGameType(table.gameType)} />
      </div>

      <div className="mb-4 flex items-center justify-start gap-x-2.5">
        <span className="text-[0.6875rem] font-bold text-[#828282]">{t("bet.result")}</span>

        {sum != null ? (
          <div className="flex items-center gap-x-1.5">
            <span className="flex size-6 items-center justify-center rounded-full bg-[#5555F6] text-[0.8125rem] font-bold leading-normal text-white">
              {sum}
            </span>
            <span className="flex min-h-6 min-w-14 items-center justify-center rounded-full bg-[#F96C00] px-1.5 text-[0.8125rem] font-bold leading-normal text-white">
              {t(sum >= 14 ? "draw.big" : "draw.small")}
            </span>
            <span className="flex min-h-6 min-w-14 items-center justify-center rounded-full bg-primary px-1.5 text-[0.8125rem] font-bold leading-normal text-white">
              {t(sum % 2 === 0 ? "draw.double" : "draw.single")}
            </span>
          </div>
        ) : lastRound?.baccaratResult ? (
          <span className="flex min-h-6 items-center justify-center rounded-full bg-primary px-2.5 text-[0.8125rem] font-bold leading-normal text-white">
            {lastRound.baccaratResult}
          </span>
        ) : (
          <span className="flex h-6 items-center text-[0.6875rem] font-bold text-[#828282]">
            -
          </span>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-x-5 gap-y-1">
        <span className="text-[0.6875rem] font-bold text-[#828282]">
          {t("bet.last_match")}
        </span>
        <span className="flex items-center gap-x-1">
          <i aria-hidden="true" className="icon-icon15 text-[1rem] text-primary" />
          <span className="text-[0.6875rem] font-bold text-[#828282]">
            #{lastRound?.roundSeq ?? "-"}
          </span>
        </span>
        <span className="flex items-center gap-x-1">
          <i aria-hidden="true" className="icon-icon48 text-[1rem] text-primary" />
          <span className="text-[0.6875rem] font-bold text-[#828282]">
            {formatMatchTime(lastRound?.startedAt ?? null)}
          </span>
        </span>
      </div>
    </div>
  );
};
