"use client";

import React, { createContext, useContext, useState, useEffect, useCallback } from "react";

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
  const [isPortrait, setIsPortrait] = useState(false);
  // Mode: "auto" (rotate 90deg on portrait), "inverted" (270deg), "portrait" (0deg force unrotated)
  const [mode, setMode] = useState<"auto" | "inverted" | "portrait">("auto");

  useEffect(() => {
    setMounted(true);

    const checkOrientation = () => {
      // Dùng window.innerHeight > window.innerWidth để phát hiện portrait
      // vì media query orientation đôi khi không chính xác trên mobile browser có thanh URL
      const portrait = window.innerHeight > window.innerWidth;
      setIsPortrait(portrait);
    };

    checkOrientation();
    window.addEventListener("resize", checkOrientation);
    window.addEventListener("orientationchange", () => {
      // orientationchange fire trước khi kích thước thực sự đổi, delay 100ms
      setTimeout(checkOrientation, 100);
      setTimeout(checkOrientation, 300);
    });

    return () => {
      window.removeEventListener("resize", checkOrientation);
    };
  }, []);

  let isRotated = false;
  let rotationDeg = 0;

  if (mode === "auto") {
    if (isPortrait) {
      isRotated = true;
      rotationDeg = 90;
    }
  } else if (mode === "inverted") {
    isRotated = true;
    rotationDeg = 270;
  }

  // Khi xoay: effective dimensions là kích thước mà game "nhìn thấy" sau khi xoay
  // Portrait phone: innerWidth ~390, innerHeight ~844
  // Sau rotate 90deg: game thấy width=844, height=390
  const effectiveWidth = isRotated ? Math.max(window.innerWidth, window.innerHeight || 0) : (typeof window !== "undefined" ? window.innerWidth : 1024);
  const effectiveHeight = isRotated ? Math.min(window.innerWidth, window.innerHeight || 0) : (typeof window !== "undefined" ? window.innerHeight : 507);

  const toggleRotation = useCallback(() => {
    setMode((prev) => {
      if (prev === "auto") return "inverted";
      if (prev === "inverted") return "portrait";
      return "auto";
    });
  }, []);

  if (!mounted) {
    return <>{children}</>;
  }

  return (
    <OrientationContext.Provider
      value={{
        isPortrait,
        isRotated,
        rotationDeg,
        effectiveWidth,
        effectiveHeight,
        toggleRotation,
      }}
    >
      {/* 
        CSS-only landscape rotation hack cho mobile portrait.
        Dùng pure CSS @media query thay vì inline style + JS state
        để tránh race condition giữa render và resize event.
        
        Kỹ thuật: transform-origin: top left + rotate(90deg) + translate
        Đây là cách chuẩn nhất được các web game sử dụng.
      */}
      <style jsx global>{`
        .game-orientation-wrapper {
          width: 100vw;
          height: 100vh;
          height: 100dvh;
          overflow: hidden;
          position: relative;
          background: #050302;
        }

        @media screen and (orientation: portrait) and (pointer: coarse) {
          .game-orientation-wrapper.game-auto-rotate {
            position: fixed;
            top: 0;
            left: 0;
            width: 100vh;
            width: 100dvh;
            height: 100vw;
            transform: rotate(90deg);
            transform-origin: top left;
            margin-left: 100vw;
          }

          .game-orientation-wrapper.game-inverted-rotate {
            position: fixed;
            top: 0;
            left: 0;
            width: 100vh;
            width: 100dvh;
            height: 100vw;
            transform: rotate(-90deg);
            transform-origin: bottom left;
            margin-top: 100vh;
            margin-top: 100dvh;
          }
        }

        /* Nút toggle rotation - chỉ hiện trên mobile portrait */
        .rotation-toggle-btn {
          display: none;
        }

        @media screen and (orientation: portrait) and (pointer: coarse) {
          .rotation-toggle-btn {
            display: flex;
            position: fixed;
            top: 12px;
            right: 12px;
            z-index: 10000;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            border-radius: 9999px;
            background: rgba(0, 0, 0, 0.8);
            border: 1px solid rgba(245, 158, 11, 0.5);
            backdrop-filter: blur(8px);
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.85);
            color: #fcd34d;
            font-size: 11px;
            font-weight: 600;
            letter-spacing: 0.02em;
            cursor: pointer;
            transition: all 0.15s;
            /* Nút này cũng phải bị xoay theo wrapper */
            transform: rotate(90deg);
            transform-origin: center;
            /* Đẩy nút ra vị trí đúng sau khi rotate */
            top: auto;
            bottom: 12px;
            right: auto;
            left: 12px;
          }

          .game-orientation-wrapper.game-inverted-rotate ~ .rotation-toggle-btn,
          .game-inverted-rotate .rotation-toggle-btn {
            transform: rotate(-90deg);
            top: 12px;
            bottom: auto;
            left: auto;
            right: 12px;
          }
        }
      `}</style>

      <div
        className={`game-orientation-wrapper ${
          mode === "auto" && isPortrait ? "game-auto-rotate" : ""
        } ${
          mode === "inverted" && isPortrait ? "game-inverted-rotate" : ""
        }`}
        style={{ touchAction: "none" }}
      >
        {children}
      </div>

      {/* Floating Rotation Toggle */}
      {isPortrait && (
        <button
          onClick={toggleRotation}
          className="rotation-toggle-btn"
          title="Đổi hướng xoay"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8" />
            <path d="M21 3v5h-5" />
          </svg>
          <span style={{ fontFamily: "monospace" }}>
            {mode === "auto" ? "Xoay 90°" : mode === "inverted" ? "Xoay 270°" : "Dọc"}
          </span>
        </button>
      )}
    </OrientationContext.Provider>
  );
};
