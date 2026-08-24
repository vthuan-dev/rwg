"use client";

import React from "react";
import Image from "next/image";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Logo phương thức thanh toán, thứ tự đúng như trang gốc
 * (`footer_logo4` … `footer_logo13`).
 *
 * Ảnh gốc đều 400×400 PNG có kênh trong suốt, đã chuyển sang WebP với
 * `alphaQuality: 100` — nén mạnh kênh alpha sẽ sinh viền răng cưa thấy rõ trên nền
 * đen của trang.
 */
const PAYMENT_LOGOS = [
  { src: "/element/footer_logo4.webp", alt: "USDT" },
  { src: "/element/footer_logo5.webp", alt: "Bitcoin" },
  { src: "/element/footer_logo6.webp", alt: "Ethereum" },
  { src: "/element/footer_logo7.webp", alt: "J9" },
  { src: "/element/footer_logo8.webp", alt: "BUSD" },
  { src: "/element/footer_logo9.webp", alt: "XRP" },
  { src: "/element/footer_logo10.webp", alt: "USDC" },
  { src: "/element/footer_logo11.webp", alt: "Dogecoin" },
  { src: "/element/footer_logo12.webp", alt: "TRON" },
  { src: "/element/footer_logo13.webp", alt: "VISA" },
];

/**
 * Nhà cung cấp trò chơi (`footer_logo15` … `footer_logo18`).
 *
 * Mỗi logo có tỷ lệ khác nhau nên phải truyền kích thước THẬT của từng ảnh, không
 * dùng một cặp width/height chung: sai tỷ lệ là logo bị bóp méo.
 */
const PROVIDER_LOGOS = [
  { src: "/element/footer_logo15.webp", alt: "Asia Gaming", width: 400, height: 260 },
  { src: "/element/footer_logo16.webp", alt: "BGaming", width: 400, height: 169 },
  { src: "/element/footer_logo17.webp", alt: "Evolution Gaming", width: 400, height: 264 },
  { src: "/element/footer_logo18.webp", alt: "Microgaming", width: 400, height: 196 },
];

export const FooterInfo: React.FC = () => {
  const { t } = useTranslation();

  return (
    // KHÔNG thêm padding đáy để tránh thanh điều hướng: MobileShell đã chừa chỗ
    // bằng biến --bottom-nav-total. Giữ cả hai sẽ hở gần 100px cuối trang.
    <footer className="w-full px-5 pt-6 pb-4 flex flex-col gap-6">
      {/* Hợp tác (1/3) + Chứng nhận (2/3), đúng tỷ lệ cột của trang gốc. */}
      <div className="flex gap-x-6">
        <div className="w-1/3">
          <h2 className="mb-2 text-[0.625rem] font-bold text-white">
            {t("footer.partners")}
          </h2>
          <Image
            alt="FIBA"
            className="block w-full h-auto"
            height={187}
            src="/element/footer_logo_1.webp"
            width={400}
          />
        </div>

        <div className="w-2/3">
          <h2 className="text-[0.625rem] font-bold text-white">
            {t("footer.certifications")}
          </h2>
          {/* `items-start` để hai logo cao khác nhau vẫn thẳng mép trên, không bị
              logo cao hơn đẩy logo kia xuống giữa. */}
          <div className="mt-2 flex items-start gap-x-4">
            <Image
              alt="Gaming Curaçao"
              className="h-auto w-[120px] max-w-full"
              height={148}
              src="/element/footer_logo_2.webp"
              width={400}
            />
            <Image
              alt="18+"
              className="size-12 shrink-0"
              height={400}
              src="/element/footer_logo3.webp"
              width={400}
            />
          </div>
        </div>
      </div>

      {/* Thanh toán trực tuyến: 7 cột như trang gốc, 3 logo còn lại tự xuống dòng. */}
      <div>
        <h2 className="mb-2 text-[0.625rem] font-bold text-white">
          {t("footer.online_payment")}
        </h2>
        {/* Kích thước cố định 36px thay vì `w-full`: khung có thể rộng tới 640px,
            để logo giãn theo cột thì mỗi icon phình lên ~75px, gấp đôi thiết kế. */}
        <div className="grid grid-cols-7 gap-3">
          {PAYMENT_LOGOS.map((logo) => (
            <Image
              alt={logo.alt}
              className="size-9"
              height={400}
              key={logo.src}
              src={logo.src}
              width={400}
            />
          ))}
        </div>
      </div>

      {/* Nhà cung cấp trò chơi: 4 cột. */}
      <div>
        <h2 className="mb-2 text-[0.625rem] font-bold text-white">
          {t("footer.game_providers")}
        </h2>
        <div className="grid grid-cols-4 items-center gap-5">
          {PROVIDER_LOGOS.map((logo) => (
            // `max-h-16` (64px) + `max-w-full`: chặn theo CHIỀU CAO để bốn logo trông
            // cân nhau về mặt quang học dù tỷ lệ khác nhau, đồng thời không tràn khỏi
            // cột trên máy hẹp. Logo rộng nhất (Evolution, tỷ lệ 1.52) ở 64px cao chỉ
            // rộng ~97px, vẫn vừa cột ~135px của khung 640px. Trên máy 375px cột co
            // xuống ~72px nên `max-w-full` sẽ hạ chiều cao thực tế — đúng ý muốn.
            <Image
              alt={logo.alt}
              className="mx-auto h-auto max-h-16 w-auto max-w-full"
              height={logo.height}
              key={logo.src}
              src={logo.src}
              width={logo.width}
            />
          ))}
        </div>
      </div>
    </footer>
  );
};
