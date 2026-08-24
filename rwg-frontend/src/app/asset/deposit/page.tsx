"use client";

import React, { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { useTranslation } from "@/context/LanguageContext";
import { ApiError, getPlayerToken, walletMe } from "@/lib/playerApi";
import { Headphones, ChevronRight } from "lucide-react";

/**
 * Trang nạp tiền — CỬA VÀO dẫn tới Chăm sóc khách hàng.
 *
 * Theo yêu cầu mới của khách hàng:
 * Khách hàng không cần tính năng nạp tiền tự động hay điền form nạp tiền trên giao diện,
 * họ chỉ cần trò chuyện với Admin, và Admin sẽ tự cộng tiền cho khách trực tiếp trong chat.
 * Do đó trang này CHỈ có một việc: dẫn người chơi sang khung chat hỗ trợ.
 *
 * Đã gỡ thẻ số dư ví và khối hướng dẫn 3 bước. Số dư đã hiện ở trang Tài sản và trang
 * Hồ sơ nên nhắc lại ở đây chỉ đẩy nút bấm xuống dưới màn hình. Khối hướng dẫn cũng vậy:
 * nó giải thích một quy trình mà bước đầu tiên chính là bấm cái nút bên dưới, nên nó chen
 * vào giữa người chơi và việc họ cần làm.
 */
export default function DepositPage() {
  const router = useRouter();
  const { t } = useTranslation();

  const [checked, setChecked] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /**
   * Kiểm tra phiên đăng nhập còn hiệu lực.
   *
   * GIỮ LẠI lời gọi `walletMe()` dù trang không còn hiển thị số dư: `getPlayerToken()`
   * chỉ biết token CÓ TỒN TẠI, không biết nó còn hạn. Không kiểm ở đây thì người chơi
   * với phiên đã hết hạn sẽ bấm sang trang chat rồi mới bị đẩy về đăng nhập.
   */
  const loadData = useCallback(async () => {
    try {
      await walletMe();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        router.replace("/login");
      } else {
        setError(t("deposit.err_failed"));
      }
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
    void loadData();
  }, [router, loadData]);

  if (!checked || loading) {
    return (
      <MobileShell>
        <div className="flex h-64 items-center justify-center text-sm text-[#8b8b8b]">
          {t("asset.loading")}...
        </div>
      </MobileShell>
    );
  }

  return (
    <MobileShell
      header={
        <TopNavigationBar
          title={t("deposit.title")}
          backHref="/profile"
        />
      }
    >
      <main className="flex grow flex-col px-5 py-6">
        <h1 className="sr-only">{t("deposit.title")}</h1>

        {error && (
          <p className="mb-4 text-xs font-medium text-[#fe1616]" role="alert">
            {error}
          </p>
        )}

        {/* Nút liên hệ chăm sóc khách hàng — việc duy nhất của trang này.
            KHÔNG dùng `mt-auto` nữa: trước đây nút bị đẩy xuống đáy để nhường chỗ cho
            thẻ số dư và khối hướng dẫn ở trên. Giờ không còn gì ở trên nên `mt-auto`
            sẽ để lại một khoảng trống lớn rồi mới tới nút. */}
        <Link
          href="/profile/contact-us"
          id="deposit-contact-support"
          className="flex w-full items-center gap-3 bg-[#fe1616] p-4 text-start transition-opacity active:opacity-85"
        >
          <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-white/10 text-white">
            <Headphones className="size-5" />
          </div>
          <div className="flex min-w-0 flex-col">
            <span className="text-[0.9375rem] font-bold text-white">
              {t("deposit.contact_support")}
            </span>
            <span className="mt-0.5 text-[11px] leading-snug text-white/75">
              {t("deposit.contact_support_hint")}
            </span>
          </div>
          <ChevronRight className="ms-auto size-5 shrink-0 text-white" />
        </Link>
      </main>
    </MobileShell>
  );
}
