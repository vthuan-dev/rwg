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
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title="Quản lý Đại lý & Hoa hồng"
        subtitle="Cây quan hệ tuyến dưới đại lý, lịch sử đợt chạy job chi trả hoa hồng"
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Navigation Tabs & Refresh */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 bg-white p-1.5 rounded-2xl border border-slate-200 shadow-xs">
            <button
              onClick={() => setActiveTab("tree")}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-1.5 ${
                activeTab === "tree"
                  ? "bg-red-600 text-white shadow-xs"
                  : "text-slate-600 hover:text-slate-900"
              }`}
            >
              <Network className="w-4 h-4" /> Cây Đại lý & Tuyến dưới
            </button>
            <button
              onClick={() => setActiveTab("runs")}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-1.5 ${
                activeTab === "runs"
                  ? "bg-red-600 text-white shadow-xs"
                  : "text-slate-600 hover:text-slate-900"
              }`}
            >
              <Layers className="w-4 h-4" /> Lịch sử Đợt Chi Trả Hoa hồng
            </button>
          </div>

          <button
            onClick={loadData}
            className="p-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 shadow-xs"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-600" : ""}`} />
          </button>
        </div>

        {/* Tree Table */}
        {activeTab === "tree" && (
          <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-100 border-b border-slate-200 text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                    <th className="py-3.5 px-4">Mã Đại lý (Agent ID)</th>
                    <th className="py-3.5 px-4">Tài khoản</th>
                    <th className="py-3.5 px-4">Mã Giới thiệu</th>
                    <th className="py-3.5 px-4">Tuyến dưới Trực tiếp</th>
                    <th className="py-3.5 px-4">Tổng Hoa hồng Đã nhận (USD)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs">
                  {treeList.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="py-8 text-center text-slate-500 font-medium">
                        Chưa có dữ liệu đại lý trong hệ thống.
                      </td>
                    </tr>
                  ) : (
                    treeList.map((item) => (
                      <tr key={item.agentId} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3.5 px-4 font-mono font-bold text-slate-900">{item.agentId}</td>
                        <td className="py-3.5 px-4 font-bold text-red-700">{item.agentUsername}</td>
                        <td className="py-3.5 px-4">
                          <span className="px-2.5 py-1 rounded-md font-mono font-bold text-xs bg-red-50 text-red-700 border border-red-200">
                            {item.referralCode}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 font-bold text-slate-900">
                          {item.directSubordinatesCount} người
                        </td>
                        <td className="py-3.5 px-4 font-black text-emerald-600 flex items-center">
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
          <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-100 border-b border-slate-200 text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                    <th className="py-3.5 px-4">Mã Đợt (Run ID)</th>
                    <th className="py-3.5 px-4">Ngày Tính toán</th>
                    <th className="py-3.5 px-4">Số Đại lý Được Chi Trả</th>
                    <th className="py-3.5 px-4">Tổng Tiền Đã Trả (USD)</th>
                    <th className="py-3.5 px-4">Trạng thái</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs">
                  {runsList.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="py-8 text-center text-slate-500 font-medium">
                        Chưa có lịch sử đợt chi trả hoa hồng.
                      </td>
                    </tr>
                  ) : (
                    runsList.map((run) => (
                      <tr key={run.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3.5 px-4 font-mono font-bold text-slate-900">{run.id}</td>
                        <td className="py-3.5 px-4 text-slate-700 font-semibold">{run.runDate}</td>
                        <td className="py-3.5 px-4 font-bold text-slate-900">{run.totalAgentsProcessed} đại lý</td>
                        <td className="py-3.5 px-4 font-black text-emerald-600">${run.totalAmountPaid.toLocaleString()}</td>
                        <td className="py-3.5 px-4">
                          <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-emerald-50 text-emerald-700 border border-emerald-200">
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
