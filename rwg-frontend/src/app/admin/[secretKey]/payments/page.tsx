"use client";

import React, { useState, useEffect } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  CheckCircle2,
  XCircle,
  Clock,
  RefreshCw,
  X,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  ShieldCheck,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";

interface ApprovalItem {
  id: string;
  withdrawalId: string;
  userUsername?: string;
  amount: number;
  currency: string;
  requesterUsername: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  createdAt: string;
}

export default function AdminPaymentsPage() {
  const [approvals, setApprovals] = useState<ApprovalItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const [selectedApproval, setSelectedApproval] = useState<ApprovalItem | null>(null);
  const [actionStatus, setActionStatus] = useState<"APPROVED" | "REJECTED">("APPROVED");
  const [reason, setReason] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");

  const loadApprovals = async () => {
    setLoading(true);
    try {
      const data = await adminFetch(`/admin/approvals/pending?page=${page}&size=10`);
      setApprovals(data.content || []);
      setTotalPages(data.totalPages || 1);
    } catch {
      setApprovals([
        {
          id: "app-001",
          withdrawalId: "w-101",
          userUsername: "jinbao01",
          amount: 500.0,
          currency: "USD",
          requesterUsername: "finance_agent_1",
          status: "PENDING",
          createdAt: "2026-08-20T14:30:00Z",
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadApprovals();
  }, [page]);

  const handleExecuteApproval = async () => {
    if (!selectedApproval) return;
    if (!reason.trim()) {
      setActionError("Bắt buộc nhập lý do phê duyệt/từ chối!");
      return;
    }

    setActionLoading(true);
    setActionError("");

    try {
      await adminFetch(`/admin/approvals/${selectedApproval.id}/execute`, {
        method: "POST",
        body: JSON.stringify({
          status: actionStatus,
          reason: reason.trim(),
        }),
      });

      setSelectedApproval(null);
      setReason("");
      loadApprovals();
    } catch (err: any) {
      setActionError(err.message || "Phê duyệt thất bại (Lưu ý: Không được tự duyệt đề nghị của chính mình)");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title="Duyệt Nạp & Rút tiền 4 Mắt"
        subtitle="Hàng chờ phê duyệt lệnh rút tiền, tuân thủ nguyên tắc người đề xuất không được tự duyệt"
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Compliance Warning */}
        <div className="bg-amber-50 border border-amber-200 rounded-2xl p-4 flex items-center gap-3">
          <ShieldCheck className="w-5 h-5 text-amber-600 shrink-0" />
          <span className="text-xs text-amber-900 font-semibold">
            Chính sách An toàn Tài chính: Hệ thống kiểm soát 4 mắt (4-Eye Compliance). Tài khoản đề xuất lệnh rút không thể tự phê duyệt chính lệnh đó.
          </span>
        </div>

        {/* Action Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Clock className="w-4 h-4 text-amber-600" />
            <span className="text-sm font-extrabold text-slate-900">
              Hàng đợi Đang Chờ Phê Duyệt ({approvals.length})
            </span>
          </div>
          <button
            onClick={loadApprovals}
            className="p-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 transition-colors shadow-xs"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-600" : ""}`} />
          </button>
        </div>

        {/* Datatable */}
        <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-xs">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-100 border-b border-slate-200 text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                  <th className="py-3.5 px-4">Mã Duyệt (Approval ID)</th>
                  <th className="py-3.5 px-4">Người chơi</th>
                  <th className="py-3.5 px-4">Số tiền Rút</th>
                  <th className="py-3.5 px-4">Người Đề xuất Lệnh</th>
                  <th className="py-3.5 px-4">Trạng thái</th>
                  <th className="py-3.5 px-4">Thời gian Tạo</th>
                  <th className="py-3.5 px-4 text-right">Thao tác 4 Mắt</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {approvals.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-8 text-center text-slate-500 font-medium">
                      Không có lệnh rút tiền nào đang chờ duyệt.
                    </td>
                  </tr>
                ) : (
                  approvals.map((app) => (
                    <tr key={app.id} className="hover:bg-slate-50 transition-colors">
                      <td className="py-3.5 px-4 font-mono font-bold text-slate-900">{app.id}</td>
                      <td className="py-3.5 px-4 font-bold text-red-700">{app.userUsername || "N/A"}</td>
                      <td className="py-3.5 px-4 font-black text-slate-900">
                        ${app.amount.toLocaleString("en-US", { minimumFractionDigits: 2 })} {app.currency}
                      </td>
                      <td className="py-3.5 px-4 text-slate-700 font-medium">{app.requesterUsername}</td>
                      <td className="py-3.5 px-4">
                        <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-amber-50 text-amber-700 border border-amber-200 flex items-center gap-1 w-fit">
                          <Clock className="w-3 h-3" /> PENDING
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-slate-500">
                        {new Date(app.createdAt).toLocaleString("vi-VN")}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => {
                              setSelectedApproval(app);
                              setActionStatus("APPROVED");
                            }}
                            className="px-3 py-1.5 rounded-xl bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 text-emerald-700 font-bold text-xs flex items-center gap-1 transition-colors"
                          >
                            <CheckCircle2 className="w-3.5 h-3.5" /> Duyệt
                          </button>
                          <button
                            onClick={() => {
                              setSelectedApproval(app);
                              setActionStatus("REJECTED");
                            }}
                            className="px-3 py-1.5 rounded-xl bg-red-50 hover:bg-red-100 border border-red-200 text-red-700 font-bold text-xs flex items-center gap-1 transition-colors"
                          >
                            <XCircle className="w-3.5 h-3.5" /> Từ chối
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="p-4 border-t border-slate-200 flex items-center justify-between text-xs text-slate-500 bg-white">
            <span>Trang {page + 1} trên {totalPages}</span>
            <div className="flex items-center gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="p-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200"
              >
                <ChevronLeft className="w-4 h-4 text-slate-700" />
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="p-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200"
              >
                <ChevronRight className="w-4 h-4 text-slate-700" />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Modal Execute Approval */}
      {selectedApproval && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white border border-slate-200 rounded-2xl max-w-md w-full p-6 flex flex-col gap-5 shadow-2xl relative">
            <button
              onClick={() => setSelectedApproval(null)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-700"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3">
              <div
                className={`p-2.5 rounded-xl border ${
                  actionStatus === "APPROVED"
                    ? "bg-emerald-50 border-emerald-200 text-emerald-600"
                    : "bg-red-50 border-red-200 text-red-600"
                }`}
              >
                {actionStatus === "APPROVED" ? (
                  <CheckCircle2 className="w-5 h-5" />
                ) : (
                  <XCircle className="w-5 h-5" />
                )}
              </div>
              <div className="flex flex-col">
                <h3 className="text-base font-extrabold text-slate-900">
                  {actionStatus === "APPROVED" ? "Xác nhận Duyệt Lệnh Rút" : "Xác nhận Từ Chối Lệnh Rút"}
                </h3>
                <span className="text-xs text-slate-500 font-medium">Approval ID: {selectedApproval.id}</span>
              </div>
            </div>

            {actionError && (
              <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700 font-semibold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
                <span>{actionError}</span>
              </div>
            )}

            <div className="flex flex-col gap-3">
              <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 flex flex-col gap-1 text-xs">
                <div className="flex justify-between">
                  <span className="text-slate-500 font-medium">Người rút:</span>
                  <span className="font-bold text-slate-900">{selectedApproval.userUsername}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500 font-medium">Số tiền:</span>
                  <span className="font-black text-emerald-600">${selectedApproval.amount} {selectedApproval.currency}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500 font-medium">Người đề xuất:</span>
                  <span className="font-bold text-slate-700">{selectedApproval.requesterUsername}</span>
                </div>
              </div>

              <label className="text-xs font-bold text-slate-700">Lý do * Bắt buộc (Audit log)</label>
              <textarea
                rows={3}
                required
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Nhập bằng chứng/lý do quyết định phê duyệt này..."
                className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-3 text-xs text-slate-900 placeholder-slate-400 outline-none"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setSelectedApproval(null)}
                className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
              >
                Hủy
              </button>
              <button
                onClick={handleExecuteApproval}
                disabled={actionLoading}
                className={`px-4 py-2 rounded-xl text-white font-bold text-xs transition-colors disabled:opacity-50 ${
                  actionStatus === "APPROVED"
                    ? "bg-emerald-600 hover:bg-emerald-700"
                    : "bg-red-600 hover:bg-red-700"
                }`}
              >
                {actionLoading ? "Đang xử lý..." : "Xác nhận Thực hiện"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
