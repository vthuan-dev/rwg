"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Loader2,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { formatMoney } from "@/lib/money";
import { useTranslation } from "@/context/LanguageContext";

/** Một dòng sổ ví — khớp WalletTransactionResponse của backend. */
interface LedgerEntry {
  id: string;
  createdAt: string;
  /** Số tiền trừ khỏi ví. Chuỗi "0..." nếu dòng này là ghi có. */
  debit: string;
  credit: string;
  balanceAfter: string;
  refType: string;
  refId: string | null;
  status: string;
}

/** Một trang sổ ví, khớp `PageResponse` của backend. */
interface LedgerPage {
  content: LedgerEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

interface Props {
  userId: string;
}

/** Bút toán do admin tạo tay cần nhận ra ngay khi soát sổ. */
const MANUAL_TYPES = new Set(["ADJUSTMENT"]);

/** Chuỗi tiền chỉ gồm số 0 và dấu chấm nghĩa là dòng này không có giá trị. */
const isZero = (raw: string | null | undefined): boolean =>
  !raw || /^0*(\.0*)?$/.test(raw);

/**
 * Sổ giao dịch ví của một người dùng.
 *
 * Đặt ngay trong hộp thoại chi tiết thay vì trang riêng: khi người vận hành đang
 * cân nhắc cộng hay trừ tiền, họ cần thấy lịch sử tại chỗ, không phải mở trang
 * khác rồi dán mã người dùng vào.
 */
export const WalletLedgerPanel: React.FC<Props> = ({ userId }) => {
  const { t } = useTranslation();
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  /** Nhãn loại bút toán; loại lạ hiện nguyên mã thay vì để trống. */
  const typeLabel = (refType: string): string => {
    const key = `admin.ledger_types.${refType}`;
    const label = t(key);
    return label === key ? refType : label;
  };

  /**
   * Lấy một trang sổ ví.
   *
   * Trả dữ liệu về thay vì tự đặt state: `setState` gọi đồng bộ trong thân effect gây
   * chuỗi render liên tiếp và luật lint của dự án chặn.
   */
  const fetchEntries = useCallback(async (): Promise<LedgerPage | null> => {
    try {
      const data = await adminFetch<LedgerPage>(
        `/admin/users/${userId}/wallet/transactions?page=${page}&size=10`
      );
      setError("");
      return data;
    } catch (err) {
      setError((err as Error).message);
      return null;
    }
  }, [userId, page]);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể đóng hộp thoại trước khi request xong, và ghi state
    // vào component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      // Đặt state bên trong hàm async, không ở thân effect — xem lý do ở trên.
      setLoading(true);
      const data = await fetchEntries();
      if (cancelled) return;
      setEntries(data?.content ?? []);
      setTotalPages(data?.totalPages || 1);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchEntries]);

  /** Thử lại từ nút bấm. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    const data = await fetchEntries();
    setEntries(data?.content ?? []);
    setTotalPages(data?.totalPages || 1);
    setLoading(false);
  }, [fetchEntries]);

  if (loading) {
    return (
      <div className="flex items-center justify-center gap-2 py-10 text-xs text-slate-500 font-semibold">
        <Loader2 className="w-4 h-4 animate-spin" />
        {t("admin.users.ledger.loading")}
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
        {t("admin.users.ledger.empty")}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col divide-y divide-slate-100 border border-slate-200 rounded-xl overflow-hidden">
        {entries.map((e) => {
          const isCredit = !isZero(e.credit);
          const amount = isCredit ? e.credit : e.debit;
          const manual = MANUAL_TYPES.has(e.refType);

          return (
            <div
              key={e.id}
              className="flex items-center gap-3 px-3.5 py-3 bg-white hover:bg-slate-50 transition-colors"
            >
              <div
                className={`p-2 rounded-lg border shrink-0 ${
                  isCredit
                    ? "bg-emerald-50 border-emerald-200 text-emerald-600"
                    : "bg-red-50 border-red-200 text-red-600"
                }`}
              >
                {isCredit ? (
                  <ArrowDownLeft className="w-3.5 h-3.5" />
                ) : (
                  <ArrowUpRight className="w-3.5 h-3.5" />
                )}
              </div>

              <div className="flex flex-col gap-0.5 min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className="text-xs font-bold text-slate-900 truncate">
                    {typeLabel(e.refType)}
                  </span>
                  {/* But toan tay phai nhan ra duoc ngay: day la thao tac cua
                      con nguoi, khac han cac dong do he thong sinh ra. */}
                  {manual && (
                    <span className="px-1.5 py-0.5 rounded font-bold text-[9px] bg-amber-50 text-amber-700 border border-amber-200 shrink-0">
                      {t("admin.users.ledger.admin_badge")}
                    </span>
                  )}
                </div>
                <span className="text-[10px] text-slate-400 font-medium">
                  {new Date(e.createdAt).toLocaleString()}
                </span>
              </div>

              <div className="flex flex-col items-end gap-0.5 shrink-0">
                <span
                  className={`text-xs font-black tabular-nums ${
                    isCredit ? "text-emerald-700" : "text-red-700"
                  }`}
                >
                  {isCredit ? "+" : "−"}
                  {formatMoney(amount)}
                </span>
                <span className="text-[10px] text-slate-500 font-semibold tabular-nums">
                  {formatMoney(e.balanceAfter)}
                </span>
              </div>
            </div>
          );
        })}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-[11px] text-slate-500">
          <span className="font-medium">
            {t("admin.states.page_of", { page: page + 1, total: totalPages })}
          </span>
          <div className="flex items-center gap-1.5">
            <button
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="p-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200"
              aria-label={t("admin.states.prev_page")}
            >
              <ChevronLeft className="w-3.5 h-3.5 text-slate-700" />
            </button>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
              className="p-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200"
              aria-label={t("admin.states.next_page")}
            >
              <ChevronRight className="w-3.5 h-3.5 text-slate-700" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
