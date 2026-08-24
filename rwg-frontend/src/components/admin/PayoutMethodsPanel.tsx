"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  Landmark,
  Loader2,
  AlertTriangle,
  Eye,
  Copy,
  Check,
  ShieldOff,
  Star,
  Trash2,
  ShieldAlert,
  Clock,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { canAdjustWallet } from "@/lib/adminIdentity";
import { useTranslation } from "@/context/LanguageContext";

/** Một phương thức nhận tiền — khớp AdminPayoutMethodResponse của backend. */
interface PayoutMethod {
  id: string;
  payoutType: string;
  bankCode: string;
  /** "****6789". KHÔNG phải số đầy đủ. */
  maskedAddress: string;
  holderName: string;
  isDefault: boolean;
  /** "ACTIVE" | "REMOVED" */
  status: string;
  createdAt: string;
}

/** Kết quả giải mã — khớp RevealedPayoutAddressResponse. */
interface RevealedAddress {
  id: string;
  payoutType: string;
  fullAddress: string;
  bankCode: string;
  holderName: string;
  revealedAt: string;
}

interface Props {
  userId: string;
}

const REVEAL_TTL_SEC = 60;

/**
 * Tài khoản ngân hàng của một người chơi.
 *
 * Mặc định CHỈ hiện phần đã che. Số đầy đủ phải bấm mới hiện, và mỗi lần bấm được
 * ghi vào nhật ký hệ thống — người vận hành được nói trước điều đó, không để họ phát
 * hiện sau.
 *
 * ĐÂY LÀ ĐƯỜNG DUY NHẤT để đổi tài khoản nhận tiền của người chơi: bên phía người chơi
 * chỉ liên kết được MỘT tài khoản và không tự gỡ được. Không có phần thêm/gỡ ở đây thì
 * ai gõ sai số tài khoản sẽ kẹt vĩnh viễn — tiền rút chạy vào số sai và cách duy nhất
 * để cứu là sửa tay trong database.
 *
 * KHÔNG CÓ CHỨC NĂNG "SỬA": muốn đổi thì gỡ rồi thêm lại. Bản ghi cũ giữ trạng thái
 * REMOVED nên lịch sử đổi tài khoản còn nguyên trong nhật ký. Sửa tại chỗ sẽ ghi đè số
 * cũ và mất dấu — đúng lúc cần điều tra thì không còn gì để xem.
 */
export const PayoutMethodsPanel: React.FC<Props> = ({ userId }) => {
  const { t } = useTranslation();

  /**
   * Quyền xem số đầy đủ trùng với quyền chạm tiền (ADMIN/FINANCE) — khớp rule
   * trong SecurityConfig.
   */
  const canReveal = canAdjustWallet();

  const [methods, setMethods] = useState<PayoutMethod[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  /** id đang chờ người dùng xác nhận hiện số đầy đủ. */
  const [confirmId, setConfirmId] = useState<string | null>(null);
  /** id đang gọi API giải mã. */
  const [revealingId, setRevealingId] = useState<string | null>(null);
  /** Kết quả giải mã theo id. Chỉ giữ trong bộ nhớ, không lưu ra localStorage. */
  const [revealed, setRevealed] = useState<Record<string, RevealedAddress>>({});
  const [revealError, setRevealError] = useState("");
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [bankLogoErrors, setBankLogoErrors] = useState<Record<string, boolean>>({});

  /** Form thêm tài khoản hộ. */
  const [addOpen, setAddOpen] = useState(false);
  const [addBankCode, setAddBankCode] = useState("");
  const [addHolder, setAddHolder] = useState("");
  const [addNumber, setAddNumber] = useState("");
  const [addReason, setAddReason] = useState("");
  const [adding, setAdding] = useState(false);
  const [mutateError, setMutateError] = useState("");

  /** id đang chờ xác nhận gỡ, và id đang gọi API gỡ. */
  const [removeConfirmId, setRemoveConfirmId] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);
  
  interface VietQRBank {
    id: number;
    name: string;
    code: string;
    bin: string;
    shortName: string;
    logo: string;
  }
  const [bankList, setBankList] = useState<VietQRBank[]>([]);

  useEffect(() => {
    let active = true;
    const fetchBanks = async () => {
      try {
        const res = await fetch("/api/banks");
        if (res.ok) {
          const data = await res.json();
          if (active && data?.banks) {
            setBankList(data.banks);
          }
        }
      } catch (err) {
        console.warn("Không tải được danh sách VietQR:", err);
      }
    };
    void fetchBanks();
    return () => { active = false; };
  }, []);

  const findBank = useCallback((codeOrBin: string | null) => {
    if (!codeOrBin) return null;
    const needle = codeOrBin.trim().toUpperCase();
    return bankList.find(b => b.code.toUpperCase() === needle || b.bin === needle) ?? null;
  }, [bankList]);

  const load = useCallback(() => {
    const run = async () => {
      setLoading(true);
      setError("");
      try {
        const list = await adminFetch<PayoutMethod[]>(
          `/admin/users/${userId}/payout-methods`
        );
        setMethods(list);
      } catch (err: any) {
        setError(err.message || String(err));
      } finally {
        setLoading(false);
      }
    };
    void run();
  }, [userId]);

  useEffect(() => {
    load();
  }, [load]);

  const doReveal = async (id: string) => {
    setConfirmId(null);
    setRevealingId(id);
    setRevealError("");
    try {
      const data = await adminFetch<RevealedAddress>(
        `/admin/users/${userId}/payout-methods/${id}/reveal`,
        { method: "POST" }
      );
      setRevealed((prev) => ({ ...prev, [id]: data }));
      setTimeLeft((prev) => ({ ...prev, [id]: REVEAL_TTL_SEC }));
    } catch (err: any) {
      setRevealError(err.message || String(err));
    } finally {
      setRevealingId(null);
    }
  };

  /** Đã có tài khoản đang hoạt động hay chưa — danh sách gồm cả bản ghi REMOVED. */
  const hasActive = methods.some((m) => m.status === "ACTIVE");

  const doAdd = async () => {
    // Lý do BẮT BUỘC: kiểm ở đây để không mất một vòng gọi mạng, nhưng backend
    // cũng kiểm (@NotBlank) vì đây là luật của dữ liệu, không của giao diện.
    if (!addReason.trim()) {
      setMutateError(t("admin.users.payout.reason_required"));
      return;
    }

    setAdding(true);
    setMutateError("");
    try {
      await adminFetch(`/admin/users/${userId}/payout-methods`, {
        method: "POST",
        body: JSON.stringify({
          bankCode: addBankCode.trim().toUpperCase(),
          accountNumber: addNumber.trim(),
          holderName: addHolder.trim(),
          reason: addReason.trim(),
        }),
      });

      setAddOpen(false);
      setAddBankCode("");
      setAddHolder("");
      setAddNumber("");
      setAddReason("");
      load();
    } catch (err: any) {
      setMutateError(err.message || String(err));
    } finally {
      setAdding(false);
    }
  };

  const doRemove = async (id: string) => {
    setRemoveConfirmId(null);
    setRemovingId(id);
    setMutateError("");
    try {
      // Lý do qua QUERY PARAM chứ không qua body: DELETE có body là hợp lệ về mặt đặc
      // tả nhưng nhiều tầng trung gian cắt bỏ nó — lý do sẽ biến mất khỏi nhật ký
      // mà không báo gì.
      const reason = encodeURIComponent(t("admin.users.payout.remove"));
      await adminFetch(
        `/admin/users/${userId}/payout-methods/${id}?reason=${reason}`,
        { method: "DELETE" }
      );
      load();
    } catch (err: any) {
      setMutateError(err.message || String(err));
    } finally {
      setRemovingId(null);
    }
  };

  const copy = (id: string, text: string) => {
    void navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  // Tự động ẩn số đầy đủ sau TTL
  const [timeLeft, setTimeLeft] = useState<Record<string, number>>({});
  useEffect(() => {
    const activeIds = Object.keys(revealed).filter((id) => (timeLeft[id] ?? 0) > 0);
    if (activeIds.length === 0) return;

    const timer = setInterval(() => {
      setTimeLeft((prev) => {
        const next = { ...prev };
        for (const id of activeIds) {
          const t = (prev[id] ?? 0) - 1;
          if (t <= 0) {
            delete next[id];
            setRevealed((prevRev) => {
              const nextRev = { ...prevRev };
              delete nextRev[id];
              return nextRev;
            });
          } else {
            next[id] = t;
          }
        }
        return next;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [revealed, timeLeft]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-10">
        <Loader2 className="w-5 h-5 text-slate-400 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center gap-3 p-4 bg-red-50 border border-red-200 rounded-xl">
        <AlertTriangle className="w-5 h-5 text-red-600 shrink-0" />
        <span className="text-xs text-red-700 font-semibold">{error}</span>
      </div>
    );
  }

  // KHÔNG return sớm khi danh sách rỗng: form thêm phải dùng được đúng lúc chưa có
  // tài khoản nào — đó mới là trường hợp cần nó nhất.

  return (
    <div className="flex flex-col gap-3">
      {!canReveal && (
        <div className="flex items-start gap-3 p-3 bg-slate-50 border border-slate-200 rounded-xl">
          <ShieldOff className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
          <span className="text-[11px] text-slate-600 font-medium leading-relaxed">
            {t("admin.users.payout.no_permission")}
          </span>
        </div>
      )}

      {revealError && (
        <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
          <AlertTriangle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
          <span className="text-xs text-red-700 font-semibold">{revealError}</span>
        </div>
      )}

      {mutateError && (
        <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
          <AlertTriangle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
          <span className="text-xs text-red-700 font-semibold">{mutateError}</span>
        </div>
      )}

      {methods.length === 0 && (
        <div className="py-6 text-center text-xs text-slate-500 font-medium">
          {t("admin.users.payout.empty")}
        </div>
      )}

      {methods.map((m) => {
        const removed = m.status === "REMOVED";
        const shown = revealed[m.id];

        return (
          <div
            key={m.id}
            className={`flex flex-col gap-3 p-4 rounded-xl border transition-colors ${
              removed
                ? "bg-slate-50 border-slate-200 opacity-70"
                : "bg-white border-slate-200"
            }`}
          >
            {/* Hàng đầu: logo ngân hàng + tên + trạng thái */}
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-2.5 min-w-0">
                <div
                  className={`rounded-xl border shrink-0 flex items-center justify-center ${
                    !bankLogoErrors[m.id] && m.bankCode
                      ? "h-10 w-28 bg-white border-slate-200 px-2"
                      : "w-10 h-10 bg-slate-100 border-slate-200 text-slate-600"
                  }`}
                >
                  {!bankLogoErrors[m.id] && m.bankCode ? (
                    <img
                      src={(() => {
                        const b = findBank(m.bankCode);
                        return b ? b.logo : `https://cdn.vietqr.io/img/${m.bankCode.toUpperCase()}.png`;
                      })()}
                      alt={m.bankCode}
                      className="h-8 w-auto max-w-[100px] object-contain"
                      onError={() =>
                        setBankLogoErrors((prev) => ({ ...prev, [m.id]: true }))
                      }
                    />
                  ) : (
                    <Landmark className="w-5 h-5" />
                  )}
                </div>

                <div className="flex flex-col gap-1 min-w-0">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <span className="text-xs font-extrabold text-slate-900">
                      {(() => {
                        const b = findBank(m.bankCode);
                        return b ? b.shortName : (m.bankCode ?? t("admin.users.payout.bank"));
                      })()}
                    </span>

                    {m.isDefault && !removed && (
                      <span className="px-1.5 py-0.5 rounded font-bold text-[9px] bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1">
                        <Star className="w-2.5 h-2.5" />
                        {t("admin.users.payout.default")}
                      </span>
                    )}

                    {removed && (
                      <span className="px-1.5 py-0.5 rounded font-bold text-[9px] bg-slate-200 text-slate-600 border border-slate-300 flex items-center gap-1">
                        <Trash2 className="w-2.5 h-2.5" />
                        {t("admin.users.payout.removed")}
                      </span>
                    )}
                  </div>

                  {/* Tên đầy đủ ngân hàng lấy từ API VietQR */}
                  {(() => {
                    const b = findBank(m.bankCode);
                    return b ? (
                      <span className="text-[10px] text-slate-400 font-medium leading-none">
                        {b.name}
                      </span>
                    ) : null;
                  })()}

                  {/* Tên chủ tài khoản ngân hàng */}
                  {m.holderName && (
                    <span className="text-[11px] text-slate-500 font-semibold truncate leading-none">
                      {m.holderName}
                    </span>
                  )}
                </div>
              </div>

              <span className="text-[10px] text-slate-400 font-medium shrink-0">
                {new Date(m.createdAt).toLocaleDateString("vi-VN")}
              </span>
            </div>

            {/* Số tài khoản: mặc định che, bấm mới hiện đầy đủ */}
            <div className={`flex flex-col gap-2.5 p-4 rounded-xl border transition-all ${
              shown 
                ? "bg-slate-50/80 border-slate-200 shadow-sm" 
                : "bg-slate-50/40 border-slate-200/60"
            }`}>
              <span className="text-[9px] font-black text-slate-400 uppercase tracking-widest leading-none">
                {t("admin.users.payout.account_number")}
              </span>

              {shown ? (
                <div className="flex items-center justify-between gap-4">
                  <span className="text-sm font-mono font-extrabold text-slate-900 break-all tracking-wide select-all leading-relaxed">
                    {shown.fullAddress}
                  </span>
                  <button
                    onClick={() => copy(m.id, shown.fullAddress)}
                    className="p-2 rounded-lg bg-white border border-slate-200 hover:bg-slate-50 text-slate-500 hover:text-slate-800 transition-all active:scale-95 shadow-xs shrink-0 flex items-center justify-center"
                    aria-label={t("admin.users.payout.copy")}
                    title={t("admin.users.payout.copy")}
                  >
                    {copiedId === m.id ? (
                      <Check className="w-4 h-4 text-emerald-600 animate-in fade-in zoom-in-50 duration-200" />
                    ) : (
                      <Copy className="w-4 h-4" />
                    )}
                  </button>
                </div>
              ) : (
                <div className="flex items-center justify-between gap-4">
                  <span className="text-sm font-mono font-extrabold text-slate-400 tracking-widest select-none">
                    {m.maskedAddress}
                  </span>

                  {canReveal && (
                    <button
                      onClick={() => setConfirmId(m.id)}
                      disabled={revealingId === m.id}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 disabled:opacity-40 text-white text-[10px] font-black active:scale-95 transition-all shrink-0 shadow-sm"
                    >
                      {revealingId === m.id ? (
                        <Loader2 className="w-3 h-3 animate-spin" />
                      ) : (
                        <Eye className="w-3.5 h-3.5" />
                      )}
                      {t("admin.users.payout.reveal")}
                    </button>
                  )}
                </div>
              )}

              {shown && (
                <span className="text-[10px] text-amber-600 font-semibold flex items-center gap-1.5 mt-0.5 animate-in fade-in slide-in-from-top-1 duration-200">
                  <Clock className="w-3.5 h-3.5 text-amber-500 shrink-0" />
                  {t("admin.users.payout.auto_hide", { seconds: timeLeft[m.id] ?? REVEAL_TTL_SEC })}
                </span>
              )}
            </div>

            {/* Xác nhận: nói TRƯỚC rằng lần xem này bị ghi nhật ký */}
            {confirmId === m.id && (
              <div className="flex flex-col gap-2.5 p-3.5 bg-amber-50 border border-amber-300 rounded-xl">
                <div className="flex items-start gap-2.5">
                  <ShieldAlert className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                  <span className="text-[11px] text-amber-900 font-semibold leading-relaxed">
                    {t("admin.users.payout.reveal_warning")}
                  </span>
                </div>
                <div className="flex items-center justify-end gap-2">
                  <button
                    onClick={() => setConfirmId(null)}
                    className="px-3 py-1.5 rounded-lg bg-white border border-slate-300 text-[11px] font-bold text-slate-700 hover:bg-slate-50"
                  >
                    {t("admin.states.cancel")}
                  </button>
                  <button
                    onClick={() => doReveal(m.id)}
                    className="px-3 py-1.5 rounded-lg bg-amber-600 hover:bg-amber-700 text-white text-[11px] font-bold transition-colors"
                  >
                    {t("admin.users.payout.reveal_confirm")}
                  </button>
                </div>
              </div>
            )}

            {/* GỠ — chỉ với bản ghi đang hoạt động. Bản ghi đã gỡ không có gì để gỡ thêm. */}
            {canReveal && !removed && (
              <div className="flex items-center justify-end">
                <button
                  onClick={() => setRemoveConfirmId(m.id)}
                  disabled={removingId === m.id}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-red-200 bg-white hover:bg-red-50 disabled:opacity-40 text-red-700 text-[10px] font-black active:scale-95 transition-all"
                >
                  {removingId === m.id ? (
                    <Loader2 className="w-3 h-3 animate-spin" />
                  ) : (
                    <Trash2 className="w-3.5 h-3.5" />
                  )}
                  {removingId === m.id
                    ? t("admin.users.payout.removing")
                    : t("admin.users.payout.remove")}
                </button>
              </div>
            )}

            {/* Nói TRƯỚC hệ quả: người chơi sẽ không rút được tiền cho đến khi có
                tài khoản mới. Đây không phải xác nhận hình thức — gỡ xong là chặn
                đường rút tiền của người ta. */}
            {removeConfirmId === m.id && (
              <div className="flex flex-col gap-2.5 p-3.5 bg-red-50 border border-red-300 rounded-xl">
                <div className="flex items-start gap-2.5">
                  <ShieldAlert className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                  <div className="flex flex-col gap-1">
                    <span className="text-[11px] text-red-900 font-black">
                      {t("admin.users.payout.remove_confirm_title")}
                    </span>
                    <span className="text-[11px] text-red-800 font-semibold leading-relaxed">
                      {t("admin.users.payout.remove_confirm_note")}
                    </span>
                  </div>
                </div>
                <div className="flex items-center justify-end gap-2">
                  <button
                    onClick={() => setRemoveConfirmId(null)}
                    className="px-3 py-1.5 rounded-lg bg-white border border-slate-300 text-[11px] font-bold text-slate-700 hover:bg-slate-50"
                  >
                    {t("admin.users.payout.cancel")}
                  </button>
                  <button
                    onClick={() => doRemove(m.id)}
                    className="px-3 py-1.5 rounded-lg bg-red-600 hover:bg-red-700 text-white text-[11px] font-bold transition-colors"
                  >
                    {t("admin.users.payout.remove_confirm")}
                  </button>
                </div>
              </div>
            )}
          </div>
        );
      })}

      {/* THÊM TÀI KHOẢN HỘ */}
      {!canReveal ? (
        <div className="flex items-start gap-3 p-3 bg-slate-50 border border-slate-200 rounded-xl">
          <ShieldOff className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
          <span className="text-[11px] text-slate-600 font-medium leading-relaxed">
            {t("admin.users.payout.no_add_permission")}
          </span>
        </div>
      ) : hasActive ? (
        // ĐÃ CÓ TÀI KHOẢN — không hiện form. Luật "mỗi người một tài khoản" áp cả
        // cho admin: hai bản ghi cùng isDefault sẽ làm lúc rút tiền có hai ứng viên và
        // kết quả phụ thuộc thứ tự trả về của DB.
        <p className="px-3 py-2 text-[11px] text-slate-500 font-medium leading-relaxed">
          {t("admin.users.payout.add_note")}
        </p>
      ) : addOpen ? (
        <div className="flex flex-col gap-3 p-4 bg-slate-50 border border-slate-200 rounded-xl">
          <span className="text-xs font-black text-slate-900">
            {t("admin.users.payout.add_title")}
          </span>

          <label className="flex flex-col gap-1">
            <span className="text-[10px] font-bold uppercase tracking-wide text-slate-500">
              {t("admin.users.payout.bank_code")}
            </span>
            <input
              className="px-3 py-2 rounded-lg border border-slate-300 bg-white text-xs text-slate-900 focus:border-slate-900 focus:outline-none"
              maxLength={16}
              onChange={(e) => setAddBankCode(e.target.value)}
              value={addBankCode}
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="text-[10px] font-bold uppercase tracking-wide text-slate-500">
              {t("admin.users.payout.holder_name")}
            </span>
            <input
              className="px-3 py-2 rounded-lg border border-slate-300 bg-white text-xs text-slate-900 focus:border-slate-900 focus:outline-none"
              maxLength={100}
              onChange={(e) => setAddHolder(e.target.value)}
              value={addHolder}
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="text-[10px] font-bold uppercase tracking-wide text-slate-500">
              {t("admin.users.payout.account_number_input")}
            </span>
            <input
              className="px-3 py-2 rounded-lg border border-slate-300 bg-white text-xs font-mono text-slate-900 focus:border-slate-900 focus:outline-none"
              inputMode="numeric"
              maxLength={32}
              onChange={(e) => setAddNumber(e.target.value)}
              value={addNumber}
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="text-[10px] font-bold uppercase tracking-wide text-slate-500">
              {t("admin.users.payout.reason")}
            </span>
            <input
              className="px-3 py-2 rounded-lg border border-slate-300 bg-white text-xs text-slate-900 focus:border-slate-900 focus:outline-none"
              maxLength={255}
              onChange={(e) => setAddReason(e.target.value)}
              placeholder={t("admin.users.payout.reason_placeholder")}
              value={addReason}
            />
          </label>

          <div className="flex items-center justify-end gap-2">
            <button
              className="px-3 py-1.5 rounded-lg bg-white border border-slate-300 text-[11px] font-bold text-slate-700 hover:bg-slate-50"
              onClick={() => {
                setAddOpen(false);
                setMutateError("");
              }}
            >
              {t("admin.users.payout.cancel")}
            </button>
            <button
              className="px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 disabled:opacity-40 text-white text-[11px] font-bold transition-colors"
              disabled={
                adding ||
                !addBankCode.trim() ||
                !addHolder.trim() ||
                !addNumber.trim() ||
                !addReason.trim()
              }
              onClick={() => void doAdd()}
            >
              {adding
                ? t("admin.users.payout.adding")
                : t("admin.users.payout.add_submit")}
            </button>
          </div>
        </div>
      ) : (
        <button
          className="flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-xl border border-dashed border-slate-300 bg-white hover:bg-slate-50 text-[11px] font-black text-slate-700 transition-colors"
          onClick={() => setAddOpen(true)}
        >
          <Landmark className="w-3.5 h-3.5" />
          {t("admin.users.payout.add_title")}
        </button>
      )}
    </div>
  );
};
