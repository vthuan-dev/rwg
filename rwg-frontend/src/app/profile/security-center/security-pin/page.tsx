"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { useTranslation } from "@/context/LanguageContext";
import { getPlayerToken } from "@/lib/playerApi";
import { ShieldCheck, Headphones, ArrowRight, KeyRound } from "lucide-react";

/**
 * Trang thông báo chuyển hướng sang CSKH khi người dùng muốn đặt lại Mật khẩu rút tiền (Mã PIN 6 số).
 */
export default function SecurityPinInfoPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }
    setChecked(true);
  }, [router]);

  return (
    <MobileShell
      header={
        <TopNavigationBar
          backHref="/profile/security-center"
          title={t("profile.security_pin")}
        />
      }
    >
      <main className="flex grow flex-col px-4 py-6">
        {checked ? (
          <div className="flex grow flex-col items-center justify-between text-center">
            <div className="flex w-full flex-col items-center pt-4">
              {/* Icon ổ khóa rút tiền */}
              <div className="relative mb-6 flex size-20 items-center justify-center rounded-full bg-rose-500/10 border border-rose-500/20 shadow-lg shadow-rose-500/5">
                <div className="absolute inset-0 rounded-full animate-ping bg-rose-500/10 opacity-75" />
                <KeyRound className="size-10 text-rose-500" />
              </div>

              {/* Tiêu đề thông báo */}
              <h2 className="mb-3 text-lg font-bold text-white">
                Đặt lại Mật khẩu rút tiền (PIN 6 số)
              </h2>

              {/* Khối thẻ thông báo */}
              <div className="w-full rounded-2xl border border-[#28282e] bg-[#16161a] p-5 text-left shadow-xl">
                <div className="mb-3 flex items-center gap-2.5 text-xs font-bold text-rose-400">
                  <ShieldCheck className="size-4 shrink-0 text-rose-400" />
                  <span>Quy định an toàn tài chính</span>
                </div>
                <p className="text-xs leading-relaxed text-[#a1a1aa]">
                  Vì lý do bảo vệ tài sản và nguồn tiền của tài khoản, việc thay đổi hoặc đặt lại{" "}
                  <strong className="text-white">Mật khẩu rút tiền 6 số</strong> được quản lý và bảo vệ trực tiếp bởi{" "}
                  <span className="text-rose-400 font-semibold">Bộ phận Chăm sóc Khách hàng</span>.
                </p>
                <div className="mt-4 rounded-xl border border-[#28282e] bg-[#0d0d0f] p-3 text-[0.75rem] text-[#8b8b93]">
                  💡 Quý khách vui lòng nhắn tin trực tiếp với bộ phận CSKH để xác nhận thông tin và được hướng dẫn đặt lại mã PIN.
                </div>
              </div>
            </div>

            {/* Nút hành động chuyển qua CSKH */}
            <div className="w-full pt-6">
              <Link
                href="/profile/contact-us"
                className="flex w-full items-center justify-center gap-2.5 rounded-xl bg-gradient-to-r from-rose-600 via-rose-500 to-rose-700 py-3.5 text-sm font-bold text-white shadow-lg shadow-rose-600/20 transition-all active:scale-98 hover:brightness-105"
              >
                <Headphones className="size-4 text-white" />
                <span>Liên hệ CSKH ngay</span>
                <ArrowRight className="size-4 text-white" />
              </Link>
            </div>
          </div>
        ) : null}
      </main>
    </MobileShell>
  );
}
