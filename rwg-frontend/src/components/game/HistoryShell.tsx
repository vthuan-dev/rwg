"use client";

import React from "react";
import { Loader2 } from "lucide-react";
import { PlayerLayout } from "@/components/layout/PlayerLayout";
import { useTranslation } from "@/context/LanguageContext";

export interface HistoryShellProps {
  /** Tiêu đề trang, đã dịch. */
  title: string;
  /** Đích nút quay lại. Đã đi qua `safeInternalPath` ở trang gọi. */
  backHref: string;
  loading: boolean;
  error: string;
  /** True khi tải xong mà không có dòng nào. */
  empty: boolean;
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  children: React.ReactNode;
}

/**
 * Khung chung cho hai trang lịch sử: thanh tiêu đề, các trạng thái, và phân trang.
 *
 * Tách ra vì hai trang lịch sử chỉ khác nhau ở phần bảng. Để mỗi trang tự dựng khung thì
 * sửa thanh tiêu đề phải nhớ sửa hai nơi.
 *
 * Bọc trong `PlayerLayout` để có cùng bề rộng và thanh điều hướng dưới như trang đặt cược
 * — không thì hai trang này rộng hết màn hình trên máy tính trong khi các trang khác bị
 * giới hạn, và bấm quay lại là thấy bố cục nhảy.
 *
 * Nút quay lại dùng `<a>` thường chứ không `next/link`: đích đến từ tham số `?ref=` trên
 * URL nên có thể là một trang không tồn tại, và `next/link` sẽ tải trước rồi báo lỗi ngay
 * khi nút hiện ra thay vì lúc người dùng bấm.
 */
export const HistoryShell: React.FC<HistoryShellProps> = ({
  title,
  backHref,
  loading,
  error,
  empty,
  page,
  totalPages,
  onPageChange,
  children,
}) => {
  const { t } = useTranslation();

  return (
    <PlayerLayout>
      <main className="flex w-full grow flex-col bg-[#0d0d0d]">
        <header className="flex h-15 items-center gap-x-3 px-5">
          <a
            aria-label={t("bet.back")}
            className="flex size-8 items-center justify-center text-primary"
            href={backHref}
          >
            <i aria-hidden="true" className="icon-icon76 text-[1.25rem]" />
          </a>
          <h1 className="text-[1.25rem] leading-[1.75rem] text-[#d0d5da]">{title}</h1>
        </header>

        <div className="flex grow flex-col px-5 pb-5">
          {loading && (
            <div className="mt-[50px] flex flex-col items-center gap-y-2">
              <Loader2 aria-hidden="true" className="size-6 animate-spin text-primary" />
              <p className="text-[0.75rem] font-bold text-primary">{t("bet.loading")}</p>
            </div>
          )}

          {!loading && error && (
            <div className="mt-[50px] flex flex-col items-center gap-y-3">
              <p
                className="text-center text-[0.875rem] font-bold text-[#ff4355]"
                role="alert"
              >
                {error}
              </p>
              <button
                className="rounded bg-primary px-4 py-2 text-[0.75rem] font-bold text-black"
                onClick={() => onPageChange(page)}
                type="button"
              >
                {t("bet.retry")}
              </button>
            </div>
          )}

          {!loading && !error && empty && (
            <p className="mt-[50px] text-center text-[0.8125rem] font-semibold text-[#828282]">
              {t("bet.history_empty")}
            </p>
          )}

          {!loading && !error && !empty && children}

          {/* Phân trang chỉ hiện khi có nhiều hơn một trang. Hai nút mờ đi thay vì biến
              mất ở đầu và cuối danh sách, để chiều rộng hàng không nhảy khi chuyển trang. */}
          {!loading && !error && totalPages > 1 && (
            <div className="mt-5 flex items-center justify-center gap-x-4">
              <button
                className="rounded border border-[#2E2E2E] px-3 py-1.5 text-[0.75rem] font-bold text-white disabled:opacity-30"
                disabled={page <= 0}
                onClick={() => onPageChange(page - 1)}
                type="button"
              >
                {t("bet.prev_page")}
              </button>
              <span className="text-[0.75rem] font-bold text-[#828282]">
                {t("bet.page_of", {
                  page: String(page + 1),
                  total: String(totalPages),
                })}
              </span>
              <button
                className="rounded border border-[#2E2E2E] px-3 py-1.5 text-[0.75rem] font-bold text-white disabled:opacity-30"
                disabled={page >= totalPages - 1}
                onClick={() => onPageChange(page + 1)}
                type="button"
              >
                {t("bet.next_page")}
              </button>
            </div>
          )}
        </div>
      </main>
    </PlayerLayout>
  );
};
