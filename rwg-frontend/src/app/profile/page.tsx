"use client";

import React, { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { PlayerLayout } from "@/components/layout/PlayerLayout";
import { LanguageModal } from "@/components/profile/LanguageModal";
import { ProfileMenuRow } from "@/components/profile/ProfileMenuRow";
import { useTranslation } from "@/context/LanguageContext";
import { useNotification } from "@/context/NotificationContext";
import { formatMoney } from "@/lib/money";
import {
  ApiError,
  getPlayerToken,
  logout,
  me,
  walletMe,
  type UserResponse,
  type Wallet,
} from "@/lib/playerApi";

/**
 * Trang hồ sơ — mục thứ tư của thanh điều hướng dưới.
 *
 * CẤU TRÚC LẤY TỪ BUNDLE CỦA TRANG GỐC (`pages/profile-663e3d4a0f7a413c.js`), không phải
 * phỏng theo ảnh chụp. Các chi tiết nhìn ảnh sẽ làm sai:
 *
 * 1. Đầu trang có tiêu đề riêng trong `<header className="px-5 h-15">`, KHÔNG dùng
 *    `Header` của trang chủ (không có logo, không có lời chào).
 * 2. Thân trang nền `bg-ss2`, `py-6` — cùng ảnh nền với `/bet` và `/draw`.
 * 3. Thẻ số dư và hai nút nạp/rút KHÔNG bo góc, nền `#1F1F1F`.
 * 4. Ảnh đại diện là LOGO (`/logo/logo2.png`) ở cỡ 60% trong vòng tròn `size-20` nền
 *    `#1f1f1f` — không phải ảnh người dùng, bản gốc không có tính năng đổi avatar.
 * 5. Ba nhóm tuỳ chọn, mỗi nhóm có một dòng tiêu đề nhỏ. Nhóm thứ hai và ba dùng
 *    `pt-7` để cách nhóm trước, KHÔNG phải `mt`.
 *
 * BA THỨ LỆCH SO VỚI BẢN GỐC, và vì sao:
 *
 * - HẠNG THÀNH VIÊN: bản gốc đọc `data.ranking.name` từ hệ thống hạng của họ. Backend
 *   mình chưa có khái niệm hạng, nên hiện nhãn cố định "Member". ĐỪNG thay bằng `role`:
 *   role của mình là USER/ADMIN, hiện chữ "USER" lên hồ sơ người chơi là vô nghĩa.
 * - SỐ DƯ: bản gốc đọc `data.member_wallets[0].balance` (nhiều ví). Mình chỉ có một ví
 *   qua `/wallet/me`, nên gọi riêng một request.
 * - HAI DÒNG BỊ BỎ: bản gốc còn `/profile/contact-us` trong nhóm thông tin. Giữ lại
 *   `announcements` thôi vì đó là hai trang chưa dựng — thêm dòng dẫn tới trang 404 thì
 *   người dùng bấm vào là mắc kẹt. Xem ghi chú ở cuối tệp.
 *
 * BẮT BUỘC ĐĂNG NHẬP: `/users/me` và `/wallet/me` đều nằm sau `bearerAuth`. Kiểm token
 * TRƯỚC khi gọi để người chưa đăng nhập được đưa tới trang đăng nhập thay vì thấy lỗi.
 */
export default function ProfilePage() {
  const router = useRouter();
  const { t } = useTranslation();
  const { unreadCount } = useNotification();

  const [user, setUser] = useState<UserResponse | null>(null);
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [languageOpen, setLanguageOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    const handleBalanceUpdate = (e: Event) => {
      const custom = e as CustomEvent<string>;
      setWallet((prev) => (prev ? { ...prev, balance: custom.detail } : null));
    };
    window.addEventListener("wallet_balance_updated", handleBalanceUpdate);

    return () => {
      window.removeEventListener("wallet_balance_updated", handleBalanceUpdate);
    };
  }, []);

  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }

    let cancelled = false;

    (async () => {
      // `allSettled` chứ không `all`: ví lỗi thì vẫn phải hiện được tên và các tuỳ chọn.
      // Trước đây dùng `all` sẽ làm cả trang trắng chỉ vì một request phụ.
      const [profile, balance] = await Promise.allSettled([me(), walletMe()]);
      if (cancelled) return;

      if (profile.status === "fulfilled") {
        setUser(profile.value);
      } else {
        const err = profile.reason;
        if (err instanceof ApiError && err.status === 401) {
          router.replace("/login");
          return;
        }
        setError(t("profile.load_failed"));
      }

      // Ví lỗi KHÔNG đặt `error`: thẻ số dư hiện 0.00 còn hơn chặn cả trang.
      if (balance.status === "fulfilled") setWallet(balance.value);

      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [router, t]);

  /**
   * Đăng xuất rồi về trang đăng nhập.
   *
   * `logout()` tự dọn token ở `finally` nên dù server lỗi thì trên máy này vẫn đăng xuất —
   * người bấm đăng xuất luôn phải được đăng xuất.
   */
  const handleLogout = async () => {
    setLoggingOut(true);
    await logout();
    router.replace("/login");
  };

  return (
    <PlayerLayout
      header={
        // Thanh logo dính trên, theo đúng bản gốc: `sticky top-0` + nền ĐEN THUẦN.
        //
        // Truyền qua prop `header` chứ không đặt trong `main`: `sticky` neo theo khối cuộn
        // gần nhất, mà `main` ở đây có nền riêng và nằm trong khung — để bên trong thì
        // thanh trôi mất khi cuộn tới phần nền `bg-ss2`.
        //
        // Nền phải ĐỤC hoàn toàn (`bg-black`, không phải nền mờ): phía dưới là ảnh nền
        // `bg-ss2` sáng, nền trong suốt sẽ để ảnh chạy xuyên qua sau logo khi cuộn.
        <header className="sticky top-0 z-10 mx-auto flex h-15 w-full items-center bg-black px-5 sm:max-w-[640px]">
          <Image
            alt="Resorts World Genting"
            className="w-[150px]"
            height={185}
            priority
            src="/logo/logo1.png"
            width={800}
          />
        </header>
      }
    >
      <main className="flex w-full grow flex-col bg-black">
        <div className="flex grow flex-col bg-ss2 py-6">
          {loading ? (
            <div className="mt-[50px] flex flex-col items-center gap-y-2">
              <Loader2 aria-hidden="true" className="size-6 animate-spin text-primary" />
              <p className="text-[0.75rem] font-bold leading-normal text-primary">
                {t("draw.loading")}
              </p>
            </div>
          ) : error ? (
            <p
              className="mt-[50px] text-center text-[0.75rem] font-bold text-[#ff4355]"
              role="alert"
            >
              {error}
            </p>
          ) : (
            <>
              {/* Khối danh tính */}
              <div className="flex gap-x-4 px-5">
                <div className="flex size-20 items-center justify-center rounded-full bg-[#1f1f1f]">
                  <Image
                    alt=""
                    aria-hidden="true"
                    className="size-[60%]"
                    height={791}
                    priority
                    src="/logo/logo2.png"
                    width={800}
                  />
                </div>

                <div className="text-star flex flex-col items-start gap-y-1 leading-normal text-white">
                  <div className="text-[0.875rem]">{user?.username ?? "-"}</div>
                  <div className="flex flex-col text-[0.6875rem]">
                    <div className="flex">
                      <span className="rounded-full border border-primary bg-black px-2 text-[0.8125rem] font-bold leading-normal text-primary">
                        {t("profile.member")}
                      </span>
                    </div>
                  </div>
                  {/* Tài khoản tạo từ form người chơi KHÔNG có email (API đăng ký không
                      nhận), nên đây thường là dấu gạch — đúng như bản gốc. */}
                  <div className="text-[0.6875rem]">{user?.email ?? "-"}</div>
                </div>
              </div>

              {/* Số dư + hai nút nạp/rút */}
              <div className="mt-6 flex flex-col gap-y-4 px-5">
                <Link
                  className="flex items-center gap-x-3 bg-[#1F1F1F] px-5 py-6"
                  href="/asset/history"
                >
                  <div className="text-[1rem] font-medium leading-[1.125rem] text-white">
                    {t("profile.wallet_1")}
                  </div>
                  <div className="flex flex-1 flex-col text-end">
                    <div className="text-[1rem] font-bold leading-[1.125rem] text-white">
                      {formatMoney(wallet?.balance)} {wallet?.currency ?? "USD"}
                    </div>
                  </div>
                  <i
                    aria-hidden="true"
                    className="icon-icon77 size-4 text-[1rem] text-primary"
                  />
                </Link>

                <div className="flex gap-x-4">
                  <Link
                    className="flex flex-1 flex-col items-center gap-y-1.5 bg-[#1F1F1F] py-3 text-center"
                    href="/asset/deposit"
                  >
                    <i
                      aria-hidden="true"
                      className="icon-icon10 size-5 text-[1.25rem] text-primary"
                    />
                    <div className="text-[0.6875rem] leading-normal text-white">
                      {t("profile.deposit")}
                    </div>
                  </Link>
                  <Link
                    className="flex flex-1 flex-col items-center gap-y-1.5 bg-[#1F1F1F] py-3 text-center"
                    href="/asset/withdraw"
                  >
                    <i
                      aria-hidden="true"
                      className="icon-icon11 size-5 text-[1.25rem] text-primary"
                    />
                    <div className="text-[0.6875rem] leading-normal text-white">
                      {t("profile.withdraw")}
                    </div>
                  </Link>
                </div>
              </div>

              {/* Ba nhóm tuỳ chọn */}
              <div className="mt-6 flex flex-col">
                <div className="border-b border-b-[#1f1f1f] px-5 py-4 text-[0.8125rem] font-medium leading-normal text-white/80">
                  {t("profile.referral_program")}
                </div>
                <ProfileMenuRow
                  href="/profile/invitation"
                  icon="icon-icon72"
                  subtitle={t("profile.invite_friends_description")}
                  title={t("profile.invite_friends")}
                />

                <div className="border-b border-b-[#1f1f1f] px-5 pt-7 pb-4 text-[0.8125rem] font-medium leading-normal text-white/80">
                  {t("profile.settings")}
                </div>
                <ProfileMenuRow
                  href="/profile/account-settings"
                  icon="icon-icon17"
                  subtitle={t("profile.account_settings_description")}
                  title={t("profile.account_settings")}
                />
                <ProfileMenuRow
                  href="/profile/security-center"
                  icon="icon-icon42"
                  subtitle={t("profile.security_center_description")}
                  title={t("profile.security_center")}
                />
                {/* Dòng ngôn ngữ nhận HÀM chứ không đường dẫn: nó mở hộp thoại tại chỗ,
                    đúng như bản gốc. */}
                <ProfileMenuRow
                  href={() => setLanguageOpen(true)}
                  icon="icon-icon50"
                  subtitle={t("profile.language_settings_description")}
                  title={t("profile.language_settings")}
                />

                <div className="border-b border-b-[#1f1f1f] px-5 pt-7 pb-4 text-[0.8125rem] font-medium leading-normal text-white/80">
                  {t("profile.information_n_contact")}
                </div>
                <ProfileMenuRow
                  href="/profile/announcements"
                  icon="icon-icon32"
                  subtitle={t("profile.announcement_description")}
                  title={t("profile.announcement")}
                  badge={
                    unreadCount > 0 ? (
                      <span className="flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full bg-[#fe1616] text-[0.6875rem] font-black text-white leading-none">
                        {unreadCount}
                      </span>
                    ) : null
                  }
                />

                <div className="mt-7 px-5">
                  {/* Nút viền: nền đen, viền và chữ màu primary — đúng biến thể `outline`
                      của bản gốc. KHÔNG dùng component `Button` vì nút đó nền đặc. */}
                  <button
                    aria-busy={loggingOut || undefined}
                    className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-none border border-primary bg-black text-[1rem] font-bold leading-normal text-primary transition-colors hover:bg-primary/10 disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                    disabled={loggingOut}
                    onClick={handleLogout}
                    type="button"
                  >
                    {loggingOut ? (
                      <Loader2 aria-hidden="true" className="size-4 animate-spin" />
                    ) : null}
                    {t("profile.logout")}
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </main>

      <LanguageModal onClose={() => setLanguageOpen(false)} open={languageOpen} />
    </PlayerLayout>
  );
}
