"use client";

import React, { useState, useEffect } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import { Network, RefreshCw, Layers, DollarSign } from "lucide-react";
import { adminFetch } from "@/lib/adminApi";

interface AffiliateNode {
  agentId: string;
  agentUsername: string;
  referralCode: string;
  directSubordinatesCount: number;
  totalCommissionsEarned: number;
}

interface CommissionRun {
  id: string;
  runDate: string;
  totalAgentsProcessed: number;
  totalAmountPaid: number;
  status: "COMPLETED" | "RUNNING" | "FAILED";
  createdAt: string;
}

export default function AdminAffiliatesPage() {
  const [activeTab, setActiveTab] = useState<"tree" | "runs">("tree");
  const [treeList, setTreeList] = useState<AffiliateNode[]>([]);
  const [runsList, setRunsList] = useState<CommissionRun[]>([]);
  const [loading, setLoading] = useState(true);

  const loadData = async () => {
    setLoading(true);
    try {
      if (activeTab === "tree") {
        const data = await adminFetch("/admin/affiliates/tree");
        setTreeList(Array.isArray(data) ? data : []);
      } else {
        const data = await adminFetch("/admin/affiliates/runs");
        setRunsList(Array.isArray(data) ? data : []);
      }
    } catch {
      // Demo fallback
      if (activeTab === "tree") {
        setTreeList([
          {
            agentId: "agt-01",
            agentUsername: "master_agent_01",
            referralCode: "RWG888",
            directSubordinatesCount: 12,
            totalCommissionsEarned: 1580.0,
          },
        ]);
      } else {
        setRunsList([
          {
            id: "run-2026-08-20",
            runDate: "2026-08-20",
            totalAgentsProcessed: 5,
            totalAmountPaid: 450.0,
            status: "COMPLETED",
            createdAt: "2026-08-20T01:00:00Z",
          },
        ]);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [activeTab]);

  return (
    <div className="flex flex-col w-full min-h-screen">
      <AdminHeader
        title="Quản lý Đại lý & Hoa hồng"
        subtitle="Cây quan hệ tuyến dưới đại lý, lịch sử đợt chạy job tính hoa hồng"
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Navigation Tabs & Refresh */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 bg-[#121216] p-1.5 rounded-2xl border border-[#1e1e26]">
            <button
              onClick={() => setActiveTab("tree")}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-1.5 ${
                activeTab === "tree"
                  ? "bg-red-600 text-white shadow-lg shadow-red-950/40"
                  : "text-gray-400 hover:text-white"
              }`}
            >
              <Network className="w-4 h-4" /> Cây Đại lý & Tuyến dưới
            </button>
            <button
              onClick={() => setActiveTab("runs")}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-1.5 ${
                activeTab === "runs"
                  ? "bg-red-600 text-white shadow-lg shadow-red-950/40"
                  : "text-gray-400 hover:text-white"
              }`}
            >
              <Layers className="w-4 h-4" /> Lịch sử Đợt Chi Trả Hoa hồng
            </button>
          </div>

          <button
            onClick={loadData}
            className="p-2 rounded-xl bg-[#121216] border border-[#1e1e26] text-gray-400 hover:text-white"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-500" : ""}`} />
          </button>
        </div>

        {/* Tree Table */}
        {activeTab === "tree" && (
          <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl overflow-hidden shadow-xl">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-[#17171e] border-b border-[#23232c] text-[11px] font-bold text-gray-400 uppercase tracking-wider">
                    <th className="py-3.5 px-4">Mã Đại lý (Agent ID)</th>
                    <th className="py-3.5 px-4">Tài khoản</th>
                    <th className="py-3.5 px-4">Mã Giới thiệu</th>
                    <th className="py-3.5 px-4">Tuyến dưới Trực tiếp</th>
                    <th className="py-3.5 px-4">Tổng Hoa hồng Đã nhận (USD)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#1c1c24] text-xs">
                  {treeList.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="py-8 text-center text-gray-500">
                        Chưa có dữ liệu đại lý trong hệ thống.
                      </td>
                    </tr>
                  ) : (
                    treeList.map((item) => (
                      <tr key={item.agentId} className="hover:bg-[#16161d] transition-colors">
                        <td className="py-3.5 px-4 font-mono font-bold text-white">{item.agentId}</td>
                        <td className="py-3.5 px-4 font-bold text-red-400">{item.agentUsername}</td>
                        <td className="py-3.5 px-4">
                          <span className="px-2.5 py-1 rounded-md font-mono font-bold text-xs bg-red-950/40 text-red-400 border border-red-900/40">
                            {item.referralCode}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 font-bold text-white">
                          {item.directSubordinatesCount} người
                        </td>
                        <td className="py-3.5 px-4 font-bold text-emerald-400 flex items-center">
                          <DollarSign className="w-3.5 h-3.5 mr-0.5" />
                          {item.totalCommissionsEarned.toLocaleString("en-US", { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Runs Table */}
        {activeTab === "runs" && (
          <div className="bg-[#121216] border border-[#1e1e26] rounded-2xl overflow-hidden shadow-xl">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-[#17171e] border-b border-[#23232c] text-[11px] font-bold text-gray-400 uppercase tracking-wider">
                    <th className="py-3.5 px-4">Mã Đợt (Run ID)</th>
                    <th className="py-3.5 px-4">Ngày Tính toán</th>
                    <th className="py-3.5 px-4">Số Đại lý Được Chi Trả</th>
                    <th className="py-3.5 px-4">Tổng Tiền Đã Trả (USD)</th>
                    <th className="py-3.5 px-4">Trạng thái</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#1c1c24] text-xs">
                  {runsList.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="py-8 text-center text-gray-500">
                        Chưa có lịch sử đợt chi trả hoa hồng.
                      </td>
                    </tr>
                  ) : (
                    runsList.map((run) => (
                      <tr key={run.id} className="hover:bg-[#16161d] transition-colors">
                        <td className="py-3.5 px-4 font-mono font-bold text-white">{run.id}</td>
                        <td className="py-3.5 px-4 text-gray-300 font-semibold">{run.runDate}</td>
                        <td className="py-3.5 px-4 font-bold text-white">{run.totalAgentsProcessed} đại lý</td>
                        <td className="py-3.5 px-4 font-bold text-emerald-400">${run.totalAmountPaid.toLocaleString()}</td>
                        <td className="py-3.5 px-4">
                          <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-emerald-950/60 text-emerald-400 border border-emerald-800/60">
                            {run.status}
                          </span>
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
    </div>
  );
}
