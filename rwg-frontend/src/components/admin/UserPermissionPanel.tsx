"use client";

import React, { useState } from "react";
import {
  ShieldCheck,
  AlertTriangle,
  CheckCircle2,
  KeyRound,
  ShieldOff,
} from "lucide-react";
import { adminFetch, AdminApiError } from "@/lib/adminApi";
import { getAdminIdentity, isSuperAdmin } from "@/lib/adminIdentity";
import { useTranslation } from "@/context/LanguageContext";

interface Props {
  userId: string;
  username: string;
  currentRole: string;
  /** Gọi lại sau khi đổi thành công để trang cha tải lại. */
  onChanged: () => void;
}

/**
 * Các vai trò có thể gán.
 *
 * Javadoc của endpoint ghi "(PLAYER | ADMIN)" nhưng AdminUserService.parseRole dùng
 * UserRole.valueOf nên nhận CẢ 5 giá trị, và SecurityConfig phân quyền riêng cho
 * FINANCE, SUPPORT, RISK — nên cả 5 đều là vai trò thật đang được dùng.
 */
const ROLES = [
  { value: "PLAYER", staff: false },
  { value: "SUPPORT", staff: true },
  { value: "RISK", staff: true },
  { value: "FINANCE", staff: true },
  { value: "ADMIN", staff: true },
];

const isStaffRole = (role: string): boolean =>
  ROLES.find((r) => r.value === role)?.staff ?? false;

/**
 * Đổi quyền tài khoản và xoá mật khẩu rút tiền.
 *
 * Gộp hai thao tác vào một chỗ vì cả hai đều thuộc nhóm "can thiệp vào khả năng
 * truy cập của người dùng", khác với việc sửa số dư hay trạng thái.
 */
export const UserPermissionPanel: React.FC<Props> = ({
  userId,
  username,
  currentRole,
  onChanged,
}) => {
  const { t } = useTranslation();
  const identity = getAdminIdentity();
  const canChangeRole = isSuperAdmin(identity);
  /** Backend chặn tự sửa; ẩn trước để không để người dùng bấm rồi mới nhận lỗi. */
  const isSelf = identity.userId === userId;

  /** Nhãn vai trò tra trong file dịch; mã lạ hiện nguyên bản. */
  const label = (role: string): string => {
    const key = `admin.roles.${role}`;
    const text = t(key);
    return text === key ? role : text;
  };

  const [newRole, setNewRole] = useState(currentRole);
  const [confirmName, setConfirmName] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [okMessage, setOkMessage] = useState("");

  const [resetting, setResetting] = useState(false);
  const [resetError, setResetError] = useState("");
  const [resetDone, setResetDone] = useState(false);

  const roleChanged = newRole !== currentRole;
  const elevating = roleChanged && isStaffRole(newRole) && !isStaffRole(currentRole);
  /**
   * Nâng lên quyền nhân sự BẮT BUỘC gõ đúng tên tài khoản.
   *
   * Đây là thao tác cấp quyền vào khu quản trị tiền — một lần bấm nhầm dòng trong
   * bảng là cấp quyền cho người sai. Bắt gõ tên buộc người thao tác phải đọc lại
   * mình đang tác động lên ai.
   */
  const needsNameConfirm = elevating;
  const nameMatches = confirmName.trim() === username;

  const submitRole = async () => {
    setError("");
    setOkMessage("");

    if (!roleChanged) {
      setError(t("admin.users.role.pick_different"));
      return;
    }
    if (needsNameConfirm && !nameMatches) {
      setError(t("admin.users.role.mismatch"));
      return;
    }

    setSubmitting(true);
    try {
      await adminFetch(`/admin/users/${userId}/role`, {
        method: "PATCH",
        body: JSON.stringify({
          role: newRole,
          reason: reason.trim() || null,
        }),
      });
      setOkMessage(
        t("admin.users.role.done", { name: username, role: label(newRole) })
      );
      setConfirmName("");
      setReason("");
      onChanged();
    } catch (err) {
      if (err instanceof AdminApiError && err.code === "CANNOT_MODIFY_SELF") {
        setError(t("admin.users.role.cannot_self"));
      } else {
        setError((err as Error).message);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const submitReset = async () => {
    setResetError("");
    setResetDone(false);
    setResetting(true);
    try {
      await adminFetch(`/admin/users/${userId}/withdrawal-password/reset`, {
        method: "POST",
      });
      setResetDone(true);
      onChanged();
    } catch (err) {
      setResetError((err as Error).message);
    } finally {
      setResetting(false);
    }
  };

  return (
    <div className="flex flex-col gap-5">
      {/* ---- Doi quyen ---- */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-slate-600" />
          <span className="text-xs font-extrabold text-slate-900 uppercase tracking-wide">
            {t("admin.users.role.title")}
          </span>
        </div>

        {!canChangeRole ? (
          <div className="flex items-start gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-xl">
            <ShieldOff className="w-5 h-5 text-slate-400 shrink-0 mt-0.5" />
            <span className="text-xs text-slate-600 font-medium leading-relaxed">
              {t("admin.users.role.only_admin")}
            </span>
          </div>
        ) : isSelf ? (
          <div className="flex items-start gap-3 p-3.5 bg-amber-50 border border-amber-300 rounded-xl">
            <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
            <span className="text-xs text-amber-800 font-semibold leading-relaxed">
              {t("admin.users.role.is_self")}
            </span>
          </div>
        ) : (
          <>
            <div className="flex items-center gap-3 px-3.5 py-3 bg-slate-50 border border-slate-200 rounded-xl">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wide">
                {t("admin.users.role.current")}
              </span>
              <span className="text-xs font-extrabold text-slate-900">
                {label(currentRole)}
              </span>
            </div>

            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="user-new-role"
                className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
              >
                {t("admin.users.role.new")}
              </label>
              <select
                id="user-new-role"
                value={newRole}
                onChange={(e) => {
                  setNewRole(e.target.value);
                  setConfirmName("");
                  setError("");
                  setOkMessage("");
                }}
                className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-xs font-bold text-slate-900 outline-none cursor-pointer"
              >
                {ROLES.map((r) => (
                  <option key={r.value} value={r.value}>
                    {label(r.value)}
                  </option>
                ))}
              </select>
            </div>

            {roleChanged && (
              <div className="flex items-start gap-3 p-3.5 bg-amber-50 border border-amber-300 rounded-xl">
                <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
                <div className="flex flex-col gap-1">
                  <span className="text-[11px] text-amber-800 font-semibold leading-relaxed">
                    {t("admin.users.role.warning")}
                  </span>
                  {elevating && (
                    <span className="text-[11px] text-amber-900 font-bold leading-relaxed">
                      {t("admin.users.role.confirm_hint")}
                    </span>
                  )}
                </div>
              </div>
            )}

            {needsNameConfirm && (
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor="user-confirm-name"
                  className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
                >
                  {t("admin.users.role.confirm_label")}
                </label>
                <input
                  id="user-confirm-name"
                  type="text"
                  value={confirmName}
                  onChange={(e) => setConfirmName(e.target.value)}
                  placeholder={username}
                  autoComplete="off"
                  className={`bg-slate-50 border rounded-xl px-3.5 py-2.5 text-xs font-mono font-bold text-slate-900 placeholder-slate-300 outline-none ${
                    confirmName && !nameMatches
                      ? "border-red-300 focus:border-red-500"
                      : "border-slate-200 focus:border-slate-900"
                  }`}
                />
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="user-role-reason"
                className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
              >
                {t("admin.states.reason_optional")}
              </label>
              <input
                id="user-role-reason"
                type="text"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={t("admin.users.role.reason_placeholder")}
                className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-xs text-slate-900 placeholder-slate-400 outline-none"
              />
            </div>

            {error && (
              <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
                <AlertTriangle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
                <span className="text-xs text-red-700 font-semibold">{error}</span>
              </div>
            )}

            {okMessage && (
              <div className="flex items-start gap-3 p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl">
                <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
                <span className="text-xs text-emerald-800 font-semibold">
                  {okMessage}
                </span>
              </div>
            )}

            <button
              onClick={submitRole}
              disabled={
                submitting || !roleChanged || (needsNameConfirm && !nameMatches)
              }
              className="py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed text-white text-xs font-bold transition-colors"
            >
              {submitting
                ? t("admin.states.processing")
                : t("admin.users.role.submit")}
            </button>
          </>
        )}
      </div>

      {/* ---- Mat khau rut tien ---- */}
      <div className="flex flex-col gap-3 pt-5 border-t border-slate-200">
        <div className="flex items-center gap-2">
          <KeyRound className="w-4 h-4 text-slate-600" />
          <span className="text-xs font-extrabold text-slate-900 uppercase tracking-wide">
            {t("admin.users.wpwd.title")}
          </span>
        </div>

        {/* Backend XOA mat khau chu khong dat ho. Phai noi ro, neu khong nguoi van
            hanh se hua voi khach la "da doi mat khau cho anh roi". */}
        <div className="flex items-start gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-xl">
          <AlertTriangle className="w-5 h-5 text-slate-400 shrink-0 mt-0.5" />
          <span className="text-[11px] text-slate-600 font-medium leading-relaxed">
            {t("admin.users.wpwd.warning")}
          </span>
        </div>

        {resetError && (
          <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
            <AlertTriangle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
            <span className="text-xs text-red-700 font-semibold">{resetError}</span>
          </div>
        )}

        {resetDone && (
          <div className="flex items-start gap-3 p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl">
            <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
            <span className="text-xs text-emerald-800 font-semibold">
              {t("admin.users.wpwd.done")}
            </span>
          </div>
        )}

        <button
          onClick={submitReset}
          disabled={resetting}
          className="py-2.5 rounded-xl bg-white hover:bg-slate-50 border border-slate-300 disabled:opacity-40 text-slate-800 text-xs font-bold transition-colors"
        >
          {resetting
            ? t("admin.states.processing")
            : t("admin.users.wpwd.reset")}
        </button>
      </div>
    </div>
  );
};
