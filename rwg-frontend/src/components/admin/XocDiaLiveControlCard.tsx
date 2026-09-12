"use client";

import React, { useState, useEffect, useCallback, useMemo } from "react";
import {
  Eye,
  RefreshCw,
  Sliders,
  AlertTriangle,
  CheckCircle2,
  TrendingUp,
  UserCheck,
  Search,
  Zap,
  Play,
  Pause,
  ShieldAlert,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { formatVND } from "@/lib/money";

interface LiveBetEntry {
  username: string;
  betType: string;
  doorLabel: string;
  stake: number | string;
  createdAt: string;
}

interface LiveBetsResponse {
  tableId?: string;
  roundId?: string;
  roundSeq?: number;
  phase?: string;
  totalBets?: number;
  doorTotals?: Record<string, number | string>;
  bets?: LiveBetEntry[];
  forceResult?: string;
  forceMode?: string;
  error?: string;
}

const FORCE_OPTIONS = [
  { key: "AUTO", label: "🎲 Tự Động (Ngẫu Nhiên)", desc: "Hệ thống tự quay ngẫu nhiên theo thuật toán", category: "auto" },
  { key: "TWO_RED", label: "🔴⚪ Chẵn (2 Đỏ 2 Trắng)", desc: "Chẵn bình thường, phổ biến nhất", category: "chan" },
  { key: "EVEN", label: "⚖️ Ép Chẵn Bất Kỳ", desc: "Ngẫu nhiên giữa 2 đỏ (80%) hoặc tứ quý (20%)", category: "chan" },
  { key: "FOUR_RED", label: "🔴🔴 Tứ Quý Đỏ (4 Đỏ)", desc: "Chẵn tứ quý đỏ (tỷ lệ ăn 1:12)", category: "chan" },
  { key: "FOUR_WHITE", label: "⚪⚪ Tứ Quý Trắng (4 Trắng)", desc: "Chẵn tứ quý trắng (tỷ lệ ăn 1:12)", category: "chan" },
  { key: "THREE_RED", label: "🔴⚪ Lẻ (3 Đỏ 1 Trắng)", desc: "Lẻ 3 đỏ 1 trắng (tỷ lệ ăn 1:2.6)", category: "le" },
  { key: "THREE_WHITE", label: "⚪🔴 Lẻ (3 Trắng 1 Đỏ)", desc: "Lẻ 3 trắng 1 đỏ (tỷ lệ ăn 1:2.6)", category: "le" },
  { key: "ODD", label: "⚖️ Ép Lẻ Bất Kỳ", desc: "Ngẫu nhiên 50/50 giữa 3 đỏ hoặc 3 trắng", category: "le" },
];

export const XocDiaLiveControlCard: React.FC = () => {
  const [data, setData] = useState<LiveBetsResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [autoRefresh, setAutoRefresh] = useState<boolean>(true);
  const [searchUser, setSearchUser] = useState<string>("");

  // Force result form state
  const [selectedForce, setSelectedForce] = useState<string>("AUTO");
  const [selectedMode, setSelectedMode] = useState<string>("ONCE");
  const [savingForce, setSavingForce] = useState<boolean>(false);
  const [toastMsg, setToastMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const fetchLiveBets = useCallback(async () => {
    try {
      const res = await adminFetch<LiveBetsResponse>("/admin/games/xocdia/live-bets");
      if (res && !res.error) {
        setData(res);
        if (res.forceResult) {
          setSelectedForce(res.forceResult);
        }
        if (res.forceMode) {
          setSelectedMode(res.forceMode);
        }
      }
    } catch (err) {
      console.warn("Lỗi tải cược trực tiếp xóc đĩa:", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLiveBets();
  }, [fetchLiveBets]);

  // Auto poll every 2.5 seconds
  useEffect(() => {
    if (!autoRefresh) return;
    const timer = setInterval(() => {
      fetchLiveBets();
    }, 2500);
    return () => clearInterval(timer);
  }, [autoRefresh, fetchLiveBets]);

  const handleApplyForce = async (forceKey?: string, mode?: string) => {
    const fKey = forceKey || selectedForce;
    const mMode = mode || selectedMode;

    setSavingForce(true);
    setToastMsg(null);
    try {
      const res = await adminFetch<{ success: boolean; forceResult: string; forceMode: string }>(
        "/admin/games/xocdia/force-result",
        {
          method: "POST",
          body: JSON.stringify({
            forceResult: fKey,
            forceMode: mMode,
          }),
        }
      );
      if (res && res.success) {
        setSelectedForce(res.forceResult);
        setSelectedMode(res.forceMode);
        const opt = FORCE_OPTIONS.find((o) => o.key === res.forceResult);
        setToastMsg({
          type: "success",
          text: `Đã can thiệp: ${opt?.label || res.forceResult} (${res.forceMode === "ONCE" ? "Áp dụng 1 vòng" : "Liên tục"})!`,
        });
        setTimeout(() => setToastMsg(null), 4000);
        void fetchLiveBets();
      }
    } catch (err) {
      setToastMsg({
        type: "error",
        text: (err as Error).message || "Không thể lưu can thiệp kết quả",
      });
    } finally {
      setSavingForce(false);
    }
  };

  // Door calculations
  const doorTotals = data?.doorTotals || {};
  const chanTotal = Number(doorTotals["Chẵn"] || 0);
  const leTotal = Number(doorTotals["Lẻ"] || 0);
  const tuDoTotal = Number(doorTotals["Tứ Quý Đỏ (4 đỏ)"] || 0);
  const tuTrangTotal = Number(doorTotals["Tứ Quý Trắng (4 trắng)"] || 0);
  const baDoTotal = Number(doorTotals["3 Đỏ 1 Trắng"] || 0);
  const baTrangTotal = Number(doorTotals["3 Trắng 1 Đỏ"] || 0);

  const totalPool = chanTotal + leTotal + tuDoTotal + tuTrangTotal + baDoTotal + baTrangTotal;

  const chanPercent = totalPool > 0 ? Math.round((chanTotal / totalPool) * 100) : 50;
  const lePercent = totalPool > 0 ? 100 - chanPercent : 50;

  // Filtered bets
  const filteredBets = useMemo(() => {
    if (!data?.bets) return [];
    if (!searchUser.trim()) return data.bets;
    const term = searchUser.toLowerCase().trim();
    return data.bets.filter((b) => b.username.toLowerCase().includes(term));
  }, [data?.bets, searchUser]);

  const activeForceOpt = FORCE_OPTIONS.find((o) => o.key === (data?.forceResult || "AUTO"));
  const isCurrentlyForced = data?.forceResult && data.forceResult !== "AUTO";

  return (
    <div className="bg-white border-2 border-indigo-200/80 rounded-2xl p-6 shadow-md flex flex-col gap-6 relative overflow-hidden">
      {/* Decorative top accent */}
      <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-blue-600 via-indigo-600 to-red-600" />

      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-4 pt-1">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-indigo-600 to-slate-900 text-white flex items-center justify-center shadow-lg shadow-indigo-500/20">
            <Eye className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-2.5">
              <h3 className="font-black text-lg text-slate-900 tracking-tight">
                Soi Cược & Can Thiệp Kết Quả Xóc Đĩa VIP
              </h3>
              <span className="px-2.5 py-0.5 rounded-full bg-red-100 text-red-700 text-xs font-black border border-red-300 animate-pulse flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-red-600" />
                LIVE
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              Theo dõi ai đang đánh cửa nào trong thời gian thực & điều chỉnh kết quả ván tới
            </p>
          </div>
        </div>

        {/* Round sequence & Auto-refresh status */}
        <div className="flex items-center gap-3">
          <div className="px-3.5 py-1.5 bg-slate-900 text-white rounded-xl text-xs font-black tracking-wide flex items-center gap-2 shadow-xs">
            <span className="text-slate-400 font-medium">Vòng:</span>
            <span className="text-amber-400 text-sm">#{data?.roundSeq || "..."}</span>
            <span className="w-1 h-3 bg-slate-700 mx-1" />
            <span className={`text-[11px] font-bold ${data?.phase === "BETTING_OPEN" ? "text-emerald-400" : "text-amber-300"}`}>
              {data?.phase === "BETTING_OPEN" ? "ĐANG NHẬN CƯỢC" : data?.phase || "CHỜ"}
            </span>
          </div>

          <button
            onClick={() => setAutoRefresh(!autoRefresh)}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold border flex items-center gap-1.5 transition-all ${
              autoRefresh
                ? "bg-emerald-50 border-emerald-300 text-emerald-700"
                : "bg-slate-100 border-slate-300 text-slate-600"
            }`}
            title={autoRefresh ? "Tự động cập nhật mỗi 2.5s" : "Đã tạm dừng"}
          >
            {autoRefresh ? <Play className="w-3.5 h-3.5 fill-emerald-600" /> : <Pause className="w-3.5 h-3.5" />}
            {autoRefresh ? "Auto-refresh: Bật" : "Tạm dừng"}
          </button>

          <button
            onClick={() => {
              setLoading(true);
              void fetchLiveBets();
            }}
            disabled={loading}
            className="p-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors"
            title="Làm mới ngay"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-indigo-600" : ""}`} />
          </button>
        </div>
      </div>

      {/* Toast notifications */}
      {toastMsg && (
        <div
          className={`p-3.5 rounded-xl border flex items-center gap-2.5 text-xs font-bold animate-fade-in ${
            toastMsg.type === "success"
              ? "bg-emerald-50 border-emerald-300 text-emerald-800"
              : "bg-red-50 border-red-300 text-red-800"
          }`}
        >
          {toastMsg.type === "success" ? (
            <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
          ) : (
            <AlertTriangle className="w-4 h-4 text-red-600 shrink-0" />
          )}
          <span>{toastMsg.text}</span>
        </div>
      )}

      {/* Section 1: Balance between Chẵn vs Lẻ */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Cửa Chẵn */}
        <div className="p-4 rounded-xl bg-gradient-to-br from-red-50 to-white border-2 border-red-200 flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-black text-red-700 tracking-wider flex items-center gap-1.5 uppercase">
              <span className="w-2.5 h-2.5 rounded-full bg-red-600 inline-block" />
              Cửa CHẴN
            </span>
            <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-red-100 text-red-800">
              {chanPercent}% tổng cược
            </span>
          </div>
          <div className="text-2xl font-black text-slate-900 tabular-nums">
            {formatVND(chanTotal)}
          </div>
          <div className="flex items-center gap-3 pt-1 text-[11px] text-slate-500 border-t border-red-100">
            <span>Tứ Quý Đỏ: <b className="text-red-700">{formatVND(tuDoTotal)}</b></span>
            <span>Tứ Quý Trắng: <b className="text-slate-700">{formatVND(tuTrangTotal)}</b></span>
          </div>
        </div>

        {/* Cửa Lẻ */}
        <div className="p-4 rounded-xl bg-gradient-to-br from-blue-50 to-white border-2 border-blue-200 flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-black text-blue-700 tracking-wider flex items-center gap-1.5 uppercase">
              <span className="w-2.5 h-2.5 rounded-full bg-blue-600 inline-block" />
              Cửa LẺ
            </span>
            <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-blue-100 text-blue-800">
              {lePercent}% tổng cược
            </span>
          </div>
          <div className="text-2xl font-black text-slate-900 tabular-nums">
            {formatVND(leTotal)}
          </div>
          <div className="flex items-center gap-3 pt-1 text-[11px] text-slate-500 border-t border-blue-100">
            <span>3 Đỏ 1 Trắng: <b className="text-red-700">{formatVND(baDoTotal)}</b></span>
            <span>3 Trắng 1 Đỏ: <b className="text-blue-700">{formatVND(baTrangTotal)}</b></span>
          </div>
        </div>
      </div>

      {/* House advantage analysis banner */}
      <div className="p-3.5 rounded-xl bg-slate-900 text-white flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-2.5">
          <TrendingUp className="w-4 h-4 text-amber-400 shrink-0" />
          <span className="text-xs font-semibold">
            {chanTotal === 0 && leTotal === 0 ? (
              "Vòng này người chơi chưa đặt cược tiền lớn."
            ) : chanTotal > leTotal ? (
              <>
                Người chơi đang dồn tiền vào <b className="text-red-400">CHẴN ({formatVND(chanTotal)})</b>.
                Gợi ý: Cho ra <b className="text-emerald-400">LẺ</b> để nhà cái thu lợi nhuận cao nhất!
              </>
            ) : (
              <>
                Người chơi đang dồn tiền vào <b className="text-blue-400">LẺ ({formatVND(leTotal)})</b>.
                Gợi ý: Cho ra <b className="text-emerald-400">CHẴN</b> để nhà cái thu lợi nhuận cao nhất!
              </>
            )}
          </span>
        </div>
        <div className="flex items-center gap-2">
          {chanTotal > leTotal ? (
            <button
              onClick={() => handleApplyForce("ODD", "ONCE")}
              disabled={savingForce}
              className="px-3 py-1 bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-black text-xs rounded-lg shadow-sm transition-all"
            >
              ⚡ Ép Lẻ Ngay (1 Vòng)
            </button>
          ) : leTotal > chanTotal ? (
            <button
              onClick={() => handleApplyForce("TWO_RED", "ONCE")}
              disabled={savingForce}
              className="px-3 py-1 bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-black text-xs rounded-lg shadow-sm transition-all"
            >
              ⚡ Ép Chẵn Ngay (1 Vòng)
            </button>
          ) : null}
        </div>
      </div>

      {/* Section 2: Can Thiệp Kết Quả Vòng Tới (Rig / Force Result Controls) */}
      <div className="p-5 rounded-2xl bg-gradient-to-br from-slate-50 to-indigo-50/40 border border-indigo-200 flex flex-col gap-4">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div className="flex items-center gap-2">
            <Sliders className="w-4 h-4 text-indigo-600" />
            <h4 className="font-black text-sm text-slate-900">
              Bảng Can Thiệp Kết Quả Vòng Tới
            </h4>
          </div>

          {/* Current Status Badge */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">Trạng thái hiện tại:</span>
            {isCurrentlyForced ? (
              <span className="px-2.5 py-1 rounded-full bg-red-600 text-white text-xs font-black flex items-center gap-1 shadow-xs">
                <ShieldAlert className="w-3.5 h-3.5" />
                ĐANG CAN THIỆP: {activeForceOpt?.label} ({data?.forceMode === "ONCE" ? "1 vòng" : "Liên tục"})
              </span>
            ) : (
              <span className="px-2.5 py-1 rounded-full bg-emerald-100 text-emerald-800 border border-emerald-300 text-xs font-bold flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                🎲 Tự Động (Ngẫu Nhiên)
              </span>
            )}
          </div>
        </div>

        {/* Quick select grid */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5">
          {FORCE_OPTIONS.map((opt) => {
            const isSelected = selectedForce === opt.key;
            return (
              <button
                key={opt.key}
                type="button"
                onClick={() => setSelectedForce(opt.key)}
                className={`p-3 rounded-xl border text-left flex flex-col gap-1 transition-all ${
                  isSelected
                    ? "bg-indigo-600 text-white border-indigo-700 shadow-md ring-2 ring-indigo-400"
                    : "bg-white hover:bg-slate-100 border-slate-200 text-slate-800"
                }`}
              >
                <div className="font-extrabold text-xs leading-tight">
                  {opt.label}
                </div>
                <div className={`text-[10px] leading-tight ${isSelected ? "text-indigo-100" : "text-slate-500"}`}>
                  {opt.desc}
                </div>
              </button>
            );
          })}
        </div>

        {/* Mode options + Save button */}
        <div className="flex items-center justify-between flex-wrap gap-4 pt-2 border-t border-indigo-100">
          <div className="flex items-center gap-4 text-xs font-bold text-slate-700">
            <span>Chế độ áp dụng:</span>
            <label className="flex items-center gap-1.5 cursor-pointer">
              <input
                type="radio"
                name="forceMode"
                value="ONCE"
                checked={selectedMode === "ONCE"}
                onChange={() => setSelectedMode("ONCE")}
                className="text-indigo-600 focus:ring-indigo-500"
              />
              <span>1 Vòng duy nhất <span className="text-emerald-600 font-semibold">(Sau đó tự về Ngẫu nhiên)</span></span>
            </label>
            <label className="flex items-center gap-1.5 cursor-pointer">
              <input
                type="radio"
                name="forceMode"
                value="CONTINUOUS"
                checked={selectedMode === "CONTINUOUS"}
                onChange={() => setSelectedMode("CONTINUOUS")}
                className="text-indigo-600 focus:ring-indigo-500"
              />
              <span>Liên tục <span className="text-amber-600 font-semibold">(Cho đến khi đổi lại)</span></span>
            </label>
          </div>

          <div className="flex items-center gap-2">
            {isCurrentlyForced && (
              <button
                type="button"
                onClick={() => handleApplyForce("AUTO", "ONCE")}
                disabled={savingForce}
                className="px-4 py-2 bg-slate-200 hover:bg-slate-300 text-slate-700 text-xs font-bold rounded-xl transition-all"
              >
                Huỷ Can Thiệp (Về Tự Động)
              </button>
            )}

            <button
              type="button"
              onClick={() => handleApplyForce()}
              disabled={savingForce}
              className="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-black rounded-xl shadow-md shadow-indigo-600/20 transition-all flex items-center gap-2 disabled:opacity-50"
            >
              <Zap className="w-4 h-4" />
              {savingForce ? "Đang lưu..." : "Áp Dụng Can Thiệp Ngay"}
            </button>
          </div>
        </div>
      </div>

      {/* Section 3: Bettors Table (Ai đánh bên nào) */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div className="flex items-center gap-2">
            <UserCheck className="w-4 h-4 text-slate-700" />
            <h4 className="font-extrabold text-sm text-slate-900">
              Chi Tiết Người Chơi Đang Cược ({data?.totalBets || 0} lệnh cược)
            </h4>
          </div>

          <div className="relative w-64">
            <Search className="w-3.5 h-3.5 absolute left-3 top-2.5 text-slate-400" />
            <input
              type="text"
              placeholder="Tìm theo username..."
              value={searchUser}
              onChange={(e) => setSearchUser(e.target.value)}
              className="w-full bg-slate-50 border border-slate-200 focus:border-indigo-600 rounded-xl pl-8 pr-3 py-1.5 text-xs text-slate-900 outline-none"
            />
          </div>
        </div>

        <div className="border border-slate-200 rounded-xl overflow-hidden shadow-xs">
          <div className="max-h-72 overflow-y-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead className="bg-slate-100 border-b border-slate-200 text-[11px] font-black text-slate-600 uppercase tracking-wider sticky top-0">
                <tr>
                  <th className="py-2.5 px-4">Người chơi</th>
                  <th className="py-2.5 px-4">Cửa cược</th>
                  <th className="py-2.5 px-4">Số tiền cược</th>
                  <th className="py-2.5 px-4 text-right">Thời gian</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredBets.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="py-8 text-center text-slate-400 font-medium">
                      {searchUser ? "Không tìm thấy người chơi phù hợp." : "Chưa có lượt cược nào trong vòng hiện tại."}
                    </td>
                  </tr>
                ) : (
                  filteredBets.map((b, idx) => {
                    const isChan = b.doorLabel.includes("Chẵn") || b.doorLabel.includes("Tứ Quý");
                    return (
                      <tr key={idx} className="hover:bg-slate-50 transition-colors">
                        <td className="py-2.5 px-4 font-extrabold text-slate-900">
                          {b.username}
                        </td>
                        <td className="py-2.5 px-4">
                          <span
                            className={`px-2.5 py-1 rounded-full text-[11px] font-black border ${
                              isChan
                                ? "bg-red-50 text-red-700 border-red-200"
                                : "bg-blue-50 text-blue-700 border-blue-200"
                            }`}
                          >
                            {b.doorLabel}
                          </span>
                        </td>
                        <td className="py-2.5 px-4 font-black text-slate-900 tabular-nums">
                          {formatVND(b.stake)}
                        </td>
                        <td className="py-2.5 px-4 text-right text-slate-400 text-[11px]">
                          {b.createdAt ? new Date(b.createdAt).toLocaleTimeString("vi-VN") : "vừa xong"}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};
