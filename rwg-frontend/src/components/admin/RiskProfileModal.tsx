"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  Loader2,
  AlertTriangle,
  Globe,
  Fingerprint,
  Ban,
  CheckCircle2,
  ShieldAlert,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { AdminModal } from "@/components/admin/AdminModal";
import { useTranslation } from "@/context/LanguageContext";

/** Một liên kết tài khoản — khớp AccountLinkResponse. */
interface AccountLink {
  id: string;
  userAId: string;
  userAUsername: string;
  userBId: string;
  userBUsername: string;
  linkType: string;
  status: string;
  blocksCommission: boolean;
  evidence: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  note: string | null;
  createdAt: string;
}

/**
 * Hồ sơ rủi ro — khớp UserRiskProfileResponse.
 *
 * Backend gộp dấu vết đăng ký và toàn bộ liên kết vào MỘT response, theo javadoc là
 * "để người vận hành không phải gọi ba API rồi tự ghép" — lúc điều tra cần thấy cả
 * bức tranh cùng lúc.
 */
interface RiskProfile {
  userId: string;
  username: string;
  registrationIp: string | null;
  hasDeviceFingerprint: boolean;
  signalRecordedAt: string | null;
  commissionBlocked: boolean;
  links: AccountLink[];
}

interface Props {
  userId: string;
  /** Tên hiển thị tạm trong lúc chờ tải, để hộp thoại không trống tiêu đề. */
  fallbackUsername?: string;
  onClose: () => void;
}

export const RiskProfileModal: React.FC<Props> = ({
  userId,
  fallbackUsername,
  onClose,
}) => {
  const { t } = useTranslation();
  const [profile, setProfile] = useState<RiskProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  /** Nhãn tra trong file dịch; mã lạ trả về nguyên bản thay vì để trống. */
  const lookup = (branch: string, code: string): string => {
    const key = `admin.${branch}.${code}`;
    const label = t(key);
    return label === key ? code : label;
  };

  /**
   * Lấy hồ sơ rủi ro của người dùng.
   *
   * Trả dữ liệu về thay vì tự đặt state: `setState` gọi đồng bộ trong thân effect gây
   * chuỗi render liên tiếp và luật lint của dự án chặn.
   */
  const fetchProfile = useCallback(async (): Promise<RiskProfile | null> => {
    try {
      const data = await adminFetch<RiskProfile>(`/admin/risk/users/${userId}`);
      setError("");
      return data;
    } catch (err) {
      setError((err as Error).message);
      return null;
    }
  }, [userId]);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể đóng hộp thoại trước khi request xong, và ghi state
    // vào component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      // Đặt state bên trong hàm async, không ở thân effect — xem lý do ở trên.
      setLoading(true);
      const data = await fetchProfile();
      if (cancelled) return;
      if (data) setProfile(data);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchProfile]);

  /** Thử lại từ nút bấm. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    const data = await fetchProfile();
    if (data) setProfile(data);
    setLoading(false);
  }, [fetchProfile]);

  return (
    <AdminModal
      isOpen={true} // Modal nay duoc mount co dieu kien tu component cha (profileTarget && <RiskProfileModal />)
      onClose={onClose}
      maxWidthClass="max-w-lg"
      title={
        <div className="flex items-center gap-3 w-full">
          <div className="p-2.5 rounded-xl bg-amber-50 border border-amber-200 shrink-0">
            <ShieldAlert className="w-5 h-5 text-amber-600" />
          </div>
          <div className="flex flex-col min-w-0">
            <h3 className="text-base font-extrabold text-slate-900 truncate">
              {t("admin.risk.profile.title")}
            </h3>
            <span className="text-xs text-slate-500 font-medium truncate">
              {profile?.username || fallbackUsername || t("admin.states.loading")}
            </span>
          </div>
        </div>
      }
    >
      <div className="flex flex-col gap-5">
        {loading && (
          <div className="flex items-center justify-center gap-2 py-10 text-xs text-slate-500 font-semibold">
            <Loader2 className="w-4 h-4 animate-spin" />
            {t("admin.states.loading")}
          </div>
        )}

        {error && (
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
        )}

        {!loading && profile && (
          <>
            {/* Hoa hong bi giu la hau qua tai chinh truc tiep, phai dat len dau. */}
            {profile.commissionBlocked ? (
              <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-300 rounded-xl">
                <Ban className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
                <div className="flex flex-col">
                  <span className="text-xs font-extrabold text-red-800">
                    {t("admin.risk.profile.blocked")}
                  </span>
                  <span className="text-[11px] text-red-700 font-medium leading-relaxed">
                    {t("admin.risk.profile.commission_blocked_desc")}
                  </span>
                </div>
              </div>
            ) : (
              <div className="flex items-center gap-2.5 px-3.5 py-3 bg-emerald-50 border border-emerald-200 rounded-xl">
                <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                <span className="text-xs font-bold text-emerald-800">
                  {t("admin.risk.profile.not_blocked")}
                </span>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1 p-3 bg-slate-50 border border-slate-200 rounded-xl">
                <div className="flex items-center gap-1.5">
                  <Globe className="w-3 h-3 text-slate-400" />
                  <span className="text-slate-500 text-[10px] uppercase font-bold">
                    {t("admin.risk.profile.reg_ip")}
                  </span>
                </div>
                <span className="text-slate-900 font-bold text-xs font-mono break-all">
                  {profile.registrationIp || "—"}
                </span>
              </div>

              <div className="flex flex-col gap-1 p-3 bg-slate-50 border border-slate-200 rounded-xl">
                <div className="flex items-center gap-1.5">
                  <Fingerprint className="w-3 h-3 text-slate-400" />
                  <span className="text-slate-500 text-[10px] uppercase font-bold">
                    {t("admin.risk.profile.device")}
                  </span>
                </div>
                <span
                  className={`font-bold text-xs ${
                    profile.hasDeviceFingerprint
                      ? "text-slate-900"
                      : "text-slate-400"
                  }`}
                >
                  {profile.hasDeviceFingerprint
                    ? t("admin.risk.profile.device_yes")
                    : t("admin.risk.profile.device_no")}
                </span>
              </div>
            </div>

            {profile.signalRecordedAt && (
              <div className="flex items-center justify-between px-1 text-[11px]">
                <span className="text-slate-500 font-semibold">
                  {t("admin.risk.profile.recorded_at")}
                </span>
                <span className="text-slate-700 font-bold">
                  {new Date(profile.signalRecordedAt).toLocaleString()}
                </span>
              </div>
            )}

            <div className="flex flex-col gap-2 pt-1">
              <span className="text-[11px] font-bold text-slate-700 uppercase tracking-wide">
                {t("admin.risk.profile.links")} ({profile.links.length})
              </span>

              {profile.links.length === 0 ? (
                <div className="py-6 text-center text-xs text-slate-500 font-medium bg-slate-50 border border-slate-200 rounded-xl">
                  {t("admin.risk.profile.no_links")}
                </div>
              ) : (
                <div className="flex flex-col gap-2">
                  {profile.links.map((link) => {
                    // Doi tuong lien quan la BEN CON LAI cua cap, khong phai
                    // chinh nguoi dang xem.
                    const isUserA = link.userAId === profile.userId;
                    const otherName = isUserA
                      ? link.userBUsername || link.userBId
                      : link.userAUsername || link.userAId;

                    return (
                      <div
                        key={link.id}
                        className="flex flex-col gap-1.5 p-3 bg-white border border-slate-200 rounded-xl"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-xs font-extrabold text-slate-900 truncate">
                            {otherName}
                          </span>
                          <span
                            className={`px-2 py-0.5 rounded-md font-bold text-[10px] border shrink-0 ${
                              link.status === "CONFIRMED"
                                ? "bg-red-50 text-red-700 border-red-200"
                                : link.status === "CLEARED"
                                  ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                  : "bg-amber-50 text-amber-700 border-amber-200"
                            }`}
                          >
                            {lookup("link_status", link.status)}
                          </span>
                        </div>

                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-[10px] font-bold text-slate-500 uppercase">
                            {lookup("link_types", link.linkType)}
                          </span>
                          {link.blocksCommission && (
                            <span className="px-1.5 py-0.5 rounded font-bold text-[9px] bg-red-50 text-red-700 border border-red-200">
                              {t("admin.risk.profile.blocks_commission_badge")}
                            </span>
                          )}
                        </div>

                        {link.evidence && (
                          <span className="text-[10px] text-slate-500 font-medium break-all">
                            {link.evidence}
                          </span>
                        )}
                        {link.note && (
                          <span className="text-[10px] text-slate-600 font-semibold italic">
                            “{link.note}”
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </AdminModal>
  );
};
