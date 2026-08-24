"use client";

import React, { useState, useEffect, useCallback, useMemo } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  CheckCircle2,
  XCircle,
  Clock,
  RefreshCw,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  ShieldCheck,
  Loader2,
  ArrowUpRight,
  History,
  Landmark,
  MessageSquareQuote,
  Eye,
  EyeOff,
  Copy,
  Check,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { AdminModal } from "@/components/admin/AdminModal";
import { AdminErrorState } from "@/components/admin/AdminStates";

/**
 * Một lệnh rút tiền, khớp `AdminWithdrawalRowResponse` của backend.
 *
 * Dùng cho CẢ hàng chờ và lịch sử: cùng một bản ghi, chỉ khác trạng thái. Tách hai interface
 * sẽ khiến hai bảng dần trôi khỏi nhau mỗi lần backend đổi một trường.
 *
 * Các trường ngân hàng có thể null (lệnh cũ tạo trước khi bắt buộc chọn tài khoản, hoặc bản
 * ghi tài khoản đã bị gỡ). Ba trường quyết định null với lệnh còn PENDING.
 */
interface WithdrawalRow {
  id: string;
  userId: string;
  username: string | null;
  amount: string;
  currency: string;
  status: string;
  bankAccountId: string | null;
  bankCode: string | null;
  maskedLast4: string | null;
  holderName: string | null;
  createdAt: string;
  decidedByUsername: string | null;
  decisionNote: string | null;
  decidedAt: string | null;
}

interface RevealedPayoutAddressResponse {
  id: string;
  payoutType: string;
  fullAddress: string;
  bankCode: string;
  holderName: string;
}

/** Một trang dữ liệu, khớp `PageResponse` của backend. */
interface PageOf<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

const PAGE_SIZE = 10;

/** Bộ lọc của bảng lịch sử. `ALL` gửi request không kèm tham số status. */
type HistoryFilter = "ALL" | "SETTLED" | "VOIDED";

/** Số tiền dạng "$1,234.00 USD" — chuỗi từ backend, không dùng float. */
function formatMoney(amount: string, currency = "USD"): string {
  const value = parseFloat(amount);
  if (Number.isNaN(value)) return `${amount} ${currency}`;
  return `$${value.toLocaleString("en-US", { minimumFractionDigits: 2 })} ${currency}`;
}

export default function AdminPaymentsPage() {
  // ===== Khu 1: lệnh rút đang chờ duyệt =====
  const [pending, setPending] = useState<WithdrawalRow[]>([]);
  const [pendingLoading, setPendingLoading] = useState(true);
  const [pendingError, setPendingError] = useState("");
  const [pendingPage, setPendingPage] = useState(0);
  const [pendingTotalPages, setPendingTotalPages] = useState(1);

  // ===== Khu 2: lịch sử lệnh rút đã xử lý =====
  const [history, setHistory] = useState<WithdrawalRow[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState("");
  const [historyPage, setHistoryPage] = useState(0);
  const [historyTotalPages, setHistoryTotalPages] = useState(1);
  const [historyFilter, setHistoryFilter] = useState<HistoryFilter>("ALL");
  const [historyTotal, setHistoryTotal] = useState(0);

  // ===== Hộp thoại quyết định =====
  const [dialog, setDialog] = useState<
    { row: WithdrawalRow; action: "APPROVED" | "REJECTED" } | null
  >(null);
  const [reason, setReason] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");

  // ===== Mã QR chuyển khoản =====
  const [qrUrl, setQrUrl] = useState<string | null>(null);
  const [bankInfo, setBankInfo] = useState<{
    bankCode: string;
    fullAddress: string;
    holderName: string;
  } | null>(null);
  const [qrLoading, setQrLoading] = useState(false);
  const [qrError, setQrError] = useState("");

  /**
   * Hiện/ẩn số tài khoản đầy đủ và mã QR.
   *
   * MẶC ĐỊNH HIỆN: người vận hành mở hộp thoại này ĐỂ chuyển khoản, nên bắt họ
   * bấm thêm một nút mỗi lần là vô ích. Nút Ẩn dành cho lúc có người đứng sau lưng
   * hoặc đang chia sẻ màn hình.
   *
   * ẨN LUÔN CẢ MÃ QR, không chỉ dòng chứ: QR MÃ HOÁ CHÍNH SỐ TÀI KHOẢN ĐÓ. Che chứ
   * mà để QR hiện thì ai chụp màn hình vẫn đọc được bằng điện thoại — việc ẩn trở
   * thành hình thức.
   *
   * Toggle này CHỈ ĐỔI HIỂN THỊ, không gọi lại endpoint reveal. Gọi lại sẽ ghi thêm một
   * dòng audit ADMIN_PAYOUT_METHOD_REVEALED cho cùng một lần xem, làm nhật ký sai sự thật.
   */
  const [sensitiveVisible, setSensitiveVisible] = useState(true);

  /** Tên trường vừa copy, dùng để đổi icon trong ~1,5 giây. */
  const [copiedField, setCopiedField] = useState<string | null>(null);

  /** Trả null khi lỗi để nơi gọi phân biệt được với một trang rỗng thật. */
  const fetchPending = useCallback(async (): Promise<PageOf<WithdrawalRow> | null> => {
    try {
      const data = await adminFetch<PageOf<WithdrawalRow>>(
        `/admin/withdrawals?status=PENDING&page=${pendingPage}&size=${PAGE_SIZE}`
      );
      setPendingError("");
      return data;
    } catch (err) {
      setPendingError((err as Error).message);
      return null;
    }
  }, [pendingPage]);

  /**
   * Lịch sử lệnh rút đã xử lý.
   *
   * Endpoint riêng `/withdrawals/history` chứ không phải `/withdrawals?status=SETTLED`: bảng
   * này cần cả SETTLED và VOIDED xếp chung theo thời gian, và cần kèm nhật ký quyết định.
   * Gọi hai lần rồi trộn ở giao diện sẽ làm phân trang sai.
   */
  const fetchHistory = useCallback(async (): Promise<PageOf<WithdrawalRow> | null> => {
    const statusParam = historyFilter === "ALL" ? "" : `status=${historyFilter}&`;
    try {
      const data = await adminFetch<PageOf<WithdrawalRow>>(
        `/admin/withdrawals/history?${statusParam}page=${historyPage}&size=${PAGE_SIZE}`
      );
      setHistoryError("");
      return data;
    } catch (err) {
      setHistoryError((err as Error).message);
      return null;
    }
  }, [historyPage, historyFilter]);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể đổi trang trước khi request xong, và ghi state vào
    // component đã tháo là cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      // setState nằm trong hàm async, không ở thân effect: setState đồng bộ trong thân effect
      // gây chuỗi render liên tiếp và bị luật lint của dự án chặn.
      setPendingLoading(true);
      const data = await fetchPending();
      if (cancelled) return;
      setPending(data?.content ?? []);
      setPendingTotalPages(data?.totalPages || 1);
      setPendingLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchPending]);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      setHistoryLoading(true);
      const data = await fetchHistory();
      if (cancelled) return;
      setHistory(data?.content ?? []);
      setHistoryTotalPages(data?.totalPages || 1);
      setHistoryTotal(data?.totalElements ?? 0);
      setHistoryLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchHistory]);

  const reloadPending = useCallback(async () => {
    setPendingLoading(true);
    const data = await fetchPending();
    setPending(data?.content ?? []);
    setPendingTotalPages(data?.totalPages || 1);
    setPendingLoading(false);
  }, [fetchPending]);

  const reloadHistory = useCallback(async () => {
    setHistoryLoading(true);
    const data = await fetchHistory();
    setHistory(data?.content ?? []);
    setHistoryTotalPages(data?.totalPages || 1);
    setHistoryTotal(data?.totalElements ?? 0);
    setHistoryLoading(false);
  }, [fetchHistory]);

  /**
   * Nạp mã QR chuyển khoản khi mở hộp thoại DUYỆT.
   *
   * Chỉ nạp khi DUYỆT, không nạp khi từ chối: từ chối làm tiền quay về ví người chơi, không
   * có khoản chuyển nào cần quét.
   */
  useEffect(() => {
    if (!dialog || dialog.action !== "APPROVED") {
      setQrUrl(null);
      setBankInfo(null);
      setQrError("");
      setQrLoading(false);
      return;
    }

    const { row } = dialog;

    // Lệnh không gắn tài khoản nào thì không có gì để giải mã — nói thẳng thay vì gọi API rồi
    // để nó thất bại với một thông báo khó hiểu.
    if (!row.bankAccountId) {
      setQrUrl(null);
      setBankInfo(null);
      setQrError("Lệnh rút này không gắn tài khoản nhận tiền nào.");
      setQrLoading(false);
      return;
    }

    setQrUrl(null);
    setBankInfo(null);
    setQrError("");
    setQrLoading(true);
    setSensitiveVisible(true);
    setCopiedField(null);

    let active = true;

    (async () => {
      try {
        // Giải mã số tài khoản ĐẦY ĐỦ của CHÍNH tài khoản người chơi đã chọn lúc gửi lệnh.
        // Mỗi lần gọi ghi một dòng audit ADMIN_PAYOUT_METHOD_REVEALED.
        const revealed = await adminFetch<RevealedPayoutAddressResponse>(
          `/admin/users/${row.userId}/payout-methods/${row.bankAccountId}/reveal`,
          { method: "POST" }
        );
        if (!active) return;

        if (revealed?.fullAddress && revealed?.bankCode) {
          setBankInfo({
            bankCode: revealed.bankCode,
            fullAddress: revealed.fullAddress,
            holderName: revealed.holderName,
          });
          // QR KHÔNG kèm số tiền — admin tự nhập khi chuyển khoản.
          setQrUrl(
            `https://img.vietqr.io/image/${revealed.bankCode}-${revealed.fullAddress}-compact2.png?logo=1`
          );
        } else {
          setQrError("Không đọc được thông tin tài khoản để tạo mã QR.");
        }
      } catch (err) {
        if (active) setQrError((err as Error).message);
      } finally {
        if (active) setQrLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [dialog]);

  /**
   * Copy một trường vào clipboard và đổi icon thành dấu tích trong 1,5 giây.
   *
   * Số tài khoản dài 10-14 chứ số; nhập tay vào app ngân hàng là cách dễ chuyển
   * sai người nhất, và chuyển sai thì không lấy lại được.
   */
  const copyField = useCallback((field: string, value: string) => {
    void navigator.clipboard.writeText(value);
    setCopiedField(field);
    window.setTimeout(() => setCopiedField(null), 1500);
  }, []);

  const openDialog = useCallback(
    (row: WithdrawalRow, action: "APPROVED" | "REJECTED") => {
      setDialog({ row, action });
      setReason("");
      setActionError("");
    },
    []
  );

  /**
   * Gửi quyết định.
   *
   * Sau khi thành công phải tải lại CẢ hai bảng: lệnh vừa xử lý rời hàng chờ và xuất hiện
   * trong lịch sử. Chỉ tải lại một bên sẽ để lại một bảng hiển thị dữ liệu đã lỗi thời.
   */
  const handleExecute = async () => {
    if (!dialog) return;
    if (!reason.trim()) {
      setActionError("Bắt buộc nhập lý do — nội dung này được ghi vào nhật ký hệ thống.");
      return;
    }

    setActionLoading(true);
    setActionError("");

    const verb = dialog.action === "APPROVED" ? "approve" : "reject";

    try {
      await adminFetch(`/admin/withdrawals/${dialog.row.id}/${verb}`, {
        method: "POST",
        body: JSON.stringify({ note: reason.trim() }),
      });

      setDialog(null);
      setReason("");
      void reloadPending();
      void reloadHistory();
    } catch (err) {
      setActionError((err as Error).message || "Thao tác thất bại.");
    } finally {
      setActionLoading(false);
    }
  };

  const dialogApproving = dialog?.action === "APPROVED";

  const historyFilters = useMemo(
    () =>
      [
        { key: "ALL" as HistoryFilter, label: "Tất cả" },
        { key: "SETTLED" as HistoryFilter, label: "Đã duyệt" },
        { key: "VOIDED" as HistoryFilter, label: "Đã từ chối" },
      ],
    []
  );

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title="Duyệt Lệnh Rút Tiền"
        subtitle="Hàng chờ xử lý và lịch sử quyết định của lệnh rút do người chơi gửi"
      />

      <div className="p-6 flex flex-col gap-8">
        <div className="bg-amber-50 border border-amber-200 rounded-2xl p-4 flex items-center gap-3">
          <ShieldCheck className="w-5 h-5 text-amber-600 shrink-0" />
          <span className="text-xs text-amber-900 font-semibold">
            Chính sách An toàn Tài chính: mọi quyết định đều ghi nhật ký kèm lý do và không thể
            xoá. Admin không được xử lý lệnh rút của chính tài khoản mình.
          </span>
        </div>

        {/* ===================== KHU 1: HÀNG CHỜ XỬ LÝ ===================== */}
        <section className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2">
                <ArrowUpRight className="w-4 h-4 text-red-600" />
                <h2 className="text-sm font-extrabold text-slate-900">
                  Lệnh rút đang chờ xử lý ({pending.length})
                </h2>
              </div>
              <span className="text-[11px] text-slate-500 font-medium">
                Tiền đã bị trừ khỏi ví ngay khi người chơi tạo lệnh — duyệt là chuyển khoản thật,
                từ chối là hoàn tiền lại ví.
              </span>
            </div>
            <button
              onClick={reloadPending}
              aria-label="Tải lại hàng chờ"
              className="p-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 transition-colors shadow-xs"
            >
              <RefreshCw
                className={`w-4 h-4 ${pendingLoading ? "animate-spin text-red-600" : ""}`}
              />
            </button>
          </div>

          {/* Lỗi tải PHẢI hiện rõ ở màn hình duyệt tiền: bảng trống im lặng dễ bị hiểu là
              "không có lệnh nào chờ", trong khi thực tế là chưa tải được. */}
          {pendingError && <AdminErrorState message={pendingError} onRetry={reloadPending} />}

          <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-100 border-b border-slate-200 text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                    <th className="py-3.5 px-4">Mã lệnh</th>
                    <th className="py-3.5 px-4">Người chơi</th>
                    <th className="py-3.5 px-4">Số tiền rút</th>
                    <th className="py-3.5 px-4">Tài khoản nhận</th>
                    <th className="py-3.5 px-4">Trạng thái</th>
                    <th className="py-3.5 px-4">Thời gian tạo</th>
                    <th className="py-3.5 px-4 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs">
                  {pendingLoading && pending.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-8 text-center text-slate-500 font-medium">
                        <span className="inline-flex items-center gap-2">
                          <Loader2 className="w-4 h-4 animate-spin" />
                          Đang tải hàng chờ...
                        </span>
                      </td>
                    </tr>
                  ) : pending.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-8 text-center text-slate-500 font-medium">
                        Không có lệnh rút nào đang chờ duyệt.
                      </td>
                    </tr>
                  ) : (
                    pending.map((row) => (
                      <tr key={row.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3.5 px-4 font-mono font-bold text-slate-900">{row.id}</td>
                        <td className="py-3.5 px-4 font-bold text-red-700">
                          {row.username ?? (
                            <span className="text-slate-400 font-medium italic">
                              không rõ ({row.userId.slice(0, 8)})
                            </span>
                          )}
                        </td>
                        <td className="py-3.5 px-4 font-black text-slate-900 tabular-nums">
                          {formatMoney(row.amount, row.currency)}
                        </td>
                        <td className="py-3.5 px-4">
                          {row.bankCode ? (
                            <div className="flex flex-col gap-0.5">
                              <span className="font-mono font-bold text-slate-900">
                                {row.bankCode} • ****{row.maskedLast4}
                              </span>
                              <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wide">
                                {row.holderName}
                              </span>
                            </div>
                          ) : (
                            <span className="text-slate-400 font-medium italic">chưa gắn</span>
                          )}
                        </td>
                        <td className="py-3.5 px-4">
                          <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-amber-50 text-amber-700 border border-amber-200 flex items-center gap-1 w-fit">
                            <Clock className="w-3 h-3" /> {row.status}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 text-slate-500">
                          {new Date(row.createdAt).toLocaleString("vi-VN")}
                        </td>
                        <td className="py-3.5 px-4 text-right">
                          <div className="flex items-center justify-end gap-2">
                            <button
                              onClick={() => openDialog(row, "APPROVED")}
                              className="px-3 py-1.5 rounded-xl bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 text-emerald-700 font-bold text-xs flex items-center gap-1 transition-colors"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" /> Duyệt
                            </button>
                            <button
                              onClick={() => openDialog(row, "REJECTED")}
                              className="px-3 py-1.5 rounded-xl bg-red-50 hover:bg-red-100 border border-red-200 text-red-700 font-bold text-xs flex items-center gap-1 transition-colors"
                            >
                              <XCircle className="w-3.5 h-3.5" /> Từ chối
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <div className="p-4 border-t border-slate-200 flex items-center justify-between text-xs text-slate-500 bg-white">
              <span>
                Trang {pendingPage + 1} trên {pendingTotalPages}
              </span>
              <div className="flex items-center gap-2">
                <button
                  disabled={pendingPage === 0}
                  onClick={() => setPendingPage((p) => Math.max(0, p - 1))}
                  aria-label="Trang trước của hàng chờ"
                  className="p-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200"
                >
                  <ChevronLeft className="w-4 h-4 text-slate-700" />
                </button>
                <button
                  disabled={pendingPage >= pendingTotalPages - 1}
                  onClick={() => setPendingPage((p) => p + 1)}
                  aria-label="Trang sau của hàng chờ"
                  className="p-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200"
                >
                  <ChevronRight className="w-4 h-4 text-slate-700" />
                </button>
              </div>
            </div>
          </div>
        </section>

        {/* ===================== KHU 2: LỊCH SỬ QUYẾT ĐỊNH ===================== */}
        <section className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2">
                <History className="w-4 h-4 text-slate-600" />
                <h2 className="text-sm font-extrabold text-slate-900">
                  Lịch sử lệnh rút đã xử lý ({historyTotal})
                </h2>
              </div>
              <span className="text-[11px] text-slate-500 font-medium">
                Ai đã quyết định, lúc nào và vì sao — đọc từ nhật ký hệ thống, không thể sửa hay
                xoá. 30 ngày gần nhất.
              </span>
            </div>

            <div className="flex items-center gap-2">
              {/* Bộ lọc reset về trang đầu: giữ nguyên trang khi đổi bộ lọc có thể rơi vào
                  trang vượt quá số trang của tập kết quả mới và hiện bảng trống. */}
              <div className="flex items-center gap-1 p-1 bg-white border border-slate-200 rounded-xl">
                {historyFilters.map((filter) => (
                  <button
                    key={filter.key}
                    onClick={() => {
                      setHistoryFilter(filter.key);
                      setHistoryPage(0);
                    }}
                    aria-pressed={historyFilter === filter.key}
                    className={`px-3 py-1.5 rounded-lg text-[11px] font-bold transition-colors ${
                      historyFilter === filter.key
                        ? "bg-slate-900 text-white"
                        : "text-slate-500 hover:bg-slate-100"
                    }`}
                  >
                    {filter.label}
                  </button>
                ))}
              </div>
              <button
                onClick={reloadHistory}
                aria-label="Tải lại lịch sử"
                className="p-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 transition-colors shadow-xs"
              >
                <RefreshCw
                  className={`w-4 h-4 ${historyLoading ? "animate-spin text-slate-700" : ""}`}
                />
              </button>
            </div>
          </div>

          {historyError && <AdminErrorState message={historyError} onRetry={reloadHistory} />}

          <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-100 border-b border-slate-200 text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                    <th className="py-3.5 px-4">Mã lệnh</th>
                    <th className="py-3.5 px-4">Người chơi</th>
                    <th className="py-3.5 px-4">Số tiền</th>
                    <th className="py-3.5 px-4">Tài khoản nhận</th>
                    <th className="py-3.5 px-4">Kết quả</th>
                    <th className="py-3.5 px-4">Người quyết định</th>
                    <th className="py-3.5 px-4">Lý do</th>
                    <th className="py-3.5 px-4">Thời điểm quyết định</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs">
                  {historyLoading && history.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="py-8 text-center text-slate-500 font-medium">
                        <span className="inline-flex items-center gap-2">
                          <Loader2 className="w-4 h-4 animate-spin" />
                          Đang tải lịch sử...
                        </span>
                      </td>
                    </tr>
                  ) : history.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="py-8 text-center text-slate-500 font-medium">
                        Chưa có lệnh rút nào được xử lý trong 30 ngày qua.
                      </td>
                    </tr>
                  ) : (
                    history.map((row) => (
                      <tr key={row.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3.5 px-4 font-mono font-bold text-slate-900">{row.id}</td>
                        <td className="py-3.5 px-4 font-bold text-slate-700">
                          {row.username ?? (
                            <span className="text-slate-400 font-medium italic">
                              không rõ ({row.userId.slice(0, 8)})
                            </span>
                          )}
                        </td>
                        <td className="py-3.5 px-4 font-black text-slate-900 tabular-nums">
                          {formatMoney(row.amount, row.currency)}
                        </td>
                        <td className="py-3.5 px-4">
                          {row.bankCode ? (
                            <div className="flex flex-col gap-0.5">
                              <span className="font-mono font-bold text-slate-900">
                                {row.bankCode} • ****{row.maskedLast4}
                              </span>
                              <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wide">
                                {row.holderName}
                              </span>
                            </div>
                          ) : (
                            <span className="text-slate-400 font-medium italic">chưa gắn</span>
                          )}
                        </td>
                        <td className="py-3.5 px-4">
                          {/* SETTLED = đã chuyển khoản, VOIDED = đã hoàn tiền về ví. Nhãn ghi
                              hệ quả với tiền, không phải tên trạng thái kỹ thuật. */}
                          {row.status === "SETTLED" ? (
                            <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1 w-fit">
                              <CheckCircle2 className="w-3 h-3" /> Đã duyệt
                            </span>
                          ) : (
                            <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-red-50 text-red-700 border border-red-200 flex items-center gap-1 w-fit">
                              <XCircle className="w-3 h-3" /> Đã từ chối · hoàn ví
                            </span>
                          )}
                        </td>
                        <td className="py-3.5 px-4 font-bold text-slate-700">
                          {row.decidedByUsername ?? (
                            <span
                              className="text-slate-400 font-medium italic"
                              title="Nhật ký của lệnh này không ghi lại tên người quyết định"
                            >
                              không ghi nhận
                            </span>
                          )}
                        </td>
                        <td className="py-3.5 px-4 max-w-[16rem]">
                          {row.decisionNote ? (
                            <span
                              className="text-slate-700 font-medium line-clamp-2"
                              title={row.decisionNote}
                            >
                              {row.decisionNote}
                            </span>
                          ) : (
                            <span className="text-slate-400 font-medium italic">
                              không có lý do
                            </span>
                          )}
                        </td>
                        <td className="py-3.5 px-4 text-slate-500">
                          {row.decidedAt
                            ? new Date(row.decidedAt).toLocaleString("vi-VN")
                            : new Date(row.createdAt).toLocaleString("vi-VN")}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <div className="p-4 border-t border-slate-200 flex items-center justify-between text-xs text-slate-500 bg-white">
              <span>
                Trang {historyPage + 1} trên {historyTotalPages}
              </span>
              <div className="flex items-center gap-2">
                <button
                  disabled={historyPage === 0}
                  onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                  aria-label="Trang trước của lịch sử"
                  className="p-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200"
                >
                  <ChevronLeft className="w-4 h-4 text-slate-700" />
                </button>
                <button
                  disabled={historyPage >= historyTotalPages - 1}
                  onClick={() => setHistoryPage((p) => p + 1)}
                  aria-label="Trang sau của lịch sử"
                  className="p-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200"
                >
                  <ChevronRight className="w-4 h-4 text-slate-700" />
                </button>
              </div>
            </div>
          </div>
        </section>
      </div>

      {/* ===================== HỘP THOẠI XÁC NHẬN ===================== */}
      <AdminModal
        isOpen={!!dialog}
        onClose={() => setDialog(null)}
        maxWidthClass="max-w-md"
        title={
          dialog && (
            <div className="flex items-center gap-3 w-full">
              <div
                className={`p-2.5 rounded-xl border shrink-0 ${
                  dialogApproving
                    ? "bg-emerald-50 border-emerald-200 text-emerald-600"
                    : "bg-red-50 border-red-200 text-red-600"
                }`}
              >
                {dialogApproving ? (
                  <CheckCircle2 className="w-5 h-5" />
                ) : (
                  <XCircle className="w-5 h-5" />
                )}
              </div>
              <div className="flex flex-col min-w-0">
                <h3 className="text-base font-extrabold text-slate-900 truncate">
                  {dialogApproving ? "Duyệt lệnh rút tiền" : "Từ chối lệnh rút tiền"}
                </h3>
                <span className="text-xs text-slate-500 font-medium truncate font-mono">
                  Order ID: {dialog.row.id}
                </span>
              </div>
            </div>
          )
        }
      >
        {dialog && (
          <div className="flex flex-col gap-5">
            {actionError && (
              <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700 font-semibold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
                <span>{actionError}</span>
              </div>
            )}

            <div className="flex flex-col gap-3">
              <div className="bg-slate-50 border border-slate-200 rounded-xl p-3.5 flex flex-col gap-2 text-xs">
                <div className="flex justify-between gap-4">
                  <span className="text-slate-500 font-medium shrink-0">Người rút:</span>
                  <span className="font-bold text-slate-900 truncate">
                    {dialog.row.username ?? "không rõ"}
                  </span>
                </div>
                <div className="flex justify-between gap-4">
                  <span className="text-slate-500 font-medium shrink-0">Số tiền:</span>
                  <span className="font-black text-slate-900 tabular-nums">
                    {formatMoney(dialog.row.amount, dialog.row.currency)}
                  </span>
                </div>
                <div className="flex justify-between gap-4">
                  <span className="text-slate-500 font-medium shrink-0">Hệ quả:</span>
                  <span
                    className={`font-bold text-right ${
                      dialogApproving ? "text-emerald-700" : "text-red-700"
                    }`}
                  >
                    {dialogApproving
                      ? "Bạn phải chuyển khoản thật cho người chơi"
                      : "Tiền được hoàn lại ví người chơi"}
                  </span>
                </div>
              </div>

              {/* Thông tin chuyển khoản: chỉ khi duyệt. */}
              {dialogApproving && (
                <div className="border border-slate-200 bg-slate-50 rounded-xl p-4 flex flex-col gap-3">
                  <div className="w-full flex items-center justify-between gap-2">
                    <span className="font-bold text-xs text-slate-700 flex items-center gap-1.5">
                      <Landmark className="w-3.5 h-3.5 text-slate-500" />
                      Tài khoản nhận tiền
                    </span>

                    {/* Nút Ẩn chỉ hiện khi ĐÃ có dữ liệu — không có gì thì không có gì để ẩn. */}
                    {bankInfo && (
                      <button
                        type="button"
                        id="toggle-bank-visibility"
                        onClick={() => setSensitiveVisible((visible) => !visible)}
                        className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-slate-300 bg-white text-[11px] font-bold text-slate-700 hover:bg-slate-100 transition-colors"
                      >
                        {sensitiveVisible ? (
                          <>
                            <EyeOff className="w-3.5 h-3.5" />
                            Ẩn thông tin
                          </>
                        ) : (
                          <>
                            <Eye className="w-3.5 h-3.5" />
                            Hiện thông tin
                          </>
                        )}
                      </button>
                    )}
                  </div>

                  {qrLoading ? (
                    <div className="h-44 flex items-center justify-center text-xs text-slate-400 gap-2">
                      <Loader2 className="w-4 h-4 animate-spin text-slate-500" />
                      Đang giải mã thông tin tài khoản...
                    </div>
                  ) : bankInfo ? (
                    <div className="flex flex-col gap-3">
                      {/* ===== Mã QR ===== */}
                      <div className="flex justify-center">
                        {sensitiveVisible && qrUrl ? (
                          <div className="flex flex-col items-center gap-1.5">
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img
                              src={qrUrl}
                              alt={`Mã VietQR chuyển tiền tới tài khoản ${bankInfo.bankCode}`}
                              className="w-48 h-48 object-contain bg-white p-2 rounded-lg border border-slate-200"
                            />
                            <span className="text-[10px] text-slate-400 font-medium">
                              Quét bằng app ngân hàng — QR không kèm số tiền
                            </span>
                          </div>
                        ) : (
                          /* QR MÃ HOÁ CHÍNH SỐ TÀI KHOẢN nên phải ẩn cùng: che dòng chứ mà để QR
                             hiện thì ai chụp màn hình vẫn đọc được bằng điện thoại. */
                          <div className="w-48 h-48 rounded-lg border border-dashed border-slate-300 bg-white/60 flex flex-col items-center justify-center gap-2 text-slate-400">
                            <EyeOff className="w-7 h-7" />
                            <span className="text-[11px] font-semibold text-center px-4 leading-snug">
                              Đang ẩn để tránh lọ trên màn hình
                            </span>
                          </div>
                        )}
                      </div>

                      {/* ===== Chi tiết tài khoản ===== */}
                      <div className="flex flex-col gap-2 bg-white border border-slate-200 rounded-lg p-3">
                        <div className="flex items-baseline justify-between gap-3">
                          <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest shrink-0">
                            Ngân hàng
                          </span>
                          <span className="text-xs font-bold text-slate-900 text-right truncate">
                            {bankInfo.bankCode}
                          </span>
                        </div>

                        <div className="h-px bg-slate-100" />

                        <div className="flex items-center justify-between gap-3">
                          <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest shrink-0">
                            Số tài khoản
                          </span>
                          <div className="flex items-center gap-2 min-w-0">
                            <span className="text-sm font-mono font-black text-slate-900 tabular-nums truncate">
                              {sensitiveVisible
                                ? bankInfo.fullAddress
                                : "•".repeat(Math.max(bankInfo.fullAddress.length, 8))}
                            </span>
                            {sensitiveVisible && (
                              <button
                                type="button"
                                id="copy-bank-account"
                                onClick={() => copyField("account", bankInfo.fullAddress)}
                                title="Copy số tài khoản"
                                className="p-1.5 rounded-md hover:bg-slate-100 text-slate-500 hover:text-slate-900 transition-colors shrink-0"
                              >
                                {copiedField === "account" ? (
                                  <Check className="w-3.5 h-3.5 text-emerald-600" />
                                ) : (
                                  <Copy className="w-3.5 h-3.5" />
                                )}
                              </button>
                            )}
                          </div>
                        </div>

                        <div className="h-px bg-slate-100" />

                        <div className="flex items-center justify-between gap-3">
                          <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest shrink-0">
                            Chủ tài khoản
                          </span>
                          <div className="flex items-center gap-2 min-w-0">
                            <span className="text-xs font-bold text-slate-900 uppercase truncate">
                              {sensitiveVisible
                                ? bankInfo.holderName
                                : "•".repeat(Math.max(bankInfo.holderName.length, 8))}
                            </span>
                            {sensitiveVisible && (
                              <button
                                type="button"
                                id="copy-holder-name"
                                onClick={() => copyField("holder", bankInfo.holderName)}
                                title="Copy tên chủ tài khoản"
                                className="p-1.5 rounded-md hover:bg-slate-100 text-slate-500 hover:text-slate-900 transition-colors shrink-0"
                              >
                                {copiedField === "holder" ? (
                                  <Check className="w-3.5 h-3.5 text-emerald-600" />
                                ) : (
                                  <Copy className="w-3.5 h-3.5" />
                                )}
                              </button>
                            )}
                          </div>
                        </div>

                        <div className="h-px bg-slate-100" />

                        {/* Số tiền KHÔNG che: nó đã hiện ở bảng phía sau và ở ô tóm tắt ngay trên,
                            che riêng ở đây không giảm rủi ro nào. */}
                        <div className="flex items-baseline justify-between gap-3">
                          <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest shrink-0">
                            Số tiền cần chuyển
                          </span>
                          <span className="text-sm font-black text-emerald-700 tabular-nums text-right">
                            {formatMoney(dialog.row.amount, dialog.row.currency)}
                          </span>
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="py-4 text-center text-xs text-red-600 font-semibold">
                      {qrError || "Không tạo được mã QR."}
                    </div>
                  )}
                </div>
              )}

              <label
                htmlFor="modal-reason"
                className="text-xs font-bold text-slate-700 flex items-center gap-1.5"
              >
                <MessageSquareQuote className="w-3.5 h-3.5 text-slate-500" />
                Lý do * Bắt buộc — sẽ hiện trong bảng lịch sử
              </label>
              <textarea
                id="modal-reason"
                rows={3}
                required
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Nhập bằng chứng/lý do cho quyết định này..."
                className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-3 text-xs text-slate-900 placeholder-slate-400 outline-none resize-none font-medium"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setDialog(null)}
                className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
              >
                Hủy
              </button>
              <button
                onClick={handleExecute}
                disabled={actionLoading}
                className={`px-4 py-2 rounded-xl text-white font-bold text-xs transition-colors disabled:opacity-50 ${
                  dialogApproving
                    ? "bg-emerald-600 hover:bg-emerald-700"
                    : "bg-red-600 hover:bg-red-700"
                }`}
              >
                {actionLoading ? "Đang xử lý..." : "Xác nhận thực hiện"}
              </button>
            </div>
          </div>
        )}
      </AdminModal>
    </div>
  );
}
