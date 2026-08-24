"use client";

import React from "react";
import Link from "next/link";
import { CreditCard, Wallet, Receipt, Headphones } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";
import { useNotification } from "@/context/NotificationContext";

interface QuickAction {
  id: string;
  titleKey: string;
  icon: React.ComponentType<{ className?: string }>;
  href: string;
}

/**
 * Bốn lối tắt, dùng ĐÚNG đường dẫn của trang gốc.
 *
 * ĐỪNG rút ngắn thành `/deposit` hay `/history`: đó là các đường dẫn KHÔNG tồn tại ở
 * bản gốc (đã đối chiếu `_buildManifest.js` của họ — chỉ có `/asset/deposit`,
 * `/asset/withdraw`, `/asset/history`, và trang hỗ trợ nằm trong khu hồ sơ ở
 * `/profile/contact-us`). Dùng đường dẫn tự đặt thì mỗi liên kết cũ người dùng còn lưu
 * đều thành 404, và sau này dựng trang thật lại phải đi sửa liên kết lần nữa.
 */
const ACTIONS: QuickAction[] = [
  {
    id: "deposit",
    titleKey: "quick_actions.deposit",
    icon: CreditCard,
    href: "/asset/deposit",
  },
  {
    id: "withdraw",
    titleKey: "quick_actions.withdraw",
    icon: Wallet,
    href: "/asset/withdraw",
  },
  {
    id: "history",
    titleKey: "quick_actions.history",
    icon: Receipt,
    href: "/asset/history",
  },
  {
    id: "support",
    titleKey: "quick_actions.support",
    icon: Headphones,
    href: "/profile/contact-us",
  },
];

/**
 * Bốn lối tắt chính, xếp lưới 2×2.
 *
 * Dùng `Link` chứ không `button`: đây là điều hướng sang trang khác, nên phải cho
 * người dùng mở tab mới / dùng phím được, và trình đọc màn hình cần đọc ra là liên
 * kết chứ không phải nút bấm.
 */
export const QuickActions: React.FC = () => {
  const { t } = useTranslation();
  // Lấy từ context thay vì tự gọi API: con số này đã được nạp sẵn và được WebSocket
  // cập nhật, nên thêm một request ở đây là dư.
  const { chatUnreadCount } = useNotification();

  return (
    <nav className="w-full px-4 my-4" aria-label={t("games.title")}>
      {/* `gap-3` (12px) thay vì `gap-2`: bốn ô tách nhau rõ hơn, đọc ra là bốn lối
          tắt riêng biệt chứ không phải một khối bị chia bằng đường kẻ. */}
      <ul className="grid grid-cols-2 gap-3">
        {ACTIONS.map((action) => {
          const Icon = action.icon;
          const badge = action.id === "support" ? chatUnreadCount : 0;
          return (
            <li key={action.id} className="flex">
              <Link
                href={action.href}
                // `py-4` + `gap-2.5`: icon và nhãn thở ra, ô cao hơn một chút cho
                // cân với khoảng cách mới giữa các ô.
                className="relative w-full min-h-11 py-4 flex flex-col items-center justify-center gap-2.5 bg-[#1b1b1f] border border-[#28282e] transition-colors active:bg-[#242429]"
              >
                <Icon className="w-5 h-5 text-primary" />
                {/* Cột rộng gấp đôi so với lần xếp 4 cột nên nhãn dài nhất ("Trò chuyện
                    trực tiếp") vừa một dòng — tăng chữ từ 10px lên 11px được. Vẫn giữ
                    `leading-tight` và `text-center` cho các ngôn ngữ có nhãn dài hơn. */}
                <span className="px-1 text-[0.6875rem] leading-tight text-center text-[#d0d5da] font-semibold">
                  {t(action.titleKey)}
                </span>
                {badge > 0 && (
                  // Đặt tuyệt đối ở góc, KHÔNG chèn vào luồng: thêm một phần tử vào
                  // cột dọc sẽ làm ô hỗ trợ cao hơn ba ô còn lại và lưới bị lệch.
                  <span
                    className="absolute top-2 end-2 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[0.5625rem] font-bold text-white"
                    aria-hidden="true"
                  >
                    {badge > 99 ? "99+" : badge}
                  </span>
                )}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
};
