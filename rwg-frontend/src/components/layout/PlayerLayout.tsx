"use client";

import React from "react";
import { BottomNav } from "@/components/layout/BottomNav";

export const PlayerLayout: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  return (
    <div className="w-full flex justify-center bg-[#070709] min-h-screen">
      <div className="w-full max-w-[500px] bg-[#0d0d0f] min-h-screen flex flex-col relative shadow-2xl pb-20 border-x border-[#1a1a1e]">
        {children}
        <BottomNav />
      </div>
    </div>
  );
};
