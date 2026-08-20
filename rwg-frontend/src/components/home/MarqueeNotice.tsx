"use client";

import React from "react";
import { Megaphone } from "lucide-react";

export const MarqueeNotice: React.FC = () => {
  return (
    <div className="w-full bg-[#161619] border-y border-[#232328] py-2 px-4 flex items-center gap-3 overflow-hidden text-xs text-gray-300">
      <Megaphone className="w-4 h-4 text-red-500 shrink-0 animate-pulse" />
      <div className="flex-1 overflow-hidden relative">
        <div className="whitespace-nowrap inline-block animate-marquee font-medium text-gray-200 tracking-wide">
          Welcome to Resort World Genting! Trải nghiệm đẳng cấp sảnh Lucky 28, British Lucky 28, Korean Lucky 28 & Taiwan Times 24/7.
        </div>
      </div>
    </div>
  );
};
