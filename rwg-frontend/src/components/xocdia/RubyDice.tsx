"use client";

import React, { useEffect, useState } from "react";

interface RubyDiceProps {
  value: number; // 1 to 6
  size?: number; // width & height in px (default: 26)
  isRolling?: boolean;
  className?: string;
}

export const RubyDice: React.FC<RubyDiceProps> = ({
  value,
  size = 26,
  isRolling = false,
  className = "",
}) => {
  const [displayValue, setDisplayValue] = useState(value);

  // When isRolling is active, rapidly cycle random faces for tactile 3D tumble
  useEffect(() => {
    if (!isRolling) {
      setDisplayValue(value);
      return;
    }
    const interval = setInterval(() => {
      setDisplayValue(Math.floor(Math.random() * 6) + 1);
    }, 60);
    return () => clearInterval(interval);
  }, [isRolling, value]);

  // Dot size proportional to dice size (~18% of dice size)
  const dotSize = Math.max(3, Math.round(size * 0.18));

  // Determine pip locations for 3x3 grid (index 0..8)
  // Grid layout:
  // 0 1 2
  // 3 4 5
  // 6 7 8
  const getActivePips = (val: number): number[] => {
    switch (val) {
      case 1:
        return [4];
      case 2:
        return [0, 8];
      case 3:
        return [0, 4, 8];
      case 4:
        return [0, 2, 6, 8];
      case 5:
        return [0, 2, 4, 6, 8];
      case 6:
        return [0, 2, 3, 5, 6, 8];
      default:
        return [4];
    }
  };

  const activePips = getActivePips(displayValue);

  return (
    <div
      style={{
        width: `${size}px`,
        height: `${size}px`,
        borderRadius: `${Math.max(3, Math.round(size * 0.22))}px`,
      }}
      className={`relative select-none flex-shrink-0 flex items-center justify-center bg-gradient-to-br from-[#ff3b5c] via-[#dc143c] to-[#7f0019] border border-amber-300/80 shadow-[inset_0_1px_2px_rgba(255,255,255,0.7),inset_0_-1px_3px_rgba(0,0,0,0.6),0_2px_6px_rgba(0,0,0,0.6)] ${
        isRolling ? "animate-spin scale-105" : ""
      } ${className}`}
    >
      {/* 3x3 pip grid */}
      <div
        className="w-full h-full p-[12%] grid grid-cols-3 grid-rows-3 items-center justify-items-center"
      >
        {[0, 1, 2, 3, 4, 5, 6, 7, 8].map((idx) => {
          const isActive = activePips.includes(idx);
          if (!isActive) return <div key={idx} className="w-full h-full" />;

          const isCenterDot = idx === 4 && displayValue === 1;
          const pipSize = isCenterDot ? Math.round(dotSize * 1.35) : dotSize;

          return (
            <div
              key={idx}
              style={{
                width: `${pipSize}px`,
                height: `${pipSize}px`,
              }}
              className={`rounded-full shadow-[inset_0_1px_1.5px_rgba(0,0,0,0.7),0_0.5px_1px_rgba(255,255,255,0.6)] ${
                isCenterDot && displayValue === 1
                  ? "bg-gradient-to-b from-white to-slate-200"
                  : "bg-white"
              }`}
            />
          );
        })}
      </div>
    </div>
  );
};
