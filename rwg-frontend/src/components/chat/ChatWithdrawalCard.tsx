"use client";

import { useState } from "react";
import { AlertTriangle, Check, Loader2, X } from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { canAdjustWallet } from "@/lib/adminIdentity";
import { useTranslation } from "@/context/LanguageContext";

/** Thông tin lệnh rút, khớp `ChatWithdrawalCardResponse` của backend. */
export interface ChatWithdrawalCardData {
  orderId: string;
  amount: string;
  currency: string;
  status: "PENDING" | "SETTLED" | "VOIDED";
  bankCode: string | null;
  /** CHỈ 4 số cuối. Số đầy đủ nằm ở endpoint reveal riêng, có ghi nhật ký mỗi lần gọi. */
  maskedLast4: string | null;
  holderName: string | null;
  requestedAt: string;
  decidedByUsername: string | null;
  decisionNote: string | null;
}

/**
 * Lý do điền sẵn khi duyệt hoặc từ chối từ trong chat.
 *
 * Backend BẮT BUỘC có lý do vì đây là thao tác chuyển tiền không hoàn tác được qua giao
 * diện. Điền sẵn để việc xử lý trong chat gọn đúng một lần bấm, nhưng vẫn cho sửa: một
 * câu chung chung cho mọi lệnh sẽ làm nhật ký mất phần "vì sao" — đúng phần cần nhất khi
 * tra soát một khoản chi bất thường vài tháng sau.
 */
const DEFAULT_NOTES = {
  approve: "Duyệt tại hộp thư hỗ trợ",
  reject: "Từ chối tại hộp thư hỗ trợ",
} as const;

interface Props {
  card: ChatWithdrawalCardData;
  /** Gọi sau khi quyết định thành công, để tải lại luồng và hộp thư. */
  onDecided: () => void;
}

/**
 * Thẻ duyệt lệnh rút, hiện trong luồng chat quản trị.
 *
 * CHỈ NHÂN SỰ THẤY — người chơi không nhận được tin này, cả qua API lẫn WebSocket
 * (backend lọc theo `visible_to`; xem `findPageBeforeVisibleToPlayer` và
 * `ChatEventPublisher.deliverLocally`).
 *
 * DÙNG LẠI ĐÚNG ENDPOINT của trang duyệt rút tiền thay vì một đường riêng: đường đó đã có
 * chuyển trạng thái nguyên tử, chặn tự duyệt lệnh của chính mình, hoàn tiền idempotent khi
 * từ chối và bắt buộc ghi nhật ký. Một endpoint thứ hai cho cùng việc là chỗ để những chốt
 * đó bị bỏ sót.
 *
 * NÚT chỉ hiện với ADMIN/FINANCE, khớp matcher `POST /admin/withdrawals/*` trong
 * SecurityConfig. SUPPORT vẫn THẤY thẻ để biết khách đang chờ gì mà trả lời — nhưng bấm
 * được thì đó là một đường chuyển tiền ra khỏi sàn mở cho vai trò đông người nhất.
 */
export function ChatWithdrawalCard({ card, onDecided }: Props) {
  const { t } = useTranslation();

  /** Hành động đang chờ xác nhận; null = chưa bấm gì. */
  const [pending, setPending] = useState<"approve" | "reject" | null>(null);
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const canDecide = canAdjustWallet();
  const isPending = card.status === "PENDING";

  const open = (action: "approve" | "reject") => {
    setPending(action);
    setNote(DEFAULT_NOTES[action]);
    setError("");
  };

  const submit = async () => {
    if (!pending) return;
    const reason = note.trim();
    if (!reason) {
      setError(t("admin.chat.withdrawal.note_required"));
      return;
    }

    setSubmitting(true);
    setError("");
    try {
      await adminFetch(`/admin/withdrawals/${card.orderId}/${pending}`, {
        method: "POST",
        body: JSON.stringify({ note: reason }),
      });
      setPending(null);
      // Tải lại thay vì tự sửa trạng thái ở máy: trạng thái thật do bảng lệnh quyết
      // định, và một lệnh có thể đã bị người khác xử lý giữa lúc thẻ này đang mở.
      onDecided();
    } catch (err) {
      setError((err as Error).message || t("admin.chat.withdrawal.failed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-md rounded-2xl border-2 border-amber-300 bg-gradient-to-br from-amber-50 to-white p-3.5 shadow-sm">
      <div className="mb-3 flex items-center gap-2">
        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-amber-400/20">
          <AlertTriangle className="h-3.5 w-3.5 text-amber-600" aria-hidden="true" />
        </span>
        <h3 className="grow text-[11px] font-black uppercase tracking-wide text-amber-800">
          {t("admin.chat.withdrawal.title")}
        </h3>
        <StatusBadge status={card.status} />
      </div>

      <p className="mb-3 text-2xl font-black leading-none text-slate-900">
        {card.amount}
        <span className="ml-1.5 text-sm font-bold text-slate-500">{card.currency}</span>
      </p>

      <dl className="mb-3 flex flex-col gap-1 text-[11px]">
        <Row label={t("admin.chat.withdrawal.bank")} value={card.bankCode} />
        <Row
          label={t("admin.chat.withdrawal.account")}
          /* Dấu chấm lửng phía trước nói rõ đây là số đã che, không phải số tài khoản
             chỉ có 4 chữ số. */
          value={card.maskedLast4 ? `\u2022\u2022\u2022\u2022 ${card.maskedLast4}` : null}
        />
        <Row label={t("admin.chat.withdrawal.holder")} value={card.holderName} />
        <Row
          label={t("admin.chat.withdrawal.requested_at")}
          value={new Date(card.requestedAt).toLocaleString()}
        />
      </dl>

      {/* Lệnh đã xử lý: hiện ai quyết định và vì sao, đọc từ nhật ký hệ thống. Đây là
          thông tin không có ở đâu khác trong luồng chat. */}
      {!isPending && (
        <div className="rounded-xl bg-slate-100 px-3 py-2 text-[11px] text-slate-600">
          {card.decidedByUsername && (
            <p className="font-bold text-slate-700">{card.decidedByUsername}</p>
          )}
          {card.decisionNote && <p className="mt-0.5">{card.decisionNote}</p>}
          {!card.decidedByUsername && !card.decisionNote && (
            <p>{t("admin.chat.withdrawal.no_decision_info")}</p>
          )}
        </div>
      )}

      {isPending && !canDecide && (
        <p className="rounded-xl bg-slate-100 px-3 py-2 text-[11px] font-medium text-slate-500">
          {t("admin.chat.withdrawal.no_permission")}
        </p>
      )}

      {isPending && canDecide && !pending && (
        <div className="flex gap-2">
          <button
            type="button"
            id={`chat-withdrawal-approve-${card.orderId}`}
            onClick={() => open("approve")}
            className="flex grow items-center justify-center gap-1.5 rounded-xl bg-emerald-600 px-3 py-2 text-[11px] font-black text-white transition hover:bg-emerald-700"
          >
            <Check className="h-3.5 w-3.5" aria-hidden="true" />
            {t("admin.chat.withdrawal.approve")}
          </button>
          <button
            type="button"
            id={`chat-withdrawal-reject-${card.orderId}`}
            onClick={() => open("reject")}
            className="flex grow items-center justify-center gap-1.5 rounded-xl border border-red-200 bg-white px-3 py-2 text-[11px] font-black text-red-600 transition hover:bg-red-50"
          >
            <X className="h-3.5 w-3.5" aria-hidden="true" />
            {t("admin.chat.withdrawal.reject")}
          </button>
        </div>
      )}

      {isPending && canDecide && pending && (
        <div className="flex flex-col gap-2">
          <label
            htmlFor={`chat-withdrawal-note-${card.orderId}`}
            className="text-[10px] font-bold uppercase tracking-wide text-slate-500"
          >
            {t("admin.chat.withdrawal.note_label")}
          </label>
          <textarea
            id={`chat-withdrawal-note-${card.orderId}`}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={2}
            maxLength={255}
            className="w-full resize-none rounded-xl border border-slate-200 px-3 py-2 text-[11px] text-slate-800 outline-none focus:border-amber-400"
          />
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => void submit()}
              disabled={submitting}
              className={`flex grow items-center justify-center gap-1.5 rounded-xl px-3 py-2 text-[11px] font-black text-white transition disabled:opacity-60 ${
                pending === "approve"
                  ? "bg-emerald-600 hover:bg-emerald-700"
                  : "bg-red-600 hover:bg-red-700"
              }`}
            >
              {submitting ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
              ) : (
                <Check className="h-3.5 w-3.5" aria-hidden="true" />
              )}
              {t("admin.chat.withdrawal.confirm")}
            </button>
            <button
              type="button"
              onClick={() => setPending(null)}
              disabled={submitting}
              className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-[11px] font-bold text-slate-500 transition hover:bg-slate-50 disabled:opacity-60"
            >
              {t("admin.chat.withdrawal.cancel")}
            </button>
          </div>
        </div>
      )}

      {error && (
        <p className="mt-2 text-[11px] font-bold text-red-600" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

/** Một dòng nhãn — giá trị. Giá trị null hiện dấu gạch, không ẩn cả dòng. */
function Row({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="shrink-0 font-medium text-slate-500">{label}</dt>
      {/* Bố cục phải giữ nguyên khi thiếu dữ liệu: ẩn dòng sẽ làm các thẻ cao thấp khác
          nhau và mắt khó so sánh khi cuộn qua nhiều thẻ. */}
      <dd className="truncate text-right font-bold text-slate-800">{value ?? "—"}</dd>
    </div>
  );
}

function StatusBadge({ status }: { status: ChatWithdrawalCardData["status"] }) {
  const { t } = useTranslation();
  const style =
    status === "SETTLED"
      ? "bg-emerald-100 text-emerald-700"
      : status === "VOIDED"
        ? "bg-red-100 text-red-700"
        : "bg-amber-200 text-amber-900";
  return (
    <span className={`rounded-full px-2 py-0.5 text-[9px] font-black uppercase ${style}`}>
      {t(`admin.chat.withdrawal.status.${status}`)}
    </span>
  );
}
