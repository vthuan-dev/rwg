"use client";

import React, { useEffect, useMemo } from "react";

interface JackpotCoinShowerProps {
  active: boolean;
  winnerName: string;
  amount: number;
  targetX?: number;
  targetY?: number;
  onDone?: () => void;
}

interface RainCoin {
  id: number;
  left: number;
  delay: number;
  duration: number;
  size: number;
  drift: number;
}

interface FlyCoin {
  id: number;
  fromX: number;
  fromY: number;
  delay: number;
  duration: number;
  size: number;
}

export const JackpotCoinShower: React.FC<JackpotCoinShowerProps> = ({
  active,
  winnerName,
  amount,
  targetX = 50,
  targetY = 88,
  onDone,
}) => {
  const rainCoins = useMemo<RainCoin[]>(
    () =>
      Array.from({ length: 90 }, (_, i) => ({
        id: i,
        left: Math.random() * 100,
        delay: Math.random() * 1.6,
        duration: 2.2 + Math.random() * 2.2,
        size: 10 + Math.random() * 16,
        drift: (Math.random() - 0.5) * 120,
      })),
    [active]
  );

  const flyCoins = useMemo<FlyCoin[]>(
    () =>
      Array.from({ length: 22 }, (_, i) => ({
        id: i,
        fromX: 38 + Math.random() * 24,
        fromY: 30 + Math.random() * 18,
        delay: 1.1 + Math.random() * 1.4,
        duration: 1.1 + Math.random() * 0.9,
        size: 12 + Math.random() * 12,
      })),
    [active]
  );

  useEffect(() => {
    if (!active || !onDone) return;
    const t = setTimeout(onDone, 6500);
    return () => clearTimeout(t);
  }, [active, onDone]);

  if (!active) return null;

  return (
    <div className="absolute inset-0 z-[75] pointer-events-none overflow-hidden">
      <style>{`
        @keyframes jp-coin-fall {
          0% { transform: translate3d(0, -40px, 0) rotateY(0deg); opacity: 0; }
          8% { opacity: 1; }
          100% { transform: translate3d(var(--drift, 0px), 105vh, 0) rotateY(720deg); opacity: 0.9; }
        }
        @keyframes jp-coin-fly {
          0% { transform: translate3d(0, 0, 0) scale(0.6); opacity: 0; }
          12% { opacity: 1; }
          100% { transform: translate3d(var(--tx, 0px), var(--ty, 0px), 0) scale(1.15); opacity: 1; }
        }
        @keyframes jp-flash {
          0%, 100% { opacity: 0.25; }
          50% { opacity: 0.7; }
        }
      `}</style>
      <div
        className="absolute inset-0 bg-gradient-to-b from-amber-300/25 via-transparent to-amber-500/20"
        style={{ animation: "jp-flash 0.9s ease-in-out 4" }}
      />
      {rainCoins.map((c) => (
        <div
          key={`rain-${c.id}`}
          className="absolute top-0 rounded-full flex items-center justify-center font-black text-amber-950"
          style={{
            left: `${c.left}%`,
            width: c.size,
            height: c.size,
            fontSize: c.size * 0.55,
            background:
              "radial-gradient(circle at 30% 30%, #fff7c2 0%, #ffd94d 35%, #f59e0b 65%, #b45309 100%)",
            border: "1.5px solid #fff3b0",
            boxShadow: "0 0 8px rgba(245,158,11,0.9), inset 0 -2px 4px rgba(120,53,15,0.6)",
            ["--drift" as string]: `${c.drift}px`,
            animation: `jp-coin-fall ${c.duration}s cubic-bezier(0.3,0.4,0.7,1) ${c.delay}s both`,
          }}
        >
          đ
        </div>
      ))}
      {flyCoins.map((c) => (
        <div
          key={`fly-${c.id}`}
          className="absolute rounded-full flex items-center justify-center font-black text-amber-950"
          style={{
            left: `${c.fromX}%`,
            top: `${c.fromY}%`,
            width: c.size,
            height: c.size,
            fontSize: c.size * 0.55,
            background:
              "radial-gradient(circle at 30% 30%, #fff7c2 0%, #ffd94d 35%, #f59e0b 65%, #b45309 100%)",
            border: "2px solid #fff3b0",
            boxShadow: "0 0 14px rgba(245,158,11,1), inset 0 -2px 4px rgba(120,53,15,0.6)",
            ["--tx" as string]: `${(targetX - c.fromX) * 4}px`,
            ["--ty" as string]: `${(targetY - c.fromY) * 4}px`,
            animation: `jp-coin-fly ${c.duration}s cubic-bezier(0.2,0.7,0.3,1) ${c.delay}s both`,
          }}
        >
          đ
        </div>
      ))}
      <div className="absolute top-[16%] left-1/2 -translate-x-1/2 flex flex-col items-center gap-1">
        <div className="px-5 py-1.5 rounded-full bg-black/70 border border-amber-300/80 text-amber-200 text-[12px] font-black tracking-[0.18em] uppercase shadow-[0_0_25px_rgba(245,158,11,0.7)]">
          {winnerName} no hu +{amount.toLocaleString()} d
        </div>
      </div>
    </div>
  );
};

export default JackpotCoinShower;
