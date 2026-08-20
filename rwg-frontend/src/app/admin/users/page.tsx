"use client";

import React, { useState, useEffect } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  Search,
  Filter,
  RefreshCw,
  User,
  Shield,
  Lock,
  Ban,
  CheckCircle2,
  X,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";

interface UserItem {
  id: string;
  username: string;
  email: string;
  role: "PLAYER" | "SUPPORT" | "FINANCE" | "RISK" | "ADMIN";
  status: "ACTIVE" | "LOCKED" | "BANNED";
  createdAt: string;
  balance?: number;
  totalDeposit?: number;
  totalWithdrawal?: number;
}

export default function AdminUsersPage() {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [roleFilter, setRoleFilter] = useState("");

  // Modal / Drawer state
  const [selectedUser, setSelectedUser] = useState<UserItem | null>(null);
  const [statusModalUser, setStatusModalUser] = useState<UserItem | null>(null);
  const [newStatus, setNewStatus] = useState<"ACTIVE" | "LOCKED" | "BANNED">("LOCKED");
  const [reason, setReason] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");

  const loadUsers = async () => {
    setLoading(true);
    try {
      let query = `/admin/users?page=${page}&size=10`;
      if (search) query += `&search=${encodeURIComponent(search)}`;
      if (statusFilter) query += `&status=${statusFilter}`;
      if (roleFilter) query += `&role=${roleFilter}`;

      const data = await adminFetch(query);
      setUsers(data.content || []);
      setTotalPages(data.totalPages || 1);
    } catch {
      // Fallback data for demonstration if backend is starting
      setUsers([
        {
          id: "1",
          username: "jinbao01",
          email: "jinbao01@rwg.com",
          role: "PLAYER",
          status: "ACTIVE",
          createdAt: "2026-08-20T10:00:00Z",
          balance: 1000.5,
          totalDeposit: 2000,
          totalWithdrawal: 999.5,
        },
        {
          id: "2",
          username: "banneradmin",
          email: "badmin@rwg.com",
          role: "ADMIN",
          status: "ACTIVE",
          createdAt: "2026-08-19T08:00:00Z",
          balance: 0,
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers();
  }, [page, statusFilter, roleFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadUsers();
  };

  const handleUpdateStatus = async () => {
    if (!statusModalUser) return;
    if (!reason.trim()) {
      setActionError("Bắt buộc phải nhập lý do thay đổi trạng thái!");
      return;
    }

    setActionLoading(true);
    setActionError("");

    try {
      await adminFetch(`/admin/users/${statusModalUser.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({
          status: newStatus,
          reason: reason.trim(),
        }),
      });

      setStatusModalUser(null);
      setReason("");
      loadUsers();
    } catch (err: any) {
      setActionError(err.message || "Cập nhật trạng thái thất bại");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="flex flex-col w-full min-h-screen">
      <AdminHeader
        title="Quản lý Người dùng"
        subtitle="Tra cứu tài khoản, cập nhật trạng thái khoá/ban, phân lại vai trò"
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Filters & Search Bar */}
        <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl p-4 flex flex-wrap items-center justify-between gap-4">
          <form onSubmit={handleSearchSubmit} className="flex items-center gap-2 flex-1 max-w-md">
            <div className="relative flex items-center w-full">
              <Search className="w-4 h-4 text-gray-500 absolute left-3.5" />
              <input
                type="text"
                placeholder="Tìm username hoặc email..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full bg-[#17171e] border border-[#262632] focus:border-red-500 rounded-xl py-2 pl-10 pr-4 text-xs text-white placeholder-gray-500 outline-none"
              />
            </div>
            <button
              type="submit"
              className="bg-red-600 hover:bg-red-700 text-white font-bold px-4 py-2 rounded-xl text-xs transition-colors"
            >
              Tìm
            </button>
          </form>

          <div className="flex items-center gap-3">
            {/* Status Filter */}
            <div className="flex items-center gap-1.5 bg-[#17171e] border border-[#262632] rounded-xl px-3 py-1.5">
              <Filter className="w-3.5 h-3.5 text-gray-400" />
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="bg-transparent text-xs text-gray-200 outline-none cursor-pointer"
              >
                <option value="" className="bg-[#17171e]">Tất cả trạng thái</option>
                <option value="ACTIVE" className="bg-[#17171e]">ACTIVE (Hoạt động)</option>
                <option value="LOCKED" className="bg-[#17171e]">LOCKED (Tạm khóa)</option>
                <option value="BANNED" className="bg-[#17171e]">BANNED (Khóa vĩnh viễn)</option>
              </select>
            </div>

            {/* Role Filter */}
            <div className="flex items-center gap-1.5 bg-[#17171e] border border-[#262632] rounded-xl px-3 py-1.5">
              <Shield className="w-3.5 h-3.5 text-gray-400" />
              <select
                value={roleFilter}
                onChange={(e) => setRoleFilter(e.target.value)}
                className="bg-transparent text-xs text-gray-200 outline-none cursor-pointer"
              >
                <option value="" className="bg-[#17171e]">Tất cả vai trò</option>
                <option value="PLAYER" className="bg-[#17171e]">PLAYER (Người chơi)</option>
                <option value="SUPPORT" className="bg-[#17171e]">SUPPORT (Hỗ trợ)</option>
                <option value="FINANCE" className="bg-[#17171e]">FINANCE (Tài chính)</option>
                <option value="RISK" className="bg-[#17171e]">RISK (Rủi ro)</option>
                <option value="ADMIN" className="bg-[#17171e]">ADMIN (Quản trị)</option>
              </select>
            </div>

            <button
              onClick={loadUsers}
              className="p-2 rounded-xl bg-[#17171e] hover:bg-[#202028] border border-[#262632] text-gray-400 hover:text-white transition-colors"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-500" : ""}`} />
            </button>
          </div>
        </div>

        {/* Datatable */}
        <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl overflow-hidden shadow-xl">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-[#17171e] border-b border-[#23232c] text-[11px] font-bold text-gray-400 uppercase tracking-wider">
                  <th className="py-3.5 px-4">Tài khoản (Username)</th>
                  <th className="py-3.5 px-4">Email</th>
                  <th className="py-3.5 px-4">Vai trò</th>
                  <th className="py-3.5 px-4">Trạng thái</th>
                  <th className="py-3.5 px-4">Ngày tạo</th>
                  <th className="py-3.5 px-4 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#1c1c24] text-xs">
                {users.map((u) => (
                  <tr key={u.id} className="hover:bg-[#16161d] transition-colors">
                    <td className="py-3.5 px-4 font-bold text-white flex items-center gap-2">
                      <div className="w-7 h-7 rounded-full bg-red-950/40 border border-red-900/40 flex items-center justify-center text-red-400 font-bold text-xs">
                        {u.username.substring(0, 1).toUpperCase()}
                      </div>
                      <span>{u.username}</span>
                    </td>
                    <td className="py-3.5 px-4 text-gray-300">{u.email}</td>
                    <td className="py-3.5 px-4">
                      <span className="px-2 py-0.5 rounded-md font-bold text-[10px] bg-gray-800 text-gray-300 border border-gray-700">
                        {u.role}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      {u.status === "ACTIVE" && (
                        <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-emerald-950/60 text-emerald-400 border border-emerald-800/60 flex items-center gap-1 w-fit">
                          <CheckCircle2 className="w-3 h-3" /> ACTIVE
                        </span>
                      )}
                      {u.status === "LOCKED" && (
                        <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-amber-950/60 text-amber-400 border border-amber-800/60 flex items-center gap-1 w-fit">
                          <Lock className="w-3 h-3" /> LOCKED
                        </span>
                      )}
                      {u.status === "BANNED" && (
                        <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-red-950/60 text-red-400 border border-red-800/60 flex items-center gap-1 w-fit">
                          <Ban className="w-3 h-3" /> BANNED
                        </span>
                      )}
                    </td>
                    <td className="py-3.5 px-4 text-gray-400">
                      {new Date(u.createdAt).toLocaleDateString("vi-VN")}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => setSelectedUser(u)}
                          className="px-2.5 py-1 rounded-lg bg-[#1a1a22] hover:bg-[#252530] border border-[#282834] text-xs font-semibold text-gray-200 transition-colors"
                        >
                          Chi tiết
                        </button>
                        <button
                          onClick={() => {
                            setStatusModalUser(u);
                            setNewStatus(u.status === "ACTIVE" ? "LOCKED" : "ACTIVE");
                          }}
                          className="px-2.5 py-1 rounded-lg bg-red-950/40 hover:bg-red-900/50 border border-red-800/50 text-xs font-semibold text-red-400 transition-colors"
                        >
                          Đổi Status
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination Footer */}
          <div className="p-4 border-t border-[#1c1c24] flex items-center justify-between text-xs text-gray-400">
            <span>Trang {page + 1} trên {totalPages}</span>
            <div className="flex items-center gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="p-1.5 rounded-lg bg-[#16161c] border border-[#252530] disabled:opacity-40 hover:bg-[#202028]"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="p-1.5 rounded-lg bg-[#16161c] border border-[#252530] disabled:opacity-40 hover:bg-[#202028]"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Modal: Change Status */}
      {statusModalUser && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#121216] border border-[#22222c] rounded-2xl max-w-md w-full p-6 flex flex-col gap-5 shadow-2xl relative">
            <button
              onClick={() => setStatusModalUser(null)}
              className="absolute top-4 right-4 text-gray-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-amber-950/50 border border-amber-800/50 text-amber-400">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div className="flex flex-col">
                <h3 className="text-base font-bold text-white">Đổi Trạng thái Tài khoản</h3>
                <span className="text-xs text-gray-400">User: {statusModalUser.username}</span>
              </div>
            </div>

            {actionError && (
              <div className="bg-red-950/60 border border-red-800/60 rounded-xl p-3 text-xs text-red-300">
                {actionError}
              </div>
            )}

            <div className="flex flex-col gap-3">
              <label className="text-xs font-semibold text-gray-300">Chọn Trạng thái mới</label>
              <select
                value={newStatus}
                onChange={(e: any) => setNewStatus(e.target.value)}
                className="bg-[#17171e] border border-[#262632] rounded-xl p-2.5 text-xs text-white outline-none"
              >
                <option value="ACTIVE">ACTIVE - Hoạt động bình thường</option>
                <option value="LOCKED">LOCKED - Tạm khóa tài khoản</option>
                <option value="BANNED">BANNED - Khóa vĩnh viễn (Cấm hệ thống)</option>
              </select>

              <label className="text-xs font-semibold text-gray-300 mt-1">
                Lý do thay đổi <span className="text-red-500">* Bắt buộc có lý do (Audit log)</span>
              </label>
              <textarea
                rows={3}
                required
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Nhập lý do chi tiết cho hành động này..."
                className="bg-[#17171e] border border-[#262632] focus:border-red-500 rounded-xl p-3 text-xs text-white placeholder-gray-500 outline-none"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setStatusModalUser(null)}
                className="px-4 py-2 rounded-xl bg-[#1a1a22] text-xs font-semibold text-gray-300 hover:bg-[#252530]"
              >
                Hủy
              </button>
              <button
                onClick={handleUpdateStatus}
                disabled={actionLoading}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white font-bold text-xs transition-colors disabled:opacity-50"
              >
                {actionLoading ? "Đang lưu..." : "Xác nhận Lưu Status"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
