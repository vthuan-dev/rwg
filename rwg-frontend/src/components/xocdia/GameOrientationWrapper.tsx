"use client";

import React, { createContext, useContext, useState, useEffect, useCallback } from "react";

interface OrientationContextType {
  isPortrait: boolean;
  isRotated: boolean;
  rotationDeg: number;
  effectiveWidth: number;
  effectiveHeight: number;
  toggleFullscreen: () => void;
  isFullscreen: boolean;
}

const OrientationContext = createContext<OrientationContextType>({
  isPortrait: false,
  isRotated: false,
  rotationDeg: 0,
  effectiveWidth: 1024,
  effectiveHeight: 507,
  toggleFullscreen: () => {},
  isFullscreen: false,
});

export const useOrientation = () => useContext(OrientationContext);

export const GameOrientationWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [mounted, setMounted] = useState(false);
  const [isPortrait, setIsPortrait] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [dimensions, setDimensions] = useState({ w: 1024, h: 507 });

  const checkOrientation = useCallback(() => {
    if (typeof window === "undefined") return;
    const w = window.innerWidth;
    const h = window.innerHeight;
    const portrait = h > w;
    const mobile =
      portrait &&
      (window.matchMedia("(pointer: coarse)").matches ||
        "ontouchstart" in window ||
        navigator.maxTouchPoints > 0 ||
        w < 768);

    setIsPortrait(portrait);
    setIsMobile(mobile);
    setDimensions({ w, h });
    setIsFullscreen(!!document.fullscreenElement);
  }, []);

  useEffect(() => {
    setMounted(true);
    checkOrientation();

    // Thử khoá màn hình ngang bằng Screen Orientation API (hỗ trợ trên Android Chrome, PWA)
    try {
      if (typeof screen !== "undefined" && screen.orientation && "lock" in screen.orientation) {
        (screen.orientation as any).lock("landscape").catch(() => {
          // Trình duyệt chặn (như iOS Safari) -> không lỗi, dùng xoay ngang tự nhiên
        });
      }
    } catch {
      // silent fallback
    }

    window.addEventListener("resize", checkOrientation);
    window.addEventListener("orientationchange", () => {
      setTimeout(checkOrientation, 100);
      setTimeout(checkOrientation, 300);
    });
    document.addEventListener("fullscreenchange", checkOrientation);

    return () => {
      window.removeEventListener("resize", checkOrientation);
      document.removeEventListener("fullscreenchange", checkOrientation);
    };
  }, [checkOrientation]);

  const toggleFullscreen = useCallback(() => {
    if (typeof document === "undefined") return;
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen?.().catch(() => {});
      try {
        if (screen.orientation && "lock" in screen.orientation) {
          (screen.orientation as any).lock("landscape").catch(() => {});
        }
      } catch {}
    } else {
      document.exitFullscreen?.().catch(() => {});
    }
  }, []);

  if (!mounted) {
    return <>{children}</>;
  }

  // Luôn là True Landscape tự nhiên, không dùng CSS rotate(90deg)
  const isRotated = false;
  const rotationDeg = 0;
  const effectiveWidth = dimensions.w;
  const effectiveHeight = dimensions.h;

  return (
    <OrientationContext.Provider
      value={{
        isPortrait,
        isRotated,
        rotationDeg,
        effectiveWidth,
        effectiveHeight,
        toggleFullscreen,
        isFullscreen,
      }}
    >
      <div className="game-orientation-container relative w-full h-full overflow-hidden bg-[#050302]">
        {/* Nếu đang cầm điện thoại dọc: Hiển thị màn hình nhắc xoay ngang sang trọng chuẩn sảnh VIP */}
        {isPortrait && isMobile ? (
          <div className="fixed inset-0 z-[9999] flex flex-col items-center justify-center bg-[#070403] text-white select-none px-6 text-center">
            {/* Background luxury gradient */}
            <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,rgba(217,119,6,0.15)_0%,rgba(0,0,0,0.95)_75%)] pointer-events-none" />

            {/* VIP Brand Badge */}
            <div className="relative mb-8 flex items-center gap-2 px-4 py-1.5 rounded-full bg-black/60 border border-amber-500/40 backdrop-blur-md shadow-lg">
              <div className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
              <span className="text-[11px] font-mono font-bold tracking-[0.2em] text-amber-300 uppercase">
                Genting VIP • Sảnh Xóc Đĩa
              </span>
            </div>

            {/* Rotating Phone Animation Icon */}
            <div className="relative w-28 h-28 mb-6 flex items-center justify-center">
              <div className="absolute inset-0 rounded-full bg-amber-500/10 blur-xl animate-pulse" />
              <div className="w-16 h-24 rounded-2xl border-2 border-amber-400/90 shadow-[0_0_25px_rgba(245,158,11,0.5)] flex flex-col items-center justify-between p-2 animate-[spin_3s_ease-in-out_infinite] [animation-direction:alternate]">
                {/* Loa thoại */}
                <div className="w-4 h-1 rounded-full bg-amber-300/80" />
                {/* Biểu tượng bàn cược */}
                <div className="w-8 h-8 rounded-lg bg-amber-500/20 border border-amber-400/40 flex items-center justify-center">
                  <span className="text-xs font-black text-amber-300">🎲</span>
                </div>
                {/* Home bar */}
                <div className="w-6 h-1 rounded-full bg-amber-400/60" />
              </div>
            </div>

            {/* Instruction Headline */}
            <h2 className="relative text-xl sm:text-2xl font-black uppercase tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-amber-100 via-amber-300 to-yellow-500 drop-shadow-md mb-2">
              Vui lòng xoay ngang điện thoại
            </h2>

            <p className="relative text-xs sm:text-sm text-amber-200/80 max-w-xs leading-relaxed mb-6 font-medium">
              Xoay thiết bị sang chế độ nằm ngang để tận hưởng trọn vẹn không gian bàn cược Bát Đĩa Thần Long 4K.
            </p>

            {/* Quick Fullscreen Button */}
            <button
              onClick={toggleFullscreen}
              className="relative px-5 py-2.5 rounded-full bg-gradient-to-r from-amber-500 via-yellow-400 to-amber-600 text-black font-bold text-xs uppercase tracking-wider shadow-[0_4px_20px_rgba(245,158,11,0.4)] active:scale-95 transition-transform cursor-pointer"
            >
              ⛶ Chạm để vào toàn màn hình
            </button>
          </div>
        ) : (
          /* Khi thiết bị ở chế độ ngang (Landscape tự nhiên): Hiển thị bàn chơi full 100% */
          children
        )}
      </div>
    </OrientationContext.Provider>
  );
};
