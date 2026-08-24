"use client";

import React from "react";
import { useTranslation } from "@/context/LanguageContext";
import type { GameRound, GameTable } from "@/lib/playerApi";
import Link from "next/link";
import { DrawCountdown } from "@/components/games/DrawCountdown";
import { DrawResultBalls, resultNumbers } from "@/components/games/DrawResultBalls";

/**
 * Hạng của sảnh: màu viền/chữ và khoá nhãn, theo đúng bảng của trang gốc.
 *
 * Trang gốc gán theo `id` bàn là số 1–4. Bàn của mình dùng UUID nên không so được;
 * thay vào đó gán theo THỨ TỰ trong danh sách, vốn do backend trả về ổn định.
 */
const TIERS = [
  { color: "#ff4355", labelKey: "draw.tier_premium" },
  { color: "#ffc443", labelKey: "draw.tier_gold" },
  { color: "#43d0ff", labelKey: "draw.tier_platinum" },
  { color: "#f943ff", labelKey: "draw.tier_diamond" },
] as const;

/** Thời lượng từng pha (giây), khớp mặc định `rwg.game.round.*` phía backend. */
const PHASE_SECONDS: Record<string, number> = {
  BETTING_OPEN: 45,
  BETTING_CLOSED: 2,
  SPINNING: 8,
  RESULT: 3,
  SETTLE: 5,
};

/** Số ô kết quả theo loại game — xem `resultNumbers` để biết vì sao khác nhau. */
function slotsForGameType(gameType: string): number {
  if (gameType === "ROULETTE") return 1;
  if (gameType === "BACCARAT") return 2;
  return 3;
}

/**
 * Định dạng thời gian `DD-MM-YYYY h:mm A` như trang gốc.
 *
 * Tự viết thay vì thêm dayjs (trang gốc dùng dayjs): chỉ cần đúng một định dạng ở
 * đúng một chỗ, kéo cả một thư viện ngày tháng vào bundle là không xứng.
 *
 * `Intl` sẽ tự dịch AM/PM và đổi thứ tự ngày/tháng theo ngôn ngữ máy — ở đây cần
 * định dạng CỐ ĐỊNH giống bản gốc nên ghép tay.
 */
function formatDrawTime(iso: string | null): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "-";

  const pad = (n: number) => String(n).padStart(2, "0");
  const hours24 = d.getHours();
  const meridiem = hours24 < 12 ? "AM" : "PM";
  // 0 giờ và 12 giờ đều hiện là 12 trong hệ 12 giờ.
  const hours12 = hours24 % 12 === 0 ? 12 : hours24 % 12;

  return `${pad(d.getDate())}-${pad(d.getMonth() + 1)}-${d.getFullYear()} ${hours12}:${pad(
    d.getMinutes()
  )} ${meridiem}`;
}

export interface GameHallCardProps {
  table: GameTable;
  round: GameRound | null;
  /** Vị trí trong danh sách, quyết định ảnh nền và hạng. */
  index: number;
  /** Gọi khi đồng hồ về 0 để tải lại vòng của bàn này. */
  onRoundExpired: (tableId: string) => void;
}

export const GameHallCard: React.FC<GameHallCardProps> = ({
  table,
  round,
  index,
  onRoundExpired,
}) => {
  const { t, locale } = useTranslation();

  // Trang gốc chỉ có 4 ảnh nền và 4 hạng, backend lại trả 6 bàn — nên quay vòng lại
  // từ đầu. Thà lặp ảnh còn hơn để hai thẻ cuối trơ nền đen giữa bốn thẻ có ảnh.
  const tier = TIERS[index % TIERS.length];
  const roomNumber = (index % 4) + 1;

  // `nameI18n` chỉ có en/vi/zh/ja. Với ms/ko phải rơi về `en`, không thì tên bàn
  // trống hoàn toàn.
  const tableName = table.nameI18n[locale] ?? table.nameI18n.en ?? table.gameType;

  const isSettled = round != null && round.status !== "OPEN";
  const numbers = resultNumbers(round);
  const hasResult = numbers.length > 0;

  return (
    // Bọc trong `Link` để cả thẻ bấm được, đúng như trang gốc. `ref=/bet` để nút quay
    // lại ở trang chi tiết đưa về danh sách sảnh.
    <Link
      className="block transition-transform active:scale-[0.99]"
      href={`/bet/detail?id=${table.gameType.toLowerCase()}&ref=/bet`}
    >
      <article
        className={`flex flex-col gap-y-3.5 rounded-md bg-[#1d1d1d] px-5 py-4 bg-room-${roomNumber}`}
      >
      <div className="flex items-center justify-between gap-x-2">
        <div className="flex items-center gap-x-2.5">
          <h2 className="text-[0.875rem] font-bold leading-[1rem] text-[#D8D8D8]">
            {tableName}
          </h2>
          <DrawCountdown
            endsAt={round?.phaseEndsAt ?? null}
            onExpired={() => onRoundExpired(table.id)}
            serverTime={round?.serverTime ?? new Date().toISOString()}
            totalSeconds={PHASE_SECONDS[round?.phase ?? "BETTING_OPEN"] ?? 45}
          />
        </div>

        {/* Nhãn hạng: viền và chữ cùng màu trên nền đen, đúng như bản gốc. Màu đặt
            inline vì Tailwind không sinh được lớp từ giá trị chạy lúc thực thi. */}
        <span
          className="shrink-0 rounded-full border bg-black px-2.5 text-[0.8125rem] font-bold leading-normal"
          style={{ borderColor: tier.color, color: tier.color }}
        >
          {t(tier.labelKey)}
        </span>
      </div>

      <DrawResultBalls numbers={numbers} slots={slotsForGameType(table.gameType)} />

      <div className="flex items-center justify-start gap-x-2.5">
        <span className="text-[0.6875rem] font-bold text-[#828282]">
          {t("draw.result")}
        </span>

        {hasResult && isSettled ? (
          <div className="flex items-center gap-x-1.5">
            {/* Tổng chỉ có nghĩa với Lucky 28. Roulette và Baccarat không có tổng nên
                bỏ hẳn huy hiệu này thay vì hiện số 0 gây hiểu sai. */}
            {round?.kl28Sum != null ? (
              <span className="flex size-6 items-center justify-center rounded-full bg-[#5555F6] text-[0.8125rem] font-bold leading-normal text-white">
                {round.kl28Sum}
              </span>
            ) : null}

            {round?.kl28Sum != null ? (
              <>
                <span className="flex min-h-6 min-w-14 items-center justify-center rounded-full bg-[#F96C00] px-1.5 text-[0.8125rem] font-bold leading-normal text-white">
                  {t(round.kl28Sum >= 14 ? "draw.big" : "draw.small")}
                </span>
                <span className="flex min-h-6 min-w-14 items-center justify-center rounded-full bg-primary px-1.5 text-[0.8125rem] font-bold leading-normal text-white">
                  {t(round.kl28Sum % 2 === 0 ? "draw.double" : "draw.single")}
                </span>
              </>
            ) : null}

            {/* Baccarat không có lớn/nhỏ hay chẵn/lẻ — hiện bên thắng thay thế. */}
            {round?.baccaratResult ? (
              <span className="flex min-h-6 items-center justify-center rounded-full bg-primary px-2.5 text-[0.8125rem] font-bold leading-normal text-white">
                {round.baccaratResult}
              </span>
            ) : null}
          </div>
        ) : (
          <span className="flex h-6 items-center text-[0.6875rem] font-bold text-[#828282]">
            -
          </span>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-x-5 gap-y-1">
        <span className="text-[0.6875rem] font-bold text-[#828282]">
          {t("draw.current_match")}
        </span>

        <span className="flex items-center gap-x-1">
          <i aria-hidden="true" className="icon-icon15 text-[1rem] text-primary" />
          <span className="text-[0.6875rem] font-bold text-[#828282]">
            #{round?.roundSeq ?? "-"}
          </span>
        </span>

        <span className="flex items-center gap-x-1">
          <i aria-hidden="true" className="icon-icon48 text-[1rem] text-primary" />
          <span className="text-[0.6875rem] font-bold text-[#828282]">
            {formatDrawTime(round?.startedAt ?? null)}
          </span>
        </span>
      </div>
      </article>
    </Link>
  );
};
