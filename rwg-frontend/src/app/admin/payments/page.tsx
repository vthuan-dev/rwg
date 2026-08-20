"use client";

import React, { useState, useEffect } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  CreditCard,
  CheckCircle2,
  XCircle,
  Clock,
  RefreshCw,
  X,
  AlertOctagon,
  DollarSign,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";

interface ApprovalRequest {
  id: string;
  type: string;
  targetId: string;
  amount: number;
  requestedBy: string;
  reason: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  createdAt: string;
}

export default function AdminPaymentsPage() {
  const [activeTab, setActiveTab] = useState<"pending" | "deposits" | "withdrawals">("pending");
  const [pendingList, setPendingList] = useState<ApprovalRequest[]>([]);
  const [loading, setLoading] = useState(true);

  // Decision Modal
  const [decideItem, setDecideItem] = useState<ApprovalRequest | null>(null);
  const [decision, setDecision] = useState<"APPROVED" | "REJECTED">("APPROVED");
  const [reason, setReason] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");

  const loadPendingApprovals = async () => {
    setLoading(true);
    try {
      const data = await adminFetch("/admin/approvals/pending?page=0&size=20");
      setPendingList(data.content || []);
    } catch {
      // Demo fallback if backend starting
      setPendingList([
        {
          id: "app-101",
          type: "WITHDRAWAL",
          targetId: "w-88",
          amount: 500.0,
          requestedBy: "support_admin",
          reason: "Rút tiền về ngân hàng mặc định",
          status: "PENDING",
          createdAt: "2026-08-20T14:30:00Z",
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === "pending") {
      loadPendingApprovals();
    }
  }, [activeTab]);

  const handleDecide = async () => {
    if (!decideItem) return;
    if (!reason.trim()) {
      setActionError("Bắt buộc phải nhập lý do phê duyệt hoặc từ chối!");
      return;
    }

    setActionLoading(true);
    setActionError("");

    try {
      await adminFetch(`/admin/approvals/${decideItem.id}/decide`, {
        method: "POST",
        body: JSON.stringify({
          action: decision,
          reason: reason.trim(),
        }),
      });

      setDecideItem(null);
      setReason("");
      loadPendingApprovals();
    } catch (err: any) {
      setActionError(err.message || "Xử lý phê duyệt thất bại");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="flex flex-col w-full min-h-screen">
      <AdminHeader
        title="Duyệt Nạp & Rút tiền (Quy trình 4 Mắt)"
        subtitle="Hàng chờ phê duyệt giao dịch tài chính, chống tự duyệt tiền"
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Tab Selection */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 bg-[#121216] p-1.5 rounded-2xl border border-[#1e1e26]">
            <button
              onClick={() => setActiveTab("pending")}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === "pending"
                  ? "bg-red-600 text-white shadow-lg shadow-red-950/40"
                  : "text-gray-400 hover:text-white"
              }`}
            >
              Hàng chờ 4 Mắt Duyệt ({pendingList.length})
            </button>
            <button
              onClick={() => setActiveTab("deposits")}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === "deposits"
                  ? "bg-red-600 text-white shadow-lg shadow-red-950/40"
                  : "text-gray-400 hover:text-white"
              }`}
            >
              Lịch sử Nạp tiền
            </button>
            <button
              onClick={() => setActiveTab("withdrawals")}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === "withdrawals"
                  ? "bg-red-600 text-white shadow-lg shadow-red-950/40"
                  : "text-gray-400 hover:text-white"
              }`}
            >
              Lịch sử Rút tiền
            </button>
          </div>

          <button
            onClick={loadPendingApprovals}
            className="p-2 rounded-xl bg-[#121216] border border-[#1e1e26] text-gray-400 hover:text-white"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-500" : ""}`} />
          </button>
        </div>

        {/* Datatable Pending Approvals */}
        {activeTab === "pending" && (
          <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl overflow-hidden shadow-xl">
            <div className="p-4 bg-[#16161c] border-b border-[#22222a] flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Clock className="w-4 h-4 text-amber-400" />
                <span className="text-xs font-bold text-white">Yêu cầu Rút tiền chờ Admin thứ 2 phê duyệt</span>
              </div>
              <span className="text-[11px] text-amber-400 bg-amber-950/40 border border-amber-900/40 px-2.5 py-1 rounded-lg">
                Người đề nghị KHÔNG thể tự duyệt lệnh của chính mình
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-[#17171e] border-b border-[#23232c] text-[11px] font-bold text-gray-400 uppercase tracking-wider">
                    <th className="py-3.5 px-4">ID Yêu cầu</th>
                    <th className="py-3.5 px-4">Loại GD</th>
                    <th className="py-3.5 px-4">Số tiền (USD)</th>
                    <th className="py-3.5 px-4">Người đề nghị</th>
                    <th className="py-3.5 px-4">Lý do đề nghị</th>
                    <th className="py-3.5 px-4">Thời gian</th>
                    <th className="py-3.5 px-4 text-right">Thao tác Phê duyệt</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#1c1c24] text-xs">
                  {pendingList.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-8 text-center text-gray-500 text-xs">
                        Hiện tại không có yêu cầu nào đang chờ phê duyệt.
                      </td>
                    </tr>
                  ) : (
                    pendingList.map((item) => (
                      <tr key={item.id} className="hover:bg-[#16161d] transition-colors">
                        <td className="py-3.5 px-4 font-mono font-bold text-white">{item.id}</td>
                        <td className="py-3.5 px-4">
                          <span className="px-2 py-0.5 rounded-md font-bold text-[10px] bg-red-950/60 text-red-400 border border-red-800/60">
                            {item.type}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 font-bold text-amber-400 flex items-center">
                          <DollarSign className="w-3.5 h-3.5 mr-0.5" />
                          {item.amount.toLocaleString("en-US", { minimumFractionDigits: 2 })}
                        </td>
                        <td className="py-3.5 px-4 text-gray-300 font-semibold">{item.requestedBy}</td>
                        <td className="py-3.5 px-4 text-gray-400">{item.reason}</td>
                        <td className="py-3.5 px-4 text-gray-400">
                          {new Date(item.createdAt).toLocaleString("vi-VN")}
                        </td>
                        <td className="py-3.5 px-4 text-right">
                          <div className="flex items-center justify-end gap-2">
                            <button
                              onClick={() => {
                                setDecideItem(item);
                                setDecision("APPROVED");
                              }}
                              className="px-3 py-1.5 rounded-xl bg-emerald-950/50 hover:bg-emerald-900/60 border border-emerald-800/60 text-emerald-400 font-bold text-xs flex items-center gap-1 transition-colors"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" /> Duyệt Lệnh
                            </button>
                            <button
                              onClick={() => {
                                setDecideItem(item);
                                setDecision("REJECTED");
                              }}
                              className="px-3 py-1.5 rounded-xl bg-red-950/50 hover:bg-red-900/60 border border-red-800/60 text-red-400 font-bold text-xs flex items-center gap-1 transition-colors"
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
          </div>
        )}
      </div>

      {/* Decision Modal */}
      {decideItem && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#121216] border border-[#22222c] rounded-2xl max-w-md w-full p-6 flex flex-col gap-5 shadow-2xl relative">
            <button
              onClick={() => setDecideItem(null)}
              className="absolute top-4 right-4 text-gray-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3">
              <div className={`p-2.5 rounded-xl border ${decision === "APPROVED" ? "bg-emerald-950/50 border-emerald-800/50 text-emerald-400" : "bg-red-950/50 border-red-800/50 text-red-400"}`}>
                <AlertOctagon className="w-5 h-5" />
              </div>
              <div className="flex flex-col">
                <h3 className="text-base font-bold text-white">
                  {decision === "APPROVED" ? "Xác nhận PHÊ DUYỆT Lệnh" : "Xác nhận TỪ CHỐI Lệnh"}
                </h3>
                <span className="text-xs text-gray-400">Request ID: {decideItem.id} | Số tiền: ${decideItem.amount}</span>
              </div>
            </div>

            {actionError && (
              <div className="bg-red-950/60 border border-red-800/60 rounded-xl p-3 text-xs text-red-300">
                {actionError}
              </div>
            )}

            <div className="flex flex-col gap-3">
              <label className="text-xs font-semibold text-gray-300">
                Lý do phê duyệt/từ chối <span className="text-red-500">* Bắt buộc (Quy trình 4 Mắt)</span>
              </label>
              <textarea
                rows={3}
                required
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Ghi rõ căn cứ duyệt hoặc nguyên nhân từ chối lệnh này..."
                className="bg-[#17171e] border border-[#262632] focus:border-red-500 rounded-xl p-3 text-xs text-white placeholder-gray-500 outline-none"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setDecideItem(null)}
                className="px-4 py-2 rounded-xl bg-[#1a1a22] text-xs font-semibold text-gray-300 hover:bg-[#252530]"
              >
                Hủy
              </button>
              <button
                onClick={handleDecide}
                disabled={actionLoading}
                className={`px-4 py-2 rounded-xl font-bold text-xs text-white transition-colors disabled:opacity-50 ${
                  decision === "APPROVED" ? "bg-emerald-600 hover:bg-emerald-700" : "bg-red-600 hover:bg-red-700"
                }`}
              >
                {actionLoading ? "Đang xử lý..." : "Xác nhận Quyết định"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
