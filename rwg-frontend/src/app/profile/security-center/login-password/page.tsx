"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { useTranslation } from "@/context/LanguageContext";
import { getPlayerToken } from "@/lib/playerApi";
import { ShieldAlert, Headphones, ArrowRight, LockKeyhole } from "lucide-react";

/**
 * Trang thông báo chuyển hướng sang CSKH khi người dùng muốn đổi Mật khẩu đăng nhập.
 */
export default function LoginPasswordInfoPage() {
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
          title={t("profile.login_password")}
        />
      }
    >
      <main className="flex grow flex-col px-4 py-6">
        {checked ? (
          <div className="flex grow flex-col items-center justify-between text-center">
            <div className="flex w-full flex-col items-center pt-4">
              {/* Icon cảnh báo bảo mật */}
              <div className="relative mb-6 flex size-20 items-center justify-center rounded-full bg-amber-500/10 border border-amber-500/20 shadow-lg shadow-amber-500/5">
                <div className="absolute inset-0 rounded-full animate-ping bg-amber-500/10 opacity-75" />
                <LockKeyhole className="size-10 text-[var(--gold)]" />
              </div>

              {/* Tiêu đề thông báo */}
              <h2 className="mb-3 text-lg font-bold text-white">
                Xác thực thay đổi Mật khẩu
              </h2>

              {/* Khối thẻ thông báo */}
              <div className="w-full rounded-2xl border border-[#28282e] bg-[#16161a] p-5 text-left shadow-xl">
                <div className="mb-3 flex items-center gap-2.5 text-xs font-bold text-amber-400">
                  <ShieldAlert className="size-4 shrink-0 text-amber-400" />
                  <span>Quy định an toàn tài khoản</span>
                </div>
                <p className="text-xs leading-relaxed text-[#a1a1aa]">
                  Vì lý do bảo mật và phòng chống xâm nhập tài khoản bất hợp pháp, việc thay đổi{" "}
                  <strong className="text-white">Mật khẩu đăng nhập</strong> được thực hiện thông qua{" "}
                  <span className="text-[var(--gold)] font-semibold">Bộ phận Chăm sóc Khách hàng 24/7</span>.
                </p>
                <div className="mt-4 rounded-xl border border-[#28282e] bg-[#0d0d0f] p-3 text-[0.75rem] text-[#8b8b93]">
                  💡 Vui lòng kết nối với chuyên viên CSKH để được hỗ trợ xác minh thông tin và cấp lại mật khẩu ngay lập tức.
                </div>
              </div>
            </div>

            {/* Nút hành động chuyển qua CSKH */}
            <div className="w-full pt-6">
              <Link
                href="/profile/contact-us"
                className="flex w-full items-center justify-center gap-2.5 rounded-xl bg-gradient-to-r from-[#e3bd66] via-[#f7e3a8] to-[#b8892f] py-3.5 text-sm font-bold text-black shadow-lg shadow-amber-500/10 transition-all active:scale-98 hover:brightness-105"
              >
                <Headphones className="size-4 text-black" />
                <span>Liên hệ CSKH ngay</span>
                <ArrowRight className="size-4 text-black" />
              </Link>
            </div>
          </div>
        ) : null}
      </main>
    </MobileShell>
  );
}
