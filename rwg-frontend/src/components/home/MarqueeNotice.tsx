"use client";

import React from "react";
import { Megaphone } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

export const MarqueeNotice: React.FC = () => {
  const { t } = useTranslation();

  return (
    <div className="w-full bg-[#161619] border-y border-[#232328] py-2 px-4 flex items-center gap-3 overflow-hidden text-xs text-gray-300">
      <Megaphone className="w-4 h-4 text-red-500 shrink-0 animate-pulse" />
      <div className="flex-1 overflow-hidden relative">
        <div className="whitespace-nowrap inline-block animate-marquee font-medium text-gray-200 tracking-wide">
          {t("marquee.notice")}
        </div>
      </div>
    </div>
  );
};
