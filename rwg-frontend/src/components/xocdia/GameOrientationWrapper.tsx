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
            /* Dùng dvh/dvw để loại trừ thanh URL Safari và home indicator */
            width: 100dvh;
            height: 100dvw;
            transform: rotate(90deg);
            transform-origin: top left;
            margin-left: 100vw;
          }

          .game-orientation-wrapper.game-inverted-rotate {
            position: fixed;
            top: 0;
            left: 0;
            width: 100dvh;
            height: 100dvw;
            transform: rotate(-90deg);
            transform-origin: bottom left;
            margin-top: 100dvh;
          }
        }

      `}</style>

      <div
        className={`game-orientation-wrapper ${
          mode === "auto" && isPortrait ? "game-auto-rotate" : ""
        } ${
          mode === "inverted" && isPortrait ? "game-inverted-rotate" : ""
        }`}
      >
        {children}
      </div>
    </OrientationContext.Provider>
  );
};
