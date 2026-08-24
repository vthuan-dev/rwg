"use client";

import React, { useEffect, useRef } from "react";

export const LotterySimulator: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const width = 360;
    const height = 80;
    canvas.width = width;
    canvas.height = height;

    let state: "MIX" | "DRAW" | "RESULT" | "WAIT" = "MIX";
    let timer = 0;

    // Simulation balls inside the cage
    const cage = { x: 55, y: 40, r: 28 };
    const balls: Array<{ x: number; y: number; vx: number; vy: number; color: string; val: number }> = [];
    const colors = ["#ef4444", "#3b82f6", "#22c55e", "#eab308", "#a855f7", "#ec4899"];

    // Initialize 15 balls inside the cage
    for (let i = 0; i < 15; i++) {
      const angle = Math.random() * Math.PI * 2;
      const dist = Math.random() * (cage.r - 8);
      balls.push({
        x: cage.x + Math.cos(angle) * dist,
        y: cage.y + Math.sin(angle) * dist,
        vx: (Math.random() - 0.5) * 5,
        vy: (Math.random() - 0.5) * 5,
        color: colors[i % colors.length],
        val: Math.floor(Math.random() * 10) + 1,
      });
    }

    // Results drawn
    let ball1 = { x: 125, y: 110, targetY: 40, val: 5, color: "#3b82f6" };
    let ball2 = { x: 145, y: 110, targetY: 40, val: 9, color: "#ef4444" };
    let ball3 = { x: 165, y: 110, targetY: 40, val: 4, color: "#22c55e" };
    let totalScore = 18;
    let bigSmall = "LỚN";
    let oddEven = "CHẴN";

    let animationId: number;

    const render = () => {
      // Draw background dark slate/gold
      const grad = ctx.createLinearGradient(0, 0, 0, height);
      grad.addColorStop(0, "#1e1b4b"); // Indigo/dark background
      grad.addColorStop(1, "#0f172a");
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, width, height);

      // Gold divider
      ctx.strokeStyle = "rgba(234, 179, 8, 0.12)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(110, 8);
      ctx.lineTo(110, height - 8);
      ctx.stroke();

      // Physics Bouncing Balls inside cage
      balls.forEach((b) => {
        // Apply random bounce boost in MIX state
        if (state === "MIX") {
          b.vx += (Math.random() - 0.5) * 0.4;
          b.vy += (Math.random() - 0.5) * 0.4;
        } else {
          // Slow down in draw state
          b.vx *= 0.95;
          b.vy *= 0.95;
        }

        // Update position
        b.x += b.vx;
        b.y += b.vy;

        // Collision with cage wall
        const dx = b.x - cage.x;
        const dy = b.y - cage.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        const maxDist = cage.r - 4; // ball radius is 3.5

        if (dist > maxDist) {
          // Normal vector
          const nx = dx / dist;
          const ny = dy / dist;

          // Reflect velocity vector
          const dot = b.vx * nx + b.vy * ny;
          b.vx -= 2 * dot * nx;
          b.vy -= 2 * dot * ny;

          // Push ball back inside cage slightly
          b.x = cage.x + nx * maxDist;
          b.y = cage.y + ny * maxDist;
        }

        // Draw ball
        ctx.fillStyle = b.color;
        ctx.beginPath();
        ctx.arc(b.x, b.y, 3, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = "rgba(255,255,255,0.4)";
        ctx.lineWidth = 0.5;
        ctx.stroke();
      });

      // Draw Glass Cage
      ctx.save();
      ctx.strokeStyle = "rgba(255,255,255,0.25)";
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(cage.x, cage.y, cage.r, 0, Math.PI * 2);
      ctx.stroke();

      // Cage metal support base
      ctx.strokeStyle = "#cbd5e1";
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.moveTo(cage.x - 30, cage.y + 26);
      ctx.lineTo(cage.x - 34, cage.y + 36);
      ctx.lineTo(cage.x + 34, cage.y + 36);
      ctx.lineTo(cage.x + 30, cage.y + 26);
      ctx.stroke();
      ctx.restore();

      // State machine logic
      timer++;
      if (state === "MIX") {
        if (timer > 90) {
          state = "DRAW";
          timer = 0;
          // Generate new dynamic result
          const v1 = Math.floor(Math.random() * 10); // 0-9
          const v2 = Math.floor(Math.random() * 10);
          const v3 = Math.floor(Math.random() * 10);
          totalScore = v1 + v2 + v3;

          ball1 = { x: 125, y: 90, targetY: 40, val: v1, color: colors[v1 % colors.length] };
          ball2 = { x: 145, y: 90, targetY: 40, val: v2, color: colors[v2 % colors.length] };
          ball3 = { x: 165, y: 90, targetY: 40, val: v3, color: colors[v3 % colors.length] };

          bigSmall = totalScore >= 14 ? "LỚN" : "NHỎ";
          oddEven = totalScore % 2 === 1 ? "LẺ" : "CHẴN";
        }
      } else if (state === "DRAW") {
        // Roll balls down
        if (timer > 15) ball1.y += (ball1.targetY - ball1.y) * 0.12;
        if (timer > 30) ball2.y += (ball2.targetY - ball2.y) * 0.12;
        if (timer > 45) ball3.y += (ball3.targetY - ball3.y) * 0.12;

        if (timer > 75) {
          state = "RESULT";
          timer = 0;
        }
      } else if (state === "RESULT") {
        if (timer > 100) {
          state = "WAIT";
          timer = 0;
        }
      } else if (state === "WAIT") {
        if (timer > 80) {
          state = "MIX";
          timer = 0;
          // Reset balls positions
          balls.forEach((b) => {
            const angle = Math.random() * Math.PI * 2;
            const dist = Math.random() * (cage.r - 8);
            b.x = cage.x + Math.cos(angle) * dist;
            b.y = cage.y + Math.sin(angle) * dist;
            b.vx = (Math.random() - 0.5) * 5;
            b.vy = (Math.random() - 0.5) * 5;
          });
        }
      }

      // Draw Tube/Pipe under the cage
      ctx.strokeStyle = "rgba(255, 255, 255, 0.15)";
      ctx.lineWidth = 8;
      ctx.beginPath();
      ctx.moveTo(cage.x + 10, cage.y + 24);
      ctx.lineTo(125, 40);
      ctx.stroke();

      // Render 3 drawn balls
      if (state !== "MIX") {
        const renderBall = (b: typeof ball1) => {
          if (b.y > height) return;
          ctx.save();
          ctx.fillStyle = b.color;
          ctx.beginPath();
          ctx.arc(b.x, b.y, 8, 0, Math.PI * 2);
          ctx.fill();
          ctx.strokeStyle = "#ffffff";
          ctx.lineWidth = 1;
          ctx.stroke();

          // Ball text
          ctx.fillStyle = "#ffffff";
          ctx.font = "bold 9px sans-serif";
          ctx.textAlign = "center";
          ctx.textBaseline = "middle";
          ctx.fillText(b.val.toString(), b.x, b.y + 0.5);
          ctx.restore();
        };

        renderBall(ball1);
        renderBall(ball2);
        renderBall(ball3);
      }

      // Draw dashboard info on the right
      ctx.fillStyle = "rgba(255,255,255,0.15)";
      ctx.font = "bold 9px sans-serif";
      ctx.textAlign = "left";
      ctx.fillText("KẾT QUẢ", 188, 23);

      if (state === "MIX") {
        ctx.fillStyle = "#a855f7";
        ctx.font = "black 12px sans-serif";
        ctx.fillText("ĐANG QUAY...", 188, 42);

        ctx.fillStyle = "rgba(255,255,255,0.4)";
        ctx.font = "medium 9px sans-serif";
        ctx.fillText("Trộn lồng cầu...", 188, 56);
      } else if (state === "DRAW") {
        ctx.fillStyle = "#3b82f6";
        ctx.font = "bold 11px sans-serif";
        ctx.fillText("ĐANG XUẤT BÓNG", 188, 42);
      } else {
        // Show result expression
        ctx.fillStyle = "#ffffff";
        ctx.font = "bold 11px monospace";
        ctx.fillText(`${ball1.val} + ${ball2.val} + ${ball3.val} = `, 188, 38);

        // Sum result circle
        ctx.save();
        ctx.fillStyle = "#eab308";
        ctx.beginPath();
        ctx.arc(262, 34, 10, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = "#0f172a";
        ctx.font = "bold 11px sans-serif";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(totalScore.toString(), 262, 34);
        ctx.restore();

        // Badges for Large/Small and Odd/Even
        const drawBadge = (txt: string, x: number, isBig: boolean) => {
          ctx.save();
          ctx.fillStyle = isBig ? "#ef4444" : "#3b82f6";
          ctx.beginPath();
          ctx.roundRect(x, 48, 32, 14, 4);
          ctx.fill();

          ctx.fillStyle = "#ffffff";
          ctx.font = "bold 8px sans-serif";
          ctx.textAlign = "center";
          ctx.textBaseline = "middle";
          ctx.fillText(txt, x + 16, 55);
          ctx.restore();
        };

        drawBadge(bigSmall, 188, bigSmall === "LỚN");
        drawBadge(oddEven, 226, oddEven === "LẺ");
      }

      // Live tag top right
      ctx.save();
      ctx.fillStyle = state === "MIX" ? "rgba(255,255,255,0.15)" : "#ef4444";
      ctx.beginPath();
      ctx.roundRect(310, 14, 38, 12, 3);
      ctx.fill();
      ctx.fillStyle = "#ffffff";
      ctx.font = "bold 7px sans-serif";
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(state === "MIX" ? "SHUFFLE" : "LIVE", 329, 20);
      ctx.restore();

      animationId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationId);
    };
  }, []);

  return (
    <div className="w-full h-[80px] bg-[#0f172a] rounded-xl overflow-hidden shadow-inner border border-slate-900 flex items-center justify-center">
      <canvas ref={canvasRef} className="block w-full h-full" />
    </div>
  );
};
