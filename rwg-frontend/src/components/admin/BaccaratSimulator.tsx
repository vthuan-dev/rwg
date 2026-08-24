"use client";

import React, { useEffect, useRef } from "react";

export const BaccaratSimulator: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Set resolution
    const width = 360;
    const height = 80;
    canvas.width = width;
    canvas.height = height;

    // Cac pha animation dem bang stateTimer, khong can bien dem khung rieng.
    let state: "DEAL" | "FLIP" | "WIN" | "WAIT" = "DEAL";
    let stateTimer = 0;

    // Card positions and values
    const playerCard = { x: 50, y: -40, targetY: 15, width: 32, height: 46, val: "A♠", color: "#0f172a", angle: 0, flipProgress: 0 };
    const bankerCard = { x: width - 50 - 32, y: -40, targetY: 15, width: 32, height: 46, val: "9♥", color: "#e11d48", angle: 0, flipProgress: 0 };

    let animationId: number;

    const drawCard = (
      c: typeof playerCard, 
      label: string, 
      score: number, 
      showScore: boolean, 
      isWinner: boolean
    ) => {
      ctx.save();

      // Shadow for card
      ctx.shadowColor = "rgba(0,0,0,0.15)";
      ctx.shadowBlur = 4;
      ctx.shadowOffsetY = 2;

      if (state === "DEAL") {
        // Draw flying card (back side)
        ctx.fillStyle = "#e11d48"; // Red back
        ctx.strokeStyle = "#ffffff";
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.roundRect(c.x, c.y, c.width, c.height, 4);
        ctx.fill();
        ctx.stroke();

        // Pattern on back
        ctx.strokeStyle = "rgba(255,255,255,0.2)";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(c.x + 5, c.y + 5);
        ctx.lineTo(c.x + c.width - 5, c.y + c.height - 5);
        ctx.moveTo(c.x + c.width - 5, c.y + 5);
        ctx.lineTo(c.x + 5, c.y + c.height - 5);
        ctx.stroke();
      } else {
        // Flip animation or Face Up
        const scaleX = state === "FLIP" ? Math.abs(Math.cos(c.flipProgress)) : 1;
        const isBack = state === "FLIP" && c.flipProgress < Math.PI / 2;

        ctx.translate(c.x + c.width / 2, c.y + c.height / 2);
        ctx.scale(scaleX, 1);
        ctx.translate(-(c.x + c.width / 2), -(c.y + c.height / 2));

        if (isBack) {
          ctx.fillStyle = "#e11d48";
          ctx.strokeStyle = "#ffffff";
          ctx.lineWidth = 1.5;
          ctx.beginPath();
          ctx.roundRect(c.x, c.y, c.width, c.height, 4);
          ctx.fill();
          ctx.stroke();
        } else {
          // Draw Winner glowing border
          if (isWinner && state === "WIN") {
            ctx.shadowColor = "#eab308";
            ctx.shadowBlur = 10;
            ctx.strokeStyle = "#eab308";
            ctx.lineWidth = 2.5;
          } else {
            ctx.strokeStyle = "#cbd5e1";
            ctx.lineWidth = 1;
          }

          // Card white face
          ctx.fillStyle = "#ffffff";
          ctx.beginPath();
          ctx.roundRect(c.x, c.y, c.width, c.height, 4);
          ctx.fill();
          ctx.stroke();

          // Card value
          ctx.shadowBlur = 0; // Reset shadow for text
          ctx.fillStyle = c.color;
          ctx.font = "bold 13px Courier, monospace";
          ctx.textAlign = "center";
          ctx.textBaseline = "middle";
          ctx.fillText(c.val, c.x + c.width / 2, c.y + c.height / 2);

          // Card corner values (small)
          ctx.font = "8px monospace";
          ctx.fillText(c.val[0], c.x + 6, c.y + 8);
          ctx.fillText(c.val[0], c.x + c.width - 6, c.y + c.height - 8);
        }
      }
      ctx.restore();

      // Draw scores and labels
      if (showScore) {
        ctx.fillStyle = "rgba(15, 23, 42, 0.8)";
        ctx.font = "bold 9px sans-serif";
        ctx.textAlign = "center";
        ctx.fillText(label, c.x + c.width / 2, 70);

        // Score badge
        ctx.save();
        if (isWinner && state === "WIN") {
          ctx.fillStyle = "#eab308";
          ctx.strokeStyle = "#ca8a04";
        } else {
          ctx.fillStyle = "#475569";
          ctx.strokeStyle = "#334155";
        }
        ctx.beginPath();
        ctx.arc(c.x + c.width / 2, 15 + c.height, 8, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = "#ffffff";
        ctx.font = "bold 9px sans-serif";
        ctx.fillText(score.toString(), c.x + c.width / 2, 16 + c.height);
        ctx.restore();
      }
    };

    const render = () => {
      // Draw background
      const grad = ctx.createLinearGradient(0, 0, 0, height);
      grad.addColorStop(0, "#064e3b"); // Emerald table top
      grad.addColorStop(1, "#022c22");
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, width, height);

      // Gold lines
      ctx.strokeStyle = "rgba(234, 179, 8, 0.15)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(10, 8);
      ctx.lineTo(width - 10, 8);
      ctx.moveTo(10, height - 8);
      ctx.lineTo(width - 10, height - 8);
      ctx.stroke();

      // Divider
      ctx.strokeStyle = "rgba(255, 255, 255, 0.08)";
      ctx.beginPath();
      ctx.moveTo(width / 2, 8);
      ctx.lineTo(width / 2, height - 8);
      ctx.stroke();

      // State machine logic
      stateTimer++;
      if (state === "DEAL") {
        // Fly cards in
        playerCard.y += (playerCard.targetY - playerCard.y) * 0.12;
        bankerCard.y += (bankerCard.targetY - bankerCard.y) * 0.12;

        if (Math.abs(playerCard.y - playerCard.targetY) < 0.5) {
          playerCard.y = playerCard.targetY;
          bankerCard.y = bankerCard.targetY;
          state = "FLIP";
          stateTimer = 0;
          playerCard.flipProgress = 0;
          bankerCard.flipProgress = 0;
        }
      } else if (state === "FLIP") {
        playerCard.flipProgress = Math.min(Math.PI, playerCard.flipProgress + 0.08);
        bankerCard.flipProgress = Math.min(Math.PI, bankerCard.flipProgress + 0.08);

        if (playerCard.flipProgress >= Math.PI && bankerCard.flipProgress >= Math.PI) {
          state = "WIN";
          stateTimer = 0;
        }
      } else if (state === "WIN") {
        if (stateTimer > 90) {
          state = "WAIT";
          stateTimer = 0;
        }
      } else if (state === "WAIT") {
        if (stateTimer > 90) {
          // Reset for new round
          state = "DEAL";
          stateTimer = 0;
          playerCard.y = -60;
          bankerCard.y = -60;
        }
      }

      // Draw Player / Banker Labels
      ctx.fillStyle = "rgba(255, 255, 255, 0.2)";
      ctx.font = "black 20px sans-serif";
      ctx.textAlign = "center";
      ctx.fillText("PLAYER", width * 0.25, 45);
      ctx.fillText("BANKER", width * 0.75, 45);

      // Render cards
      const showScore = state !== "DEAL";
      drawCard(playerCard, "PLAYER", 8, showScore, true); // Player wins with 8
      drawCard(bankerCard, "BANKER", 6, showScore, false);

      animationId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationId);
    };
  }, []);

  return (
    <div className="w-full h-[80px] bg-[#022c22] rounded-xl overflow-hidden shadow-inner border border-emerald-950 flex items-center justify-center">
      <canvas ref={canvasRef} className="block w-full h-full" />
    </div>
  );
};
