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
    <header className="w-full bg-[#0d0d10] border-b border-[#1a1a1f] px-6 py-4 flex items-center justify-between sticky top-0 z-40 backdrop-blur-md bg-opacity-90">
      {/* Title */}
      <div className="flex flex-col">
        <h1 className="text-lg font-bold text-white tracking-tight leading-tight">
          {title}
        </h1>
        {subtitle && (
          <p className="text-xs text-gray-400 font-medium">{subtitle}</p>
        )}
      </div>

      {/* System Status Badges */}
      <div className="flex items-center gap-3">
        {/* Backend API 8081 Status Badge */}
        <div className="bg-[#141418] border border-[#23232a] rounded-lg px-3 py-1.5 flex items-center gap-2">
          <Radio className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
          <span className="text-[11px] font-semibold text-gray-300">
            API 8081 Online
          </span>
        </div>

        {/* Logged-in Admin User Badge */}
        <div className="bg-red-950/40 border border-red-900/40 rounded-lg px-3 py-1.5 flex items-center gap-2">
          <Shield className="w-3.5 h-3.5 text-red-500" />
          <span className="text-[11px] font-bold text-red-400 uppercase tracking-wide">
            SUPER ADMIN
          </span>
        </div>
      </div>
    </header>
  );
};
