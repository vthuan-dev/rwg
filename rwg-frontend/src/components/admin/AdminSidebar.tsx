"use client";

import React from "react";
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
  X,
} from "lucide-react";
import { removeAdminToken } from "@/lib/adminApi";
import { canViewLedger } from "@/lib/adminIdentity";
import { useTranslation } from "@/context/LanguageContext";
import { ADMIN_URL_PREFIX } from "@/lib/constants";
import { useAdminLayout } from "@/components/admin/AdminLayoutContext";

export const AdminSidebar: React.FC = () => {
  const pathname = usePathname();
  const { t } = useTranslation();
  const {
    isMobileSidebarOpen,
    closeMobileSidebar,
    pendingCount,
    chatUnread,
  } = useAdminLayout();

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
        ...(canViewLedger()
          ? [
              {
                href: `${ADMIN_URL_PREFIX}/ledger`,
                label: t("admin.nav.ledger"),
                icon: BookText,
              },
            ]
          : []),
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
    // URL TUYỆT ĐỐI dựng từ origin hiện tại, không gán đường dẫn tương đối để trình
    // duyệt tự giải nghĩa. Dùng window.location chứ không dùng router của Next: đăng
    // xuất CẦN tải lại cả trang để xóa sạch state cũ còn trong bộ nhớ.
    const loginPath = `${ADMIN_URL_PREFIX}/login`;
    window.location.href = new URL(loginPath, window.location.origin).toString();
  };

  const renderNavLinks = (onItemClick?: () => void) => (
    <nav className="flex flex-col gap-5">
      {sections.map((section) => (
        <div key={section.label} className="flex flex-col gap-1">
          <span className="px-3.5 pb-1 text-[10px] font-bold text-slate-500 uppercase tracking-widest">
            {section.label}
          </span>
          {section.items.map((item) => {
            const Icon = item.icon;
            // Mục Tổng quan trùng prefix với mọi đường dẫn khác nên phải so
            // khớp tuyệt đối, không dùng startsWith.
            const isActive =
              item.href === ADMIN_URL_PREFIX
                ? pathname === ADMIN_URL_PREFIX
                : pathname.startsWith(item.href);

            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onItemClick}
                aria-current={isActive ? "page" : undefined}
                className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${
                  isActive
                    ? "bg-red-500/15 border border-red-500/30 text-red-300"
                    : "text-slate-400 border border-transparent hover:text-white hover:bg-white/5 active:bg-white/10"
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
  );

  return (
    <>
      {/* ===================== DESKTOP SIDEBAR ===================== */}
      <aside className="hidden lg:flex w-64 bg-[#0f172a] min-h-screen flex-col justify-between p-4 sticky top-0 h-screen select-none border-r border-white/5 overflow-y-auto shrink-0 z-30">
        <div className="flex flex-col gap-5">
          {/* Logo Resorts World Genting */}
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

          {renderNavLinks()}
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

      {/* ===================== MOBILE DRAWER BACKDROP ===================== */}
      {isMobileSidebarOpen && (
        <div
          className="fixed inset-0 bg-slate-950/75 backdrop-blur-xs z-50 lg:hidden transition-opacity duration-300 animate-in fade-in"
          onClick={closeMobileSidebar}
          aria-hidden="true"
        />
      )}

      {/* ===================== MOBILE OFF-CANVAS DRAWER ===================== */}
      <aside
        className={`fixed inset-y-0 left-0 z-50 w-72 max-w-[85vw] bg-[#0f172a] flex flex-col justify-between p-4 lg:hidden shadow-2xl border-r border-white/10 transition-transform duration-300 ease-out select-none overflow-y-auto ${
          isMobileSidebarOpen
            ? "translate-x-0"
            : "-translate-x-full pointer-events-none"
        }`}
        aria-label={t("admin.title")}
      >
        <div className="flex flex-col gap-4">
          {/* Top: Logo + Close button */}
          <div className="flex items-center justify-between pb-3 border-b border-white/10">
            <Link
              href={ADMIN_URL_PREFIX}
              onClick={closeMobileSidebar}
              className="block px-1 py-1"
              aria-label={t("admin.title")}
            >
              <Image
                src="/logo/logo1.png"
                alt={t("admin.title")}
                width={800}
                height={185}
                priority
                className="h-auto w-full max-w-[150px]"
              />
            </Link>
            <button
              type="button"
              onClick={closeMobileSidebar}
              className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
              aria-label="Đóng menu"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Navigation Links with auto-close */}
          {renderNavLinks(closeMobileSidebar)}
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
    </>
  );
};
