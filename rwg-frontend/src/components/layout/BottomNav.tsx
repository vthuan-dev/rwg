"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Home, Gamepad2, FileText, User } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

interface NavItem {
  href: string;
  labelKey: string;
  icon: React.ComponentType<{ className?: string }>;
}

const NAV_ITEMS: NavItem[] = [
  { href: "/", labelKey: "nav.home", icon: Home },
  { href: "/bet", labelKey: "nav.games", icon: Gamepad2 },
  { href: "/draw", labelKey: "nav.history", icon: FileText },
  { href: "/profile", labelKey: "nav.profile", icon: User },
];

/**
 * Xác định mục nào đang được chọn.
 *
 * Trang chủ so khớp CHÍNH XÁC, còn lại so theo tiền tố. Nếu dùng tiền tố cho cả
 * "/" thì mọi đường dẫn đều bắt đầu bằng "/" nên mục trang chủ luôn sáng.
 *
 * `/draw` VÀ `/draw/history` đều thuộc mục "Lịch sử quay số" — `/draw` là danh
 * sách sảnh, `/draw/history` là chi tiết một sảnh. Trước đây `/draw/...` được gán
 * cho mục "Trò chơi" vì lúc đó chỉ có trang chi tiết và nó vào từ `/bet`; giờ
 * `/draw` là đích của chính mục này nên phải trả về đây, không thì đứng ở `/draw`
 * sẽ thấy HAI mục cùng sáng.
 */
const isItemActive = (pathname: string, href: string): boolean => {
  if (href === "/") return pathname === "/";
  if (href === "/bet") return pathname.startsWith("/bet");
  return pathname === href || pathname.startsWith(`${href}/`);
};

/**
 * Thanh điều hướng dưới cho khu người chơi.
 *
 * Chiều cao lấy từ biến `--bottom-nav-height` trong globals.css, cùng biến mà
 * khung nội dung dùng để chừa chỗ — nếu mỗi bên giữ một số riêng thì nội dung
 * cuối trang sẽ bị thanh này che.
 *
 * `pb-[--safe-bottom]`: `fixed bottom-0` đặt cạnh dưới trùng đúng chỗ thanh
 * gesture của iPhone, nhãn chữ bị che một phần và hệ điều hành ăn bớt vùng bấm.
 *
 * CỐ TÌNH KHÔNG dùng `hover:` cho mục không active: trên màn cảm ứng trạng thái
 * hover bị "dính" sau khi bấm, nên mục vừa bấm giữ màu sáng trông như đang được
 * chọn dù đã sang trang khác. `active:` chỉ hiện lúc ngón tay còn chạm.
 */
export const BottomNav: React.FC = () => {
  const pathname = usePathname();
  const { t } = useTranslation();

  return (
    <nav
      aria-label={t("nav.home")}
      // Nền đen trong suốt, làm mờ những gì trôi qua bên dưới.
      //
      // `bg-black/90` là nền MẶC ĐỊNH cho trình duyệt không có `backdrop-filter`:
      // thiếu lớp mờ thì nội dung cuộn qua dưới thanh vẫn đọc được xuyên qua và
      // nhãn chữ lẫn vào ảnh. Chỉ khi hỗ trợ mới hạ xuống `bg-black/60` để thấy
      // rõ độ trong.
      //
      // `border-white/5` thay vì màu đặc `#222228`: viền đặc trên nền trong suốt
      // trông như một vạch nổi lơ lửng, còn viền trắng mờ thì hoà theo nền.
      className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full sm:max-w-[640px] z-50 bg-black/90 supports-[backdrop-filter]:bg-black/60 backdrop-blur-xl border-t border-white/5"
      style={{ paddingBottom: "var(--safe-bottom)" }}
    >
      <ul className="grid grid-cols-4" style={{ height: "var(--bottom-nav-height)" }}>
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const isActive = isItemActive(pathname, item.href);
          return (
            <li key={item.href} className="flex">
              <Link
                href={item.href}
                // min-h-11 = 44px: mức tối thiểu cho mục bấm bằng ngón tay.
                className={`flex flex-col items-center justify-center gap-1 w-full min-h-11 transition-colors active:bg-white/5 ${
                  isActive ? "text-primary" : "text-[#8b8b8b]"
                }`}
                aria-current={isActive ? "page" : undefined}
              >
                <Icon className="w-5 h-5" />
                <span
                  className={`text-[0.6875rem] leading-none ${
                    isActive ? "font-bold" : "font-normal"
                  }`}
                >
                  {t(item.labelKey)}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
};
