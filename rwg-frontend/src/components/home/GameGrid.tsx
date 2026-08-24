"use client";

import React from "react";
import Image from "next/image";
import Link from "next/link";
import { useTranslation } from "@/context/LanguageContext";

interface GameItem {
  id: string;
  nameKey: string;
  image: string;
  /**
   * `gameType` của bàn ở backend, dùng làm tham số `?id=` của `/bet/detail`.
   *
   * Dùng `gameType` chứ không dùng UUID: đường dẫn ngắn, đọc được, và không đổi khi
   * seed lại dữ liệu. Trang chi tiết nhận cả hai dạng.
   */
  gameType: string;
}

/**
 * Bốn bàn chơi, thứ tự trái sang phải rồi xuống dòng.
 *
 * Ảnh tải từ trang gốc (`/element/game1..4.png`, 800×506) rồi chuyển sang WebP:
 * tổng 654 KB PNG xuống còn 132 KB, giảm 80% mà mắt thường không thấy khác.
 *
 * `gameType` phải khớp dữ liệu seed của backend. Bốn bàn dưới đây cùng dùng bộ cược
 * Lớn/Nhỏ/Lẻ/Chẵn nên trang đặt cược vẽ được ngay — xem `betOptionsFor`.
 */
const GAMES: GameItem[] = [
  {
    id: "lucky28",
    nameKey: "games.lucky28",
    // Tên file mang tên nội dung ảnh. Khi đổi ảnh mà GIỮ nguyên tên file, trình
    // duyệt vẫn dùng bản cũ trong bộ đệm (Next đặt ETag theo đường dẫn, không theo
    // nội dung) — đổi tên là cách chắc chắn nhất để nó tải lại.
    image: "/element/game1-roulette.webp",
    gameType: "LUCKY28",
  },
  {
    id: "british28",
    nameKey: "games.british28",
    image: "/element/game2.webp",
    gameType: "BRITISH_LUCKY28",
  },
  {
    id: "korean28",
    nameKey: "games.korean28",
    image: "/element/game3.webp",
    gameType: "KL28",
  },
  {
    id: "taiwantimes",
    nameKey: "games.taiwantimes",
    image: "/element/game4.webp",
    gameType: "TAIWAN_TIMES",
  },
];

/**
 * Lưới trò chơi 2 cột.
 *
 * `sizes` là BẮT BUỘC với `next/image fill`: thiếu nó Next mặc định `100vw` và tải
 * ảnh rộng bằng cả khung nhìn, trong khi mỗi ô chỉ chiếm một nửa — tốn gấp đôi
 * dung lượng, và trên mạng di động thì thấy rõ.
 *
 * Công thức: dưới 640px mỗi ô ≈ nửa khung nhìn trừ khoảng cách; từ 640px trở lên
 * khung bị chặn ở 640px nên ô cố định khoảng 310px.
 */
const GRID_IMAGE_SIZES = "(max-width: 640px) 50vw, 310px";

export const GameGrid: React.FC = () => {
  const { t } = useTranslation();

  return (
    <section className="w-full px-4 my-2">
      <h2 className="text-[0.9375rem] font-bold text-white mb-3 tracking-wide">
        {t("games.title")}
      </h2>

      <ul className="grid grid-cols-2 gap-3">
        {GAMES.map((game) => (
          <li key={game.id} className="flex">
            <Link
              href={`/bet/detail?id=${game.gameType.toLowerCase()}&ref=/`}
              className="group w-full bg-[#141417] border border-[#25252b] overflow-hidden flex flex-col transition-transform active:scale-[0.98]"
            >
              {/* `aspect-[800/506]` khớp ĐÚNG tỷ lệ ảnh gốc (1.581), không phải 16/10
                  (1.600). Lệch tỷ lệ thì `object-cover` sẽ cắt mất một dải ảnh — với
                  ảnh có mặt người ở sát mép trên thì thấy ngay. */}
              <div className="relative w-full aspect-[800/506] overflow-hidden">
                {/* `game-thumb-fade` làm ảnh mờ dần về đáy (xem globals.css). Ảnh hoà
                    vào chính nền của ô nên KHÔNG cần lớp phủ gradient, và cũng không
                    cần đường kẻ ngăn giữa ảnh với nhãn. */}
                <Image
                  src={game.image}
                  alt={t(game.nameKey)}
                  fill
                  sizes={GRID_IMAGE_SIZES}
                  className="object-cover game-thumb-fade"
                />
              </div>

              {/* min-h-11 ở nhãn: cả ô đã lớn hơn 44px, nhưng giữ mức này để nhãn
                  không bị bóp khi tên trò chơi xuống 2 dòng ở ngôn ngữ dài hơn.
                  `-mt-4` kéo nhãn lên nằm trong vùng ảnh đã mờ, nên chữ trông như
                  đặt trực tiếp trên ảnh chứ không phải ở một dải riêng bên dưới. */}
              <div className="relative -mt-4 min-h-11 px-2 pb-3 flex items-center justify-center">
                <span className="text-[0.8125rem] font-bold text-center text-[#d0d5da] leading-tight">
                  {t(game.nameKey)}
                </span>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
};
