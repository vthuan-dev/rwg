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

export const AdminSidebar: React.FC = () => {
  const pathname = usePathname();

  const navItems = [
    {
      href: "/admin",
      label: "Tổng quan",
      icon: LayoutDashboard,
    },
    {
      href: "/admin/users",
      label: "Quản lý Người dùng",
      icon: Users,
    },
    {
      href: "/admin/payments",
      label: "Duyệt Nạp & Rút tiền",
      icon: CreditCard,
    },
    {
      href: "/admin/games",
      label: "Quản lý Bàn chơi",
      icon: Gamepad2,
    },
    {
      href: "/admin/risk",
      label: "Chống Gian lận (Risk)",
      icon: ShieldAlert,
    },
    {
      href: "/admin/affiliates",
      label: "Đại lý & Hoa hồng",
      icon: Network,
    },
    {
      href: "/admin/banners",
      label: "Banner & Media",
      icon: ImageIcon,
    },
  ];

  const handleLogout = () => {
    removeAdminToken();
    window.location.href = "/admin/login";
  };

  return (
    <aside className="w-64 bg-[#0d0d10] border-r border-[#1a1a1f] min-h-screen flex flex-col justify-between p-4 sticky top-0 h-screen select-none">
      {/* Top Logo & Title */}
      <div className="flex flex-col gap-6">
        <div className="flex items-center gap-3 px-2 py-1">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-red-600 to-red-900 border border-red-500/40 flex items-center justify-center text-white font-black text-sm shadow-lg shadow-red-950/50">
            <ShieldCheck className="w-5 h-5 text-white" />
          </div>
          <div className="flex flex-col">
            <span className="text-white font-extrabold text-base tracking-tight leading-tight">
              RWG Admin
            </span>
            <span className="text-red-500 font-bold text-[10px] uppercase tracking-widest leading-none">
              Backoffice Suite
            </span>
          </div>
        </div>

        {/* Navigation Items */}
        <nav className="flex flex-col gap-1.5">
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
                    ? "bg-red-950/50 border border-red-600/40 text-red-400 shadow-md shadow-red-950/20"
                    : "text-gray-400 hover:text-white hover:bg-[#16161b]"
                }`}
              >
                <Icon
                  className={`w-4 h-4 ${
                    isActive ? "text-red-500" : "text-gray-400"
                  }`}
                />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </div>

      {/* Logout Button */}
      <div className="pt-4 border-t border-[#1a1a1f]">
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold text-gray-400 hover:text-red-400 hover:bg-red-950/30 transition-all border border-transparent hover:border-red-900/40"
        >
          <LogOut className="w-4 h-4 text-red-500" />
          <span>Đăng xuất Admin</span>
        </button>
      </div>
    </aside>
  );
};
