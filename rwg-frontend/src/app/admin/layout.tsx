"use client";

import React from "react";
import { usePathname } from "next/navigation";
import { AdminSidebar } from "@/components/admin/AdminSidebar";

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const isLoginPage = pathname === "/admin/login";

  if (isLoginPage) {
    return (
      <div className="w-full min-h-screen bg-[#070709] text-white flex items-center justify-center font-sans antialiased">
        {children}
      </div>
    );
  }

  return (
    <div className="w-full min-h-screen bg-[#070709] text-white flex font-sans antialiased">
      {/* Admin Sidebar */}
      <AdminSidebar />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 bg-[#09090c]">
        {children}
      </div>
    </div>
  );
}
