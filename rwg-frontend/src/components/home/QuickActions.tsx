"use client";

import React from "react";
import { CreditCard, Wallet, Receipt, Headphones } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

export const QuickActions: React.FC = () => {
  const { t } = useTranslation();

  const actions = [
    {
      id: "deposit",
      titleKey: "quick_actions.deposit",
      icon: CreditCard,
      color: "text-red-500",
    },
    {
      id: "withdraw",
      titleKey: "quick_actions.withdraw",
      icon: Wallet,
      color: "text-red-500",
    },
    {
      id: "history",
      titleKey: "quick_actions.history",
      icon: Receipt,
      color: "text-red-500",
    },
    {
      id: "support",
      titleKey: "quick_actions.support",
      icon: Headphones,
      color: "text-red-500",
    },
  ];

  return (
    <div className="w-full px-4 my-4">
      <div className="grid grid-cols-2 gap-3">
        {actions.map((action) => {
          const Icon = action.icon;
          return (
            <button
              key={action.id}
              className="bg-[#1b1b1f] hover:bg-[#242429] active:scale-[0.98] border border-[#28282e] hover:border-red-600/50 rounded-xl p-3.5 flex flex-col items-center justify-center gap-2 transition-all shadow-md group"
            >
              <div className="p-2 rounded-lg bg-red-950/40 border border-red-900/30 group-hover:bg-red-900/40 transition-colors">
                <Icon className={`w-5 h-5 ${action.color}`} />
              </div>
              <span className="text-xs font-semibold text-gray-200 tracking-tight">
                {t(action.titleKey)}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
};
