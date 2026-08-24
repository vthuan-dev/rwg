"use client";

import React from "react";
import Link from "next/link";
import { useTranslation } from "@/context/LanguageContext";
import type { GameRound, GameTable } from "@/lib/playerApi";
import {
  drawRoomNumber,
  drawSummary,
  drawTier,
  formatDrawTime,
} from "@/lib/drawDisplay";

/**
 * Thẻ một sảnh trên trang lịch sử quay số: đầu thẻ có ảnh nền, rồi bảng bốn ván gần nhất.
 *
 * CẤU TRÚC LẤY TỪ BUNDLE CỦA TRANG GỐC, không phải phỏng theo ảnh chụp. Bốn chi tiết
 * dưới đây nhìn ảnh sẽ làm sai, nên ĐỪNG "sửa cho đẹp hơn":
 *
 * 1. KHÔNG bo góc ở bất kỳ tầng nào. Thẻ ở trang `/bet` có `rounded-md`, thẻ này không.
 * 2. Nhãn hạng nằm SAU tên sảnh trong markup, được `flex-col-reverse` lật lên trên. Viết
 *    xuôi rồi bỏ `flex-col-reverse` thì khoảng cách `gap-y` phân bố khác đi.
 * 3. Chữ trong hai viên thuốc Nhỏ/Lớn và Chẵn/Lẻ là `#1F1F1F` (gần đen), KHÔNG phải
 *    trắng. Trên nền cam `#F96C00` và đỏ `#ff4355`, chữ trắng nhìn xa trông giống nhưng
 *    độ tương phản thấp hơn hẳn.
 * 4. Các dòng KHÔNG có đường kẻ. Phân tách chỉ nhờ nền `#191919` của hàng tiêu đề khác
 *    `#1D1D1D` của thân bảng.
 *
 * VÌ SAO KHÔNG DÙNG LẠI `GameHallCard`: thẻ đó vẽ MỘT ván kèm đồng hồ đếm ngược và ô bi
 * có ảnh `ball.webp`; thẻ này vẽ BẢNG bốn ván với vòng tròn màu đặc. Nhồi hai chế độ vào
 * một component sẽ thành một mớ điều kiện chỉ để dùng chung đúng một dòng `bg-room-*`.
 */
export interface DrawHistoryHallCardProps {
  table: GameTable;
  /** Các ván đã xong, mới nhất trước. Bản gốc hiện 4 dòng mỗi sảnh. */
  rounds: GameRound[];
  /** Thứ tự trong danh sách, quyết định ảnh nền và hạng. */
  index: number;
}

/** Một vòng tròn số. Bản gốc dùng cùng một kiểu cho cả ô tổng và các ô số. */
const ResultCircle: React.FC<{ value: string | number }> = ({ value }) => (
  <div className="flex size-6 items-center justify-center rounded-full bg-[#5555F6] text-[0.8125rem] font-bold text-white">
    {value}
  </div>
);

export const DrawHistoryHallCard: React.FC<DrawHistoryHallCardProps> = ({
  table,
  rounds,
  index,
}) => {
  const { t, locale } = useTranslation();

  const tier = drawTier(index);
  const roomNumber = drawRoomNumber(index);

  // `nameI18n` chỉ có en/vi/zh/ja. Với ms/ko phải rơi về `en`, không thì tên sảnh trống
  // hoàn toàn.
  const tableName = table.nameI18n[locale] ?? table.nameI18n.en ?? table.gameType;

  // Đường dẫn chi tiết nhận `gameType` chữ thường — `/draw/history` đã tự đổi sang UUID.
  const detailHref = `/draw/history?id=${table.gameType.toLowerCase()}&ref=/draw`;

  return (
    <div className="flex flex-col">
      {/* Tầng 1 — đầu thẻ. Cả dải này bấm được, đúng như bản gốc: họ bọc `Link` quanh
          phần đầu chứ không quanh cả thẻ, nên bấm vào một dòng kết quả KHÔNG điều hướng. */}
      <Link
        className={`flex items-center justify-between bg-[#1d1d1d] px-5 py-5.5 bg-room-${roomNumber}`}
        href={detailHref}
      >
        <div className="flex flex-col-reverse gap-y-1.5">
          <div className="text-[0.875rem] font-bold leading-[1rem] text-[#D8D8D8]">
            {tableName}
          </div>
          {/* Màu đặt inline: Tailwind không sinh được lớp từ giá trị tính lúc chạy. */}
          <div
            className="max-w-fit rounded-full border bg-black px-2.25 text-[0.8125rem] font-bold leading-normal"
            style={{ borderColor: tier.color, color: tier.color }}
          >
            {t(tier.labelKey)}
          </div>
        </div>

        {/* `max-h-fit` để nút không bị kéo cao bằng khối tên sảnh hai dòng bên trái. */}
        <div className="max-h-fit bg-[#FF4355] px-3 py-1 text-[0.688rem] font-medium leading-normal text-white">
          {t("draw.more_result")}
        </div>
      </Link>

      {/* Tầng 2 — hàng tiêu đề. Nền sáng hơn thân bảng một chút để tách hai vùng mà
          không cần đường kẻ. */}
      <div className="flex gap-x-4 bg-[#191919] px-5 py-3 text-[0.625rem] font-bold leading-normal text-[#FFF]">
        <div className="min-w-[100px]">{t("draw.match_column")}</div>
        <div className="w-full text-center">{t("draw.result_column")}</div>
      </div>

      {/* Tầng 3 — thân bảng. */}
      <div className="flex flex-col bg-[#1D1D1D] leading-normal">
        {rounds.length === 0 ? (
          <div className="px-5 py-3 text-center text-[0.625rem] font-bold text-[#828282]">
            {t("draw.no_records")}
          </div>
        ) : (
          rounds.map((round) => {
            const summary = drawSummary(round);

            return (
              <div
                className="flex items-center gap-x-3 px-5 py-3"
                key={round.roundId}
              >
                <div className="min-w-[100px] text-[0.625rem]">
                  <span className="font-bold">#{round.roundSeq}</span>
                  <br />
                  <span>{formatDrawTime(round.startedAt)}</span>
                </div>

                <div className="flex w-full flex-col items-center gap-y-2 sm:flex-row sm:justify-center">
                  {summary.voided ? (
                    <span className="text-[0.625rem] font-bold text-[#828282]">
                      {t("bet.round_voided")}
                    </span>
                  ) : (
                    <>
                      <div className="flex items-center">
                        {/* Ô tổng và dấu "+" CHỈ có với Lucky 28. Roulette và Baccarat
                            không có khái niệm tổng, hiện số 0 ở đây là nói sai kết quả. */}
                        {summary.sum != null ? (
                          <>
                            <ResultCircle value={summary.sum} />
                            <div className="mx-2.5 text-[0.8125rem] font-bold text-white">
                              +
                            </div>
                          </>
                        ) : null}

                        <div className="flex items-center gap-x-1">
                          {summary.numbers.map((n, i) => (
                            <ResultCircle key={i} value={n} />
                          ))}
                        </div>
                      </div>

                      <div className="flex items-center">
                        {/* Nhỏ/Lớn và Chẵn/Lẻ: chỉ Lucky 28 có. Baccarat thay bằng bên
                            thắng. Roulette không có nhãn nào — để trống thay vì vẽ hai
                            viên thuốc rỗng. */}
                        {summary.sizeKey ? (
                          <div className="ms-1 flex min-h-6 min-w-14 items-center justify-center rounded-full bg-[#F96C00] px-1.5">
                            <span className="text-[0.8125rem] font-bold leading-normal text-[#1F1F1F]">
                              {t(summary.sizeKey)}
                            </span>
                          </div>
                        ) : null}

                        {summary.parityKey ? (
                          <div className="ms-1 flex min-h-6 min-w-14 items-center justify-center rounded-full bg-primary px-1.5">
                            <span className="text-[0.8125rem] font-bold leading-normal text-[#1F1F1F]">
                              {t(summary.parityKey)}
                            </span>
                          </div>
                        ) : null}

                        {summary.baccaratResult ? (
                          <div className="ms-1 flex min-h-6 items-center justify-center rounded-full bg-primary px-2.5">
                            <span className="text-[0.8125rem] font-bold leading-normal text-[#1F1F1F]">
                              {summary.baccaratResult}
                            </span>
                          </div>
                        ) : null}
                      </div>
                    </>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
