"use client";

import React from "react";
import Image from "next/image";
import { Loader2 } from "lucide-react";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Khung chung cho ba trang lịch sử tài sản: thanh tiêu đề, các trạng thái, phân trang.
 *
 * Tách ra vì ba trang chỉ khác nhau ở nguồn dữ liệu và cách vẽ từng thẻ. Để mỗi trang tự
 * dựng khung thì sửa trạng thái rỗng phải nhớ sửa ba nơi.
 *
 * KHÔNG dùng lại `HistoryShell` của khu đặt cược: khung đó bọc trong `PlayerLayout` (có
 * thanh điều hướng dưới) và nút quay lại của nó nhận đích từ `?ref=`. Các trang tài sản
 * dùng thanh tiêu đề `type="default"` không có thanh dưới, và bản gốc đặt đích quay lại
 * cứng theo từng trang.
 *
 * TRẠNG THÁI RỖNG lấy đúng bản gốc: logo `size-15` cộng dòng chữ, đặt ở GIỮA vùng nhìn
 * thấy bằng `absolute top-1/2 left-1/2 -translate-1/2` — không phải nằm ở đầu danh sách.
 */
export interface AssetHistoryShellProps {
  title: string;
  backHref: string;
  loading: boolean;
  error: string;
  /** True khi tải xong mà không có dòng nào. */
  empty: boolean;
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  /** Nút góc phải thanh tiêu đề, ví dụ lối vào lịch sử. */
  rightSlot?: React.ReactNode;
  children: React.ReactNode;
}

export const AssetHistoryShell: React.FC<AssetHistoryShellProps> = ({
  title,
  backHref,
  loading,
  error,
  empty,
  page,
  totalPages,
  onPageChange,
  rightSlot,
  children,
}) => {
  const { t } = useTranslation();

  return (
    <MobileShell
      header={<TopNavigationBar backHref={backHref} rightSlot={rightSlot} title={title} />}
    >
      {/* `relative` để trạng thái rỗng định vị theo vùng nội dung, không phải theo cả
          trang — thiếu nó thì logo bị thanh tiêu đề che một nửa. */}
      <main className="relative flex grow flex-col p-5">
        {loading ? (
          <div className="absolute top-1/2 left-1/2 flex -translate-1/2 flex-col items-center gap-y-2">
            <Loader2 aria-hidden="true" className="size-6 animate-spin text-primary" />
            <p className="text-[0.75rem] font-bold leading-normal text-primary">
              {t("asset.loading")}
            </p>
          </div>
        ) : error ? (
          <div className="absolute top-1/2 left-1/2 flex -translate-1/2 flex-col items-center gap-y-3">
            <p
              className="text-center text-[0.875rem] font-bold text-[#ff4355]"
              role="alert"
            >
              {error}
            </p>
            <button
              className="bg-primary px-4 py-2 text-[0.75rem] font-bold text-white"
              onClick={() => onPageChange(page)}
              type="button"
            >
              {t("asset.retry")}
            </button>
          </div>
        ) : empty ? (
          <div className="absolute top-1/2 left-1/2 flex -translate-1/2 flex-col items-center gap-y-3">
            <Image
              alt=""
              aria-hidden="true"
              className="size-15"
              height={791}
              src="/logo/logo2.png"
              width={800}
            />
            <p className="text-center font-bold text-white">{t("asset.no_records")}</p>
          </div>
        ) : (
          <>
            <div className="flex flex-col gap-y-4">{children}</div>

            {/* Phân trang chỉ hiện khi có nhiều hơn một trang. Hai nút mờ đi thay vì biến
                mất ở đầu và cuối danh sách, để chiều rộng hàng không nhảy khi chuyển. */}
            {totalPages > 1 ? (
              <div className="mt-5 flex items-center justify-center gap-x-4">
                <button
                  className="border border-[#2E2E2E] px-3 py-1.5 text-[0.75rem] font-bold text-white disabled:opacity-30"
                  disabled={page <= 0}
                  onClick={() => onPageChange(page - 1)}
                  type="button"
                >
                  {t("asset.prev_page")}
                </button>
                <span className="text-[0.75rem] font-bold text-[#828282]">
                  {t("asset.page_of", {
                    page: String(page + 1),
                    total: String(totalPages),
                  })}
                </span>
                <button
                  className="border border-[#2E2E2E] px-3 py-1.5 text-[0.75rem] font-bold text-white disabled:opacity-30"
                  disabled={page >= totalPages - 1}
                  onClick={() => onPageChange(page + 1)}
                  type="button"
                >
                  {t("asset.next_page")}
                </button>
              </div>
            ) : null}
          </>
        )}
      </main>
    </MobileShell>
  );
};
