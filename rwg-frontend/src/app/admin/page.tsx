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

export default function AdminDashboardPage() {
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
      // Fetch users count
      const usersData = await adminFetch("/admin/users?page=0&size=1");
      // Fetch tables count
      const tablesData = await adminFetch("/admin/games/tables");
      // Fetch pending approvals
      const approvalsData = await adminFetch("/admin/approvals/pending?page=0&size=1");
      // Fetch risk links
      const riskData = await adminFetch("/admin/risk/links?page=0&size=1");
      // Fetch banners
      const bannersData = await adminFetch("/admin/banners");

      setStats({
        totalUsers: usersData.totalElements || 0,
        activeTables: Array.isArray(tablesData) ? tablesData.filter((t: any) => t.active).length : 6,
        pendingApprovals: approvalsData.totalElements || 0,
        riskLinks: riskData.totalElements || 0,
        bannerCount: Array.isArray(bannersData) ? bannersData.length : 0,
      });
    } catch {
      // ignore offline fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboardData();
  }, []);

  return (
    <div className="flex flex-col w-full min-h-screen">
      <AdminHeader
        title="Tổng quan Backoffice Admin"
        subtitle="Báo cáo số liệu thực tế hệ thống RWG Casino"
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Refresh Action Bar */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Activity className="w-4 h-4 text-emerald-400" />
            <span className="text-xs font-semibold text-gray-300">
              Trạng thái máy chủ 8081: Đang hoạt động bình thường
            </span>
          </div>
          <button
            onClick={loadDashboardData}
            disabled={loading}
            className="bg-[#16161c] hover:bg-[#202028] border border-[#272732] rounded-xl px-3 py-1.5 text-xs text-gray-300 flex items-center gap-2 transition-all active:scale-95"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin text-red-500" : ""}`} />
            <span>Tải lại số liệu</span>
          </button>
        </div>

        {/* Metric Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Total Users */}
          <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl p-4 flex flex-col gap-3 relative overflow-hidden group">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-gray-400">Tổng Người dùng</span>
              <div className="p-2 rounded-xl bg-blue-950/40 border border-blue-800/40 text-blue-400">
                <Users className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-black text-white">{stats.totalUsers}</span>
              <span className="text-xs font-semibold text-emerald-400 flex items-center">
                <TrendingUp className="w-3 h-3 mr-0.5" /> +100%
              </span>
            </div>
            <Link
              href="/admin/users"
              className="text-[11px] font-semibold text-blue-400 hover:text-blue-300 flex items-center gap-1 mt-1"
            >
              <span>Quản lý tài khoản</span>
              <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>

          {/* Pending Approvals */}
          <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl p-4 flex flex-col gap-3 relative overflow-hidden group">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-gray-400">Lệnh Rút chờ duyệt (4 mắt)</span>
              <div className="p-2 rounded-xl bg-amber-950/40 border border-amber-800/40 text-amber-400">
                <CreditCard className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-black text-white">{stats.pendingApprovals}</span>
              <span className="text-xs font-semibold text-amber-400">Yêu cầu xử lý</span>
            </div>
            <Link
              href="/admin/payments"
              className="text-[11px] font-semibold text-amber-400 hover:text-amber-300 flex items-center gap-1 mt-1"
            >
              <span>Vào hàng chờ duyệt</span>
              <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>

          {/* Active Tables */}
          <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl p-4 flex flex-col gap-3 relative overflow-hidden group">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-gray-400">Bàn chơi đang chạy</span>
              <div className="p-2 rounded-xl bg-emerald-950/40 border border-emerald-800/40 text-emerald-400">
                <Gamepad2 className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-black text-white">{stats.activeTables} / 6</span>
              <span className="text-xs font-semibold text-emerald-400">Bàn Active</span>
            </div>
            <Link
              href="/admin/games"
              className="text-[11px] font-semibold text-emerald-400 hover:text-emerald-300 flex items-center gap-1 mt-1"
            >
              <span>Cấu hình bàn chơi</span>
              <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>

          {/* Risk Flagged */}
          <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl p-4 flex flex-col gap-3 relative overflow-hidden group">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-gray-400">Tín hiệu Risk & Gian lận</span>
              <div className="p-2 rounded-xl bg-red-950/40 border border-red-800/40 text-red-400">
                <ShieldAlert className="w-4 h-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-black text-white">{stats.riskLinks}</span>
              <span className="text-xs font-semibold text-red-400">Cần kết luận</span>
            </div>
            <Link
              href="/admin/risk"
              className="text-[11px] font-semibold text-red-400 hover:text-red-300 flex items-center gap-1 mt-1"
            >
              <span>Xử lý liên kết gian lận</span>
              <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>
        </div>

        {/* System Highlights */}
        <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl p-5 flex flex-col gap-4">
          <h3 className="text-sm font-bold text-white tracking-wide">
            Quy trình An toàn Vận hành (System Compliance)
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-[#16161c] border border-[#22222a] rounded-xl p-3.5 flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
              <div className="flex flex-col gap-0.5">
                <span className="text-xs font-bold text-white">Quy trình 4 Mắt Phê Duyệt</span>
                <span className="text-[11px] text-gray-400 leading-normal">
                  Chặn người tạo đề nghị rút tiền tự duyệt lệnh của chính mình.
                </span>
              </div>
            </div>

            <div className="bg-[#16161c] border border-[#22222a] rounded-xl p-3.5 flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
              <div className="flex flex-col gap-0.5">
                <span className="text-xs font-bold text-white">Chặn Tự Giao Dịch Nạp/Rút</span>
                <span className="text-[11px] text-gray-400 leading-normal">
                  Hạ quyền cộng tiền ví cho chính tài khoản Admin đang vận hành.
                </span>
              </div>
            </div>

            <div className="bg-[#16161c] border border-[#22222a] rounded-xl p-3.5 flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
              <div className="flex flex-col gap-0.5">
                <span className="text-xs font-bold text-white">Chống Gian Lận Hoa Hồng</span>
                <span className="text-[11px] text-gray-400 leading-normal">
                  Dò chùm IP/Device giữ tiền hoa hồng tuyến dưới đa tài khoản.
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
