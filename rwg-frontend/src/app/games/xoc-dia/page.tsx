"use client";

import React, { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { getPlayerToken, gameTables } from "@/lib/playerApi";
import XocDiaLandscapeGame from "@/components/xocdia/XocDiaLandscapeGame";
import { GameOrientationWrapper } from "@/components/xocdia/GameOrientationWrapper";

export default function XocDiaPage() {
  const router = useRouter();
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(null);
  const [isTableDisabled, setIsTableDisabled] = useState<boolean>(false);
  const [loading, setLoading] = useState(true);
  const [progress, setProgress] = useState(0);
  const [statusText, setStatusText] = useState("Đang xác thực tài khoản Genting VIP...");

  useEffect(() => {
    // Bắt buộc phải đăng nhập mới được vào trang
    const token = getPlayerToken();
    if (!token) {
      router.replace("/login");
      return;
    }
    setIsAuthenticated(true);

    let isSubscribed = true;

    // Kiểm tra bàn chơi có đang ACTIVE không
    const verifyTable = async () => {
      try {
        const tables = await gameTables();
        const xoc = tables.find((t) => t.gameType === "XOC_DIA" && t.status === "ACTIVE");
        if (!xoc) {
          if (isSubscribed) {
            setIsTableDisabled(true);
            setLoading(false);
          }
          return false;
        }
        if (isSubscribed) {
          setIsTableDisabled(false);
        }
        return true;
      } catch {
        return true;
      }
    };

    void verifyTable();
    const tableCheckTimer = setInterval(verifyTable, 6000);

    const CRITICAL_ASSETS = [
      "/games/xocdia/assets_hd/casino_table_master.webp",
      "/games/xocdia/assets_hd/dealer_throne_alpha.webp",
      "/games/xocdia/assets_hd/card_chan_clean.webp",
      "/games/xocdia/assets_hd/card_le_clean.webp",
      "/games/xocdia/assets_hd/four_vi_clean.webp",
      "/games/xocdia/assets_hd/chip_tray_full.webp",
      "/games/xocdia/assets_hd/roadmap_board.webp",
      "/games/xocdia/assets_hd/chip_10k_3d.webp",
      "/games/xocdia/assets_hd/chip_20k_3d.webp",
      "/games/xocdia/assets_hd/chip_50k_3d.webp",
      "/games/xocdia/assets_hd/chip_100k_3d.webp",
      "/games/xocdia/assets_hd/chip_500k_3d.webp",
      "/games/xocdia/assets_hd/chip_1m_3d.webp",
      "/games/xocdia/assets_hd/chip_5m_3d.webp",
      "/games/xocdia/assets_hd/avatar_1.webp",
      "/games/xocdia/assets_hd/avatar_2.webp",
      "/games/xocdia/assets_hd/avatar_3.webp",
      "/games/xocdia/assets_hd/avatar_4.webp",
      "/games/xocdia/set_closed.webp",
      "/games/xocdia/plate.webp",
      "/games/xocdia/bowl_fitted.webp",
      "/games/xocdia/coin-red.webp",
      "/games/xocdia/coin-white.webp",
    ];

    let loadedCount = 0;
    const total = CRITICAL_ASSETS.length;
    let isDone = false;
    const startTime = Date.now();
    const MIN_LOAD_TIME = 600; // ms để có hiệu ứng chuyển cảnh mượt
    const MAX_LOAD_TIME = 1400; // ms tối đa

    const finish = () => {
      if (isDone) return;
      isDone = true;
      setProgress(100);
      setStatusText("Hoàn tất! Chúc đại gia đại thắng... 🎲");
      setTimeout(() => setLoading(false), 180);
    };

    const updateStatus = (pct: number) => {
      setProgress(pct);
      if (pct < 30) {
        setStatusText("Đang kết nối máy chủ Genting VIP...");
      } else if (pct < 70) {
        setStatusText("Tải đồ họa bàn cược Bát Đĩa Thần Long...");
      } else if (pct < 100) {
        setStatusText("Đồng bộ số dư ví & Tỷ giá quy đổi...");
      }
    };

    CRITICAL_ASSETS.forEach((src) => {
      const img = new Image();
      img.onload = img.onerror = () => {
        loadedCount++;
        const pct = Math.min(99, Math.floor((loadedCount / total) * 100));
        updateStatus(pct);
        if (loadedCount >= total) {
          const elapsed = Date.now() - startTime;
          const wait = Math.max(0, MIN_LOAD_TIME - elapsed);
          setTimeout(finish, wait);
        }
      };
      img.src = src;
    });

    // Fallback timeout an toàn: tối đa 1.4s luôn vào game
    const fallbackTimer = setTimeout(finish, MAX_LOAD_TIME);

    return () => {
      isSubscribed = false;
      clearInterval(tableCheckTimer);
      clearTimeout(fallbackTimer);
    };
  }, [router]);

  return (
    <GameOrientationWrapper>
      {isAuthenticated === null || !isAuthenticated ? (
        <div className="relative w-full h-full flex items-center justify-center bg-[#050302] text-white select-none">
          <div className="flex flex-col items-center gap-3">
            <div className="w-9 h-9 rounded-full border-2 border-amber-400 border-t-transparent animate-spin" />
            <p className="text-xs font-mono text-amber-300 tracking-wider">ĐANG XÁC THỰC PHIÊN ĐĂNG NHẬP GENTING VIP...</p>
          </div>
        </div>
      ) : isTableDisabled ? (
        <div className="relative w-full h-full flex items-center justify-center bg-[#070503] text-white p-6 font-sans select-none">
          {/* Subtle Ambient Background */}
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,rgba(245,158,11,0.08)_0%,rgba(0,0,0,0.95)_100%)] pointer-events-none" />

          <div className="relative z-10 max-w-md w-full bg-[#120e0a]/95 border border-amber-500/30 rounded-2xl p-6 sm:p-8 backdrop-blur-2xl shadow-[0_12px_48px_rgba(0,0,0,0.95)] flex flex-col items-center text-center">
            {/* Warning / Lock Badge */}
            <div className="w-16 h-16 rounded-full bg-red-500/10 border border-red-500/30 flex items-center justify-center mb-4 text-red-400 shadow-[0_0_24px_rgba(239,68,68,0.2)]">
              <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>

            <h2 className="text-xl sm:text-2xl font-black tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-amber-200 via-amber-400 to-yellow-500 uppercase mb-2">
              BÀN CHƠI ĐANG TẠM ĐÓNG
            </h2>

            <p className="text-xs sm:text-sm text-slate-300 leading-relaxed mb-6 font-medium">
              Bàn cược Xóc Đĩa VIP hiện đang tạm ngừng nhận cược hoặc đang bảo trì định kỳ. Quý khách vui lòng chọn các trò chơi hấp dẫn khác trên sàn!
            </p>

            <div className="flex flex-col sm:flex-row gap-3 w-full">
              <button
                onClick={() => router.replace("/")}
                className="flex-1 py-3 px-4 rounded-xl bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-black font-extrabold text-xs uppercase tracking-wider transition-all active:scale-95 shadow-[0_4px_16px_rgba(245,158,11,0.3)]"
              >
                Về Trang Chủ
              </button>
              <button
                onClick={() => router.replace("/bet")}
                className="flex-1 py-3 px-4 rounded-xl bg-[#1f1914] hover:bg-[#2a221b] border border-amber-500/30 text-amber-300 font-bold text-xs uppercase tracking-wider transition-all active:scale-95"
              >
                Xem Sảnh Game Khác
              </button>
            </div>
          </div>
        </div>
      ) : loading ? (
        <div className="relative w-full h-full flex items-center justify-center bg-[#050302] text-white select-none overflow-hidden font-sans">
          {/* Cinematic 16:9 Gentleman High-Roller Masterpiece Background */}
          <div className="absolute inset-0 w-full h-full overflow-hidden">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/games/xocdia/assets_hd/loading_gentleman_vip.webp"
              alt="Xoc Dia Genting VIP"
              style={{
                transform: `scale(${1 + (progress / 100) * 0.06})`,
                transition: "transform 120ms ease-out",
              }}
              className="w-full h-full object-cover object-center brightness-95 contrast-105"
            />
            {/* Atmospheric Cinematic Gradients */}
            <div className="absolute inset-0 bg-gradient-to-t from-black/95 via-black/25 to-black/60 pointer-events-none" />
            <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,transparent_45%,rgba(0,0,0,0.85)_100%)] pointer-events-none" />
          </div>

          {/* Top VIP Badge - Clean, elegant, no redundant clutter */}
          <div className="absolute top-6 left-1/2 -translate-x-1/2 z-20 flex items-center gap-2 px-5 py-1.5 rounded-full bg-black/60 border border-amber-500/40 backdrop-blur-md shadow-[0_4px_24px_rgba(0,0,0,0.9)]">
            <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
            <span className="text-[10px] font-mono font-bold tracking-[0.25em] text-amber-300 uppercase">
              RESORTS WORLD GENTING • VIP CLUB
            </span>
          </div>

          {/* Bottom Loading Dock / HUD */}
          <div className="absolute bottom-8 sm:bottom-10 left-1/2 -translate-x-1/2 w-full max-w-md px-6 z-20 flex flex-col items-center">
            {/* Game Title - Pure typography, no redundant icons */}
            <div className="text-center mb-3.5">
              <h1 className="text-2xl sm:text-3xl font-black tracking-[0.14em] text-transparent bg-clip-text bg-gradient-to-r from-amber-100 via-amber-300 to-yellow-500 drop-shadow-[0_4px_20px_rgba(0,0,0,0.95)] uppercase">
                XÓC ĐĨA CỬU LONG VIP
              </h1>
              <p className="text-[12px] sm:text-[13px] text-amber-200/90 font-medium tracking-wide drop-shadow mt-1 min-h-[20px] transition-all duration-300">
                {statusText}
              </p>
            </div>

            {/* Golden Progress Bar - Sleek luxury track */}
            <div className="w-full h-2 rounded-full bg-black/80 border border-amber-500/50 p-[1.5px] shadow-[0_4px_20px_rgba(0,0,0,0.9),0_0_15px_rgba(245,158,11,0.25)] backdrop-blur-md">
              <div
                className="h-full rounded-full bg-gradient-to-r from-amber-600 via-yellow-400 to-amber-200 transition-all duration-100 relative shadow-[0_0_12px_rgba(250,204,21,0.9)]"
                style={{ width: `${progress}%` }}
              >
                {/* Shimmer light tip */}
                <div className="absolute right-0 top-0 bottom-0 w-2.5 bg-white/90 rounded-full blur-[1px]" />
              </div>
            </div>

            {/* Security & Percentage Row - Clean & Minimalist */}
            <div className="w-full flex items-center justify-between mt-2.5 text-[10px] sm:text-[11px] font-mono text-amber-300/80">
              <span className="tracking-wider uppercase drop-shadow text-slate-300">
                Mã hóa SHA-256 • Sảnh VIP #01
              </span>
              <span className="font-bold text-amber-200 drop-shadow tracking-wider">{progress}%</span>
            </div>
          </div>
        </div>
      ) : (
        <XocDiaLandscapeGame />
      )}
    </GameOrientationWrapper>
  );
}
