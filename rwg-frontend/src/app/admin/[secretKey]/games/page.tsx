"use client";

import React, { useState, useEffect } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  Gamepad2,
  CheckCircle2,
  XCircle,
  RefreshCw,
  Sliders,
  X,
  AlertCircle,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";

interface GameTable {
  id: string;
  name: string;
  engineType: string;
  active: boolean;
  minBet: number;
  maxBet: number;
  updatedAt?: string;
}

export default function AdminGamesPage() {
  const [tables, setTables] = useState<GameTable[]>([]);
  const [loading, setLoading] = useState(true);

  // Edit Limits Modal
  const [editTable, setEditTable] = useState<GameTable | null>(null);
  const [minBet, setMinBet] = useState("1.0");
  const [maxBet, setMaxBet] = useState("10000.0");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");

  const loadTables = async () => {
    setLoading(true);
    try {
      const data = await adminFetch("/admin/games/tables");
      if (Array.isArray(data)) {
        setTables(data);
      }
    } catch {
      setTables([
        {
          id: "11111111-1111-1111-1111-111111111111",
          name: "Lucky 28 Standard",
          engineType: "KL28",
          active: true,
          minBet: 1.0,
          maxBet: 10000.0,
        },
        {
          id: "22222222-2222-2222-2222-222222222222",
          name: "British Lucky 28 Vip",
          engineType: "KL28",
          active: true,
          minBet: 5.0,
          maxBet: 50000.0,
        },
        {
          id: "33333333-3333-3333-3333-333333333333",
          name: "Korean Lucky 28 Speed",
          engineType: "KL28",
          active: true,
          minBet: 1.0,
          maxBet: 20000.0,
        },
        {
          id: "44444444-4444-4444-4444-444444444444",
          name: "Taiwan Times Special",
          engineType: "KL28",
          active: true,
          minBet: 10.0,
          maxBet: 100000.0,
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTables();
  }, []);

  const handleToggleStatus = async (table: GameTable) => {
    try {
      await adminFetch(`/admin/games/tables/${table.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ active: !table.active }),
      });
      loadTables();
    } catch (err: any) {
      alert(err.message || "Đổi trạng thái bàn thất bại");
    }
  };

  const handleSaveLimits = async () => {
    if (!editTable) return;
    setActionLoading(true);
    setActionError("");

    try {
      await adminFetch(`/admin/games/tables/${editTable.id}/limits`, {
        method: "PATCH",
        body: JSON.stringify({
          minBet: String(minBet),
          maxBet: String(maxBet),
        }),
      });

      setEditTable(null);
      loadTables();
    } catch (err: any) {
      setActionError(err.message || "Cập nhật hạn mức thất bại");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title="Quản lý Bàn chơi Casino"
        subtitle="Cấu hình sảnh Lucky 28, bật/tắt bàn khẩn cấp và chỉnh hạn mức cược"
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Action Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Gamepad2 className="w-5 h-5 text-red-600" />
            <span className="text-sm font-extrabold text-slate-900">Danh sách Tất cả Bàn chơi ({tables.length})</span>
          </div>
          <button
            onClick={loadTables}
            className="p-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 shadow-xs"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-600" : ""}`} />
          </button>
        </div>

        {/* Game Tables Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {tables.map((table) => (
            <div
              key={table.id}
              className={`bg-white border rounded-2xl p-5 flex flex-col justify-between gap-4 transition-all shadow-xs hover:shadow-md ${
                table.active ? "border-slate-200" : "border-red-300 bg-red-50/30"
              }`}
            >
              <div className="flex items-start justify-between">
                <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2">
                    <span className="text-base font-extrabold text-slate-900">{table.name}</span>
                    <span className="px-2 py-0.5 rounded-md font-bold text-[10px] bg-slate-100 text-slate-700 border border-slate-200">
                      {table.engineType}
                    </span>
                  </div>
                  <span className="text-[11px] font-mono text-slate-400">{table.id}</span>
                </div>

                {/* Status Toggle Badge */}
                <button
                  onClick={() => handleToggleStatus(table)}
                  className={`px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer ${
                    table.active
                      ? "bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100"
                      : "bg-red-50 text-red-700 border border-red-200 hover:bg-red-100"
                  }`}
                >
                  {table.active ? (
                    <>
                      <CheckCircle2 className="w-3.5 h-3.5" /> Bàn đang BẬT
                    </>
                  ) : (
                    <>
                      <XCircle className="w-3.5 h-3.5" /> Bàn đã TẮT
                    </>
                  )}
                </button>
              </div>

              {/* Limits Information */}
              <div className="grid grid-cols-2 gap-3 p-3 bg-slate-50 border border-slate-200 rounded-xl text-xs">
                <div className="flex flex-col">
                  <span className="text-slate-500 text-[10px] uppercase font-bold">Cược tối thiểu (Min)</span>
                  <span className="text-slate-900 font-bold">${table.minBet.toLocaleString()}</span>
                </div>
                <div className="flex flex-col">
                  <span className="text-slate-500 text-[10px] uppercase font-bold">Cược tối đa (Max)</span>
                  <span className="text-amber-700 font-bold">${table.maxBet.toLocaleString()}</span>
                </div>
              </div>

              {/* Actions */}
              <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
                <button
                  onClick={() => {
                    setEditTable(table);
                    setMinBet(String(table.minBet));
                    setMaxBet(String(table.maxBet));
                  }}
                  className="px-3.5 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 border border-slate-200 text-xs font-bold text-slate-800 flex items-center gap-1.5 transition-colors"
                >
                  <Sliders className="w-3.5 h-3.5 text-amber-600" />
                  <span>Sửa Hạn mức Cược</span>
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Modal Edit Limits */}
      {editTable && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white border border-slate-200 rounded-2xl max-w-md w-full p-6 flex flex-col gap-5 shadow-2xl relative">
            <button
              onClick={() => setEditTable(null)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-700"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-600">
                <Sliders className="w-5 h-5" />
              </div>
              <div className="flex flex-col">
                <h3 className="text-base font-extrabold text-slate-900">Chỉnh Hạn mức Bàn chơi</h3>
                <span className="text-xs text-slate-500 font-medium">{editTable.name}</span>
              </div>
            </div>

            {actionError && (
              <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700 font-semibold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
                <span>{actionError}</span>
              </div>
            )}

            <div className="flex flex-col gap-3">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-slate-700">Min Bet (USD)</label>
                <input
                  type="number"
                  value={minBet}
                  onChange={(e) => setMinBet(e.target.value)}
                  className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-bold"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-slate-700">Max Bet (USD)</label>
                <input
                  type="number"
                  value={maxBet}
                  onChange={(e) => setMaxBet(e.target.value)}
                  className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-bold"
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setEditTable(null)}
                className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
              >
                Hủy
              </button>
              <button
                onClick={handleSaveLimits}
                disabled={actionLoading}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white font-bold text-xs transition-colors disabled:opacity-50"
              >
                {actionLoading ? "Đang lưu..." : "Xác nhận Lưu Hạn mức"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
