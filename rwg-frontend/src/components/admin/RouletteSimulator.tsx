"use client";

import React, { useEffect, useRef } from "react";

export const RouletteSimulator: React.FC = () => {
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

    let state: "SPIN" | "STOP" | "RESULT" | "WAIT" = "SPIN";
    let timer = 0;
    let wheelAngle = 0;
    let ballAngle = 0;
    let ballSpeed = 0.15;
    let ballRadius = 24;
    let resultNumber = 17;
    let resultColor = "#0f172a"; // Black

    const numbers = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26];

    let animationId: number;

    const render = () => {
      // Draw background dark slate/gold
      const grad = ctx.createLinearGradient(0, 0, 0, height);
      grad.addColorStop(0, "#0f172a");
      grad.addColorStop(1, "#020617");
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, width, height);

      // Gold divider
      ctx.strokeStyle = "rgba(234, 179, 8, 0.12)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(130, 8);
      ctx.lineTo(130, height - 8);
      ctx.stroke();

      // Draw Roulette Wheel
      const wheelX = 65;
      const wheelY = 40;
      const wheelR = 30;

      ctx.save();
      ctx.translate(wheelX, wheelY);
      ctx.rotate(wheelAngle);

      // Outer wood rim shadow
      ctx.shadowColor = "rgba(0, 0, 0, 0.5)";
      ctx.shadowBlur = 8;
      ctx.fillStyle = "#451a03"; // Wood
      ctx.beginPath();
      ctx.arc(0, 0, wheelR + 4, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;

      // Outer gold rim
      ctx.strokeStyle = "#ca8a04";
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.arc(0, 0, wheelR, 0, Math.PI * 2);
      ctx.stroke();

      // Draw sectors (Red / Black / Green)
      const numSectors = 37;
      const sectorAngle = (Math.PI * 2) / numSectors;

      for (let i = 0; i < numSectors; i++) {
        ctx.save();
        ctx.rotate(i * sectorAngle);

        ctx.beginPath();
        ctx.moveTo(0, 0);
        ctx.arc(0, 0, wheelR - 1, -sectorAngle / 2, sectorAngle / 2);
        ctx.closePath();

        const num = numbers[i];
        if (num === 0) {
          ctx.fillStyle = "#22c55e"; // Green 0
        } else if (i % 2 === 1) {
          ctx.fillStyle = "#ef4444"; // Red
        } else {
          ctx.fillStyle = "#1e293b"; // Black
        }
        ctx.fill();
        ctx.restore();
      }

      // Golden turret center
      ctx.fillStyle = "#eab308";
      ctx.beginPath();
      ctx.arc(0, 0, 8, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = "#fef08a";
      ctx.beginPath();
      ctx.arc(0, 0, 3, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();

      // State machine logic
      timer++;
      if (state === "SPIN") {
        wheelAngle += 0.02;
        ballAngle -= ballSpeed;
        ballSpeed = Math.max(0.1, ballSpeed - 0.0005);

        if (timer > 100) {
          state = "STOP";
          timer = 0;
          const idx = Math.floor(Math.random() * numbers.length);
          resultNumber = numbers[idx];
          if (resultNumber === 0) resultColor = "#22c55e";
          else if (idx % 2 === 1) resultColor = "#ef4444";
          else resultColor = "#1e293b";
        }
      } else if (state === "STOP") {
        wheelAngle += 0.01;
        // Ball slowly locks to the wheel's angle + offset
        ballAngle += (wheelAngle - ballAngle) * 0.1;
        ballRadius = Math.max(14, ballRadius - 0.5);

        if (timer > 50) {
          state = "RESULT";
          timer = 0;
        }
      } else if (state === "RESULT") {
        wheelAngle += 0.003;
        ballAngle = wheelAngle;
        ballRadius = 14;

        if (timer > 90) {
          state = "WAIT";
          timer = 0;
        }
      } else if (state === "WAIT") {
        wheelAngle += 0.003;
        ballAngle = wheelAngle;
        ballRadius = 14;

        if (timer > 60) {
          state = "SPIN";
          timer = 0;
          ballSpeed = 0.18;
          ballRadius = 24;
        }
      }

      // Draw Ball (only if not waiting)
      if (state !== "WAIT") {
        const ballX = wheelX + Math.cos(ballAngle) * ballRadius;
        const ballY = wheelY + Math.sin(ballAngle) * ballRadius;
        ctx.save();
        ctx.fillStyle = "#ffffff";
        ctx.shadowColor = "#ffffff";
        ctx.shadowBlur = 4;
        ctx.beginPath();
        ctx.arc(ballX, ballY, 3, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
      }

      // Draw Info Dashboard on the right
      ctx.fillStyle = "rgba(255,255,255,0.15)";
      ctx.font = "bold 9px sans-serif";
      ctx.textAlign = "left";
      ctx.fillText("TRẠNG THÁI", 145, 23);

      if (state === "SPIN" || state === "STOP") {
        ctx.fillStyle = "#eab308";
        ctx.font = "black 13px sans-serif";
        ctx.fillText("ĐANG QUAY...", 145, 42);

        ctx.fillStyle = "rgba(255,255,255,0.4)";
        ctx.font = "medium 9px sans-serif";
        ctx.fillText("Đang chờ bóng rơi", 145, 56);
      } else {
        // Show result
        ctx.fillStyle = "#22c55e";
        ctx.font = "black 11px sans-serif";
        ctx.fillText("VÀO Ô SỐ", 145, 38);

        // Result Number badge
        ctx.save();
        ctx.fillStyle = resultColor;
        ctx.strokeStyle = "rgba(255,255,255,0.2)";
        ctx.lineWidth = 1;
        ctx.shadowColor = resultColor;
        ctx.shadowBlur = state === "RESULT" ? 8 : 0;

        ctx.beginPath();
        ctx.roundRect(220, 16, 42, 42, 8);
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = "#ffffff";
        ctx.font = "bold 20px monospace";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(resultNumber.toString(), 241, 37);
        ctx.restore();

        // Under-text
        ctx.fillStyle = "rgba(255,255,255,0.5)";
        ctx.font = "bold 9px sans-serif";
        ctx.textAlign = "left";
        const colorName = resultColor === "#22c55e" ? "Green" : resultColor === "#ef4444" ? "Red" : "Black";
        ctx.fillText(`${colorName} (Thắng)`, 145, 56);
      }

      // Statistics grid (mock numbers history)
      ctx.fillStyle = "rgba(255,255,255,0.2)";
      ctx.font = "bold 8px sans-serif";
      ctx.fillText("LỊCH SỬ:", 282, 23);

      const history = [32, 0, 15, 19, 4];
      history.forEach((n, idx) => {
        ctx.save();
        if (n === 0) ctx.fillStyle = "#22c55e";
        else if (n % 2 === 0) ctx.fillStyle = "#ef4444";
        else ctx.fillStyle = "#1e293b";

        ctx.beginPath();
        ctx.arc(286 + idx * 15, 38, 6, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = "#ffffff";
        ctx.font = "bold 7px sans-serif";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(n.toString(), 286 + idx * 15, 38);
        ctx.restore();
      });

      animationId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationId);
    };
  }, []);

  return (
    <div className="w-full h-[80px] bg-[#020617] rounded-xl overflow-hidden shadow-inner border border-slate-900 flex items-center justify-center">
      <canvas ref={canvasRef} className="block w-full h-full" />
    </div>
  );
};
