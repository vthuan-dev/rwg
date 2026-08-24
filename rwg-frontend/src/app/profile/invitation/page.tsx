"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import QRCode from "qrcode";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { useTranslation } from "@/context/LanguageContext";
import {
  ApiError,
  getPlayerToken,
  myReferralCode,
  type ReferralCode,
} from "@/lib/playerApi";
import { siteUrl } from "@/lib/constants";
import { Copy, Check, Loader2 } from "lucide-react";

/**
 * Trang Mời bạn bè — mã giới thiệu, mã QR và liên kết đăng ký.
 *
 * Mã QR được VẼ TẠI TRÌNH DUYỆT bằng thư viện `qrcode`, không gọi dịch vụ tạo QR bên
 * ngoài (api.qrserver.com, chart.googleapis.com...). Gửi liên kết giới thiệu sang máy
 * chủ thứ ba là để lộ cấu trúc mã giới thiệu của toàn hệ thống cho một bên không liên
 * quan, và trang sẽ trắng QR mỗi lần dịch vụ đó chết hoặc bị chặn.
 *
 * MÃ KHÔNG SINH Ở CLIENT: backend sinh ở lần gọi `/affiliate/me/code` đầu tiên rồi lưu
 * lại. Mã phải duy nhất toàn hệ thống và tra cứu được ngược về chủ sở hữu khi tính hoa
 * hồng — hai điều một mình trình duyệt không làm được.
 */
export default function InvitationPage() {
  const router = useRouter();
  const { t } = useTranslation();

  const [checked, setChecked] = useState(false);
  const [loading, setLoading] = useState(true);
  const [referral, setReferral] = useState<ReferralCode | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState<"code" | "link" | null>(null);

  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  /**
   * Liên kết đăng ký đầy đủ.
   *
   * Tên miền lấy từ `NEXT_PUBLIC_SITE_URL` qua `siteUrl()`, KHÔNG gán cứng ở đây.
   * Backend chỉ trả đường dẫn tương đối vì nó nằm sau reverse proxy nên không biết
   * tên miền công khai — xem `ReferralCodeResponse`.
   *
   * Trả "" khi chưa có mã: một liên kết dở dang bị copy đi thì người nhận mở ra
   * trang lỗi, còn ô trống thì nhìn ra ngay là chưa tải xong.
   */
  const fullLink = referral ? siteUrl(referral.registerPath) : "";

  const loadCode = useCallback(async () => {
    try {
      setReferral(await myReferralCode());
      setError(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        router.replace("/login");
        return;
      }
      setError(t("profile.invitation.err_failed"));
    } finally {
      setLoading(false);
    }
  }, [router, t]);

  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }
    setChecked(true);
    void loadCode();
  }, [router, loadCode]);

  /**
   * Vẽ mã QR sau khi có liên kết.
   *
   * Phụ thuộc vào `fullLink` chứ không phải `referral`: lần render đầu trên server
   * `window` chưa tồn tại nên `fullLink` là "" dù `referral` đã có. Nếu theo dõi
   * `referral` thì QR sẽ được vẽ từ chuỗi rỗng và không bao giờ vẽ lại.
   */
  useEffect(() => {
    if (!fullLink || !canvasRef.current) return;

    void QRCode.toCanvas(canvasRef.current, fullLink, {
      width: 200,
      margin: 1,
      // Nền TRẮNG ĐỤC, không trong suốt: trang này nền tối, QR trong suốt sẽ thành
      // các ô tối trên nền tối và không máy nào quét được.
      color: { dark: "#000000", light: "#ffffff" },
      errorCorrectionLevel: "M",
    });
  }, [fullLink]);

  /** Copy một trường và đổi icon thành dấu tích trong 1,5 giây. */
  const copy = (field: "code" | "link", value: string) => {
    void navigator.clipboard.writeText(value);
    setCopied(field);
    window.setTimeout(() => setCopied(null), 1500);
  };

  if (!checked || loading) {
    return (
      <MobileShell>
        <div className="flex h-64 items-center justify-center gap-2 text-sm text-[#8b8b8b]">
          <Loader2 className="size-4 animate-spin" />
          {t("asset.loading")}...
        </div>
      </MobileShell>
    );
  }

  return (
    <MobileShell
      header={
        <TopNavigationBar
          title={t("profile.invitation.title")}
          backHref="/profile"
        />
      }
    >
      <main className="flex grow flex-col">
        <h1 className="sr-only">{t("profile.invitation.title")}</h1>

        {error && (
          <p
            className="mx-5 mt-4 text-xs font-medium text-[#fe1616]"
            role="alert"
          >
            {error}
          </p>
        )}

        {/* ===== Ảnh nền REFER A FRIEND + mã QR đè lên ===== */}
        <div className="relative flex flex-col items-center">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/element/refer-friends-bg.png"
            alt=""
            aria-hidden="true"
            className="w-full select-none object-contain"
          />

          {/*
            QR đặt ĐÈ LÊN phần dưới của ảnh, khớp bố cục trang gốc. Dùng `absolute`
            theo phần trăm thay vì pixel cố định: ảnh co giãn theo chiều rộng khung
            (tối đa 640px) nên một offset pixel sẽ lệch trên mọi màn hình khác.
          */}
          <div className="absolute bottom-[6%] flex flex-col items-center gap-2.5">
            <div className="rounded-lg bg-white p-2 shadow-lg">
              {/* Canvas cố định 200px: QR nhỏ hơn thì camera điện thoại khó bắt nét. */}
              <canvas
                ref={canvasRef}
                id="referral-qr"
                width={200}
                height={200}
                className="block size-[130px] sm:size-[150px]"
                aria-label={t("profile.invitation.qr_alt")}
              />
            </div>
          </div>
        </div>

        {/* ===== Tiêu đề & mô tả ===== */}
        <div className="mt-2 flex flex-col items-center px-5 text-center">
          <h2 className="text-[0.8125rem] font-bold text-white">
            {t("profile.invitation.heading")}
          </h2>
          <p className="mt-1 text-[0.6875rem] leading-relaxed text-[#8b8b8b]">
            {t("profile.invitation.subheading")}
          </p>
        </div>

        {/* ===== Mã giới thiệu & liên kết ===== */}
        <div className="mt-6 flex flex-col gap-y-3 px-5 pb-8">
          {/* Mã giới thiệu */}
          <div className="flex items-center justify-between gap-3 bg-[#1f1f1f] px-4 py-3.5">
            <span className="shrink-0 text-[0.75rem] text-[#8b8b8b]">
              {t("profile.invitation.code_label")}
            </span>
            <div className="flex min-w-0 items-center gap-2">
              <span className="truncate font-mono text-[0.8125rem] font-bold tracking-wider text-white">
                {referral?.code ?? "—"}
              </span>
              <button
                type="button"
                id="copy-referral-code"
                disabled={!referral}
                onClick={() => referral && copy("code", referral.code)}
                aria-label={t("profile.invitation.copy_code")}
                className="shrink-0 text-[#fe1616] transition-opacity active:opacity-60 disabled:opacity-40"
              >
                {copied === "code" ? (
                  <Check className="size-4 text-emerald-500" />
                ) : (
                  <Copy className="size-4" />
                )}
              </button>
            </div>
          </div>

          {/* Liên kết giới thiệu */}
          <div className="flex items-center justify-between gap-3 bg-[#1f1f1f] px-4 py-3.5">
            <span className="shrink-0 text-[0.75rem] text-[#8b8b8b]">
              {t("profile.invitation.link_label")}
            </span>
            <div className="flex min-w-0 items-center gap-2">
              {/*
                `dir="rtl"` + `truncate`: cắt liên kết dài ở ĐẦU chứ không ở cuối, để
                phần mã giới thiệu — thứ duy nhất người dùng cần nhận ra — luôn hiện.
                Cắt ở cuối sẽ ra "https://resortworld.../..." và mọi liên kết trông
                giống hệt nhau.
              */}
              <span
                dir="rtl"
                className="truncate text-[0.75rem] text-white/90"
                title={fullLink}
              >
                {fullLink || "—"}
              </span>
              <button
                type="button"
                id="copy-referral-link"
                disabled={!fullLink}
                onClick={() => fullLink && copy("link", fullLink)}
                aria-label={t("profile.invitation.copy_link")}
                className="shrink-0 text-[#fe1616] transition-opacity active:opacity-60 disabled:opacity-40"
              >
                {copied === "link" ? (
                  <Check className="size-4 text-emerald-500" />
                ) : (
                  <Copy className="size-4" />
                )}
              </button>
            </div>
          </div>
        </div>
      </main>
    </MobileShell>
  );
}
