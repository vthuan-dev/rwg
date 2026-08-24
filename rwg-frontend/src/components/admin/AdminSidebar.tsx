"use client";

import React, { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Users,
  CreditCard,
  Gamepad2,
  Network,
  Image as ImageIcon,
  BookText,
  Headphones,
  LogOut,
} from "lucide-react";
import { removeAdminToken, adminFetch, getAdminToken } from "@/lib/adminApi";
import { useTranslation } from "@/context/LanguageContext";
import { ADMIN_URL_PREFIX } from "@/lib/constants";

export const AdminSidebar: React.FC = () => {
  const pathname = usePathname();
  const { t } = useTranslation();

  const [pendingCount, setPendingCount] = useState(0);
  const [chatUnread, setChatUnread] = useState(0);

  const fetchPendingCount = useCallback(async () => {
    if (!getAdminToken()) return;
    try {
      const res = await adminFetch<{ pendingWithdrawals: number }>(
        "/admin/withdrawals/pending-count"
      );
      setPendingCount(res.pendingWithdrawals || 0);
    } catch {
      // Bỏ qua lỗi kết nối/auth khi polling nền
    }
  }, []);

  /**
   * Số LUỒNG đang chờ trả lời, không phải số tin.
   *
   * Viên đếm là để nói "có bao nhiêu người đang đợi", nên dùng `conversations`.
   * Lấy `messages` sẽ cho ra con số 40 chỉ vì một người gõ liên tục 40 dòng, và nhân
   * sự sẽ hiểu sai mức độ cấp bách.
   */
  const fetchChatUnread = useCallback(async () => {
    if (!getAdminToken()) return;
    try {
      const res = await adminFetch<{ messages: number; conversations: number }>(
        "/admin/chat/unread-count"
      );
      setChatUnread(res.conversations || 0);
    } catch {
      // Bỏ qua: cùng lý do như viên đếm lệnh rút ở trên.
    }
  }, []);

  useEffect(() => {
    void fetchPendingCount();
    void fetchChatUnread();

    // MỘT bộ đếm thời gian cho cả hai lời gọi: hai `setInterval` riêng sẽ lệch pha và
    // tạo ra hai đợt request rải rác thay vì một đợt gọn.
    const interval = setInterval(() => {
      void fetchPendingCount();
      void fetchChatUnread();
    }, 20000); // 20 giây

    return () => clearInterval(interval);
  }, [fetchPendingCount, fetchChatUnread]);

  /**
   * Chia nhóm theo nghiệp vụ thay vì một danh sách phẳng.
   *
   * Với 8 mục, danh sách phẳng buộc người dùng phải đọc từng dòng mới tìm được
   * thứ cần. Nhóm lại giúp nhắm đúng khối trước rồi mới đọc chi tiết.
   */
  const sections = [
    {
      label: t("admin.nav.group_operations"),
      items: [
        {
          href: `${ADMIN_URL_PREFIX}`,
          label: t("admin.nav.dashboard"),
          icon: LayoutDashboard,
        },
        {
          href: `${ADMIN_URL_PREFIX}/users`,
          label: t("admin.nav.users"),
          icon: Users,
        },
        // Hộp thư hỗ trợ nằm ở nhóm VẬN HÀNH, không phải nhóm hệ thống: nó là việc
        // phải làm hằng ngày và có người đang chờ ở đầu bên kia.
        {
          href: `${ADMIN_URL_PREFIX}/support`,
          label: t("admin.nav.support"),
          icon: Headphones,
        },
      ],
    },
    {
      label: t("admin.nav.group_finance"),
      items: [
        {
          href: `${ADMIN_URL_PREFIX}/payments`,
          label: t("admin.nav.payments"),
          icon: CreditCard,
        },
        {
          href: `${ADMIN_URL_PREFIX}/affiliates`,
          label: t("admin.nav.affiliates"),
          icon: Network,
        },
        // SỔ SÁCH Ở NHÓM TÀI CHÍNH, không ở nhóm cấu hình hệ thống: đối chiếu
        // sổ sách là việc tài chính làm thường xuyên, không phải một thiết lập.
        {
          href: `${ADMIN_URL_PREFIX}/ledger`,
          label: t("admin.nav.ledger"),
          icon: BookText,
        },
      ],
    },
    {
      label: t("admin.nav.group_system"),
      items: [
        {
          href: `${ADMIN_URL_PREFIX}/games`,
          label: t("admin.nav.games"),
          icon: Gamepad2,
        },
        {
          href: `${ADMIN_URL_PREFIX}/banners`,
          label: t("admin.nav.banners"),
          icon: ImageIcon,
        },
      ],
    },
  ];

  const handleLogout = () => {
    removeAdminToken();
    // URL TUYET DOI dung tu origin hien tai, khong gan duong dan tuong doi de trinh
    // duyet tu giai nghia. Dung window.location chu khong dung router cua Next: dang
    // xuat CAN tai lai ca trang de xoa sach state cu con trong bo nho.
    const loginPath = `${ADMIN_URL_PREFIX}/login`;
    window.location.href = new URL(loginPath, window.location.origin).toString();
  };

  return (
    <aside className="w-64 bg-[#0f172a] min-h-screen flex flex-col justify-between p-4 sticky top-0 h-screen select-none border-r border-white/5 overflow-y-auto">
      <div className="flex flex-col gap-5">
        {/* Logo. Anh goc 800x185 (ti le ~4.32:1) nen khai bao dung kich thuoc that
            de Next.js giu nguyen ti le va khong gay layout shift khi tai.
            Logo la chu mau trang nen BAT BUOC dat tren nen sam moi doc duoc. */}
        <Link
          href={ADMIN_URL_PREFIX}
          className="block px-2 py-3"
          aria-label={t("admin.title")}
        >
          <Image
            src="/logo/logo1.png"
            alt={t("admin.title")}
            width={800}
            height={185}
            priority
            className="h-auto w-full max-w-[190px]"
          />
        </Link>

        <nav className="flex flex-col gap-5">
          {sections.map((section) => (
            <div key={section.label} className="flex flex-col gap-1">
              <span className="px-3.5 pb-1 text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                {section.label}
              </span>
              {section.items.map((item) => {
                const Icon = item.icon;
                // Muc Tong quan trung prefix voi moi duong dan khac nen phai so
                // khop tuyet doi, khong dung startsWith.
                const isActive =
                  item.href === ADMIN_URL_PREFIX
                    ? pathname === ADMIN_URL_PREFIX
                    : pathname.startsWith(item.href);

                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    aria-current={isActive ? "page" : undefined}
                    className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${
                      isActive
                        ? "bg-red-500/15 border border-red-500/30 text-red-300"
                        : "text-slate-400 border border-transparent hover:text-white hover:bg-white/5"
                    }`}
                  >
                    <Icon
                      className={`w-4 h-4 shrink-0 ${
                        isActive ? "text-red-400" : "text-slate-500"
                      }`}
                    />
                    <span>{item.label}</span>
                    {item.href === `${ADMIN_URL_PREFIX}/payments` && pendingCount > 0 ? (
                      <span className="ms-auto flex items-center justify-center h-5 min-w-[20px] px-1.5 rounded-full bg-red-600 text-[10px] font-black text-white ring-2 ring-[#0f172a]">
                        {pendingCount}
                      </span>
                    ) : null}
                    {item.href === `${ADMIN_URL_PREFIX}/support` && chatUnread > 0 ? (
                      <span className="ms-auto flex items-center justify-center h-5 min-w-[20px] px-1.5 rounded-full bg-red-600 text-[10px] font-black text-white ring-2 ring-[#0f172a]">
                        {chatUnread}
                      </span>
                    ) : null}
                  </Link>
                );
              })}
            </div>
          ))}
        </nav>
      </div>

      <div className="pt-4 mt-4 border-t border-white/10">
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold text-slate-400 hover:text-red-300 hover:bg-red-500/10 transition-all border border-transparent hover:border-red-500/25 cursor-pointer"
        >
          <LogOut className="w-4 h-4 text-red-400" />
          <span>{t("admin.logout")}</span>
        </button>
      </div>
    </aside>
  );
};
