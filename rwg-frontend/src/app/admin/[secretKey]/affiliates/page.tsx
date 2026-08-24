"use client";

import React, { useState, useEffect, useCallback } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  Network,
  RefreshCw,
  Layers,
  Settings2,
  Play,
  Search,
  Save,
  AlertCircle,
  CheckCircle2,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { formatMoney } from "@/lib/money";
import { isSuperAdmin, canAdjustWallet } from "@/lib/adminIdentity";
import { AdminErrorState, AdminEmptyState } from "@/components/admin/AdminStates";
import { AdminModal } from "@/components/admin/AdminModal";
import { useTranslation } from "@/context/LanguageContext";

/** Một chứng từ chi hoa hồng — khớp CommissionRunResponse. */
interface CommissionRun {
  id: string;
  agentId: string;
  periodDate: string;
  level: number;
  turnover: string;
  rate: string;
  amount: string;
  createdAt: string;
}

/** Cấu hình % hoa hồng — khớp CommissionSettingsResponse. */
interface CommissionSettings {
  level1Rate: string;
  level2Rate: string;
  updatedAt: string;
  updatedBy: string | null;
}

/** Thành viên tuyến dưới — khớp DownlineMemberResponse. */
interface DownlineMember {
  userId: string;
  username: string;
  level: number;
  joinedAt: string;
}

/** Một trang thành viên tuyến dưới, khớp `PageResponse` của backend. */
interface DownlinePage {
  content: DownlineMember[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** Một trang chứng từ chi hoa hồng, khớp `PageResponse` của backend. */
interface CommissionRunPage {
  content: CommissionRun[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/**
 * Kết quả tải của một tab.
 *
 * Dạng hợp có thẻ phân biệt thay vì hai trường rời nhau: hai tab trả hai kiểu khác nhau,
 * và cách này khiến TypeScript tự chặn việc đọc sai trường cho tab đang mở.
 */
type TabData =
  | { kind: "commissions"; data: CommissionRunPage }
  | { kind: "config"; data: CommissionSettings };

type Tab = "commissions" | "downline" | "config";

export default function AdminAffiliatesPage() {
  const { t } = useTranslation();
  const canEditConfig = isSuperAdmin();
  const canRunPayout = canAdjustWallet();

  const [tab, setTab] = useState<Tab>("commissions");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [runs, setRuns] = useState<CommissionRun[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const [settings, setSettings] = useState<CommissionSettings | null>(null);
  const [level1, setLevel1] = useState("");
  const [level2, setLevel2] = useState("");

  // Tra cứu tuyến dưới cần biết đại lý nào, nên phải nhập id trước.
  const [agentId, setAgentId] = useState("");
  const [downlineLevel, setDownlineLevel] = useState(1);
  const [downline, setDownline] = useState<DownlineMember[]>([]);
  const [downlineSearched, setDownlineSearched] = useState(false);

  const [showRunModal, setShowRunModal] = useState(false);
  const [runDate, setRunDate] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");
  const [actionOk, setActionOk] = useState("");

  /**
   * Lấy dữ liệu của tab đang mở.
   *
   * Trả dữ liệu về thay vì tự đặt state: `setState` gọi đồng bộ trong thân effect gây
   * chuỗi render liên tiếp và luật lint của dự án chặn. Tab `downline` không tải ở đây
   * vì nó cần mã đại lý do người vận hành nhập.
   */
  const fetchTabData = useCallback(async (): Promise<TabData | null> => {
    try {
      if (tab === "commissions") {
        // Backend: /admin/affiliate (SO IT, khong phai /affiliates).
        const data = await adminFetch<CommissionRunPage>(
          `/admin/affiliate/commissions?page=${page}&size=20`
        );
        setLoadError("");
        return { kind: "commissions", data };
      }
      if (tab === "config") {
        const data = await adminFetch<CommissionSettings>("/admin/affiliate/config");
        setLoadError("");
        return { kind: "config", data };
      }
      setLoadError("");
      return null;
    } catch (err) {
      setLoadError((err as Error).message);
      return null;
    }
  }, [tab, page]);

  /** Đưa kết quả vào state. Dùng chung cho lần tải đầu và nút Tải lại. */
  const applyResult = useCallback((result: TabData | null) => {
    if (result === null) {
      return;
    }
    if (result.kind === "commissions") {
      setRuns(result.data.content ?? []);
      setTotalPages(result.data.totalPages || 1);
      return;
    }
    setSettings(result.data);
    setLevel1(result.data.level1Rate);
    setLevel2(result.data.level2Rate);
  }, []);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể đổi tab trước khi request xong, và ghi state vào
    // component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      // Đặt state bên trong hàm async, không ở thân effect — xem lý do ở trên.
      setLoading(true);
      const result = await fetchTabData();
      if (cancelled) return;
      applyResult(result);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchTabData, applyResult]);

  /** Tải lại từ nút bấm. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    applyResult(await fetchTabData());
    setLoading(false);
  }, [fetchTabData, applyResult]);

  const searchDownline = async () => {
    if (!agentId.trim()) return;
    setLoading(true);
    setLoadError("");
    setDownlineSearched(true);
    try {
      const data = await adminFetch<DownlinePage>(
        `/admin/affiliate/users/${agentId.trim()}/downline?level=${downlineLevel}&page=0&size=50`
      );
      setDownline(data.content ?? []);
    } catch (err) {
      setLoadError((err as Error).message);
      setDownline([]);
    } finally {
      setLoading(false);
    }
  };

  const saveConfig = async () => {
    setActionLoading(true);
    setActionError("");
    setActionOk("");
    try {
      const data = await adminFetch<CommissionSettings>("/admin/affiliate/config", {
        method: "PATCH",
        // Ti le gui dang CHUOI: backend parse BigDecimal.
        body: JSON.stringify({ level1Rate: level1.trim(), level2Rate: level2.trim() }),
      });
      setSettings(data);
      setActionOk(t("affiliates.save_ok"));
    } catch (err) {
      setActionError((err as Error).message);
    } finally {
      setActionLoading(false);
    }
  };

  const triggerRun = async () => {
    if (!runDate) {
      setActionError(t("affiliates.pick_date"));
      return;
    }
    setActionLoading(true);
    setActionError("");
    try {
      await adminFetch(
        `/admin/affiliate/commissions/run?periodDate=${runDate}`,
        { method: "POST" }
      );
      setShowRunModal(false);
      setRunDate("");
      setTab("commissions");
      setPage(0);
      void reload();
    } catch (err) {
      setActionError((err as Error).message);
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title={t("affiliates.title")}
        subtitle={t("affiliates.subtitle")}
      />

      <div className="p-6 flex flex-col gap-6">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div className="flex items-center gap-1 p-1 bg-white border border-slate-200 rounded-2xl shadow-xs">
            {(
              [
                ["commissions", t("affiliates.tab_commissions"), Layers],
                ["downline", t("affiliates.tab_downline"), Network],
                ["config", t("affiliates.tab_config"), Settings2],
              ] as const
            ).map(([key, label, Icon]) => (
              <button
                key={key}
                onClick={() => {
                  setTab(key);
                  setPage(0);
                  setActionOk("");
                  setActionError("");
                }}
                className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                  tab === key
                    ? "bg-slate-900 text-white"
                    : "text-slate-500 hover:text-slate-900 hover:bg-slate-50"
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                {label}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-2">
            {canRunPayout && (
              <button
                onClick={() => {
                  setShowRunModal(true);
                  setActionError("");
                }}
                className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition-colors"
              >
                <Play className="w-3.5 h-3.5" />
                {t("affiliates.btn_recomm")}
              </button>
            )}
            <button
              onClick={reload}
              className="p-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 shadow-xs"
              aria-label={t("admin.states.refresh")}
            >
              <RefreshCw
                className={`w-4 h-4 ${loading ? "animate-spin text-red-600" : ""}`}
              />
            </button>
          </div>
        </div>

        {loadError && <AdminErrorState message={loadError} onRetry={reload} />}

        {/* Tab: chung tu hoa hong */}
        {!loadError && tab === "commissions" && (
          <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-100 border-b border-slate-200 text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                    <th className="py-3.5 px-4">{t("affiliates.col_period")}</th>
                    <th className="py-3.5 px-4">{t("affiliates.col_agent")}</th>
                    <th className="py-3.5 px-4">{t("affiliates.col_level")}</th>
                    <th className="py-3.5 px-4">{t("affiliates.col_turnover")}</th>
                    <th className="py-3.5 px-4">{t("affiliates.col_rate")}</th>
                    <th className="py-3.5 px-4">{t("affiliates.col_commission")}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs">
                  {runs.length === 0 ? (
                    <tr>
                      <td colSpan={6}>
                        <AdminEmptyState message={t("affiliates.empty_commissions")} />
                      </td>
                    </tr>
                  ) : (
                    runs.map((r) => (
                      <tr key={r.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3.5 px-4 font-bold text-slate-900">
                          {r.periodDate}
                        </td>
                        <td className="py-3.5 px-4 font-mono text-[10px] text-slate-600">
                          {r.agentId.slice(0, 8)}
                        </td>
                        <td className="py-3.5 px-4">
                          <span className="px-2 py-0.5 rounded-md font-bold text-[10px] bg-slate-100 text-slate-700 border border-slate-200">
                            F{r.level}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 text-slate-700 font-semibold tabular-nums">
                          {formatMoney(r.turnover)}
                        </td>
                        <td className="py-3.5 px-4 text-slate-600 font-semibold tabular-nums">
                          {r.rate}
                        </td>
                        <td className="py-3.5 px-4 font-black text-emerald-700 tabular-nums">
                          {formatMoney(r.amount)}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="p-4 border-t border-slate-200 flex items-center justify-between text-xs text-slate-500">
                <span>
                  {t("admin.states.page_of", { page: page + 1, total: totalPages })}
                </span>
                <div className="flex items-center gap-2">
                  <button
                    disabled={page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    className="px-3 py-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200 font-bold"
                  >
                    {t("admin.states.prev_page")}
                  </button>
                  <button
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage((p) => p + 1)}
                    className="px-3 py-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200 font-bold"
                  >
                    {t("admin.states.next_page")}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Tab: tuyen duoi */}
        {!loadError && tab === "downline" && (
          <div className="flex flex-col gap-4">
            <div className="bg-white border border-slate-200 rounded-2xl p-4 flex flex-wrap items-end gap-3 shadow-xs">
              <div className="flex flex-col gap-1.5 flex-1 min-w-[280px]">
                <label
                  htmlFor="agent-id"
                  className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
                >
                  {t("affiliates.search_agent_uuid")}
                </label>
                <input
                  id="agent-id"
                  type="text"
                  value={agentId}
                  onChange={(e) => setAgentId(e.target.value)}
                  placeholder={t("affiliates.search_agent_placeholder")}
                  className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-xs font-mono text-slate-900 placeholder-slate-400 outline-none"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor="downline-level"
                  className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
                >
                  {t("affiliates.search_level")}
                </label>
                <select
                  id="downline-level"
                  value={downlineLevel}
                  onChange={(e) => setDownlineLevel(Number(e.target.value))}
                  className="bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2.5 text-xs font-bold text-slate-900 outline-none"
                >
                  <option value={1}>{t("affiliates.opt_f1")}</option>
                  <option value={2}>{t("affiliates.opt_f2")}</option>
                </select>
              </div>
              <button
                onClick={searchDownline}
                disabled={!agentId.trim()}
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 disabled:opacity-40 text-white text-xs font-bold transition-colors"
              >
                <Search className="w-3.5 h-3.5" />
                {t("affiliates.btn_search")}
              </button>
            </div>

            {downlineSearched && (
              <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-xs">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-100 border-b border-slate-200 text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                      <th className="py-3.5 px-4">{t("affiliates.col_account")}</th>
                      <th className="py-3.5 px-4">{t("affiliates.col_level")}</th>
                      <th className="py-3.5 px-4">{t("affiliates.col_joined")}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 text-xs">
                    {downline.length === 0 ? (
                      <tr>
                        <td colSpan={3}>
                          <AdminEmptyState message={t("affiliates.empty_downline")} />
                        </td>
                      </tr>
                    ) : (
                      downline.map((m) => (
                        <tr key={m.userId} className="hover:bg-slate-50 transition-colors">
                          <td className="py-3.5 px-4 font-bold text-slate-900">
                            {m.username}
                          </td>
                          <td className="py-3.5 px-4">
                            <span className="px-2 py-0.5 rounded-md font-bold text-[10px] bg-slate-100 text-slate-700 border border-slate-200">
                              F{m.level}
                            </span>
                          </td>
                          <td className="py-3.5 px-4 text-slate-500">
                            {new Date(m.joinedAt).toLocaleDateString()}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* Tab: cau hinh ti le */}
        {!loadError && tab === "config" && settings && (
          <div className="bg-white border border-slate-200 rounded-2xl p-6 flex flex-col gap-5 shadow-xs max-w-xl">
            {/* Doi ti le hoa hong la ADMIN-only trong SecurityConfig, FINANCE khong duoc. */}
            {!canEditConfig && (
              <div className="flex items-start gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-xl">
                <AlertCircle className="w-5 h-5 text-slate-400 shrink-0 mt-0.5" />
                <span className="text-xs text-slate-600 font-medium leading-relaxed">
                  {t("affiliates.config_admin_only")}
                </span>
              </div>
            )}

            {actionOk && (
              <div className="flex items-start gap-3 p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl">
                <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
                <span className="text-xs text-emerald-800 font-semibold">{actionOk}</span>
              </div>
            )}

            {actionError && (
              <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
                <AlertCircle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
                <span className="text-xs text-red-700 font-semibold">{actionError}</span>
              </div>
            )}

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor="level1-rate"
                  className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
                >
                  {t("affiliates.rate_f1")}
                </label>
                <input
                  id="level1-rate"
                  type="text"
                  inputMode="decimal"
                  value={level1}
                  disabled={!canEditConfig}
                  onChange={(e) => setLevel1(e.target.value)}
                  className="bg-slate-50 border border-slate-200 focus:border-slate-900 disabled:opacity-60 rounded-xl px-3.5 py-2.5 text-sm font-bold text-slate-900 outline-none tabular-nums"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor="level2-rate"
                  className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
                >
                  {t("affiliates.rate_f2")}
                </label>
                <input
                  id="level2-rate"
                  type="text"
                  inputMode="decimal"
                  value={level2}
                  disabled={!canEditConfig}
                  onChange={(e) => setLevel2(e.target.value)}
                  className="bg-slate-50 border border-slate-200 focus:border-slate-900 disabled:opacity-60 rounded-xl px-3.5 py-2.5 text-sm font-bold text-slate-900 outline-none tabular-nums"
                />
              </div>
            </div>

            <div className="flex items-start gap-3 p-3.5 bg-amber-50 border border-amber-300 rounded-xl">
              <AlertCircle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
              <span className="text-[11px] text-amber-800 font-semibold leading-relaxed">
                {t("affiliates.config_warn")}
              </span>
            </div>

            <div className="flex items-center justify-between pt-1">
              <span className="text-[11px] text-slate-400 font-medium">
                {t("affiliates.last_updated")}{" "}
                {new Date(settings.updatedAt).toLocaleString()}
              </span>
              {canEditConfig && (
                <button
                  onClick={saveConfig}
                  disabled={actionLoading}
                  className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 disabled:opacity-50 text-white text-xs font-bold transition-colors"
                >
                  <Save className="w-3.5 h-3.5" />
                  {actionLoading ? t("admin.states.saving") : t("affiliates.btn_save_rate")}
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Modal chay lai dot chi */}
      <AdminModal
        isOpen={showRunModal}
        onClose={() => setShowRunModal(false)}
        maxWidthClass="max-w-md"
        title={
          <div className="flex items-center gap-3 w-full">
            <div className="p-2.5 rounded-xl bg-slate-100 border border-slate-200 text-slate-700 shrink-0">
              <Play className="w-5 h-5" />
            </div>
            <h3 className="text-base font-extrabold text-slate-900 truncate">
              {t("affiliates.modal_settle_title")}
            </h3>
          </div>
        }
      >
        <div className="flex flex-col gap-5">
          <div className="flex items-start gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-xl">
            <AlertCircle className="w-5 h-5 text-slate-400 shrink-0 mt-0.5" />
            <span className="text-[11px] text-slate-600 font-medium leading-relaxed">
              {t("affiliates.modal_settle_subtitle")}
            </span>
          </div>

          {actionError && (
            <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
              <AlertCircle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
              <span className="text-xs text-red-700 font-semibold">{actionError}</span>
            </div>
          )}

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="run-date"
              className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
            >
              {t("affiliates.settle_date")}
            </label>
            <input
              id="run-date"
              type="date"
              value={runDate}
              onChange={(e) => setRunDate(e.target.value)}
              className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-xs font-bold text-slate-900 outline-none cursor-pointer"
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-1">
            <button
              onClick={() => setShowRunModal(false)}
              className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
            >
              {t("admin.states.cancel")}
            </button>
            <button
              onClick={triggerRun}
              disabled={actionLoading}
              className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-bold text-xs transition-colors disabled:opacity-50"
            >
              {actionLoading ? t("affiliates.run_in_progress") : t("affiliates.btn_run")}
            </button>
          </div>
        </div>
      </AdminModal>
    </div>
  );
}
