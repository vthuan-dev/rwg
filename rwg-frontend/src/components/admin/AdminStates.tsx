"use client";

import React from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

interface Props {
  /**
   * Thông báo từ backend. KHÔNG dịch ở đây: backend đã trả message theo ngôn ngữ
   * qua messages*.properties.
   */
  message: string;
  onRetry?: () => void;
}

/**
 * Thông báo lỗi tải dữ liệu cho các trang quản trị.
 *
 * VÌ SAO CẦN: trước đây khi gọi API thất bại, các trang đổ dữ liệu bịa vào giao
 * diện mà không báo gì. Ở khu quản trị tiền, số liệu giả trông như thật nguy hiểm
 * hơn hẳn một trang trống — người vận hành có thể ra quyết định dựa trên nó.
 */
export const AdminErrorState: React.FC<Props> = ({ message, onRetry }) => {
  const { t } = useTranslation();

  return (
    <div className="flex flex-col items-center gap-4 py-14 px-6 bg-white border border-red-200 rounded-2xl">
      <div className="p-3 rounded-xl bg-red-50 border border-red-200">
        <AlertTriangle className="w-6 h-6 text-red-600" />
      </div>
      <div className="flex flex-col items-center gap-1.5 text-center max-w-md">
        <span className="text-sm font-extrabold text-slate-900">
          {t("admin.states.load_failed")}
        </span>
        <span className="text-xs text-slate-600 font-medium leading-relaxed">
          {message}
        </span>
      </div>
      {onRetry && (
        <button
          onClick={onRetry}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition-colors"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          {t("admin.states.retry")}
        </button>
      )}
    </div>
  );
};

/** Trạng thái rỗng THẬT — API trả về thành công nhưng không có dòng nào. */
export const AdminEmptyState: React.FC<{ message: string }> = ({ message }) => (
  <div className="py-12 text-center text-xs text-slate-500 font-medium">
    {message}
  </div>
);
