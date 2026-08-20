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
      <div className="w-full min-h-screen bg-slate-100 text-slate-900 flex items-center justify-center font-sans antialiased">
        {children}
      </div>
    );
  }

  return (
    <div className="w-full min-h-screen bg-slate-100 text-slate-900 flex font-sans antialiased">
      {/* Admin Sidebar - Fixed Left Sidebar Light */}
      <AdminSidebar />

      {/* Main Content Area - Full Desktop Width Light */}
      <main className="flex-1 flex flex-col min-w-0 bg-slate-50 overflow-y-auto">
        {children}
      </main>
    </div>
  );
}
