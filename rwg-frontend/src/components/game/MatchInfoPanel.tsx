"use client";

import React from "react";
import { useTranslation } from "@/context/LanguageContext";
import type { GameRound } from "@/lib/playerApi";
import { MatchTimer } from "@/components/game/MatchTimer";

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

export interface MatchInfoPanelProps {
  round: GameRound | null;
  /** Đường dẫn nút "Lịch sử quay số", đã gồm `?id=` và `?ref=`. */
  drawHistoryHref: string;
  /** Đường dẫn nút "Lịch sử cược". */
  betHistoryHref: string;
  /** Gọi khi một trong hai đồng hồ về 0, để tải lại dữ liệu vòng. */
  onExpired: () => void;
}

/**
 * Khối thông tin ván hiện tại: mã ván, thời gian, hai đồng hồ, hai nút lịch sử.
 *
 * HAI ĐỒNG HỒ có ý nghĩa khác nhau:
 * - "Ván tiếp theo" đếm tới `roundEndsAt` — lúc cả vòng xong và vòng mới bắt đầu.
 * - "Thời gian đặt cược" đếm tới `phaseEndsAt` — lúc ĐÓNG CỬA cược, sớm hơn.
 *
 * Đồng hồ thứ hai chỉ có nghĩa trong pha `BETTING_OPEN`. Ở các pha sau (đang quay, công
 * bố kết quả, thanh toán) thì cửa cược đã đóng, nên hiện `00:00` thay vì đếm tiếp thời
 * gian của một pha không liên quan gì đến việc đặt cược.
 */
export const MatchInfoPanel: React.FC<MatchInfoPanelProps> = ({
  round,
  drawHistoryHref,
  betHistoryHref,
  onExpired,
}) => {
  const { t } = useTranslation();

  const serverTime = round?.serverTime ?? new Date().toISOString();
  const bettingOpen = round?.phase === "BETTING_OPEN";

  return (
    <div className="flex flex-col px-5 py-4">
      <div className="flex items-start justify-between gap-x-3.5">
        <div className="flex flex-1 flex-col gap-y-3">
          <div className="text-[0.6875rem] font-bold text-[#828282]">
            {t("bet.current_match")}
          </div>

          <div className="flex items-center gap-x-2">
            <i aria-hidden="true" className="icon-icon15 text-[1rem] text-primary" />
            <span className="text-[0.6875rem] font-bold text-[#828282]">
              #{round?.roundSeq ?? "-"}
            </span>
          </div>

          <div className="flex items-center gap-x-2">
            <i aria-hidden="true" className="icon-icon48 text-[1rem] text-primary" />
            <span className="text-[0.6875rem] font-bold text-[#828282]">
              {formatMatchTime(round?.startedAt ?? null)}
            </span>
          </div>
        </div>

        <div className="flex flex-1 gap-x-6">
          <div className="flex flex-col gap-y-1">
            <div className="text-[0.6875rem] font-bold text-[#828282]">
              {t("bet.next_match")}
            </div>
            <MatchTimer
              className="text-[1rem] font-bold leading-normal text-primary"
              endsAt={round?.roundEndsAt ?? null}
              onExpired={onExpired}
              serverTime={serverTime}
            />
          </div>

          <div className="flex flex-col gap-y-1">
            <div className="text-[0.6875rem] font-bold text-[#828282]">
              {t("bet.bet_time")}
            </div>
            {bettingOpen ? (
              <MatchTimer
                className="text-[1rem] font-bold leading-normal text-primary"
                endsAt={round?.phaseEndsAt ?? null}
                // KHÔNG truyền onExpired: đồng hồ "Ván tiếp theo" đã lo việc tải lại.
                // Hai đồng hồ cùng gọi thì thành hai lượt gọi API cho một sự kiện.
                serverTime={serverTime}
              />
            ) : (
              <span className="text-[1rem] font-bold leading-normal text-primary">
                {round ? "00:00" : "--:--"}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Hai nút lịch sử. Dùng <a> thường chứ không next/link: hai trang này chưa tồn
          tại, next/link sẽ tải trước và nhận 404 ngay khi khối này hiện ra. */}
      <div className="mt-5 flex items-center justify-between gap-x-3.5 text-primary">
        <a
          className="flex flex-1 items-center justify-center border border-primary py-1.5"
          href={drawHistoryHref}
        >
          <i aria-hidden="true" className="icon-icon5 size-3.5 text-[0.875rem]" />
          <span className="ms-1.5 text-[0.6875rem] font-bold">{t("bet.draw_history")}</span>
        </a>
        <a
          className="flex flex-1 items-center justify-center border border-primary py-1.5"
          href={betHistoryHref}
        >
          <i aria-hidden="true" className="icon-icon5 size-3.5 text-[0.875rem]" />
          <span className="ms-1.5 text-[0.6875rem] font-bold">{t("bet.bet_history")}</span>
        </a>
      </div>
    </div>
  );
};
