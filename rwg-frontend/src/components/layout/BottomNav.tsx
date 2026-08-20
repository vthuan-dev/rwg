"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Home, Gamepad2, FileText, User } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

export const BottomNav: React.FC = () => {
  const pathname = usePathname();
  const { t } = useTranslation();

  const navItems = [
    {
      href: "/",
      labelKey: "nav.home",
      icon: Home,
    },
    {
      href: "/games",
      labelKey: "nav.games",
      icon: Gamepad2,
    },
    {
      href: "/history",
      labelKey: "nav.history",
      icon: FileText,
    },
    {
      href: "/profile",
      labelKey: "nav.profile",
      icon: User,
    },
  ];

  return (
    <div className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[500px] bg-[#111114]/95 backdrop-blur-md border-t border-[#222228] z-50 px-2 py-1.5">
      <div className="grid grid-cols-4 items-center">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex flex-col items-center justify-center gap-1 py-1 px-2 transition-all rounded-lg active:scale-95 ${
                isActive
                  ? "text-red-500 font-bold"
                  : "text-gray-400 hover:text-gray-200"
              }`}
            >
              <Icon
                className={`w-5 h-5 ${
                  isActive ? "text-red-500 scale-110" : "text-gray-400"
                } transition-transform`}
              />
              <span className="text-[11px] tracking-tight leading-none">
                {t(item.labelKey)}
              </span>
            </Link>
          );
        })}
      </div>
    </div>
  );
};
