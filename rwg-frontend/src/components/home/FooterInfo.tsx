"use client";

import React from "react";
import { useTranslation } from "@/context/LanguageContext";

export const FooterInfo: React.FC = () => {
  const { t } = useTranslation();

  return (
    <footer className="w-full px-4 pt-4 pb-12 bg-[#0c0c0e] border-t border-[#1a1a1e] text-xs text-gray-400 flex flex-col gap-5">
      {/* Hợp tác & Chứng nhận */}
      <div className="grid grid-cols-2 gap-4">
        {/* Hợp tác */}
        <div>
          <h4 className="text-[11px] font-bold text-gray-300 uppercase mb-2">
            {t("footer.partners")}
          </h4>
          <div className="flex items-center gap-2">
            <div className="bg-[#18181c] border border-[#26262c] rounded-lg p-2 flex items-center justify-center gap-1">
              <div className="w-6 h-6 rounded-full bg-gradient-to-br from-amber-500 to-red-600 flex items-center justify-center font-extrabold text-[10px] text-white">
                FIBA
              </div>
              <div className="flex flex-col text-[9px] leading-tight font-semibold text-gray-200">
                <span>FIBA</span>
                <span className="text-[7px] text-gray-400">Basketball</span>
              </div>
            </div>
          </div>
        </div>

        {/* Chứng nhận */}
        <div>
          <h4 className="text-[11px] font-bold text-gray-300 uppercase mb-2">
            {t("footer.certifications")}
          </h4>
          <div className="flex items-center gap-2">
            <div className="bg-[#18181c] border border-green-800/40 rounded-lg px-2.5 py-1.5 flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span className="text-[10px] font-bold text-gray-200">
                GC Curacao
              </span>
            </div>
            <div className="w-7 h-7 rounded-full border border-gray-600 flex items-center justify-center font-bold text-[10px] text-gray-300">
              18+
            </div>
          </div>
        </div>
      </div>

      {/* Thanh toán trực tuyến */}
      <div>
        <h4 className="text-[11px] font-bold text-gray-300 uppercase mb-2">
          {t("footer.online_payment")}
        </h4>
        <div className="flex flex-wrap items-center gap-2">
          {/* Crypto icons */}
          <div className="w-7 h-7 rounded-full bg-emerald-900/40 border border-emerald-500/40 flex items-center justify-center font-bold text-emerald-400 text-xs">
            ₮
          </div>
          <div className="w-7 h-7 rounded-full bg-amber-900/40 border border-amber-500/40 flex items-center justify-center font-bold text-amber-400 text-xs">
            ₿
          </div>
          <div className="w-7 h-7 rounded-full bg-blue-900/40 border border-blue-500/40 flex items-center justify-center font-bold text-blue-400 text-xs">
            Ξ
          </div>
          <div className="w-7 h-7 rounded-full bg-purple-900/40 border border-purple-500/40 flex items-center justify-center font-bold text-purple-400 text-xs">
            J9
          </div>
          <div className="w-7 h-7 rounded-full bg-gray-800 border border-gray-600 flex items-center justify-center font-bold text-gray-200 text-[10px]">
            VISA
          </div>
        </div>
      </div>

      {/* Trò chơi Providers */}
      <div>
        <h4 className="text-[11px] font-bold text-gray-300 uppercase mb-2">
          {t("footer.game_providers")}
        </h4>
        <div className="grid grid-cols-2 gap-2">
          <div className="bg-[#151518] border border-[#242429] rounded-lg p-2 flex items-center gap-2">
            <span className="font-extrabold text-red-500 text-xs">AG</span>
            <span className="text-[10px] font-bold text-gray-300">
              Asia Gaming
            </span>
          </div>
          <div className="bg-[#151518] border border-[#242429] rounded-lg p-2 flex items-center gap-2">
            <span className="font-extrabold text-amber-500 text-xs">BG</span>
            <span className="text-[10px] font-bold text-gray-300">
              BGaming
            </span>
          </div>
          <div className="bg-[#151518] border border-[#242429] rounded-lg p-2 flex items-center gap-2">
            <span className="font-extrabold text-blue-400 text-xs">EVO</span>
            <span className="text-[10px] font-bold text-gray-300">
              Evolution
            </span>
          </div>
          <div className="bg-[#151518] border border-[#242429] rounded-lg p-2 flex items-center gap-2">
            <span className="font-extrabold text-emerald-500 text-xs">MG</span>
            <span className="text-[10px] font-bold text-gray-300">
              Microgaming
            </span>
          </div>
        </div>
      </div>

      {/* Copyright */}
      <div className="text-center text-[10px] text-gray-600 pt-2 border-t border-[#18181c]">
        {t("footer.copyright")}
      </div>
    </footer>
  );
};
