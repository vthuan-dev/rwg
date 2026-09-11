"use client";

import React, { useState, useEffect, useCallback, createContext, useContext } from "react";
import { RotateCw, Smartphone } from "lucide-react";

interface OrientationContextType {
  isPortrait: boolean;
  isRotated: boolean;
  rotationDeg: number;
  effectiveWidth: number;
  effectiveHeight: number;
  toggleRotation: () => void;
}

const OrientationContext = createContext<OrientationContextType>({
  isPortrait: false,
  isRotated: false,
  rotationDeg: 0,
  effectiveWidth: 1024,
  effectiveHeight: 507,
  toggleRotation: () => {},
});

export const useOrientation = () => useContext(OrientationContext);

export const GameOrientationWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [mounted, setMounted] = useState(false);
  const [windowSize, setWindowSize] = useState({ width: 0, height: 0 });
  // Mode: "auto" (rotate 90deg on portrait), "inverted" (270deg), "portrait" (0deg force unrotated)
  const [mode, setMode] = useState<"auto" | "inverted" | "portrait">("auto");

  useEffect(() => {
    setMounted(true);
    const updateSize = () => {
      setWindowSize({
        width: window.innerWidth,
        height: window.innerHeight,
      });
    };

    updateSize();
    window.addEventListener("resize", updateSize);
    window.addEventListener("orientationchange", updateSize);
    if (typeof screen !== "undefined" && screen.orientation) {
      screen.orientation.addEventListener("change", updateSize);
    }

    const t1 = setTimeout(updateSize, 100);
    const t2 = setTimeout(updateSize, 300);

    return () => {
      window.removeEventListener("resize", updateSize);
      window.removeEventListener("orientationchange", updateSize);
      if (typeof screen !== "undefined" && screen.orientation) {
        screen.orientation.removeEventListener("change", updateSize);
      }
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, []);

  const w = windowSize.width || 1024;
  const h = windowSize.height || 507;
  const isNaturalPortrait = h > w;

  let isRotated = false;
  let rotationDeg = 0;

  if (mode === "auto") {
    if (isNaturalPortrait) {
      isRotated = true;
      rotationDeg = 90;
    } else {
      isRotated = false;
      rotationDeg = 0;
    }
  } else if (mode === "inverted") {
    isRotated = true;
    rotationDeg = 270;
  } else if (mode === "portrait") {
    isRotated = false;
    rotationDeg = 0;
  }

  // Effective dimensions for the inner content (always landscape aspect ratio when rotated)
  const effectiveWidth = isRotated ? Math.max(w, h) : w;
  const effectiveHeight = isRotated ? Math.min(w, h) : h;

  const toggleRotation = useCallback(() => {
    setMode((prev) => {
      if (prev === "auto") return "inverted";
      if (prev === "inverted") return "portrait";
      return "auto";
    });
  }, []);

  if (!mounted) {
    return (
      <div className="relative w-full h-full bg-[#050302] flex items-center justify-center select-none overflow-hidden font-sans">
        {children}
      </div>
    );
  }

  return (
    <OrientationContext.Provider
      value={{
        isPortrait: isNaturalPortrait,
        isRotated,
        rotationDeg,
        effectiveWidth,
        effectiveHeight,
        toggleRotation,
      }}
    >
      <div
        className="fixed inset-0 w-screen h-[100dvh] bg-[#050302] overflow-hidden select-none"
        style={{ touchAction: "none" }}
      >
        <div
          style={{
            position: "absolute",
            // Khi xoay 90deg: chiều rộng visual = chiều cao viewport, chiều cao visual = chiều rộng viewport.
            // Phải gán NGƯỢC lại vì CSS rotate chỉ xoay hình ảnh, không đổi kích thước layout box.
            width: isRotated ? `${effectiveHeight}px` : "100%",
            height: isRotated ? `${effectiveWidth}px` : "100%",
            left: "50%",
            top: "50%",
            transform: isRotated
              ? `translate(-50%, -50%) rotate(${rotationDeg}deg)`
              : "translate(-50%, -50%)",
            transformOrigin: "center center",
            overflow: "hidden",
          }}
          className="bg-[#050302] flex items-center justify-center"
        >
          {children}
        </div>

        {/* Floating Rotation Control (Visible when screen is held vertically) */}
        {isNaturalPortrait && (
          <button
            onClick={toggleRotation}
            title="Đổi hướng xoay màn hình (90° / 270° / Gốc)"
            className="fixed top-3 right-3 z-[1000] flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-black/80 hover:bg-black/95 active:scale-95 text-amber-300 border border-amber-500/50 backdrop-blur-md shadow-[0_4px_16px_rgba(0,0,0,0.85)] text-xs font-semibold tracking-wide transition-all"
          >
            <RotateCw className="w-3.5 h-3.5 text-amber-400" />
            <span className="text-[11px] font-mono">
              {rotationDeg === 90 ? "Xoay 90°" : rotationDeg === 270 ? "Xoay 270°" : "Màn hình dọc"}
            </span>
          </button>
        )}
      </div>
    </OrientationContext.Provider>
  );
};
