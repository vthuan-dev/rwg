"use client";

import React from "react";
import Image from "next/image";
import type { GameRound } from "@/lib/playerApi";

/**
 * Ô số kết quả: ảnh quả cầu làm nền, số nằm giữa.
 *
 * Chưa có kết quả thì VẪN vẽ viên bi, chỉ thay số bằng dấu `?` — đúng như trang gốc.
 * Bỏ hẳn ảnh bi lúc chờ sẽ làm ô trông như một khung rỗng bị lỗi, và ảnh phải tải
 * lại từ đầu ở mỗi vòng thay vì đã nằm sẵn trong bộ đệm.
 *
 * `ball.webp` chỉ hiện ở kích thước 28px nên `sizes="28px"` — thiếu `sizes`, Next mặc
 * định `100vw` và tải bản ảnh rộng bằng cả khung nhìn cho một ô bé xíu.
 */
const ResultBall: React.FC<{ value: string | null }> = ({ value }) => (
  <div className="flex size-10 items-center justify-center rounded-[0.3125rem] border border-[#4D4D4D] bg-black shadow-[0_2px_5px_rgba(0,0,0,0.1)]">
    <div className="relative flex size-7 items-center justify-center">
      <Image
        alt=""
        aria-hidden="true"
        className="absolute size-7"
        height={400}
        sizes="28px"
        src="/element/ball.webp"
        width={400}
      />
      <span className="relative text-[0.8125rem] font-bold leading-[1.3125rem] text-black">
        {value ?? "?"}
      </span>
    </div>
  </div>
);

/**
 * Rút các số kết quả ra khỏi một vòng chơi, theo từng loại game.
 *
 * Ba loại game trả ba dạng dữ liệu khác nhau — trang mẫu chỉ vẽ được dạng Lucky 28
 * vì họ chỉ có loại đó:
 *
 * - Lucky 28 và biến thể: `kl28Numbers` là ba số cách nhau bằng dấu phẩy.
 * - Roulette: `winningNumber`, MỘT số duy nhất.
 * - Baccarat: quân bài hai bên, không có số nào để đặt vào ô — trả điểm hai bên.
 */
export function resultNumbers(round: GameRound | null): string[] {
  if (!round) return [];

  if (round.kl28Numbers) {
    return round.kl28Numbers.split(",").map((n) => n.trim()).filter(Boolean);
  }
  if (round.winningNumber != null) {
    return [String(round.winningNumber)];
  }
  if (round.baccaratPlayerScore != null && round.baccaratBankerScore != null) {
    return [String(round.baccaratPlayerScore), String(round.baccaratBankerScore)];
  }
  return [];
}

export interface DrawResultBallsProps {
  numbers: string[];
  /**
   * Số ô luôn hiển thị, kể cả khi chưa có kết quả.
   *
   * Cần thiết để chiều cao thẻ KHÔNG đổi giữa lúc chờ và lúc có kết quả — nếu để ô
   * tự xuất hiện thì cả danh sách 6 thẻ nhảy lên nhảy xuống mỗi vòng.
   */
  slots: number;
}

export const DrawResultBalls: React.FC<DrawResultBallsProps> = ({ numbers, slots }) => (
  <div className="relative flex items-center justify-start gap-x-5">
    {Array.from({ length: slots }, (_, index) => (
      <ResultBall key={index} value={numbers[index] ?? null} />
    ))}
  </div>
);
