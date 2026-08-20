'use client';

import { useEffect, useRef } from 'react';
import * as PIXI from 'pixi.js';

interface Kl28CanvasProps {
  numbers?: number[]; // [n1, n2, n3] e.g. [1, 6, 0]
  isSpinning?: boolean;
  width?: number;
  height?: number;
}

export default function Kl28Canvas({
  numbers = [0, 0, 0],
  isSpinning = false,
  width = 600,
  height = 200,
}: Kl28CanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const appRef = useRef<PIXI.Application | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    // Initialize PixiJS Application
    const app = new PIXI.Application();
    
    let isDestroyed = false;

    app.init({
      width,
      height,
      backgroundColor: 0x0f172a, // Dark slate theme
      resolution: window.devicePixelRatio || 1,
      autoDensity: true,
    }).then(() => {
      if (isDestroyed || !containerRef.current) {
        app.destroy(true);
        return;
      }

      appRef.current = app;
      containerRef.current.appendChild(app.canvas);

      // Render 3 Ball Containers
      const ballRadius = 36;
      const spacing = 140;
      const startX = width / 2 - spacing;

      numbers.forEach((num, index) => {
        const ballContainer = new PIXI.Container();
        ballContainer.x = startX + index * spacing;
        ballContainer.y = height / 2;

        // Ball Circle Graphic (Gradient-like shiny sphere)
        const graphics = new PIXI.Graphics();
        graphics.circle(0, 0, ballRadius);
        graphics.fill(0x2563eb); // Vivid Blue
        graphics.stroke({ width: 3, color: 0x60a5fa });

        // Number Text
        const text = new PIXI.Text({
          text: String(num),
          style: {
            fontFamily: 'Inter, sans-serif',
            fontSize: 32,
            fontWeight: 'bold',
            fill: 0xffffff,
          },
        });
        text.anchor.set(0.5);

        ballContainer.addChild(graphics);
        ballContainer.addChild(text);
        app.stage.addChild(ballContainer);
      });
    });

    return () => {
      isDestroyed = true;
      if (appRef.current) {
        appRef.current.destroy(true, { children: true });
        appRef.current = null;
      }
    };
  }, [width, height, numbers, isSpinning]);

  return (
    <div
      ref={containerRef}
      className="relative flex items-center justify-center rounded-xl overflow-hidden shadow-2xl border border-slate-800"
    />
  );
}
