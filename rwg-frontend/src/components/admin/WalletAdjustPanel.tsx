"use client";

import React, { useState } from "react";
import {
  Plus,
  Minus,
  AlertTriangle,
  CheckCircle2,
  ShieldOff,
} from "lucide-react";
import { adminFetch, AdminApiError } from "@/lib/adminApi";
import { canAdjustWallet, getAdminIdentity } from "@/lib/adminIdentity";
import { formatMoney, isValidPositiveAmount } from "@/lib/money";
import { useTranslation } from "@/context/LanguageContext";


/** Kết quả điều chỉnh đã thực thi — khớp WalletAdjustmentResponse của backend. */
interface WalletAdjustmentResponse {
  userId: string;
  direction: string;
  amount: string;
  balanceBefore: string;
  balanceAfter: string;
  reason: string;
  idempotencyKey: string;
  adjustedAt: string;
}

interface Props {
  userId: string;
  username: string;
  /** Số dư hiện tại dạng chuỗi, lấy từ AdminUserDetailResponse.walletBalance. */
  currentBalance: string;
  currency: string;
  /** Gọi lại sau khi tiền ĐÃ chuyển, để trang cha tải lại số dư. */
  onAdjusted: () => void;
}

type Direction = "CREDIT" | "DEBIT";

export const WalletAdjustPanel: React.FC<Props> = ({
  userId,
  username,
  currentBalance,
  currency,
  onAdjusted,
}) => {
  const { t } = useTranslation();
  const identity = getAdminIdentity();
  const allowed = canAdjustWallet(identity);
  /** Backend chặn tự giao dịch; ẩn form trước để không để admin bấm rồi mới báo lỗi. */
  const isSelf = identity.userId === userId;

  const [direction, setDirection] = useState<Direction>("CREDIT");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState<WalletAdjustmentResponse | null>(null);

  const parsedAmount = Number(amount);
  const amountValid = isValidPositiveAmount(amount);


  /**
   * Số dư dự kiến CHỈ để đối chiếu bằng mắt. Số chính thức do backend tính bằng
   * BigDecimal; Number của JS là số thực dấu phẩy động nên không dùng cho tiền thật.
   */
  const projected = amountValid
    ? direction === "CREDIT"
      ? Number(currentBalance) + parsedAmount
      : Number(currentBalance) - parsedAmount
    : null;

  const resetForm = () => {
    setAmount("");
    setReason("");
  };

  /** Đổi mã lỗi của backend thành câu tiếng người dùng hiểu được. */
  const messageFor = (err: AdminApiError): string => {
    switch (err.code) {
      case "CANNOT_MODIFY_SELF":
        return t("admin.users.wallet.err_self");
      case "INSUFFICIENT_BALANCE":
        return t("admin.users.wallet.err_insufficient");
      default:
        return err.message;
    }
  };

  const handleSubmit = async () => {
    setError("");

    if (!amountValid) {
      setError(t("admin.users.wallet.err_amount"));
      return;
    }
    if (!reason.trim()) {
      setError(t("admin.users.wallet.err_reason"));
      return;
    }

    setSubmitting(true);
    setDone(null);

    try {
      // Gui amount NGUYEN DANG CHUOI: backend parse sang BigDecimal, chuyen qua
      // Number truoc khi gui se lam tron sai o cac so le.
      //
      // adminFetch (khong phai adminFetchWithStatus): endpoint nay CHI tra 200 ke tu khi
      // bo quy trinh 4 mat - truoc day con nhanh 202 cho khoan vuot han muc.
      const data = await adminFetch<WalletAdjustmentResponse>(
        `/admin/users/${userId}/wallet/adjust`,
        {
          method: "POST",
          body: JSON.stringify({
            amount: amount.trim(),
            direction,
            reason: reason.trim(),
          }),
        }
      );

      setDone(data);
      resetForm();
      onAdjusted();
    } catch (err) {
      setError(
        err instanceof AdminApiError
          ? messageFor(err)
          : (err as Error).message
      );
    } finally {
      setSubmitting(false);
    }
  };

  if (!allowed || isSelf) {
    return (
      <div className="flex items-start gap-3 p-4 bg-slate-50 border border-slate-200 rounded-xl">
        <ShieldOff className="w-5 h-5 text-slate-400 shrink-0 mt-0.5" />
        <p className="text-xs text-slate-600 font-medium leading-relaxed">
          {isSelf
            ? t("admin.users.wallet.err_self")
            : t("admin.users.wallet.no_permission")}
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {/* Ket qua da thuc thi */}
      {done && (
        <div className="flex items-start gap-3 p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl">
          <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
          <div className="flex flex-col gap-0.5">
            <span className="text-xs font-bold text-emerald-800">
              {done.direction === "CREDIT" ? "+" : "-"}
              {formatMoney(done.amount)} {currency}
            </span>
            <span className="text-[11px] text-emerald-700 font-medium">
              {t("admin.users.wallet.balance")}: {formatMoney(done.balanceBefore)} →{" "}
              <strong>{formatMoney(done.balanceAfter)}</strong>
            </span>
          </div>
        </div>
      )}


      {error && (
        <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
          <AlertTriangle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
          <span className="text-xs text-red-700 font-semibold leading-relaxed">
            {error}
          </span>
        </div>
      )}

      {/* Huong dieu chinh */}
      <div className="flex flex-col gap-2">
        <label className="text-[11px] font-bold text-slate-700 uppercase tracking-wide">
          {t("admin.users.wallet.direction")}
        </label>
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            id="wallet-direction-credit"
            onClick={() => setDirection("CREDIT")}
            className={`flex items-center justify-center gap-2 py-2.5 rounded-xl text-xs font-bold border transition-all ${
              direction === "CREDIT"
                ? "bg-emerald-50 border-emerald-300 text-emerald-700"
                : "bg-white border-slate-200 text-slate-500 hover:border-slate-300"
            }`}
          >
            <Plus className="w-3.5 h-3.5" />
            {t("admin.users.wallet.credit")}
          </button>
          <button
            type="button"
            id="wallet-direction-debit"
            onClick={() => setDirection("DEBIT")}
            className={`flex items-center justify-center gap-2 py-2.5 rounded-xl text-xs font-bold border transition-all ${
              direction === "DEBIT"
                ? "bg-red-50 border-red-300 text-red-700"
                : "bg-white border-slate-200 text-slate-500 hover:border-slate-300"
            }`}
          >
            <Minus className="w-3.5 h-3.5" />
            {t("admin.users.wallet.debit")}
          </button>
        </div>
      </div>

      {/* So tien */}
      <div className="flex flex-col gap-2">
        <label
          htmlFor="wallet-adjust-amount"
          className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
        >
          {t("admin.users.wallet.amount")} ({currency})
        </label>
        <input
          id="wallet-adjust-amount"
          type="text"
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          placeholder={t("admin.users.wallet.amount_placeholder")}
          className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-sm font-bold text-slate-900 placeholder-slate-400 outline-none tabular-nums transition-colors"
        />
      </div>

      {/* Xem truoc so du */}
      {projected !== null && (
        <div className="flex items-center justify-between px-3.5 py-3 bg-slate-50 border border-slate-200 rounded-xl">
          <div className="flex flex-col gap-0.5">
            <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">
              {t("admin.users.wallet.preview")}
            </span>
            <span className="text-[10px] text-slate-400 font-medium">
              {t("admin.users.wallet.preview_note")}
            </span>
          </div>
          <div className="flex items-center gap-2 text-sm font-extrabold tabular-nums">
            <span className="text-slate-400">{formatMoney(currentBalance)}</span>
            <span className="text-slate-300">→</span>
            <span
              className={
                projected < 0
                  ? "text-red-600"
                  : direction === "CREDIT"
                    ? "text-emerald-700"
                    : "text-slate-900"
              }
            >
              {projected.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
              })}
            </span>
          </div>
        </div>
      )}


      {/* Ly do */}
      <div className="flex flex-col gap-2">
        <label
          htmlFor="wallet-adjust-reason"
          className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
        >
          {t("admin.users.wallet.reason")}{" "}
          <span className="text-red-600 normal-case font-semibold tracking-normal">
            * {t("admin.users.wallet.reason_hint")}
          </span>
        </label>
        <textarea
          id="wallet-adjust-reason"
          rows={3}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={t("admin.users.wallet.reason_placeholder")}
          className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-3 text-xs font-medium text-slate-900 placeholder-slate-400 outline-none resize-none transition-colors"
        />
      </div>

      <button
        type="button"
        id="wallet-adjust-submit"
        onClick={handleSubmit}
        disabled={submitting}
        className={`w-full py-3 rounded-xl text-xs font-extrabold uppercase tracking-wider text-white transition-all disabled:opacity-50 disabled:cursor-not-allowed ${
          direction === "CREDIT"
            ? "bg-emerald-700 hover:bg-emerald-800"
            : "bg-red-700 hover:bg-red-800"
        }`}
      >
        {submitting
          ? t("admin.users.wallet.submitting")
          : `${t("admin.users.wallet.submit")} — ${username}`}
      </button>
    </div>
  );
};
