"use client";

import React, { use } from "react";
import { usePathname, notFound } from "next/navigation";
import { AdminSidebar } from "@/components/admin/AdminSidebar";
import { ADMIN_SECRET_PATH, ADMIN_URL_PREFIX } from "@/lib/constants";

export default function AdminSecretLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ secretKey: string }>;
}) {
  const resolvedParams = use(params);
  const pathname = usePathname();

  // If secret path does not match env NEXT_PUBLIC_ADMIN_SECRET_PATH (default "2026"), return 404!
  if (resolvedParams.secretKey !== ADMIN_SECRET_PATH) {
    notFound();
  }

  const isLoginPage = pathname === `${ADMIN_URL_PREFIX}/login`;

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
