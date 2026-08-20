"use client";

import React from "react";
import { Shield, Radio } from "lucide-react";

interface AdminHeaderProps {
  title: string;
  subtitle?: string;
}

export const AdminHeader: React.FC<AdminHeaderProps> = ({
  title,
  subtitle,
}) => {
  return (
    <header className="w-full bg-white border-b border-slate-200 px-6 py-4 flex items-center justify-between sticky top-0 z-40 shadow-xs">
      {/* Title */}
      <div className="flex flex-col">
        <h1 className="text-lg font-extrabold text-slate-900 tracking-tight leading-tight">
          {title}
        </h1>
        {subtitle && (
          <p className="text-xs text-slate-500 font-medium">{subtitle}</p>
        )}
      </div>

      {/* System Status Badges */}
      <div className="flex items-center gap-3">
        {/* Backend API 8081 Status Badge */}
        <div className="bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-1.5 flex items-center gap-2">
          <Radio className="w-3.5 h-3.5 text-emerald-600 animate-pulse" />
          <span className="text-[11px] font-bold text-emerald-700">
            API 8081 Online
          </span>
        </div>

        {/* Logged-in Admin User Badge */}
        <div className="bg-red-50 border border-red-200 rounded-lg px-3 py-1.5 flex items-center gap-2">
          <Shield className="w-3.5 h-3.5 text-red-600" />
          <span className="text-[11px] font-bold text-red-700 uppercase tracking-wide">
            SUPER ADMIN
          </span>
        </div>
      </div>
    </header>
  );
};
