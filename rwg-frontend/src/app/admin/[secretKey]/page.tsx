"use client";

import React, { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  Users,
  CreditCard,
  Gamepad2,
  ShieldAlert,
  ArrowUpRight,
  RefreshCw,
  Activity,
  CheckCircle2,
  ArrowDownToLine,
  ArrowUpFromLine,
  Coins,
  Network,
  Lock,
  Ban,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { formatMoney } from "@/lib/money";
import { useTranslation } from "@/context/LanguageContext";
import { ADMIN_URL_PREFIX } from "@/lib/constants";
import { AdminErrorState } from "@/components/admin/AdminStates";

/** Số liệu tổng hợp — khớp DashboardSummaryResponse. */
interface Summary {
  from: string;
  to: string;
  totalDeposits: string;
  totalWithdrawals: string;
  totalTurnover: string;
  totalCommissionPaid: string;
  newUsers: number;
  pendingWithdrawals: number;
  totalUsers: number;
  lockedUsers: number;
  bannedUsers: number;
}

interface GameTable {
  id: string;
  status: "ACTIVE" | "DISABLED";
}

/**
 * Phản hồi phân trang mà ta chỉ cần con số tổng.
 *
 * Gọi với `size=1` để backend không phải dựng cả trang dữ liệu chỉ để ta đếm.
 */
interface CountOnlyPage {
  totalElements: number;
}

/** Toàn bộ số liệu của trang tổng quan, gom một chỗ để đặt state một lượt. */
interface DashboardData {
  summary: Summary;
  riskLinks: number;
  tableStats: { active: number; total: number };
}

export default function AdminDashboardPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [summary, setSummary] = useState<Summary | null>(null);
  const [riskLinks, setRiskLinks] = useState(0);
  const [tableStats, setTableStats] = useState({ active: 0, total: 0 });

  /**
   * Lấy mọi số liệu của trang tổng quan.
   *
   * Trả dữ liệu về thay vì tự đặt state: `setState` gọi đồng bộ trong thân effect gây
   * chuỗi render liên tiếp và luật lint của dự án chặn.
   *
   * Trả `null` khi lỗi — KHÔNG trả số 0: một trang tổng quan toàn số 0 sẽ bị hiểu là hệ
   * thống không có giao dịch nào, trong khi thực ra là không tải được dữ liệu.
   */
  const fetchDashboard = useCallback(async (): Promise<DashboardData | null> => {
    try {
      // Goi song song: cac so lieu doc lap nhau, khong can tuan tu.
      // /admin/dashboard/summary tra san toan bo so lieu tai chinh, thay vi phai
      // dem tay bang totalElements cua tung danh sach nhu truoc day.
      const [summaryData, risk, tables] = await Promise.all([
        adminFetch<Summary>("/admin/dashboard/summary"),
        adminFetch<CountOnlyPage>("/admin/risk/links?status=SUSPECTED&page=0&size=1"),
        adminFetch<GameTable[]>("/admin/games/tables"),
      ]);

      const list = Array.isArray(tables) ? tables : [];
      setLoadError("");
      return {
        summary: summaryData,
        riskLinks: risk.totalElements || 0,
        tableStats: {
          active: list.filter((x) => x.status === "ACTIVE").length,
          total: list.length,
        },
      };
    } catch (err) {
      setLoadError((err as Error).message);
      return null;
    }
  }, []);

  /** Đưa kết quả vào state. Dùng chung cho lần tải đầu và nút Tải lại. */
  const applyResult = useCallback((data: DashboardData | null) => {
    setSummary(data?.summary ?? null);
    setRiskLinks(data?.riskLinks ?? 0);
    setTableStats(data?.tableStats ?? { active: 0, total: 0 });
  }, []);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể rời trang trước khi request xong, và ghi state vào
    // component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      // Đặt state bên trong hàm async, không ở thân effect — xem lý do ở trên.
      setLoading(true);
      const data = await fetchDashboard();
      if (cancelled) return;
      applyResult(data);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchDashboard, applyResult]);

  /** Tải lại từ nút bấm. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    applyResult(await fetchDashboard());
    setLoading(false);
  }, [fetchDashboard, applyResult]);

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title={t("admin.dashboard.title")}
        subtitle={t("admin.dashboard.subtitle")}
      />

      <div className="p-6 flex flex-col gap-6">
        <div className="flex items-center justify-between">
          {/* KHONG khang dinh trang thai server o day: badge trong AdminHeader da
              kiem tra that qua /admin/health. Mot dong chu xanh co dinh se van
              xanh khi backend sap, va no mau thuan voi badge do ben canh. */}
          <div className="flex items-center gap-2">
            <Activity className="w-4 h-4 text-slate-400" />
            <span className="text-xs font-semibold text-slate-700">
              {t("admin.dashboard.data_window")}
            </span>
            {summary ? (
              <span className="text-xs text-slate-500 font-bold tabular-nums ml-1">
                {summary.from} → {summary.to}
              </span>
            ) : (
              <span className="text-xs text-slate-400 font-medium ml-1">—</span>
            )}
          </div>
          <button
            onClick={reload}
            disabled={loading}
            className="bg-white hover:bg-slate-100 border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 font-semibold flex items-center gap-2 transition-all shadow-xs active:scale-95"
          >
            <RefreshCw
              className={`w-3.5 h-3.5 ${loading ? "animate-spin text-red-600" : ""}`}
            />
            <span>{t("admin.dashboard.reload")}</span>
          </button>
        </div>

        {loadError ? (
          <AdminErrorState message={loadError} onRetry={reload} />
        ) : (
          <>
            {/* Tai chinh 30 ngay */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              <MoneyCard
                label={t("admin.dashboard.total_deposits")}
                value={summary?.totalDeposits}
                icon={<ArrowDownToLine className="w-4 h-4" />}
                tone="emerald"
              />
              <MoneyCard
                label={t("admin.dashboard.total_withdrawals")}
                value={summary?.totalWithdrawals}
                icon={<ArrowUpFromLine className="w-4 h-4" />}
                tone="red"
              />
              <MoneyCard
                label={t("admin.dashboard.total_turnover")}
                value={summary?.totalTurnover}
                icon={<Coins className="w-4 h-4" />}
                tone="blue"
              />
              <MoneyCard
                label={t("admin.dashboard.total_commission")}
                value={summary?.totalCommissionPaid}
                icon={<Network className="w-4 h-4" />}
                tone="amber"
              />
            </div>

            {/* Hang doi can xu ly */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              <StatCard
                label={t("admin.dashboard.total_users")}
                value={summary?.totalUsers ?? 0}
                hint={t("admin.dashboard.hint_new_users", {
                  count: summary?.newUsers ?? 0,
                })}
                hintTone="emerald"
                icon={<Users className="w-4 h-4" />}
                tone="blue"
                href={`${ADMIN_URL_PREFIX}/users`}
                action={t("admin.dashboard.action_users")}
              />
              <StatCard
                label={t("admin.dashboard.pending_withdrawals")}
                value={summary?.pendingWithdrawals ?? 0}
                hint={t("admin.dashboard.hint_withdrawals_pending")}
                hintTone="amber"
                icon={<CreditCard className="w-4 h-4" />}
                tone="amber"
                href={`${ADMIN_URL_PREFIX}/payments`}
                action={t("admin.dashboard.action_approvals")}
              />
              <StatCard
                label={t("admin.dashboard.active_tables")}
                value={`${tableStats.active} / ${tableStats.total}`}
                hint={t("admin.dashboard.hint_tables_open")}
                hintTone="emerald"
                icon={<Gamepad2 className="w-4 h-4" />}
                tone="emerald"
                href={`${ADMIN_URL_PREFIX}/games`}
                action={t("admin.dashboard.action_tables")}
              />
              <StatCard
                label={t("admin.dashboard.risk_signals")}
                value={riskLinks}
                hint={t("admin.dashboard.hint_risk_pending")}
                hintTone="red"
                icon={<ShieldAlert className="w-4 h-4" />}
                tone="red"
                href={`${ADMIN_URL_PREFIX}/risk`}
                action={t("admin.dashboard.action_risk")}
              />
            </div>

            {/* Tai khoan bi han che — chi hien khi thuc su co */}
            {summary && (summary.lockedUsers > 0 || summary.bannedUsers > 0) && (
              <div className="flex flex-wrap items-center gap-3">
                {summary.lockedUsers > 0 && (
                  <div className="flex items-center gap-2 px-4 py-2.5 bg-white border border-amber-200 rounded-xl">
                    <Lock className="w-4 h-4 text-amber-600" />
                    <span className="text-xs font-bold text-slate-900">
                      {t("admin.dashboard.locked_accounts", {
                        count: summary.lockedUsers,
                      })}
                    </span>
                  </div>
                )}
                {summary.bannedUsers > 0 && (
                  <div className="flex items-center gap-2 px-4 py-2.5 bg-white border border-red-200 rounded-xl">
                    <Ban className="w-4 h-4 text-red-600" />
                    <span className="text-xs font-bold text-slate-900">
                      {t("admin.dashboard.banned_accounts", {
                        count: summary.bannedUsers,
                      })}
                    </span>
                  </div>
                )}
              </div>
            )}
          </>
        )}

        <div className="bg-white border border-slate-200 rounded-2xl p-5 flex flex-col gap-4 shadow-xs">
          <h3 className="text-sm font-extrabold text-slate-900 tracking-wide">
            {t("admin.dashboard.compliance_title")}
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {[
              [
                t("admin.dashboard.compliance_self_title"),
                t("admin.dashboard.compliance_self_desc"),
              ],
              [
                t("admin.dashboard.compliance_fraud_title"),
                t("admin.dashboard.compliance_fraud_desc"),
              ],
            ].map(([title, desc]) => (
              <div
                key={title}
                className="bg-slate-50 border border-slate-200 rounded-xl p-4 flex items-start gap-3"
              >
                <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
                <div className="flex flex-col gap-0.5">
                  <span className="text-xs font-bold text-slate-900">{title}</span>
                  <span className="text-[11px] text-slate-600 leading-normal">
                    {desc}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

const TONES: Record<string, string> = {
  emerald: "bg-emerald-50 border-emerald-200 text-emerald-600",
  red: "bg-red-50 border-red-200 text-red-600",
  blue: "bg-blue-50 border-blue-200 text-blue-600",
  amber: "bg-amber-50 border-amber-200 text-amber-600",
};

const HINT_TONES: Record<string, string> = {
  emerald: "text-emerald-600",
  red: "text-red-600",
  amber: "text-amber-600",
};

/** Thẻ số tiền. Hiện dấu gạch khi chưa có dữ liệu, không hiện 0 để tránh nhầm. */
const MoneyCard: React.FC<{
  label: string;
  value?: string;
  icon: React.ReactNode;
  tone: string;
}> = ({ label, value, icon, tone }) => {
  const { t } = useTranslation();

  return (
    <div className="bg-white border border-slate-200 rounded-2xl p-5 flex flex-col gap-3 shadow-xs">
      <div className="flex items-center justify-between">
        <span className="text-xs font-bold text-slate-500 uppercase tracking-wide">
          {label}
        </span>
        <div className={`p-2 rounded-xl border ${TONES[tone]}`}>{icon}</div>
      </div>
      <span className="text-2xl font-black text-slate-900 tabular-nums">
        {value === undefined ? "—" : formatMoney(value)}
      </span>
      <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide">
        {t("admin.dashboard.last_30_days")}
      </span>
    </div>
  );
};

const StatCard: React.FC<{
  label: string;
  value: number | string;
  hint: string;
  hintTone: string;
  icon: React.ReactNode;
  tone: string;
  href: string;
  action: string;
}> = ({ label, value, hint, hintTone, icon, tone, href, action }) => (
  <div className="bg-white border border-slate-200 rounded-2xl p-5 flex flex-col gap-3 shadow-xs hover:shadow-md transition-shadow">
    <div className="flex items-center justify-between">
      <span className="text-xs font-bold text-slate-500 uppercase tracking-wide">
        {label}
      </span>
      <div className={`p-2 rounded-xl border ${TONES[tone]}`}>{icon}</div>
    </div>
    <div className="flex items-baseline gap-2">
      <span className="text-3xl font-black text-slate-900 tabular-nums">{value}</span>
      <span className={`text-xs font-bold ${HINT_TONES[hintTone] || "text-slate-500"}`}>
        {hint}
      </span>
    </div>
    <Link
      href={href}
      className="text-[11px] font-bold text-slate-600 hover:text-slate-900 flex items-center gap-1 mt-1"
    >
      <span>{action}</span>
      <ArrowUpRight className="w-3 h-3" />
    </Link>
  </div>
);
