"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  Users,
  CreditCard,
  Gamepad2,
  ShieldAlert,
  ArrowUpRight,
  RefreshCw,
  TrendingUp,
  Activity,
  CheckCircle2,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { useTranslation } from "@/context/LanguageContext";

export default function AdminDashboardPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalUsers: 0,
    activeTables: 6,
    pendingApprovals: 0,
    riskLinks: 0,
    bannerCount: 0,
  });

  const loadDashboardData = async () => {
    setLoading(true);
    try {
      const usersData = await adminFetch("/admin/users?page=0&size=1");
      const tablesData = await adminFetch("/admin/games/tables");
      const approvalsData = await adminFetch("/admin/approvals/pending?page=0&size=1");
      const riskData = await adminFetch("/admin/risk/links?page=0&size=1");
      const bannersData = await adminFetch("/admin/banners");

      setStats({
        totalUsers: usersData.totalElements || 0,
        activeTables: Array.isArray(tablesData) ? tablesData.filter((t: any) => t.active).length : 6,
        pendingApprovals: approvalsData.totalElements || 0,
        riskLinks: riskData.totalElements || 0,
        bannerCount: Array.isArray(bannersData) ? bannersData.length : 0,
      });
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboardData();
  }, []);

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title={t("admin.dashboard.title")}
        subtitle={t("admin.dashboard.subtitle")}
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Refresh Action Bar */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Activity className="w-4 h-4 text-emerald-600" />
            <span className="text-xs font-semibold text-slate-700">
              {t("admin.dashboard.server_status")}
            </span>
          </div>
          <button
            onClick={loadDashboardData}
            disabled={loading}
            className="bg-white hover:bg-slate-100 border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 font-semibold flex items-center gap-2 transition-all shadow-xs active:scale-95"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin text-red-600" : ""}`} />
            <span>{t("admin.dashboard.reload")}</span>
          </button>
        </div>

        {/* Metric Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Total Users */}
          <div className="bg-white border border-slate-200 rounded-2xl p-5 flex flex-col gap-3 relative overflow-hidden shadow-xs hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wide">{t("admin.dashboard.total_users")}</span>
              <div className="p-2 rounded-xl bg-blue-50 border border-blue-200 text-blue-600">
                <Users className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-black text-slate-900">{stats.totalUsers}</span>
              <span className="text-xs font-bold text-emerald-600 flex items-center">
                <TrendingUp className="w-3 h-3 mr-0.5" /> +100%
              </span>
            </div>
            <Link
              href="/admin/users"
              className="text-[11px] font-bold text-blue-600 hover:text-blue-700 flex items-center gap-1 mt-1"
            >
              <span>{t("admin.dashboard.action_users")}</span>
              <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>

          {/* Pending Approvals */}
          <div className="bg-white border border-slate-200 rounded-2xl p-5 flex flex-col gap-3 relative overflow-hidden shadow-xs hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wide">{t("admin.dashboard.pending_approvals")}</span>
              <div className="p-2 rounded-xl bg-amber-50 border border-amber-200 text-amber-600">
                <CreditCard className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-black text-slate-900">{stats.pendingApprovals}</span>
              <span className="text-xs font-bold text-amber-600">Active</span>
            </div>
            <Link
              href="/admin/payments"
              className="text-[11px] font-bold text-amber-600 hover:text-amber-700 flex items-center gap-1 mt-1"
            >
              <span>{t("admin.dashboard.action_approvals")}</span>
              <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>

          {/* Active Tables */}
          <div className="bg-white border border-slate-200 rounded-2xl p-5 flex flex-col gap-3 relative overflow-hidden shadow-xs hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wide">{t("admin.dashboard.active_tables")}</span>
              <div className="p-2 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-600">
                <Gamepad2 className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-black text-slate-900">{stats.activeTables} / 6</span>
              <span className="text-xs font-bold text-emerald-600">Active</span>
            </div>
            <Link
              href="/admin/games"
              className="text-[11px] font-bold text-emerald-600 hover:text-emerald-700 flex items-center gap-1 mt-1"
            >
              <span>{t("admin.dashboard.action_tables")}</span>
              <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>

          {/* Risk Flagged */}
          <div className="bg-white border border-slate-200 rounded-2xl p-5 flex flex-col gap-3 relative overflow-hidden shadow-xs hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wide">{t("admin.dashboard.risk_signals")}</span>
              <div className="p-2 rounded-xl bg-red-50 border border-red-200 text-red-600">
                <ShieldAlert className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-black text-slate-900">{stats.riskLinks}</span>
              <span className="text-xs font-bold text-red-600">Pending</span>
            </div>
            <Link
              href="/admin/risk"
              className="text-[11px] font-bold text-red-600 hover:text-red-700 flex items-center gap-1 mt-1"
            >
              <span>{t("admin.dashboard.action_risk")}</span>
              <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>
        </div>

        {/* System Highlights */}
        <div className="bg-white border border-slate-200 rounded-2xl p-5 flex flex-col gap-4 shadow-xs">
          <h3 className="text-sm font-extrabold text-slate-900 tracking-wide">
            {t("admin.dashboard.compliance_title")}
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
              <div className="flex flex-col gap-0.5">
                <span className="text-xs font-bold text-slate-900">{t("admin.dashboard.compliance_4eye_title")}</span>
                <span className="text-[11px] text-slate-600 leading-normal">
                  {t("admin.dashboard.compliance_4eye_desc")}
                </span>
              </div>
            </div>

            <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
              <div className="flex flex-col gap-0.5">
                <span className="text-xs font-bold text-slate-900">{t("admin.dashboard.compliance_self_title")}</span>
                <span className="text-[11px] text-slate-600 leading-normal">
                  {t("admin.dashboard.compliance_self_desc")}
                </span>
              </div>
            </div>

            <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
              <div className="flex flex-col gap-0.5">
                <span className="text-xs font-bold text-slate-900">{t("admin.dashboard.compliance_fraud_title")}</span>
                <span className="text-[11px] text-slate-600 leading-normal">
                  {t("admin.dashboard.compliance_fraud_desc")}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
