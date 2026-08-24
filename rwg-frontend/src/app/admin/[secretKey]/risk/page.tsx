"use client";

import React, { useState, useEffect, useCallback } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  ShieldAlert,
  CheckCircle2,
  XCircle,
  RefreshCw,
  Plus,
  AlertTriangle,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { AdminErrorState, AdminEmptyState } from "@/components/admin/AdminStates";
import { RiskProfileModal } from "@/components/admin/RiskProfileModal";
import { AdminModal } from "@/components/admin/AdminModal";
import { canViewRisk } from "@/lib/adminIdentity";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Liên kết tài khoản — khớp AccountLinkResponse của backend.
 *
 * KHÔNG có trường điểm tín nhiệm: hệ thống dùng `blocksCommission` để nói thẳng
 * liên kết này có đang giữ tiền hay không, thay vì để người vận hành tự suy từ
 * (linkType, status) rồi đoán sai.
 */
interface AccountLink {
  id: string;
  userAId: string;
  userAUsername: string;
  userBId: string;
  userBUsername: string;
  linkType: "SHARED_IP" | "SHARED_DEVICE" | "MANUAL";
  status: "SUSPECTED" | "CONFIRMED" | "CLEARED";
  blocksCommission: boolean;
  evidence: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  note: string | null;
  createdAt: string;
}

/** Một trang liên kết tài khoản, khớp `PageResponse` của backend. */
interface AccountLinkPage {
  content: AccountLink[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export default function AdminRiskPage() {
  const { t } = useTranslation();

  /** Nhãn loại liên kết tra trong file dịch; loại lạ hiện nguyên mã. */
  const linkTypeLabel = (type: string): string => {
    const key = `admin.link_types.${type}`;
    const label = t(key);
    return label === key ? type : label;
  };

  // SecurityConfig yeu cau ADMIN hoac RISK cho CA GET /admin/risk/**, nen neu
  // khong co quyen thi khong hien nut mo ho so.
  const canOpenProfile = canViewRisk();

  /** Ho so rui ro dang mo: {id, ten} de tieu de hop thoai khong trong khi tai. */
  const [profileTarget, setProfileTarget] = useState<{
    id: string;
    username: string;
  } | null>(null);

  const [links, setLinks] = useState<AccountLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  // Conclusion Modal
  const [concludeItem, setConcludeItem] = useState<AccountLink | null>(null);
  const [newStatus, setNewStatus] = useState<"CONFIRMED" | "CLEARED">("CONFIRMED");
  const [reason, setReason] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");

  // Manual Link Modal
  const [showManualModal, setShowManualModal] = useState(false);
  const [userAInput, setUserAInput] = useState("");
  const [userBInput, setUserBInput] = useState("");
  const [manualReason, setManualReason] = useState("");

  /**
   * Lấy danh sách liên kết tài khoản đáng ngờ.
   *
   * Trả dữ liệu về thay vì tự đặt state: `setState` gọi đồng bộ trong thân effect gây
   * chuỗi render liên tiếp và luật lint của dự án chặn.
   *
   * Trả `null` khi lỗi — KHÔNG đổ dữ liệu giả: một liên kết gian lận bịa đặt có thể dẫn
   * đến việc giữ tiền hoa hồng của người vô tội.
   */
  const fetchLinks = useCallback(async (): Promise<AccountLink[] | null> => {
    try {
      const data = await adminFetch<AccountLinkPage>("/admin/risk/links?page=0&size=20");
      setLoadError("");
      return data.content ?? [];
    } catch (err) {
      setLoadError((err as Error).message);
      return null;
    }
  }, []);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể rời trang trước khi request xong, và ghi state vào
    // component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      // Đặt state bên trong hàm async, không ở thân effect — xem lý do ở trên.
      setLoading(true);
      const data = await fetchLinks();
      if (cancelled) return;
      setLinks(data ?? []);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchLinks]);

  /** Tải lại từ nút bấm hoặc sau khi ghi. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    setLinks((await fetchLinks()) ?? []);
    setLoading(false);
  }, [fetchLinks]);

  const handleConclude = async () => {
    if (!concludeItem) return;
    if (!reason.trim()) {
      setActionError(t("admin.risk.review_reason_required"));
      return;
    }

    setActionLoading(true);
    setActionError("");

    try {
      // Backend: PATCH /admin/risk/links/{id}. Truong la `note`, KHONG phai `reason`.
      await adminFetch(`/admin/risk/links/${concludeItem.id}`, {
        method: "PATCH",
        body: JSON.stringify({
          status: newStatus,
          note: reason.trim(),
        }),
      });

      setConcludeItem(null);
      setReason("");
      void reload();
    } catch (err) {
      setActionError((err as Error).message || t("admin.risk.review_failed"));
    } finally {
      setActionLoading(false);
    }
  };

  const handleCreateManualLink = async () => {
    if (!userAInput.trim() || !userBInput.trim() || !manualReason.trim()) {
      setActionError(t("admin.risk.manual_incomplete"));
      return;
    }

    setActionLoading(true);
    setActionError("");

    try {
      // Backend: POST /admin/risk/links. Truong la `note`, KHONG phai `reason`.
      await adminFetch("/admin/risk/links", {
        method: "POST",
        body: JSON.stringify({
          userAId: userAInput.trim(),
          userBId: userBInput.trim(),
          note: manualReason.trim(),
        }),
      });

      setShowManualModal(false);
      setUserAInput("");
      setUserBInput("");
      setManualReason("");
      void reload();
    } catch (err) {
      setActionError((err as Error).message || t("admin.risk.manual_failed"));
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title={t("admin.risk.title")}
        subtitle={t("admin.risk.subtitle")}
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Action Bar */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ShieldAlert className="w-5 h-5 text-red-600" />
            <span className="text-sm font-extrabold text-slate-900">
              {t("admin.risk.queue_title")} ({links.length})
            </span>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowManualModal(true)}
              className="bg-red-600 hover:bg-red-700 text-white font-bold px-3.5 py-2 rounded-xl text-xs flex items-center gap-1.5 transition-colors shadow-sm"
            >
              <Plus className="w-4 h-4" />
              <span>{t("admin.risk.manual_link")}</span>
            </button>
            <button
              onClick={reload}
              className="p-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 shadow-xs"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-600" : ""}`} />
            </button>
          </div>
        </div>

        {/* Bang trong de bi hieu la "khong co gian lan nao"; loi tai du lieu phai
            duoc noi ro thay vi an di. */}
        {loadError ? (
          <AdminErrorState message={loadError} onRetry={reload} />
        ) : (
        <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-xs">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-100 border-b border-slate-200 text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                  <th className="py-3.5 px-4">{t("admin.risk.col_id")}</th>
                  <th className="py-3.5 px-4">{t("admin.risk.col_pair")}</th>
                  <th className="py-3.5 px-4">{t("admin.risk.col_type")}</th>
                  <th className="py-3.5 px-4">{t("admin.risk.col_evidence")}</th>
                  <th className="py-3.5 px-4">{t("admin.risk.col_hold")}</th>
                  <th className="py-3.5 px-4">{t("admin.risk.col_detected")}</th>
                  <th className="py-3.5 px-4 text-right">
                    {t("admin.risk.col_action")}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {links.length === 0 ? (
                  <tr>
                    <td colSpan={7}>
                      <AdminEmptyState message={t("admin.risk.empty")} />
                    </td>
                  </tr>
                ) : (
                  links.map((link) => (
                    <tr key={link.id} className="hover:bg-slate-50 transition-colors">
                      <td className="py-3.5 px-4 font-mono font-bold text-slate-900">{link.id}</td>
                      <td className="py-3.5 px-4">
                        <LinkedName
                          userId={link.userAId}
                          username={link.userAUsername}
                          clickable={canOpenProfile}
                          onOpen={setProfileTarget}
                        />
                        <span className="text-slate-400 mx-1 border-b border-dashed border-slate-300 px-1">↔</span>
                        <LinkedName
                          userId={link.userBId}
                          username={link.userBUsername}
                          clickable={canOpenProfile}
                          onOpen={setProfileTarget}
                        />
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="px-2 py-0.5 rounded-md font-bold text-[10px] bg-slate-100 text-slate-700 border border-slate-200">
                          {linkTypeLabel(link.linkType)}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-slate-600 font-medium max-w-[180px] truncate">
                        {link.evidence || "—"}
                      </td>
                      <td className="py-3.5 px-4">
                        {/* blocksCommission la su that ve viec tien co dang bi giu hay
                            khong; status chi la ket luan. Hien ca hai de khong nham. */}
                        {link.blocksCommission ? (
                          <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-red-50 text-red-700 border border-red-200 flex items-center gap-1 w-fit">
                            <XCircle className="w-3 h-3" /> {link.status} —{" "}
                            {t("admin.risk.holding")}
                          </span>
                        ) : link.status === "CLEARED" ? (
                          <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1 w-fit">
                            <CheckCircle2 className="w-3 h-3" /> CLEARED —{" "}
                            {t("admin.risk.released")}
                          </span>
                        ) : (
                          <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-amber-50 text-amber-700 border border-amber-200 flex items-center gap-1 w-fit">
                            <AlertTriangle className="w-3 h-3" /> {link.status}
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-slate-500">
                        {new Date(link.createdAt).toLocaleDateString()}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <button
                          onClick={() => {
                            setConcludeItem(link);
                            setNewStatus(link.status === "CONFIRMED" ? "CLEARED" : "CONFIRMED");
                          }}
                          className="px-3 py-1.5 rounded-xl bg-red-50 hover:bg-red-100 border border-red-200 text-red-700 font-bold text-xs transition-colors"
                        >
                          {t("admin.risk.write_review")}
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
        )}
      </div>

      {/* Conclude Modal */}
      <AdminModal
        isOpen={!!concludeItem}
        onClose={() => setConcludeItem(null)}
        maxWidthClass="max-w-md"
        title={
          concludeItem && (
            <div className="flex items-center gap-3 w-full">
              <div className="p-2.5 rounded-xl bg-red-50 border border-red-200 text-red-600 shrink-0">
                <ShieldAlert className="w-5 h-5" />
              </div>
              <div className="flex flex-col min-w-0">
                <h3 className="text-base font-extrabold text-slate-900 truncate">
                  {t("admin.risk.review_modal_title")}
                </h3>
                <span className="text-xs text-slate-500 font-medium truncate font-mono">
                  {t("admin.risk.col_id")}: {concludeItem.id}
                </span>
              </div>
            </div>
          )
        }
      >
        {concludeItem && (
          <div className="flex flex-col gap-5">
            {actionError && (
              <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700 font-semibold animate-modal-panel-in">
                {actionError}
              </div>
            )}

            <div className="flex flex-col gap-3">
              <label
                htmlFor="risk-review-status"
                className="text-xs font-bold text-slate-700"
              >
                {t("admin.risk.review_status")}
              </label>
              <select
                id="risk-review-status"
                value={newStatus}
                onChange={(e) =>
                  // Hai <option> bên dưới là toàn bộ giá trị có thể có, nên phép ép kiểu
                  // này an toàn. Dùng `any` ở đây sẽ làm mất luôn kiểm tra kiểu của state.
                  setNewStatus(e.target.value as "CONFIRMED" | "CLEARED")
                }
                className="bg-slate-50 border border-slate-200 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-bold cursor-pointer"
              >
                <option value="CONFIRMED">
                  {t("admin.risk.opt_confirmed")}
                </option>
                <option value="CLEARED">{t("admin.risk.opt_cleared")}</option>
              </select>

              <label
                htmlFor="risk-review-reason"
                className="text-xs font-bold text-slate-700"
              >
                {t("admin.risk.review_reason")}
              </label>
              <textarea
                id="risk-review-reason"
                rows={3}
                required
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={t("admin.risk.review_reason_placeholder")}
                className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-3 text-xs text-slate-900 placeholder-slate-400 outline-none resize-none font-medium"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setConcludeItem(null)}
                className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
              >
                {t("admin.states.cancel")}
              </button>
              <button
                onClick={handleConclude}
                disabled={actionLoading}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white font-bold text-xs transition-colors disabled:opacity-50"
              >
                {actionLoading
                  ? t("admin.states.saving")
                  : t("admin.risk.review_submit")}
              </button>
            </div>
          </div>
        )}
      </AdminModal>

      {/* Manual Link Modal */}
      <AdminModal
        isOpen={showManualModal}
        onClose={() => setShowManualModal(false)}
        maxWidthClass="max-w-md"
        title={
          <div className="flex items-center gap-3 w-full">
            <div className="p-2.5 rounded-xl bg-red-50 border border-red-200 text-red-600 shrink-0">
              <Plus className="w-5 h-5" />
            </div>
            <div className="flex flex-col min-w-0">
              <h3 className="text-base font-extrabold text-slate-900 truncate">
                {t("admin.risk.manual_modal_title")}
              </h3>
              <span className="text-xs text-slate-500 font-medium truncate">
                {t("admin.risk.manual_modal_subtitle")}
              </span>
            </div>
          </div>
        }
      >
        <div className="flex flex-col gap-5">
          {actionError && (
            <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700 font-semibold">
              {actionError}
            </div>
          )}

          <div className="flex flex-col gap-3">
            <label
              htmlFor="risk-user-a"
              className="text-xs font-bold text-slate-700"
            >
              User ID A
            </label>
            <input
              id="risk-user-a"
              type="text"
              value={userAInput}
              onChange={(e) => setUserAInput(e.target.value)}
              placeholder={t("admin.risk.manual_user_a")}
              className="bg-slate-50 border border-slate-200 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-bold"
            />

            <label
              htmlFor="risk-user-b"
              className="text-xs font-bold text-slate-700"
            >
              User ID B
            </label>
            <input
              id="risk-user-b"
              type="text"
              value={userBInput}
              onChange={(e) => setUserBInput(e.target.value)}
              placeholder={t("admin.risk.manual_user_b")}
              className="bg-slate-50 border border-slate-200 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-bold"
            />

            <label
              htmlFor="risk-manual-reason"
              className="text-xs font-bold text-slate-700"
            >
              {t("admin.risk.manual_reason")}
            </label>
            <textarea
              id="risk-manual-reason"
              rows={2}
              value={manualReason}
              onChange={(e) => setManualReason(e.target.value)}
              placeholder={t("admin.risk.manual_reason_placeholder")}
              className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-3 text-xs text-slate-900 outline-none resize-none font-medium"
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              onClick={() => setShowManualModal(false)}
              className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
            >
              {t("admin.states.cancel")}
            </button>
            <button
              onClick={handleCreateManualLink}
              disabled={actionLoading}
              className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white font-bold text-xs transition-colors disabled:opacity-50"
            >
              {actionLoading
                ? t("admin.states.saving")
                : t("admin.risk.manual_submit")}
            </button>
          </div>
        </div>
      </AdminModal>

      {/* Ho so rui ro cua mot tai khoan */}
      {profileTarget && (
        <RiskProfileModal
          userId={profileTarget.id}
          fallbackUsername={profileTarget.username}
          onClose={() => setProfileTarget(null)}
        />
      )}
    </div>
  );
}

/**
 * Tên tài khoản trong bảng liên kết.
 *
 * Khi không có quyền xem hồ sơ thì render thành chữ thường, không phải nút bị vô
 * hiệu: một nút bấm được nhưng luôn thất bại còn tệ hơn là không có nút.
 */
const LinkedName: React.FC<{
  userId: string;
  username: string;
  clickable: boolean;
  onOpen: (target: { id: string; username: string }) => void;
}> = ({ userId, username, clickable, onOpen }) => {
  const { t } = useTranslation();
  const display = username || userId;

  if (!clickable) {
    return <span className="font-bold text-red-700">{display}</span>;
  }

  return (
    <button
      onClick={() => onOpen({ id: userId, username: display })}
      className="font-bold text-red-700 hover:text-red-900 underline decoration-dotted decoration-red-300 hover:decoration-red-600 underline-offset-2 transition-colors cursor-pointer"
      title={t("admin.risk.open_profile", { name: display })}
    >
      {display}
    </button>
  );
};
