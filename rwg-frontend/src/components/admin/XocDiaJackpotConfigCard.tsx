"use client";

import React, { useState, useEffect } from "react";
import {
  Sparkles,
  Zap,
  Flame,
  Check,
  Save,
  RefreshCw,
  Coins,
  Dices,
  Trophy,
  AlertCircle,
  HelpCircle,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { RubyDice } from "@/components/xocdia/RubyDice";

interface JackpotConfig {
  pool: number;
  minPool: number;
  triggerMode: "AUTO" | "FORCE_NEXT_ROUND" | "OFF";
  autoRate: number;
  targetDoor: string;
  winnerMode: "ALL_BETTOR_SHARE" | "SPECIFIC_USER" | "RANDOM_BETTOR";
  targetUser: string;
  lastWon?: string;
  threshold: number;
  winMode: "FULL_POOL" | "PERCENT_POOL" | "FIXED_AMOUNT";
  winValue: string;
  requireBet?: string;
  feeRate?: string;
}

const DOORS = [
  { key: "RANDOM", label: "🎲 Ngẫu nhiên (Hệ thống tự chọn)", dice: 0 },
  { key: "CHAN", label: "CHẴN (Tứ Quý 6-6-6-6)", dice: 6 },
  { key: "LE", label: "LẺ (Tứ Quý 1-1-1-1)", dice: 1 },
  { key: "FOUR_RED", label: "4 ĐỎ (Tứ Quý 2-2-2-2)", dice: 2 },
  { key: "FOUR_WHITE", label: "4 TRẮNG (Tứ Quý 4-4-4-4)", dice: 4 },
  { key: "THREE_WHITE", label: "3 TRẮNG 1 ĐỎ (Tứ Quý 3-3-3-3)", dice: 3 },
  { key: "THREE_RED", label: "3 ĐỎ 1 TRẮNG (Tứ Quý 5-5-5-5)", dice: 5 },
];

export const XocDiaJackpotConfigCard: React.FC = () => {
  const [config, setConfig] = useState<JackpotConfig>({
    pool: 295320203,
    minPool: 100000000,
    triggerMode: "AUTO",
    autoRate: 0.001,
    targetDoor: "RANDOM",
    winnerMode: "ALL_BETTOR_SHARE",
    targetUser: "",
    lastWon: "",
    threshold: 200000000,
    winMode: "FULL_POOL",
    winValue: "100",
    requireBet: "true",
  });

  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [triggering, setTriggering] = useState<boolean>(false);
  const [successMsg, setSuccessMsg] = useState<string>("");
  const [errorMsg, setErrorMsg] = useState<string>("");

  const fetchConfig = async () => {
    setLoading(true);
    setErrorMsg("");
    try {
      const data = await adminFetch<JackpotConfig>(
        "/admin/settings/xocdia-jackpot"
      );
      if (data) {
        setConfig(data);
      }
    } catch (err) {
      console.warn("Lỗi đọc cấu hình jackpot xocdia:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConfig();
  }, []);

  const handleSave = async () => {
    setSaving(true);
    setErrorMsg("");
    setSuccessMsg("");
    try {
      const data = await adminFetch<JackpotConfig>(
        "/admin/settings/xocdia-jackpot",
        {
          method: "PUT",
          body: JSON.stringify(config),
        }
      );
      if (data) {
        setConfig(data);
      }
      setSuccessMsg("Đã lưu cấu hình Jackpot Xóc Đĩa thành công!");
      setTimeout(() => setSuccessMsg(""), 3500);
    } catch (err: unknown) {
      console.error("Lỗi lưu cấu hình jackpot:", err);
      setErrorMsg("Không thể lưu cấu hình jackpot. Vui lòng thử lại.");
    } finally {
      setSaving(false);
    }
  };

  const handleForceTrigger = async () => {
    setTriggering(true);
    setErrorMsg("");
    setSuccessMsg("");
    try {
      const data = await adminFetch<JackpotConfig>(
        "/admin/settings/xocdia-jackpot/force-trigger",
        {
          method: "POST",
          body: JSON.stringify({
            targetDoor: config.targetDoor,
            winnerMode: config.winnerMode,
            targetUser: config.targetUser,
            winMode: config.winMode,
            winValue: config.winValue,
            threshold: config.threshold,
          }),
        }
      );
      if (data) {
        setConfig(data);
      }
      setSuccessMsg("⚡ ĐÃ KÍCH HOẠT NỔ HŨ VÁN TIẾP THEO THÀNH CÔNG!");
      setTimeout(() => setSuccessMsg(""), 4500);
    } catch (err: unknown) {
      console.error("Lỗi ép nổ hũ:", err);
      setErrorMsg("Lỗi khi ép nổ hũ. Vui lòng kiểm tra lại.");
    } finally {
      setTriggering(false);
    }
  };

  return (
    <div className="bg-white border border-amber-300/80 rounded-2xl p-6 shadow-sm flex flex-col gap-6 relative overflow-hidden">
      {/* Background Glow Accent */}
      <div className="absolute -top-16 -right-16 w-56 h-56 bg-amber-400/10 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute -bottom-16 -left-16 w-56 h-56 bg-red-400/10 rounded-full blur-3xl pointer-events-none" />

      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-4 pb-4 border-b border-slate-100">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-xl bg-gradient-to-tr from-amber-500 to-yellow-400 flex items-center justify-center text-white shadow-md shadow-amber-500/20">
            <Trophy className="w-6 h-6 drop-shadow" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-slate-800 tracking-tight">
                Quản Lý Jackpot & Nổ Hũ Xóc Đĩa
              </h2>
              {config.triggerMode === "FORCE_NEXT_ROUND" ? (
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-red-100 text-red-700 border border-red-300 flex items-center gap-1 animate-pulse">
                  <Flame className="w-3 h-3 text-red-600" />
                  SẴN SÀNG NỔ VÁN TỚI
                </span>
              ) : config.triggerMode === "AUTO" ? (
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-100 text-emerald-700 border border-emerald-300 flex items-center gap-1">
                  <Sparkles className="w-3 h-3 text-emerald-600" />
                  TỰ ĐỘNG MINH BẠCH
                </span>
              ) : (
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-slate-100 text-slate-600 border border-slate-200">
                  TẠM TẮT NỔ HŨ
                </span>
              )}
            </div>
            <p className="text-xs text-slate-500">
              Cơ chế bộ 4 xúc xắc đỏ Tứ Quý gán theo 6 cửa cược Xóc Đĩa chuẩn Sunwin / Casino
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={fetchConfig}
            disabled={loading}
            className="p-2 rounded-xl text-slate-500 hover:text-slate-800 hover:bg-slate-100 transition-all cursor-pointer"
            title="Tải lại dữ liệu"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
          </button>
        </div>
      </div>

      {/* Messages */}
      {successMsg && (
        <div className="flex items-center gap-2 p-3 bg-emerald-50 text-emerald-800 border border-emerald-200 rounded-xl text-xs font-semibold animate-in fade-in">
          <Check className="w-4 h-4 text-emerald-600 flex-shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}
      {errorMsg && (
        <div className="flex items-center gap-2 p-3 bg-red-50 text-red-800 border border-red-200 rounded-xl text-xs font-semibold animate-in fade-in">
          <AlertCircle className="w-4 h-4 text-red-600 flex-shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Grid Controls */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Box 1: Quỹ Hũ Jackpot Hiện Tại */}
        <div className="bg-gradient-to-br from-amber-50 to-orange-50/40 border border-amber-200/90 rounded-xl p-4 flex flex-col justify-between shadow-xs">
          <div>
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold uppercase tracking-wider text-amber-800 flex items-center gap-1.5">
                <Coins className="w-4 h-4 text-amber-600" />
                Quỹ Hũ Hiện Tại (VND)
              </span>
            </div>
            <div className="my-2">
              <input
                type="number"
                value={config.pool}
                onChange={(e) =>
                  setConfig((prev) => ({
                    ...prev,
                    pool: Math.max(0, parseInt(e.target.value) || 0),
                  }))
                }
                className="w-full text-xl font-black font-mono text-amber-900 bg-white/90 border border-amber-300 rounded-lg px-3 py-2 shadow-inner focus:outline-none focus:ring-2 focus:ring-amber-500"
              />
              <p className="text-[11px] text-amber-700/80 mt-1 font-mono font-medium">
                ≈ {config.pool.toLocaleString()} đ
              </p>
            </div>
          </div>

          <div className="mt-4 pt-3 border-t border-amber-200/60">
            <label className="text-[11px] font-bold text-slate-700 block mb-1">
              Muc Hu Khoi Diem Sau Khi No (Min Pool):
            </label>
            <input
              type="number"
              value={config.minPool}
              onChange={(e) =>
                setConfig((prev) => ({
                  ...prev,
                  minPool: Math.max(0, parseInt(e.target.value) || 0),
                }))
              }
              className="w-full text-xs font-mono font-bold text-slate-800 bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-amber-500"
            />
            <label className="text-[11px] font-bold text-slate-700 block mb-1 mt-3">
              Nguong pool moi duoc no (Threshold):
            </label>
            <input
              type="number"
              value={config.threshold}
              onChange={(e) =>
                setConfig((prev) => ({
                  ...prev,
                  threshold: Math.max(0, parseInt(e.target.value) || 0),
                }))
              }
              className="w-full text-xs font-mono font-bold text-slate-800 bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-amber-500"
            />
            <label className="text-[11px] font-bold text-slate-700 block mb-1 mt-3">
              Muc thuong:
            </label>
            <div className="flex gap-2">
              <select
                value={config.winMode}
                onChange={(e) =>
                  setConfig((prev) => ({
                    ...prev,
                    winMode: e.target.value as JackpotConfig["winMode"],
                  }))
                }
                className="flex-1 text-xs font-semibold text-slate-800 bg-white border border-slate-200 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-amber-500 cursor-pointer"
              >
                <option value="FULL_POOL">Full hu</option>
                <option value="PERCENT_POOL">% hu</option>
                <option value="FIXED_AMOUNT">So tien co dinh</option>
              </select>
              <input
                type="text"
                value={config.winValue}
                onChange={(e) => setConfig((prev) => ({ ...prev, winValue: e.target.value }))}
                placeholder={config.winMode === "FIXED_AMOUNT" ? "VD: 50000000" : "VD: 100"}
                className="flex-1 text-xs font-mono font-bold text-slate-800 bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-amber-500"
              />
            </div>
            <label className="flex items-center gap-2 text-[11px] font-bold text-slate-700 mt-3 cursor-pointer">
              <input
                type="checkbox"
                checked={config.requireBet !== "false"}
                onChange={(e) =>
                  setConfig((prev) => ({ ...prev, requireBet: e.target.checked ? "true" : "false" }))
                }
                className="accent-amber-500"
              />
              Chi no neu user co dat cuoc van do
            </label>
            {config.lastWon && (
              <p className="text-[11px] text-emerald-700 font-semibold mt-2 bg-emerald-50 border border-emerald-200 rounded-lg px-2 py-1">
                Van vua no: {config.lastWon}
              </p>
            )}
          </div>
        </div>

        {/* Box 2: Chế Độ Kích Hoạt Nổ Hũ */}
        <div className="bg-slate-50/70 border border-slate-200 rounded-xl p-4 flex flex-col justify-between">
          <div>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-700 flex items-center gap-1.5 mb-3">
              <Zap className="w-4 h-4 text-amber-500" />
              Chế Độ Nổ Hũ
            </span>

            <div className="space-y-2">
              <label className="flex items-center gap-2 text-xs font-semibold text-slate-800 cursor-pointer p-2 rounded-lg bg-white border border-slate-200 hover:border-amber-400">
                <input
                  type="radio"
                  name="triggerMode"
                  checked={config.triggerMode === "AUTO"}
                  onChange={() =>
                    setConfig((prev) => ({ ...prev, triggerMode: "AUTO" }))
                  }
                  className="text-amber-600 focus:ring-amber-500"
                />
                <span>Tự động theo tỷ lệ (%) mỗi ván</span>
              </label>

              <label className="flex items-center gap-2 text-xs font-semibold text-slate-800 cursor-pointer p-2 rounded-lg bg-white border border-slate-200 hover:border-amber-400">
                <input
                  type="radio"
                  name="triggerMode"
                  checked={config.triggerMode === "FORCE_NEXT_ROUND"}
                  onChange={() =>
                    setConfig((prev) => ({
                      ...prev,
                      triggerMode: "FORCE_NEXT_ROUND",
                    }))
                  }
                  className="text-red-600 focus:ring-red-500"
                />
                <span className="text-red-700 font-bold">
                  Ép nổ ván tiếp theo (100% nổ)
                </span>
              </label>

              <label className="flex items-center gap-2 text-xs font-semibold text-slate-800 cursor-pointer p-2 rounded-lg bg-white border border-slate-200 hover:border-amber-400">
                <input
                  type="radio"
                  name="triggerMode"
                  checked={config.triggerMode === "OFF"}
                  onChange={() =>
                    setConfig((prev) => ({ ...prev, triggerMode: "OFF" }))
                  }
                  className="text-slate-600 focus:ring-slate-500"
                />
                <span className="text-slate-500">Tắt nổ hũ (chỉ tích lũy)</span>
              </label>
            </div>
          </div>

          {config.triggerMode === "AUTO" && (
            <div className="mt-3 pt-3 border-t border-slate-200">
              <div className="flex items-center justify-between text-xs font-semibold text-slate-700 mb-1">
                <span>Xác suất nổ mỗi ván:</span>
                <span className="font-mono font-bold text-amber-600">
                  {(config.autoRate * 100).toFixed(2)}%
                </span>
              </div>
              <input
                type="range"
                min="0.0005"
                max="0.05"
                step="0.0005"
                value={config.autoRate}
                onChange={(e) =>
                  setConfig((prev) => ({
                    ...prev,
                    autoRate: parseFloat(e.target.value),
                  }))
                }
                className="w-full accent-amber-500 cursor-pointer"
              />
            </div>
          )}
        </div>

        {/* Box 3: Chỉ Định Cửa Nổ & Người Hưởng */}
        <div className="bg-slate-50/70 border border-slate-200 rounded-xl p-4 flex flex-col justify-between">
          <div>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-700 flex items-center gap-1.5 mb-3">
              <Dices className="w-4 h-4 text-rose-500" />
              Cửa Cược & Người Nhận Hũ
            </span>

            {/* Cửa nổ */}
            <div className="mb-3">
              <label className="text-[11px] font-bold text-slate-600 block mb-1">
                Cửa Cược Kích Hoạt Nổ:
              </label>
              <select
                value={config.targetDoor}
                onChange={(e) =>
                  setConfig((prev) => ({ ...prev, targetDoor: e.target.value }))
                }
                className="w-full text-xs font-semibold text-slate-800 bg-white border border-slate-200 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-amber-500 cursor-pointer"
              >
                {DOORS.map((d) => (
                  <option key={d.key} value={d.key}>
                    {d.label}
                  </option>
                ))}
              </select>
            </div>

            {/* Người nhận hũ */}
            <div className="mb-3">
              <label className="text-[11px] font-bold text-slate-600 block mb-1">
                Người Nhận Giải:
              </label>
              <select
                value={config.winnerMode}
                onChange={(e) =>
                  setConfig((prev) => ({
                    ...prev,
                    winnerMode: e.target.value as JackpotConfig["winnerMode"],
                  }))
                }
                className="w-full text-xs font-semibold text-slate-800 bg-white border border-slate-200 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-amber-500 cursor-pointer"
              >
                <option value="ALL_BETTOR_SHARE">
                  👥 Chia thưởng theo tỷ lệ cược (Minh bạch)
                </option>
                <option value="SPECIFIC_USER">
                  🎯 Chỉ định 1 Username / Khách VIP cụ thể
                </option>
                <option value="RANDOM_BETTOR">
                  🎲 Chọn ngẫu nhiên 1 người may mắn
                </option>
              </select>
            </div>

            {/* Input username khi chọn SPECIFIC_USER */}
            {config.winnerMode === "SPECIFIC_USER" && (
              <div className="animate-in fade-in">
                <label className="text-[11px] font-bold text-amber-800 block mb-1">
                  Nhập Username người chơi:
                </label>
                <input
                  type="text"
                  placeholder="Ví dụ: player123 hoặc Kyoko"
                  value={config.targetUser}
                  onChange={(e) =>
                    setConfig((prev) => ({
                      ...prev,
                      targetUser: e.target.value.trim(),
                    }))
                  }
                  className="w-full text-xs font-bold text-slate-800 bg-white border border-amber-300 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-amber-500"
                />
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Mini Preview of 6 Dice Door Assignments */}
      <div className="bg-slate-900 text-white rounded-xl p-4 flex flex-col gap-3 shadow-inner">
        <div className="flex items-center justify-between text-xs font-bold text-amber-300">
          <span className="flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5" />
            Bảng Tra Cứu Xúc Xắc 3D Gán Theo Cửa Cược:
          </span>
          <span className="text-[10px] text-slate-400 font-normal">
            *Khi 4 xúc xắc tại bàn lắc ra Tứ Quý cùng số này ➔ Cửa đó NỔ HŨ
          </span>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-2.5">
          <div className="bg-slate-800/80 border border-slate-700/80 rounded-lg p-2 flex flex-col items-center gap-1.5 text-center">
            <RubyDice value={1} size={24} />
            <span className="text-[11px] font-bold text-rose-300">Cửa LẺ</span>
            <span className="text-[9px] text-slate-400 font-mono">Tứ Quý [1]</span>
          </div>

          <div className="bg-slate-800/80 border border-slate-700/80 rounded-lg p-2 flex flex-col items-center gap-1.5 text-center">
            <RubyDice value={2} size={24} />
            <span className="text-[11px] font-bold text-amber-300">4 ĐỎ</span>
            <span className="text-[9px] text-slate-400 font-mono">Tứ Quý [2]</span>
          </div>

          <div className="bg-slate-800/80 border border-slate-700/80 rounded-lg p-2 flex flex-col items-center gap-1.5 text-center">
            <RubyDice value={3} size={24} />
            <span className="text-[11px] font-bold text-amber-300">3 TRẮNG</span>
            <span className="text-[9px] text-slate-400 font-mono">Tứ Quý [3]</span>
          </div>

          <div className="bg-slate-800/80 border border-slate-700/80 rounded-lg p-2 flex flex-col items-center gap-1.5 text-center">
            <RubyDice value={4} size={24} />
            <span className="text-[11px] font-bold text-slate-200">4 TRẮNG</span>
            <span className="text-[9px] text-slate-400 font-mono">Tứ Quý [4]</span>
          </div>

          <div className="bg-slate-800/80 border border-slate-700/80 rounded-lg p-2 flex flex-col items-center gap-1.5 text-center">
            <RubyDice value={5} size={24} />
            <span className="text-[11px] font-bold text-rose-300">3 ĐỎ</span>
            <span className="text-[9px] text-slate-400 font-mono">Tứ Quý [5]</span>
          </div>

          <div className="bg-slate-800/80 border border-slate-700/80 rounded-lg p-2 flex flex-col items-center gap-1.5 text-center">
            <RubyDice value={6} size={24} />
            <span className="text-[11px] font-bold text-amber-300">Cửa CHẴN</span>
            <span className="text-[9px] text-slate-400 font-mono">Tứ Quý [6]</span>
          </div>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex items-center justify-between flex-wrap gap-3 pt-2 border-t border-slate-100">
        <button
          type="button"
          onClick={handleForceTrigger}
          disabled={triggering}
          className="px-4 py-2.5 rounded-xl font-black text-xs bg-gradient-to-r from-red-600 to-rose-600 hover:from-red-500 hover:to-rose-500 active:scale-95 text-white shadow-md shadow-red-600/30 flex items-center gap-2 cursor-pointer transition-all disabled:opacity-50"
        >
          <Flame className={`w-4 h-4 ${triggering ? "animate-bounce" : ""}`} />
          {triggering ? "ĐANG KÍCH HOẠT..." : "⚡ KÍCH HOẠT NỔ HŨ VÁN KẾ TIẾP (1-CLICK)"}
        </button>

        <button
          type="button"
          onClick={handleSave}
          disabled={saving}
          className="px-5 py-2.5 rounded-xl font-bold text-xs bg-amber-500 hover:bg-amber-600 active:scale-95 text-slate-900 flex items-center gap-2 cursor-pointer shadow-sm transition-all disabled:opacity-50"
        >
          {saving ? (
            <RefreshCw className="w-4 h-4 animate-spin" />
          ) : (
            <Save className="w-4 h-4" />
          )}
          {saving ? "Đang lưu..." : "Lưu Cấu Hình Jackpot"}
        </button>
      </div>
    </div>
  );
};
