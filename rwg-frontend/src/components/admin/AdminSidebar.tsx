"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Users,
  CreditCard,
  Gamepad2,
  ShieldAlert,
  Network,
  Image as ImageIcon,
  LogOut,
  ShieldCheck,
} from "lucide-react";
import { removeAdminToken } from "@/lib/adminApi";
import { useTranslation } from "@/context/LanguageContext";

export const AdminSidebar: React.FC = () => {
  const pathname = usePathname();
  const { t } = useTranslation();

  const navItems = [
    {
      href: "/admin",
      label: t("admin.nav.dashboard"),
      icon: LayoutDashboard,
    },
    {
      href: "/admin/users",
      label: t("admin.nav.users"),
      icon: Users,
    },
    {
      href: "/admin/payments",
      label: t("admin.nav.payments"),
      icon: CreditCard,
    },
    {
      href: "/admin/games",
      label: t("admin.nav.games"),
      icon: Gamepad2,
    },
    {
      href: "/admin/risk",
      label: t("admin.nav.risk"),
      icon: ShieldAlert,
    },
    {
      href: "/admin/affiliates",
      label: t("admin.nav.affiliates"),
      icon: Network,
    },
    {
      href: "/admin/banners",
      label: t("admin.nav.banners"),
      icon: ImageIcon,
    },
  ];

  const handleLogout = () => {
    removeAdminToken();
    window.location.href = "/admin/login";
  };

  return (
    <aside className="w-64 bg-white border-r border-slate-200 min-h-screen flex flex-col justify-between p-4 sticky top-0 h-screen select-none shadow-xs">
      {/* Top Logo & Title */}
      <div className="flex flex-col gap-6">
        <div className="flex items-center gap-3 px-2 py-1">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-red-600 to-red-700 border border-red-500/40 flex items-center justify-center text-white font-black text-sm shadow-md shadow-red-200">
            <ShieldCheck className="w-5 h-5 text-white" />
          </div>
          <div className="flex flex-col">
            <span className="text-slate-900 font-extrabold text-base tracking-tight leading-tight">
              {t("admin.title")}
            </span>
            <span className="text-red-600 font-bold text-[10px] uppercase tracking-widest leading-none">
              {t("admin.subtitle")}
            </span>
          </div>
        </div>

        {/* Navigation Items */}
        <nav className="flex flex-col gap-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive =
              item.href === "/admin"
                ? pathname === "/admin"
                : pathname.startsWith(item.href);

            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${
                  isActive
                    ? "bg-red-50 border border-red-200 text-red-700 shadow-xs"
                    : "text-slate-600 hover:text-slate-900 hover:bg-slate-100"
                }`}
              >
                <Icon
                  className={`w-4 h-4 ${
                    isActive ? "text-red-600" : "text-slate-500"
                  }`}
                />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </div>

      {/* Logout Button */}
      <div className="pt-4 border-t border-slate-200">
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold text-slate-600 hover:text-red-600 hover:bg-red-50 transition-all border border-transparent hover:border-red-100"
        >
          <LogOut className="w-4 h-4 text-red-600" />
          <span>{t("admin.logout")}</span>
        </button>
      </div>
    </aside>
  );
};
