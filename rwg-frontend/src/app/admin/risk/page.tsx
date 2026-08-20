"use client";

import React, { useState, useEffect } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  ShieldAlert,
  CheckCircle2,
  XCircle,
  RefreshCw,
  Plus,
  X,
  AlertTriangle,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";

interface AccountLink {
  id: string;
  userAId: string;
  userBId: string;
  userAUsername?: string;
  userBUsername?: string;
  linkType: "SHARED_IP" | "SHARED_DEVICE" | "MANUAL";
  status: "SUSPECTED" | "CONFIRMED" | "CLEARED";
  score: number;
  updatedAt: string;
}

export default function AdminRiskPage() {
  const [links, setLinks] = useState<AccountLink[]>([]);
  const [loading, setLoading] = useState(true);

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

  const loadRiskLinks = async () => {
    setLoading(true);
    try {
      const data = await adminFetch("/admin/risk/links?page=0&size=20");
      setLinks(data.content || []);
    } catch {
      // Demo fallback
      setLinks([
        {
          id: "link-01",
          userAId: "usr-a",
          userBId: "usr-b",
          userAUsername: "user_alpha",
          userBUsername: "user_beta",
          linkType: "SHARED_IP",
          status: "SUSPECTED",
          score: 85,
          updatedAt: "2026-08-20T16:00:00Z",
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRiskLinks();
  }, []);

  const handleConclude = async () => {
    if (!concludeItem) return;
    if (!reason.trim()) {
      setActionError("Bắt buộc nhập lý do kết luận gian lận!");
      return;
    }

    setActionLoading(true);
    setActionError("");

    try {
      await adminFetch("/admin/risk/conclude", {
        method: "POST",
        body: JSON.stringify({
          linkId: concludeItem.id,
          status: newStatus,
          reason: reason.trim(),
        }),
      });

      setConcludeItem(null);
      setReason("");
      loadRiskLinks();
    } catch (err: any) {
      setActionError(err.message || "Ghi nhận kết luận thất bại");
    } finally {
      setActionLoading(false);
    }
  };

  const handleCreateManualLink = async () => {
    if (!userAInput.trim() || !userBInput.trim() || !manualReason.trim()) {
      setActionError("Điền đầy đủ User A, User B và Lý do liên kết!");
      return;
    }

    setActionLoading(true);
    setActionError("");

    try {
      await adminFetch("/admin/risk/manual-link", {
        method: "POST",
        body: JSON.stringify({
          userAId: userAInput.trim(),
          userBId: userBInput.trim(),
          reason: manualReason.trim(),
        }),
      });

      setShowManualModal(false);
      setUserAInput("");
      setUserBInput("");
      setManualReason("");
      loadRiskLinks();
    } catch (err: any) {
      setActionError(err.message || "Tạo liên kết thất bại");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="flex flex-col w-full min-h-screen">
      <AdminHeader
        title="Quản lý Rủi ro & Gian lận (Risk)"
        subtitle="Dò chùm IP/Device đa tài khoản, kết luận giữ tiền hoa hồng"
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Action Bar */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ShieldAlert className="w-5 h-5 text-red-500" />
            <span className="text-sm font-bold text-white">Hàng đợi Tín hiệu Gian lận ({links.length})</span>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowManualModal(true)}
              className="bg-red-600 hover:bg-red-700 text-white font-bold px-3.5 py-2 rounded-xl text-xs flex items-center gap-1.5 transition-colors shadow-lg shadow-red-950/40"
            >
              <Plus className="w-4 h-4" />
              <span>Nối Liên kết Thủ công</span>
            </button>
            <button
              onClick={loadRiskLinks}
              className="p-2 rounded-xl bg-[#121216] border border-[#1e1e26] text-gray-400 hover:text-white"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-500" : ""}`} />
            </button>
          </div>
        </div>

        {/* Links Table */}
        <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl overflow-hidden shadow-xl">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-[#17171e] border-b border-[#23232c] text-[11px] font-bold text-gray-400 uppercase tracking-wider">
                  <th className="py-3.5 px-4">ID Liên kết</th>
                  <th className="py-3.5 px-4">Cặp Tài khoản (User A ↔ User B)</th>
                  <th className="py-3.5 px-4">Loại Tín hiệu</th>
                  <th className="py-3.5 px-4">Điểm Tín nhiệm</th>
                  <th className="py-3.5 px-4">Trạng thái Giữ Tiền</th>
                  <th className="py-3.5 px-4">Cập nhật</th>
                  <th className="py-3.5 px-4 text-right">Thao tác Kết luận</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#1c1c24] text-xs">
                {links.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-8 text-center text-gray-500 text-xs">
                      Không phát hiện tín hiệu gian lận nào trong hàng đợi.
                    </td>
                  </tr>
                ) : (
                  links.map((link) => (
                    <tr key={link.id} className="hover:bg-[#16161d] transition-colors">
                      <td className="py-3.5 px-4 font-mono font-bold text-white">{link.id}</td>
                      <td className="py-3.5 px-4">
                        <span className="font-bold text-red-400">{link.userAUsername || link.userAId}</span>
                        <span className="text-gray-500 mx-1 border-b border-dashed border-gray-600 px-1">↔</span>
                        <span className="font-bold text-red-400">{link.userBUsername || link.userBId}</span>
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="px-2 py-0.5 rounded-md font-bold text-[10px] bg-gray-800 text-gray-300">
                          {link.linkType}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 font-bold text-amber-400">{link.score} / 100</td>
                      <td className="py-3.5 px-4">
                        {link.status === "CONFIRMED" && (
                          <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-red-950/60 text-red-400 border border-red-800/60 flex items-center gap-1 w-fit">
                            <XCircle className="w-3 h-3" /> CONFIRMED (Đang Giữ Tiền)
                          </span>
                        )}
                        {link.status === "CLEARED" && (
                          <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-emerald-950/60 text-emerald-400 border border-emerald-800/60 flex items-center gap-1 w-fit">
                            <CheckCircle2 className="w-3 h-3" /> CLEARED (Thôi Giữ)
                          </span>
                        )}
                        {link.status === "SUSPECTED" && (
                          <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-amber-950/60 text-amber-400 border border-amber-800/60 flex items-center gap-1 w-fit">
                            <AlertTriangle className="w-3 h-3" /> SUSPECTED (Nghi Vấn)
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-gray-400">
                        {new Date(link.updatedAt).toLocaleDateString("vi-VN")}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <button
                          onClick={() => {
                            setConcludeItem(link);
                            setNewStatus(link.status === "CONFIRMED" ? "CLEARED" : "CONFIRMED");
                          }}
                          className="px-3 py-1.5 rounded-xl bg-red-950/50 hover:bg-red-900/60 border border-red-800/60 text-red-400 font-bold text-xs transition-colors"
                        >
                          Ghi Kết luận
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Conclude Modal */}
      {concludeItem && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#121216] border border-[#22222c] rounded-2xl max-w-md w-full p-6 flex flex-col gap-5 shadow-2xl relative">
            <button
              onClick={() => setConcludeItem(null)}
              className="absolute top-4 right-4 text-gray-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-red-950/50 border border-red-800/50 text-red-400">
                <ShieldAlert className="w-5 h-5" />
              </div>
              <div className="flex flex-col">
                <h3 className="text-base font-bold text-white">Ghi Kết luận Gian lận</h3>
                <span className="text-xs text-gray-400">Link ID: {concludeItem.id}</span>
              </div>
            </div>

            {actionError && (
              <div className="bg-red-950/60 border border-red-800/60 rounded-xl p-3 text-xs text-red-300">
                {actionError}
              </div>
            )}

            <div className="flex flex-col gap-3">
              <label className="text-xs font-semibold text-gray-300">Kết luận Trạng thái</label>
              <select
                value={newStatus}
                onChange={(e: any) => setNewStatus(e.target.value)}
                className="bg-[#17171e] border border-[#262632] rounded-xl p-2.5 text-xs text-white outline-none"
              >
                <option value="CONFIRMED">CONFIRMED - Xác nhận gian lận (Giữ hoa hồng)</option>
                <option value="CLEARED">CLEARED - Minh bạch (Giải tỏa giữ tiền)</option>
              </select>

              <label className="text-xs font-semibold text-gray-300">Lý do Kết luận *</label>
              <textarea
                rows={3}
                required
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Nhập bằng chứng/nguyên nhân cho kết luận gian lận này..."
                className="bg-[#17171e] border border-[#262632] focus:border-red-500 rounded-xl p-3 text-xs text-white placeholder-gray-500 outline-none"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setConcludeItem(null)}
                className="px-4 py-2 rounded-xl bg-[#1a1a22] text-xs font-semibold text-gray-300 hover:bg-[#252530]"
              >
                Hủy
              </button>
              <button
                onClick={handleConclude}
                disabled={actionLoading}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white font-bold text-xs transition-colors disabled:opacity-50"
              >
                {actionLoading ? "Đang lưu..." : "Xác nhận Lưu Kết luận"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Manual Link Modal */}
      {showManualModal && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#121216] border border-[#22222c] rounded-2xl max-w-md w-full p-6 flex flex-col gap-5 shadow-2xl relative">
            <button
              onClick={() => setShowManualModal(false)}
              className="absolute top-4 right-4 text-gray-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-red-950/50 border border-red-800/50 text-red-400">
                <Plus className="w-5 h-5" />
              </div>
              <div className="flex flex-col">
                <h3 className="text-base font-bold text-white">Nối Liên kết Thủ công</h3>
                <span className="text-xs text-gray-400">Tạo liên kết gian lận bằng tay giữa 2 user</span>
              </div>
            </div>

            {actionError && (
              <div className="bg-red-950/60 border border-red-800/60 rounded-xl p-3 text-xs text-red-300">
                {actionError}
              </div>
            )}

            <div className="flex flex-col gap-3">
              <label className="text-xs font-semibold text-gray-300">User ID A</label>
              <input
                type="text"
                value={userAInput}
                onChange={(e) => setUserAInput(e.target.value)}
                placeholder="Nhập ID tài khoản A"
                className="bg-[#17171e] border border-[#262632] rounded-xl p-2.5 text-xs text-white outline-none"
              />

              <label className="text-xs font-semibold text-gray-300">User ID B</label>
              <input
                type="text"
                value={userBInput}
                onChange={(e) => setUserBInput(e.target.value)}
                placeholder="Nhập ID tài khoản B"
                className="bg-[#17171e] border border-[#262632] rounded-xl p-2.5 text-xs text-white outline-none"
              />

              <label className="text-xs font-semibold text-gray-300">Lý do nối liên kết *</label>
              <textarea
                rows={2}
                value={manualReason}
                onChange={(e) => setManualReason(e.target.value)}
                placeholder="Ghi rõ thông tin nghi vấn..."
                className="bg-[#17171e] border border-[#262632] focus:border-red-500 rounded-xl p-3 text-xs text-white outline-none"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setShowManualModal(false)}
                className="px-4 py-2 rounded-xl bg-[#1a1a22] text-xs font-semibold text-gray-300 hover:bg-[#252530]"
              >
                Hủy
              </button>
              <button
                onClick={handleCreateManualLink}
                disabled={actionLoading}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white font-bold text-xs transition-colors disabled:opacity-50"
              >
                {actionLoading ? "Đang lưu..." : "Xác nhận Nối Cặp"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
