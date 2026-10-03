"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Loader2,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Eye,
  EyeOff,
  Trash2,
  ShieldAlert,
  CheckCircle2,
  X,
  Lock,
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
  hidden?: boolean;
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

interface PendingAction {
  type: "TOGGLE_HIDE" | "DELETE" | "TOGGLE_HIDE_ALL";
  txId?: string;
  hide?: boolean;
  title: string;
  desc: string;
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
 *
 * Hỗ trợ:
 * - Ẩn / Hiện dòng giao dịch (âm thầm đối với người chơi, không gửi thông báo)
 * - Xóa vĩnh viễn dòng giao dịch
 * - Bộ lọc "Ẩn các dòng đã ẩn" trên giao diện Admin
 * - Bảo vệ bằng mã PIN bảo mật Admin
 */
export const WalletLedgerPanel: React.FC<Props> = ({ userId }) => {
  const { t } = useTranslation();
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  // Bộ lọc ẩn các dòng đã ẩn khỏi tầm mắt của Admin
  const [hideHiddenFromAdmin, setHideHiddenFromAdmin] = useState(false);

  // Thao tác yêu cầu PIN bảo mật
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [pinInput, setPinInput] = useState("");
  const [showPinText, setShowPinText] = useState(false);
  const [pinSubmitting, setPinSubmitting] = useState(false);
  const [pinError, setPinError] = useState("");
  const [successToast, setSuccessToast] = useState("");

  /** Nhãn loại bút toán; loại lạ hiện nguyên mã thay vì để trống. */
  const typeLabel = (refType: string): string => {
    const key = `admin.ledger_types.${refType}`;
    const label = t(key);
    return label === key ? refType : label;
  };

  /**
   * Lấy một trang sổ ví.
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
    let cancelled = false;

    (async () => {
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

  /** Thử lại từ nút bấm. */
  const reload = useCallback(async () => {
    setLoading(true);
    const data = await fetchEntries();
    setEntries(data?.content ?? []);
    setTotalPages(data?.totalPages || 1);
    setLoading(false);
  }, [fetchEntries]);

  /** Xử lý xác nhận thực hiện thao tác nhạy cảm với mã PIN */
  const handleExecutePinAction = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!pinInput.trim() || !pendingAction) return;

    setPinSubmitting(true);
    setPinError("");

    try {
      if (pendingAction.type === "TOGGLE_HIDE" && pendingAction.txId) {
        await adminFetch(
          `/admin/users/${userId}/wallet/transactions/${pendingAction.txId}/toggle-hide`,
          {
            method: "PATCH",
            body: JSON.stringify({ confirmPin: pinInput.trim() }),
          }
        );
        setSuccessToast("Đã cập nhật trạng thái ẩn/hiện giao dịch thành công (âm thầm với khách).");
      } else if (pendingAction.type === "DELETE" && pendingAction.txId) {
        await adminFetch(
          `/admin/users/${userId}/wallet/transactions/${pendingAction.txId}`,
          {
            method: "DELETE",
            body: JSON.stringify({ confirmPin: pinInput.trim() }),
          }
        );
        setSuccessToast("Đã xóa vĩnh viễn giao dịch khỏi hệ thống thành công.");
      } else if (pendingAction.type === "TOGGLE_HIDE_ALL") {
        await adminFetch(
          `/admin/users/${userId}/wallet/transactions/toggle-hide-all?hide=${pendingAction.hide ?? true}`,
          {
            method: "PATCH",
            body: JSON.stringify({ confirmPin: pinInput.trim() }),
          }
        );
        setSuccessToast(
          pendingAction.hide
            ? "Đã ẩn TOÀN BỘ lịch sử giao dịch ví của người chơi này (âm thầm)."
            : "Đã hiện lại TOÀN BỘ lịch sử giao dịch ví của người chơi này."
        );
      }

      setPendingAction(null);
      setPinInput("");
      await reload();
      setTimeout(() => setSuccessToast(""), 4000);
    } catch (err: unknown) {
      setPinError((err as Error).message || "Mã PIN không chính xác hoặc quyền bị từ chối.");
    } finally {
      setPinSubmitting(false);
    }
  };

  const displayedEntries = hideHiddenFromAdmin
    ? entries.filter((e) => !e.hidden)
    : entries;

  const hiddenCount = entries.filter((e) => e.hidden).length;

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

  return (
    <div className="flex flex-col gap-3">
      {/* Toast thông báo thành công */}
      {successToast && (
        <div className="flex items-center gap-2 px-3 py-2 bg-emerald-50 border border-emerald-300 text-emerald-800 text-xs font-semibold rounded-xl animate-in fade-in slide-in-from-top-1">
          <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
          <span>{successToast}</span>
        </div>
      )}

      {/* Thanh công cụ quản trị: Lọc ẩn/hiện cho Admin và Thao tác hàng loạt */}
      <div className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl">
        <div className="flex items-center gap-2.5">
          <label className="flex items-center gap-2 cursor-pointer select-none text-xs font-bold text-slate-700 hover:text-slate-900">
            <input
              type="checkbox"
              checked={hideHiddenFromAdmin}
              onChange={(e) => setHideHiddenFromAdmin(e.target.checked)}
              className="w-4 h-4 rounded border-slate-300 text-red-600 focus:ring-red-500 cursor-pointer"
            />
            <span>Ẩn các dòng đã ẩn (không show ra cho admin)</span>
          </label>

          {hiddenCount > 0 && (
            <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-rose-100 text-rose-700 border border-rose-200">
              {hiddenCount} dòng đang ẩn khỏi khách
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5 ml-auto">
          <button
            type="button"
            onClick={() => {
              setPendingAction({
                type: "TOGGLE_HIDE_ALL",
                hide: true,
                title: "Ẩn TOÀN BỘ lịch sử giao dịch",
                desc: "Toàn bộ giao dịch trong ví của người chơi này sẽ bị ẩn âm thầm (người chơi không thấy gì và không nhận thông báo).",
              });
              setPinInput("");
              setPinError("");
            }}
            className="flex items-center gap-1 px-2.5 py-1 text-[11px] font-bold text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-300 rounded-lg transition"
            title="Ẩn toàn bộ lịch sử khỏi người chơi"
          >
            <EyeOff className="w-3.5 h-3.5 text-amber-600" />
            Ẩn tất cả
          </button>
          <button
            type="button"
            onClick={() => {
              setPendingAction({
                type: "TOGGLE_HIDE_ALL",
                hide: false,
                title: "Hiện lại TOÀN BỘ lịch sử giao dịch",
                desc: "Tất cả giao dịch trong ví của người chơi này sẽ được hiển thị lại bình thường.",
              });
              setPinInput("");
              setPinError("");
            }}
            className="flex items-center gap-1 px-2.5 py-1 text-[11px] font-bold text-emerald-800 bg-emerald-50 hover:bg-emerald-100 border border-emerald-300 rounded-lg transition"
            title="Hiện lại toàn bộ lịch sử cho người chơi"
          >
            <Eye className="w-3.5 h-3.5 text-emerald-600" />
            Hiện tất cả
          </button>
        </div>
      </div>

      {/* Danh sách giao dịch */}
      {displayedEntries.length === 0 ? (
        <div className="py-10 text-center text-xs text-slate-500 font-medium bg-slate-50/50 border border-dashed border-slate-200 rounded-xl">
          {entries.length === 0
            ? t("admin.users.ledger.empty")
            : "Tất cả các dòng giao dịch đã bị ẩn theo bộ lọc của Admin."}
        </div>
      ) : (
        <div className="flex flex-col divide-y divide-slate-100 border border-slate-200 rounded-xl overflow-hidden shadow-xs">
          {displayedEntries.map((e) => {
            const isCredit = !isZero(e.credit);
            const amount = isCredit ? e.credit : e.debit;
            const manual = MANUAL_TYPES.has(e.refType);
            const isHidden = !!e.hidden;

            return (
              <div
                key={e.id}
                className={`flex items-center gap-3 px-3.5 py-3 transition-colors ${
                  isHidden
                    ? "bg-rose-50/40 hover:bg-rose-50/70 border-l-4 border-l-rose-500"
                    : "bg-white hover:bg-slate-50"
                }`}
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
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="text-xs font-bold text-slate-900 truncate">
                      {typeLabel(e.refType)}
                    </span>
                    {/* Bút toán tay admin */}
                    {manual && (
                      <span className="px-1.5 py-0.5 rounded font-bold text-[9px] bg-amber-50 text-amber-700 border border-amber-200 shrink-0">
                        {t("admin.users.ledger.admin_badge")}
                      </span>
                    )}
                    {/* Badge đã ẩn khỏi người chơi */}
                    {isHidden && (
                      <span className="px-1.5 py-0.5 rounded font-extrabold text-[9px] bg-rose-100 text-rose-700 border border-rose-300 shrink-0 flex items-center gap-1 shadow-2xs">
                        <EyeOff className="w-2.5 h-2.5" />
                        ĐÃ ẨN KHỎI KHÁCH
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

                {/* Nút thao tác Quản trị: Ẩn/Hiện và Xóa */}
                <div className="flex items-center gap-1 shrink-0 ml-1 pl-2 border-l border-slate-200">
                  {/* Nút Ẩn / Hiện */}
                  {isHidden ? (
                    <button
                      type="button"
                      onClick={() => {
                        setPendingAction({
                          type: "TOGGLE_HIDE",
                          txId: e.id,
                          title: "Hiện lại giao dịch này",
                          desc: `Giao dịch ${typeLabel(e.refType)} (${isCredit ? "+" : "−"}${formatMoney(amount)}) sẽ hiển thị lại bình thường trong lịch sử người chơi.`,
                        });
                        setPinInput("");
                        setPinError("");
                      }}
                      className="p-1.5 rounded-lg bg-emerald-50 text-emerald-700 hover:bg-emerald-100 border border-emerald-300 transition"
                      title="Hiện lại cho người chơi"
                    >
                      <Eye className="w-3.5 h-3.5" />
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => {
                        setPendingAction({
                          type: "TOGGLE_HIDE",
                          txId: e.id,
                          title: "Ẩn giao dịch khỏi người chơi",
                          desc: `Giao dịch ${typeLabel(e.refType)} (${isCredit ? "+" : "−"}${formatMoney(amount)}) sẽ bị ẩn âm thầm khỏi lịch sử ví người chơi (không gửi thông báo).`,
                        });
                        setPinInput("");
                        setPinError("");
                      }}
                      className="p-1.5 rounded-lg bg-amber-50 text-amber-700 hover:bg-amber-100 border border-amber-300 transition"
                      title="Ẩn khỏi người chơi (âm thầm)"
                    >
                      <EyeOff className="w-3.5 h-3.5" />
                    </button>
                  )}

                  {/* Nút Xóa vĩnh viễn */}
                  <button
                    type="button"
                    onClick={() => {
                      setPendingAction({
                        type: "DELETE",
                        txId: e.id,
                        title: "Xóa vĩnh viễn dòng lịch sử",
                        desc: `Dòng giao dịch ${typeLabel(e.refType)} (${isCredit ? "+" : "−"}${formatMoney(amount)}) sẽ bị xóa hoàn toàn khỏi cơ sở dữ liệu và biến mất vĩnh viễn khỏi lịch sử người chơi.`,
                      });
                      setPinInput("");
                      setPinError("");
                    }}
                    className="p-1.5 rounded-lg bg-rose-50 text-rose-700 hover:bg-rose-100 border border-rose-300 transition"
                    title="Xóa vĩnh viễn dòng lịch sử này"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

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

      {/* Modal nhập mã PIN bảo mật Admin */}
      {pendingAction && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden flex flex-col animate-in zoom-in-95 duration-150">
            {/* Header modal */}
            <div className="flex items-center justify-between px-5 py-4 bg-slate-900 text-white">
              <div className="flex items-center gap-2.5">
                <div className="p-2 rounded-lg bg-amber-500/20 text-amber-400 border border-amber-500/30">
                  <ShieldAlert className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold">{pendingAction.title}</h3>
                  <p className="text-[11px] text-slate-400">Xác thực mã PIN quản trị viên</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => {
                  setPendingAction(null);
                  setPinInput("");
                  setPinError("");
                }}
                className="p-1 text-slate-400 hover:text-white rounded-lg transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Nội dung modal */}
            <form onSubmit={handleExecutePinAction} className="p-5 flex flex-col gap-4">
              <p className="text-xs text-slate-600 leading-relaxed">
                {pendingAction.desc}
              </p>

              <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl flex items-start gap-2.5 text-amber-900 text-[11px]">
                <Lock className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                <div>
                  <span className="font-bold">Lưu ý bảo mật:</span> Thao tác này âm thầm đối với người chơi (không gửi thông báo). Cần nhập mã PIN quản trị bảo vệ để xác nhận.
                </div>
              </div>

              {pinError && (
                <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs font-semibold flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
                  <span>{pinError}</span>
                </div>
              )}

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-slate-700">
                  Mã PIN bảo mật Admin:
                </label>
                <div className="relative">
                  <input
                    type={showPinText ? "text" : "password"}
                    autoFocus
                    maxLength={10}
                    placeholder="Nhập mã PIN bảo mật"
                    value={pinInput}
                    onChange={(e) => setPinInput(e.target.value)}
                    className="w-full px-3.5 py-2.5 text-sm font-mono tracking-widest bg-slate-50 border border-slate-300 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-amber-500 focus:bg-white transition"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPinText(!showPinText)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-400 hover:text-slate-700"
                  >
                    {showPinText ? "Ẩn" : "Hiện"}
                  </button>
                </div>
              </div>

              <div className="flex items-center justify-end gap-2.5 mt-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  disabled={pinSubmitting}
                  onClick={() => {
                    setPendingAction(null);
                    setPinInput("");
                    setPinError("");
                  }}
                  className="px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-100 rounded-xl transition"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={pinSubmitting || !pinInput.trim()}
                  className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-white bg-slate-900 hover:bg-black disabled:opacity-50 rounded-xl shadow-xs transition"
                >
                  {pinSubmitting && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  Xác nhận thực hiện
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
