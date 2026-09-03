"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  LogIn,
  ShieldAlert,
  Loader2,
  AlertTriangle,
  Globe,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { useTranslation } from "@/context/LanguageContext";
import { formatRelativeTime, formatAbsoluteDateTime } from "@/lib/datetime";

/** Một lần đăng nhập — khớp LoginHistoryEntryResponse của backend. */
interface LoginHistoryEntry {
  at: string;
  /** false CHỈ khi lần đó đăng nhập thất bại. */
  success: boolean;
  /** Có thể null với bản ghi cũ ghi trước khi hệ thống lấy IP. */
  ipAddress: string | null;
  channel: "PLAYER" | "BACKOFFICE";
}

interface Props {
  userId: string;
}

/** Số dòng nạp về. Backend chặn trần 100; 20 là đủ để thấy một chuỗi bất thường. */
const LIMIT = 20;

/**
 * Lịch sử đăng nhập của một tài khoản: khi nào, từ IP nào, thành công hay thất bại.
 *
 * ĐẶT NGAY TRONG HỘP THOẠI CHI TIẾT, cùng lý do như sổ ví: khi người vận hành đang quyết
 * định có khoá một tài khoản hay không, họ cần thấy chuỗi đăng nhập tại chỗ. Bắt họ mở trang
 * nhật ký hệ thống rồi dán mã người dùng vào là ba bước cho một câu hỏi.
 *
 * Bảng danh sách đã hiện MỘT mốc (lần gần nhất). Panel này trả lời câu tiếp theo — "trước đó
 * thì sao" — thứ mà một mốc đơn lẻ không nói được: 12 lần thất bại liên tiếp rồi một lần
 * thành công là một câu chuyện hoàn toàn khác với một lần đăng nhập bình thường.
 */
export const LoginHistoryPanel: React.FC<Props> = ({ userId }) => {
  const { t, locale } = useTranslation();
  const [entries, setEntries] = useState<LoginHistoryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  /**
   * Nạp lịch sử.
   *
   * Trả dữ liệu về thay vì tự đặt state: `setState` gọi đồng bộ trong thân effect gây chuỗi
   * render liên tiếp và luật lint của dự án chặn.
   */
  const fetchEntries = useCallback(async (): Promise<LoginHistoryEntry[] | null> => {
    try {
      const data = await adminFetch<LoginHistoryEntry[]>(
        `/admin/users/${userId}/login-history?limit=${LIMIT}`
      );
      setError("");
      return data;
    } catch (err) {
      setError((err as Error).message);
      return null;
    }
  }, [userId]);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể đóng hộp thoại trước khi request xong, và ghi state vào
    // component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      setLoading(true);
      const data = await fetchEntries();
      if (cancelled) return;
      setEntries(data ?? []);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchEntries]);

  /** Thử lại từ nút bấm. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    setEntries((await fetchEntries()) ?? []);
    setLoading(false);
  }, [fetchEntries]);

  if (loading) {
    return (
      <div className="flex items-center justify-center gap-2 py-10 text-xs text-slate-500 font-semibold">
        <Loader2 className="w-4 h-4 animate-spin" />
        {t("admin.users.login_history.loading")}
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
        <AlertTriangle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
        <div className="flex flex-col gap-2">
          <span className="text-xs text-red-700 font-semibold">{error}</span>
          <button
            onClick={reload}
            className="text-[11px] font-bold text-red-700 underline w-fit"
          >
            {t("admin.states.retry")}
          </button>
        </div>
      </div>
    );
  }

  if (entries.length === 0) {
    return (
      <div className="py-10 text-center text-xs text-slate-500 font-medium">
        {t("admin.users.login_history.empty")}
      </div>
    );
  }

  return (
    <div className="flex flex-col divide-y divide-slate-100 border border-slate-200 rounded-xl overflow-hidden">
      {entries.map((e, index) => (
        <div
          // `at` KHÔNG đủ làm khoá: hai lần gõ sai liên tiếp trong cùng một giây sẽ trùng
          // mốc thời gian. Ghép thêm chỉ số để khoá luôn phân biệt được.
          key={`${e.at}-${index}`}
          className="flex items-center gap-3 px-3.5 py-3 bg-white hover:bg-slate-50 transition-colors"
        >
          <div
            className={`p-2 rounded-lg border shrink-0 ${
              e.success
                ? "bg-emerald-50 border-emerald-200 text-emerald-600"
                : "bg-red-50 border-red-200 text-red-600"
            }`}
          >
            {e.success ? (
              <LogIn className="w-3.5 h-3.5" />
            ) : (
              <ShieldAlert className="w-3.5 h-3.5" />
            )}
          </div>

          <div className="flex flex-col gap-0.5 min-w-0 flex-1">
            <div className="flex items-center gap-1.5 flex-wrap">
              <span
                className={`text-xs font-bold ${
                  e.success ? "text-slate-900" : "text-red-700"
                }`}
              >
                {e.success
                  ? t("admin.users.login_history.success")
                  : t("admin.users.login_history.failed")}
              </span>

              {/* Đăng nhập vào khu quản trị được đánh dấu riêng: với một tài khoản nhân
                  sự, vào backoffice và vào trang khách là hai mức rủi ro khác nhau. */}
              {e.channel === "BACKOFFICE" && (
                <span className="px-1.5 py-0.5 rounded font-bold text-[9px] bg-violet-50 text-violet-700 border border-violet-200 shrink-0">
                  {t("admin.users.login_history.channel_backoffice")}
                </span>
              )}
            </div>

            {/* Thời gian tương đối đọc nhanh hơn, ngày giờ đầy đủ nằm trong tooltip cho
                lúc cần con số chính xác để đối chiếu với nhật ký khác. */}
            <span
              className="text-[10px] text-slate-400 font-medium"
              title={formatAbsoluteDateTime(e.at, locale)}
            >
              {formatRelativeTime(e.at, locale)}
            </span>
          </div>

          {e.ipAddress && (
            <div className="flex items-center gap-1 shrink-0 text-slate-500">
              <Globe className="w-3 h-3 shrink-0" />
              <span className="text-[10px] font-semibold tabular-nums">
                {e.ipAddress}
              </span>
            </div>
          )}
        </div>
      ))}
    </div>
  );
};
