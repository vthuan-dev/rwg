"use client";

import React from "react";
import Link from "next/link";

interface HeaderProps {
  username?: string;
}

export const Header: React.FC<HeaderProps> = ({ username = "jinbao01" }) => {
  return (
    <header className="w-full bg-[#0d0d0f] px-4 pt-3 pb-2 flex flex-col gap-2 border-b border-[#1a1a1e]">
      {/* Top Logo Row */}
      <div className="flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2.5">
          {/* Genting Crown Emblem */}
          <div className="w-8 h-8 rounded-full border border-white/20 bg-gradient-to-tr from-red-900 to-red-600 flex items-center justify-center text-white font-bold text-xs shadow-md">
            RW
          </div>
          <div className="flex flex-col">
            <span className="text-white text-sm font-black tracking-wider leading-none">
              Resorts World
            </span>
            <span className="text-gray-400 text-[10px] font-bold tracking-widest uppercase leading-tight">
              GENTING
            </span>
          </div>
        </Link>
      </div>

      {/* Greeting Banner Red Text */}
      <div className="mt-1">
        <p className="text-red-500 font-bold text-base sm:text-lg tracking-tight">
          Chào mừng quay trở lại, {username}
        </p>
      </div>
    </header>
  );
};
