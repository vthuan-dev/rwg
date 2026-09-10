"use client";

import React, { useState, useEffect } from "react";
import { Bot, Check, Save, Users, MessageSquareCode, Sparkles, RefreshCw, AlertCircle } from "lucide-react";
import { adminFetch } from "@/lib/adminApi";

const ALL_BOT_PREVIEWS = [
  { id: "p1", name: "Kyoko", avatar: "/games/xocdia/assets_hd/avatar_1.png" },
  { id: "p2", name: "Huyen_VIP", avatar: "/games/xocdia/assets_hd/avatar_2.png" },
  { id: "p3", name: "DaiPhat", avatar: "/games/xocdia/assets_hd/avatar_3.png" },
  { id: "p4", name: "Techweat", avatar: "/games/xocdia/assets_hd/avatar_4.png" },
  { id: "p5", name: "Cool_Man", avatar: "/games/xocdia/assets_hd/avatar_5.png" },
  { id: "p6", name: "Lady_V", avatar: "/games/xocdia/assets_hd/avatar_6.png" },
  { id: "p7", name: "YoungVIP", avatar: "/games/xocdia/assets_hd/avatar_7.png" },
  { id: "p8", name: "Nsumy", avatar: "/games/xocdia/assets_hd/avatar_8.png" },
];

export const XocDiaBotConfigCard: React.FC = () => {
  const [botCount, setBotCount] = useState<number>(4);
  const [botChatEnabled, setBotChatEnabled] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [successMsg, setSuccessMsg] = useState<string>("");
  const [errorMsg, setErrorMsg] = useState<string>("");

  const fetchConfig = async () => {
    setLoading(true);
    setErrorMsg("");
    try {
      const data = await adminFetch<{ botCount: number; botChatEnabled: boolean }>(
        "/admin/settings/xocdia-config"
      );
      if (data && typeof data.botCount === "number") {
        setBotCount(data.botCount);
        setBotChatEnabled(data.botChatEnabled !== false);
      }
    } catch (err) {
      console.warn("Lỗi đọc cấu hình xocdia:", err);
      // Giữ giá trị mặc định nếu chưa set
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
      await adminFetch<{ botCount: number; botChatEnabled: boolean }>(
        "/admin/settings/xocdia-config",
        {
          method: "PUT",
          body: JSON.stringify({
            botCount,
            botChatEnabled,
          }),
        }
      );
      setSuccessMsg(`Đã lưu thành công! Bàn Xóc Đĩa hiện có ${botCount} Bot hoạt động.`);
      setTimeout(() => setSuccessMsg(""), 3500);
    } catch (err) {
      setErrorMsg((err as Error).message || "Không thể lưu cấu hình bot");
    } finally {
      setSaving(false);
    }
  };

  const activeBots = ALL_BOT_PREVIEWS.slice(0, botCount);

  return (
    <div className="bg-white border border-amber-200/80 rounded-2xl p-6 shadow-sm flex flex-col gap-5 relative overflow-hidden">
      {/* Background ambient decorative badge */}
      <div className="absolute top-0 right-0 w-48 h-48 bg-gradient-to-bl from-amber-500/10 via-red-500/5 to-transparent rounded-bl-full pointer-events-none" />

      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-amber-500 to-amber-700 text-white flex items-center justify-center shadow-md">
            <Bot className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="font-extrabold text-base text-slate-900">
                Cấu Hình Bot Ảo Bàn Xóc Đĩa VIP
              </h3>
              <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 text-[10px] font-bold border border-amber-300">
                Live Game
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              Tùy chỉnh số lượng người chơi ảo ngồi tại bàn cược và tự động chat tâm lý dụ cược
            </p>
          </div>
        </div>

        <button
          onClick={fetchConfig}
          disabled={loading}
          className="p-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-600 transition-colors"
          title="Tải lại cấu hình"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-amber-600" : ""}`} />
        </button>
      </div>

      {/* Main Settings Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 pt-2">
        {/* Left Column: Number of Bots */}
        <div className="flex flex-col gap-3 p-4 rounded-xl bg-slate-50/80 border border-slate-200">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-slate-800 font-bold text-sm">
              <Users className="w-4 h-4 text-amber-600" />
              <span>Số lượng Bot ngồi bàn:</span>
            </div>
            <span className="px-3 py-1 rounded-full bg-amber-600 text-white font-mono font-black text-sm shadow-xs">
              {botCount} BOT
            </span>
          </div>

          {/* Slider */}
          <div className="flex items-center gap-3 pt-2">
            <span className="text-xs font-bold text-slate-400">1</span>
            <input
              type="range"
              min={1}
              max={8}
              step={1}
              value={botCount}
              onChange={(e) => setBotCount(parseInt(e.target.value, 10))}
              className="flex-1 accent-amber-600 cursor-pointer h-2 bg-slate-200 rounded-lg"
            />
            <span className="text-xs font-bold text-slate-400">8</span>
          </div>

          {/* Preset Buttons */}
          <div className="flex items-center gap-2 flex-wrap pt-1">
            {[
              { count: 3, label: "3 Bot (Riêng tư)" },
              { count: 4, label: "4 Bot (Tiêu chuẩn)" },
              { count: 6, label: "6 Bot (Đông vui)" },
              { count: 8, label: "8 Bot (Full Bàn VIP)" },
            ].map((preset) => (
              <button
                key={preset.count}
                type="button"
                onClick={() => setBotCount(preset.count)}
                className={`px-2.5 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  botCount === preset.count
                    ? "bg-amber-600 text-white shadow-xs scale-105"
                    : "bg-white text-slate-700 hover:bg-amber-50 border border-slate-200"
                }`}
              >
                {preset.label}
              </button>
            ))}
          </div>

          {/* Visual Bot Preview */}
          <div className="mt-2 pt-2 border-t border-slate-200/80">
            <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block mb-2">
              Danh sách Bot sẽ ngồi tại bàn ({activeBots.length} nhân vật):
            </span>
            <div className="flex items-center gap-2 flex-wrap">
              {activeBots.map((b) => (
                <div
                  key={b.id}
                  className="flex items-center gap-1.5 px-2 py-1 rounded-lg bg-white border border-slate-200 shadow-xs"
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={b.avatar} alt={b.name} className="w-5 h-5 rounded-full object-cover border border-amber-400" />
                  <span className="text-xs font-semibold text-slate-700">{b.name}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Right Column: Bot Chat & Psychological Dụ Cược */}
        <div className="flex flex-col gap-3 p-4 rounded-xl bg-slate-50/80 border border-slate-200">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-slate-800 font-bold text-sm">
              <MessageSquareCode className="w-4 h-4 text-emerald-600" />
              <span>Hệ thống Bot Chat Dụ Cược:</span>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={botChatEnabled}
                onChange={(e) => setBotChatEnabled(e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
            </label>
          </div>

          <div className="p-3 rounded-xl bg-white border border-slate-200 flex flex-col gap-2">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-800">
              <Sparkles className="w-3.5 h-3.5 text-amber-500" />
              <span>Logic 100+ Tin Nhắn Giả Lập Tâm Lý Sòng Bạc:</span>
            </div>
            <ul className="text-xs text-slate-600 space-y-1 pl-4 list-disc">
              <li>
                <strong>Khi mở cược (`BETTING_OPEN`):</strong> Hô cầu Chẵn, rủ rê bẻ Lẻ, dụ lót vị Tứ Tử 1:16, soi cầu bệt 5 tay.
              </li>
              <li>
                <strong>Khi mở bát (`RESULT` / `SETTLE`):</strong> Khoe ăn đậm kích thích lòng tham, tiếc nuối đòi gấp thếp x2 gỡ lại, khen Dealer xóc son.
              </li>
              <li>
                <strong>Tương tác Bot AI Bảo Kê:</strong> Thỉnh thoảng có bot buột miệng nói gắt để Bot AI nhảy vào troll và dọa kick, tạo không khí sòng bài 5 sao chân thật 100%.
              </li>
            </ul>
          </div>

          <div className="text-[11px] text-slate-500 italic mt-auto">
            * Chỉ những bot đang ngồi bàn ({botCount} bot) mới xuất hiện trong khung chat với tên và ảnh đại diện thật.
          </div>
        </div>
      </div>

      {/* Notifications & Save Button */}
      <div className="flex items-center justify-between flex-wrap gap-3 pt-2 border-t border-slate-200">
        <div>
          {successMsg && (
            <div className="flex items-center gap-1.5 text-xs font-bold text-emerald-700 bg-emerald-50 px-3 py-1.5 rounded-xl border border-emerald-200">
              <Check className="w-4 h-4 text-emerald-600" />
              <span>{successMsg}</span>
            </div>
          )}
          {errorMsg && (
            <div className="flex items-center gap-1.5 text-xs font-bold text-red-700 bg-red-50 px-3 py-1.5 rounded-xl border border-red-200">
              <AlertCircle className="w-4 h-4 text-red-600" />
              <span>{errorMsg}</span>
            </div>
          )}
        </div>

        <button
          onClick={handleSave}
          disabled={saving}
          className="px-5 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-bold text-xs shadow-md hover:shadow-lg flex items-center gap-2 active:scale-95 transition-all disabled:opacity-50 cursor-pointer"
        >
          <Save className="w-4 h-4" />
          <span>{saving ? "Đang lưu cấu hình..." : "Lưu Cấu Hình Bot"}</span>
        </button>
      </div>
    </div>
  );
};
