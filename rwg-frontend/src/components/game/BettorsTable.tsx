"use client";

import React, { useEffect, useState } from "react";
import { motion, useReducedMotion } from "framer-motion";
import { useTranslation } from "@/context/LanguageContext";
import { formatMoney } from "@/lib/money";
import type { PlayerBet } from "@/lib/playerApi";

/** Chiều cao mỗi dòng, tính bằng px. Phải khớp `ROW_HEIGHT` dùng trong phép dịch. */
const ROW_HEIGHT = 30;
/** Số dòng nhìn thấy cùng lúc. */
const VISIBLE_ROWS = 3;
/** Khoảng thời gian cuộn sang dòng tiếp theo. */
const SCROLL_INTERVAL_MS = 3000;

/** Định dạng `h:mm:ss A` cho cột thời gian. */
function formatBetTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "-";
  const pad = (n: number) => String(n).padStart(2, "0");
  const h24 = d.getHours();
  const h12 = h24 % 12 === 0 ? 12 : h24 % 12;
  return `${h12}:${pad(d.getMinutes())}:${pad(d.getSeconds())} ${h24 < 12 ? "AM" : "PM"}`;
}

/** Sáu ký tự cuối của id, dùng làm mã cược ngắn cho dễ đọc. */
function shortCode(id: string): string {
  return id.replace(/-/g, "").slice(-6).toUpperCase();
}

export interface BettorsTableProps {
  bets: PlayerBet[];
  /** Số thứ tự ván, hiện ở cột "Mã lượt quay". */
  roundSeq: number | null;
  /** Nhãn của từng loại cược, để cột "Mã cược" đọc được. */
  labelFor: (betType: string) => string;
}

/**
 * Bảng danh sách cược, tự cuộn.
 *
 * KHÁC BẢN GỐC MỘT ĐIỂM QUAN TRỌNG: trang gốc hiện cược của MỌI người chơi, lấy từ
 * endpoint `display-bets`, kèm dòng "1738833 người dùng đặt cược" — con số đó là hằng số
 * viết cứng trong mã nguồn của họ, không phải dữ liệu thật.
 *
 * Backend RWG không có endpoint trả cược của người khác, và thêm một endpoint như vậy là
 * quyết định về quyền riêng tư chứ không phải việc dựng giao diện. Nên bảng này hiện cược
 * của CHÍNH người đang đăng nhập trong ván hiện tại, với số đếm là con số thật.
 *
 * Cuộn dừng lại khi đã tới dòng cuối, đúng như bản gốc — không quay vòng vô hạn.
 */
export const BettorsTable: React.FC<BettorsTableProps> = ({ bets, roundSeq, labelFor }) => {
  const { t } = useTranslation();
  const reduceMotion = useReducedMotion();
  const [offset, setOffset] = useState(0);

  /**
   * Danh sách đổi thì cuộn lại từ đầu.
   *
   * Điều chỉnh state ngay trong thân render thay vì trong `useEffect`: đây là mẫu React
   * khuyến nghị cho việc đồng bộ state theo props. Gọi `setOffset` trong effect sẽ vẽ
   * một lần với vị trí cuộn CŨ rồi mới vẽ lại — thấy được thành một nháy dịch dòng.
   */
  const [seenCount, setSeenCount] = useState(bets.length);
  if (seenCount !== bets.length) {
    setSeenCount(bets.length);
    setOffset(0);
  }

  useEffect(() => {
    if (bets.length <= VISIBLE_ROWS) return;

    const timer = setInterval(() => {
      setOffset((prev) => {
        // Dừng khi dòng cuối đã lên tới đáy vùng nhìn thấy.
        if (prev + VISIBLE_ROWS >= bets.length) return prev;
        return prev + 1;
      });
    }, SCROLL_INTERVAL_MS);

    return () => clearInterval(timer);
  }, [bets.length]);

  // Kẹp lại cho chắc: nếu danh sách bị ngắn đi giữa hai nhịp đếm thì phần bù cũ sẽ trỏ
  // ra ngoài mảng và bảng cuộn hết ra khỏi vùng nhìn thấy.
  const shownOffset = Math.min(offset, Math.max(0, bets.length - VISIBLE_ROWS));

  return (
    <div>
      <p className="mb-4 text-center text-[0.6875rem] font-bold text-primary">
        {bets.length} {t("bet.user_betting")}
      </p>

      <div
        className="relative overflow-hidden"
        style={{ maxHeight: `${(VISIBLE_ROWS + 1) * ROW_HEIGHT + 20}px` }}
      >
        <table className="w-full text-[0.625rem]">
          <thead>
            <tr className="text-[#979797]">
              <th className="text-start font-normal">{t("bet.user")}</th>
              <th className="text-end font-normal">{t("bet.draw_no")}</th>
              <th className="text-end font-normal">{t("bet.stake_column")}</th>
              <th className="text-end font-normal">{t("bet.bet_no")}</th>
              <th className="text-end font-normal">{t("bet.bet_time")}</th>
            </tr>
          </thead>

          {bets.length > 0 ? (
            <motion.tbody
              animate={{ y: reduceMotion ? 0 : -(ROW_HEIGHT * shownOffset) }}
              className="relative w-full overflow-hidden"
              style={{ maxHeight: `${VISIBLE_ROWS * ROW_HEIGHT}px` }}
              transition={{ duration: 0.6, ease: "easeInOut" }}
            >
              {bets.map((bet, index) => (
                <tr
                  className={`font-bold text-white transition-opacity duration-500 ${
                    index < shownOffset ? "opacity-0" : "opacity-100"
                  }`}
                  key={bet.id}
                  style={{ height: `${ROW_HEIGHT}px` }}
                >
                  <td className="py-2 text-start">{t("bet.you")}</td>
                  <td className="text-end">{roundSeq ?? "-"}</td>
                  <td className="text-end">${formatMoney(bet.stake)}</td>
                  <td className="text-end">{labelFor(bet.betType)}</td>
                  <td className="text-end">{formatBetTime(bet.createdAt)}</td>
                </tr>
              ))}
            </motion.tbody>
          ) : (
            <tbody>
              <tr>
                <td className="py-4 text-center text-[#828282]" colSpan={5}>
                  {t("bet.no_bets_yet")}
                </td>
              </tr>
            </tbody>
          )}
        </table>
      </div>
    </div>
  );
};

/** Mã cược ngắn, xuất riêng để chỗ khác dùng lại nếu cần. */
export { shortCode };
