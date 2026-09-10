"use client";

import React, { useEffect, useRef } from "react";
import * as PIXI from "pixi.js";

export interface XocDiaCanvasProps {
  phase: "BETTING_OPEN" | "BETTING_CLOSED" | "SPINNING" | "RESULT" | "SETTLE";
  coins: number[]; // [1, 1, 0, 1] (1: Red, 0: White)
  width?: number;
  height?: number;
}

export const XocDiaCanvas: React.FC<XocDiaCanvasProps> = ({
  phase,
  coins = [1, 1, 0, 0],
  width = 400,
  height = 310,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const appRef = useRef<PIXI.Application | null>(null);

  const stateRef = useRef({
    phase,
    coins,
    time: 0,
    bowlLiftProgress: 0, // 0 = fully closed, 1 = fully open
    isDragging: false,
    dragStartY: 0,
    dragStartX: 0,
    manualOffset: { x: 0, y: 0 },
  });

  stateRef.current.phase = phase;
  stateRef.current.coins = coins;

  useEffect(() => {
    if (!containerRef.current) return;

    let isDestroyed = false;
    const app = new PIXI.Application();

    app
      .init({
        width,
        height,
        backgroundAlpha: 0,
        resolution: Math.min(window.devicePixelRatio || 1, 2),
        autoDensity: true,
        antialias: true,
      })
      .then(async () => {
        if (isDestroyed || !containerRef.current) {
          app.destroy(true);
          return;
        }

        appRef.current = app;
        containerRef.current.innerHTML = "";
        containerRef.current.appendChild(app.canvas);

        // Load photorealistic 30-degree isometric assets
        let setClosedTexture: PIXI.Texture;
        let plateTexture: PIXI.Texture;
        let bowlFittedTexture: PIXI.Texture;
        let redCoinTexture: PIXI.Texture;
        let whiteCoinTexture: PIXI.Texture;

        try {
          [
            setClosedTexture,
            plateTexture,
            bowlFittedTexture,
            redCoinTexture,
            whiteCoinTexture,
          ] = await Promise.all([
            PIXI.Assets.load("/games/xocdia/set_closed.png"),
            PIXI.Assets.load("/games/xocdia/plate.png"),
            PIXI.Assets.load("/games/xocdia/bowl_fitted.png"),
            PIXI.Assets.load("/games/xocdia/coin-red.png"),
            PIXI.Assets.load("/games/xocdia/coin-white.png"),
          ]);
        } catch (e) {
          console.error("Failed to load Xoc Dia textures, fallback to defaults", e);
          return;
        }

        if (isDestroyed) return;

        const scaleRatio = width / 400;
        const centerX = width / 2;
        const centerY = height * 0.61;
        const scale = 0.22 * scaleRatio;

        // 1. Ambient Table Felt Shadow
        const tableShadow = new PIXI.Graphics();
        tableShadow.ellipse(centerX, centerY + 16 * scaleRatio, 110 * scaleRatio, 40 * scaleRatio);
        tableShadow.fill({ color: 0x000000, alpha: 0.68 });
        app.stage.addChild(tableShadow);

        // 2. Glow Aura Ring (visible while shaking or waiting)
        const glowRing = new PIXI.Graphics();
        glowRing.ellipse(0, 0, 114 * scaleRatio, 80 * scaleRatio);
        glowRing.stroke({ width: 3.5 * scaleRatio, color: 0xf59e0b, alpha: 0.45 });
        glowRing.x = centerX;
        glowRing.y = centerY;
        glowRing.visible = false;
        app.stage.addChild(glowRing);

        // ==========================================
        // 3. CLOSED SET CONTAINER (BETTING & SHAKING)
        // Completely sealed, 0 gap, 100% "k lộ bên trong"
        // ==========================================
        const closedSetContainer = new PIXI.Container();
        closedSetContainer.x = centerX;
        closedSetContainer.y = centerY;

        const closedSetSprite = new PIXI.Sprite(setClosedTexture);
        closedSetSprite.anchor.set(0.5, 0.5);
        closedSetSprite.scale.set(scale);
        closedSetContainer.addChild(closedSetSprite);
        app.stage.addChild(closedSetContainer);

        // ==========================================
        // 4. OPEN SET CONTAINER (RESULT & NẶN BÁT)
        // Plate + 4 Coins + Lifted Bowl with physics
        // ==========================================
        const openSetContainer = new PIXI.Container();
        openSetContainer.visible = false;
        app.stage.addChild(openSetContainer);

        // 4.1 Plate Sprite
        const plateContainer = new PIXI.Container();
        plateContainer.x = centerX;
        plateContainer.y = centerY;
        const plateSprite = new PIXI.Sprite(plateTexture);
        plateSprite.anchor.set(0.5, 0.5);
        plateSprite.scale.set(scale);
        plateContainer.addChild(plateSprite);
        openSetContainer.addChild(plateContainer);

        // 4.2 Four Coins Container (on the emerald velvet cushion)
        const coinsContainer = new PIXI.Container();
        coinsContainer.x = centerX;
        coinsContainer.y = centerY;
        openSetContainer.addChild(coinsContainer);

        // Diamond positions on the plate dish (spaced for maximum clarity)
        const coinSpacing = 16 * scaleRatio;
        const coinOffsets = [
          { x: 0, y: -coinSpacing * 0.95, rot: -0.04 },
          { x: 0, y: coinSpacing * 0.95, rot: 0.05 },
          { x: -coinSpacing * 1.25, y: 0, rot: 0.06 },
          { x: coinSpacing * 1.25, y: 0, rot: -0.08 },
        ];

        const coinSprites: PIXI.Sprite[] = [];
        coinOffsets.forEach((pos) => {
          // Sharp drop shadow for each coin
          const cShadow = new PIXI.Graphics();
          cShadow.ellipse(pos.x, pos.y + 3 * scaleRatio, 15 * scaleRatio, 10 * scaleRatio);
          cShadow.fill({ color: 0x000000, alpha: 0.60 });
          coinsContainer.addChild(cShadow);

          // Crystal clear perspective coin sprite
          const cSprite = new PIXI.Sprite(redCoinTexture);
          cSprite.anchor.set(0.5, 0.5);
          cSprite.scale.x = (54 * scaleRatio) / 512;
          cSprite.scale.y = (46 * scaleRatio) / 512;
          cSprite.x = pos.x;
          cSprite.y = pos.y;
          cSprite.rotation = pos.rot;
          coinsContainer.addChild(cSprite);
          coinSprites.push(cSprite);
        });

        // 4.3 Bowl Shadow (projects onto plate when bowl lifts)
        const bowlShadow = new PIXI.Graphics();
        bowlShadow.ellipse(centerX, centerY - 18 * scaleRatio, 75 * scaleRatio, 26 * scaleRatio);
        bowlShadow.fill({ color: 0x000000, alpha: 0.45 });
        openSetContainer.addChild(bowlShadow);

        // 4.4 Bowl Container (Interactive Nặn Bát)
        const bowlContainer = new PIXI.Container();
        // Closed position: offset dy = -35 * scale = -7.7 * scaleRatio
        const closedBowlY = centerY - 7.7 * scaleRatio;
        bowlContainer.x = centerX;
        bowlContainer.y = closedBowlY;
        bowlContainer.eventMode = "static";
        bowlContainer.cursor = "grab";

        const bowlSprite = new PIXI.Sprite(bowlFittedTexture);
        bowlSprite.anchor.set(0.5, 0.5);
        bowlSprite.scale.set(scale);
        bowlContainer.addChild(bowlSprite);
        openSetContainer.addChild(bowlContainer);

        // Interactive "Nặn Bát" (drag to peek)
        let isPointerDown = false;
        let startY = 0;
        let startX = 0;

        bowlContainer.on("pointerdown", (event: PIXI.FederatedPointerEvent) => {
          if (stateRef.current.phase === "RESULT" || stateRef.current.phase === "SETTLE") {
            isPointerDown = true;
            stateRef.current.isDragging = true;
            startY = event.globalY;
            startX = event.globalX;
            bowlContainer.cursor = "grabbing";
          }
        });

        const onGlobalMove = (event: PointerEvent) => {
          if (!isPointerDown) return;
          const dy = event.clientY - startY;
          const dx = event.clientX - startX;
          stateRef.current.manualOffset = {
            x: Math.min(Math.max(dx, -60 * scaleRatio), 60 * scaleRatio),
            y: Math.min(Math.max(dy, -110 * scaleRatio), 15 * scaleRatio),
          };
        };

        const onGlobalUp = () => {
          if (isPointerDown) {
            isPointerDown = false;
            stateRef.current.isDragging = false;
            bowlContainer.cursor = "grab";
          }
        };

        window.addEventListener("pointermove", onGlobalMove);
        window.addEventListener("pointerup", onGlobalUp);

        // ==========================================
        // 5. Main 60 FPS Render & Physics Loop
        // ==========================================
        app.ticker.add((ticker) => {
          const delta = ticker.deltaTime;
          stateRef.current.time += delta * 0.05;
          const t = stateRef.current.time;
          const currentPhase = stateRef.current.phase;
          const currentCoins = stateRef.current.coins;

          // Update coin textures according to round outcome
          coinSprites.forEach((sprite, idx) => {
            const isRed = currentCoins[idx] === 1;
            sprite.texture = isRed ? redCoinTexture : whiteCoinTexture;
          });

          // Animation logic by phase:
          if (currentPhase === "SPINNING") {
            // LẮC BÁT: Vigorous rhythmic casino rattle shaking
            closedSetContainer.visible = true;
            openSetContainer.visible = false;

            glowRing.visible = true;
            glowRing.alpha = 0.5 + Math.sin(t * 12) * 0.3;
            glowRing.scale.set(1 + Math.sin(t * 8) * 0.04);

            const shakeX = (Math.sin(t * 32) * 8 + Math.cos(t * 48) * 3) * scaleRatio;
            const shakeY = (Math.sin(t * 26) * 4) * scaleRatio;
            const shakeRot = Math.sin(t * 28) * 0.065;

            closedSetContainer.x = centerX + shakeX;
            closedSetContainer.y = centerY + shakeY;
            closedSetContainer.rotation = shakeRot;

            tableShadow.x = centerX + shakeX * 0.4;
            tableShadow.y = centerY + 16 * scaleRatio + shakeY * 0.3;

            stateRef.current.bowlLiftProgress = 0;
            stateRef.current.manualOffset = { x: 0, y: 0 };
          } else if (currentPhase === "BETTING_OPEN" || currentPhase === "BETTING_CLOSED") {
            // ĐẶT CƯỢC: Bát úp kín hoàn toàn trên đĩa, ZERO GAP, không lộ bên trong
            closedSetContainer.visible = true;
            openSetContainer.visible = false;

            glowRing.visible = currentPhase === "BETTING_OPEN";
            if (glowRing.visible) {
              glowRing.alpha = 0.28 + Math.sin(t * 3) * 0.15;
              glowRing.scale.set(1.0 + Math.sin(t * 2.5) * 0.02);
            }

            // Smooth return to center
            closedSetContainer.x += (centerX - closedSetContainer.x) * 0.15;
            closedSetContainer.y += (centerY - closedSetContainer.y) * 0.15;
            closedSetContainer.rotation += (0 - closedSetContainer.rotation) * 0.15;

            tableShadow.x = centerX;
            tableShadow.y = centerY + 16 * scaleRatio;

            stateRef.current.bowlLiftProgress = 0;
            stateRef.current.manualOffset = { x: 0, y: 0 };
          } else if (currentPhase === "RESULT" || currentPhase === "SETTLE") {
            // MỞ BÁT / NẶN BÁT: Smooth lift up & reveal 4 coins
            closedSetContainer.visible = false;
            openSetContainer.visible = true;
            glowRing.visible = false;

            // Interpolate lift progress (0 -> 1)
            if (!stateRef.current.isDragging) {
              stateRef.current.bowlLiftProgress = Math.min(
                1,
                stateRef.current.bowlLiftProgress + delta * 0.028
              );
            }
            const p = stateRef.current.bowlLiftProgress;
            // Ease out cubic
            const easeP = 1 - Math.pow(1 - p, 3);

            // Calculate auto lift + manual drag offset ("Nặn bát")
            // Max lift is 52px * (width / 160) so it stays inside canvas
            const autoLiftY = -52 * (width / 160) * easeP;
            const autoTilt = -0.14 * easeP;

            const targetX = centerX + stateRef.current.manualOffset.x;
            const targetY = closedBowlY + autoLiftY + stateRef.current.manualOffset.y;
            const targetRot = autoTilt;

            bowlContainer.x += (targetX - bowlContainer.x) * 0.18;
            bowlContainer.y += (targetY - bowlContainer.y) * 0.18;
            bowlContainer.rotation += (targetRot - bowlContainer.rotation) * 0.18;

            // As bowl lifts higher, its shadow on the plate softens and expands
            const liftDistance = closedBowlY - bowlContainer.y;
            bowlShadow.alpha = Math.max(0.04, 0.45 - (liftDistance / 100) * 0.4);
            bowlShadow.scale.set(1 + (liftDistance / 90) * 0.25);

            // Subtle pulsing shine on winning coins
            coinSprites.forEach((cSprite, idx) => {
              const baseScaleX = (54 * scaleRatio) / 512;
              const baseScaleY = (46 * scaleRatio) / 512;
              const pulse = 1 + Math.sin(t * 5 + idx) * 0.04;
              cSprite.scale.x = baseScaleX * pulse;
              cSprite.scale.y = baseScaleY * pulse;
            });
          }
        });

        return () => {
          window.removeEventListener("pointermove", onGlobalMove);
          window.removeEventListener("pointerup", onGlobalUp);
        };
      })
      .catch((err) => {
        console.error("PIXI init error in XocDiaCanvas", err);
      });

    return () => {
      isDestroyed = true;
      if (appRef.current) {
        try {
          appRef.current.destroy(true, { children: true, texture: false });
        } catch {
          // ignore cleanup errors
        }
        appRef.current = null;
      }
    };
  }, [width, height]);

  return (
    <div className="relative flex items-center justify-center select-none">
      <div
        ref={containerRef}
        style={{ width, height }}
        className="relative overflow-visible touch-none"
      />
    </div>
  );
};

export default XocDiaCanvas;

