"use client";

import React, { useEffect, useRef, useState } from "react";
import * as PIXI from "pixi.js";
import { useRouter } from "next/navigation";

type Phase = "BETTING_OPEN" | "BETTING_CLOSED" | "SPINNING" | "RESULT" | "SETTLE";

interface BetState {
  XOC_DIA_EVEN: number;
  XOC_DIA_ODD: number;
  XOC_DIA_FOUR_RED: number;
  XOC_DIA_FOUR_WHITE: number;
  XOC_DIA_THREE_RED: number;
  XOC_DIA_THREE_WHITE: number;
}

interface RoundHistory {
  seq: number;
  redCount: number;
  isEven: boolean;
}

interface FlyingChipVisual {
  sprite: PIXI.Sprite;
  fromX: number;
  fromY: number;
  toX: number;
  toY: number;
  progress: number;
}

export const XocDiaPixiGame: React.FC<{
  onOpenRules?: () => void;
  onOpenTopWins?: () => void;
}> = ({ onOpenRules, onOpenTopWins }) => {
  const router = useRouter();
  const canvasContainerRef = useRef<HTMLDivElement>(null);
  const appRef = useRef<PIXI.Application | null>(null);

  // Responsive stage scale
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const onResize = () => {
      const s = Math.min(window.innerWidth / 1024, window.innerHeight / 507);
      setScale(s);
    };
    onResize();
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  // Game state reference for 60 FPS ticker access
  const gameStateRef = useRef({
    phase: "BETTING_OPEN" as Phase,
    timeLeft: 15,
    roundSeq: 1088,
    coins: [1, 1, 0, 0], // 2 red 2 white
    prevCoins: [1, 1, 1, 1],
    balance: 5030000,
    selectedChip: 10000,
    soundEnabled: true,
    jackpot: 295313767,
    serverBets: {
      even: 476.61,
      odd: 476.51,
      fourRed: 3.44,
      fourWhite: 2.26,
      threeWhite: 4.36,
      threeRed: 5.56,
    },
    bets: {
      XOC_DIA_EVEN: 0,
      XOC_DIA_ODD: 0,
      XOC_DIA_FOUR_RED: 0,
      XOC_DIA_FOUR_WHITE: 0,
      XOC_DIA_THREE_RED: 0,
      XOC_DIA_THREE_WHITE: 0,
    } as BetState,
    prevBets: null as BetState | null,
    history: [
      { seq: 1070, redCount: 2, isEven: true },
      { seq: 1071, redCount: 3, isEven: false },
      { seq: 1072, redCount: 2, isEven: true },
      { seq: 1073, redCount: 0, isEven: true },
      { seq: 1074, redCount: 1, isEven: false },
      { seq: 1075, redCount: 2, isEven: true },
      { seq: 1076, redCount: 4, isEven: true },
      { seq: 1077, redCount: 3, isEven: false },
      { seq: 1078, redCount: 1, isEven: false },
      { seq: 1079, redCount: 2, isEven: true },
      { seq: 1080, redCount: 2, isEven: true },
      { seq: 1081, redCount: 3, isEven: false },
      { seq: 1082, redCount: 0, isEven: true },
      { seq: 1083, redCount: 2, isEven: true },
      { seq: 1084, redCount: 1, isEven: false },
      { seq: 1085, redCount: 4, isEven: true },
      { seq: 1086, redCount: 2, isEven: true },
      { seq: 1087, redCount: 3, isEven: false },
    ] as RoundHistory[],
    time: 0,
    bowlLiftProgress: 0,
    isDragging: false,
    manualOffset: { x: 0, y: 0 },
    lastWinAmount: null as number | null,
    flyingChips: [] as FlyingChipVisual[],
  });

  // Web Audio Synthesizer
  const audioCtxRef = useRef<AudioContext | null>(null);
  const playSound = (type: "chip" | "shake" | "bell" | "win" | "tick") => {
    if (!gameStateRef.current.soundEnabled) return;
    try {
      if (!audioCtxRef.current) {
        const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
        if (AudioCtx) audioCtxRef.current = new AudioCtx();
      }
      const ctx = audioCtxRef.current;
      if (!ctx) return;
      if (ctx.state === "suspended") ctx.resume();
      const now = ctx.currentTime;

      if (type === "chip") {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = "triangle";
        osc.frequency.setValueAtTime(1600, now);
        osc.frequency.exponentialRampToValueAtTime(700, now + 0.05);
        gain.gain.setValueAtTime(0.28, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.05);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + 0.05);
      } else if (type === "shake") {
        for (let i = 0; i < 5; i++) {
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          osc.type = "sine";
          osc.frequency.setValueAtTime(800 + i * 150, now + i * 0.07);
          gain.gain.setValueAtTime(0.15, now + i * 0.07);
          gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.07 + 0.05);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(now + i * 0.07);
          osc.stop(now + i * 0.07 + 0.05);
        }
      } else if (type === "bell") {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = "sine";
        osc.frequency.setValueAtTime(880, now);
        gain.gain.setValueAtTime(0.25, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.6);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + 0.6);
      } else if (type === "win") {
        [523.25, 659.25, 783.99, 1046.5].forEach((freq, i) => {
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          osc.type = "triangle";
          osc.frequency.setValueAtTime(freq, now + i * 0.09);
          gain.gain.setValueAtTime(0.3, now + i * 0.09);
          gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.09 + 0.35);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(now + i * 0.09);
          osc.stop(now + i * 0.09 + 0.35);
        });
      } else if (type === "tick") {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = "sine";
        osc.frequency.setValueAtTime(1200, now);
        gain.gain.setValueAtTime(0.1, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.03);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + 0.03);
      }
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    if (!canvasContainerRef.current) return;

    let isDestroyed = false;
    const app = new PIXI.Application();

    app
      .init({
        width: 1024,
        height: 507,
        backgroundColor: 0x080402,
        resolution: Math.min(window.devicePixelRatio || 1, 2),
        autoDensity: true,
        antialias: true,
      })
      .then(async () => {
        if (isDestroyed || !canvasContainerRef.current) {
          app.destroy(true);
          return;
        }

        appRef.current = app;
        canvasContainerRef.current.innerHTML = "";
        canvasContainerRef.current.appendChild(app.canvas);

        // Load textures
        const texturePaths = [
          "/games/xocdia/dealer_clean.png",
          "/games/xocdia/dragon_felt.png",
          "/games/xocdia/set_closed.png",
          "/games/xocdia/plate.png",
          "/games/xocdia/bowl_fitted.png",
          "/games/xocdia/coin-red.png",
          "/games/xocdia/coin-white.png",
          "/games/xocdia/p1.png",
          "/games/xocdia/p2.png",
          "/games/xocdia/p3.png",
          "/games/xocdia/p4.png",
          "/games/xocdia/p5.png",
          "/games/xocdia/p6.png",
          "/games/xocdia/p7.png",
          "/games/xocdia/p8.png",
          "/games/xocdia/hero.png",
          "/games/xocdia/chip_1k.png",
          "/games/xocdia/chip_5k.png",
          "/games/xocdia/chip_10k.png",
          "/games/xocdia/chip_50k.png",
        ];

        let textures: Record<string, PIXI.Texture> = {};
        try {
          const loaded = await Promise.all(texturePaths.map((p) => PIXI.Assets.load(p)));
          texturePaths.forEach((p, i) => {
            const name = p.split("/").pop() || "";
            textures[name] = loaded[i];
          });
        } catch (e) {
          console.error("Failed to load textures for pure Pixi game", e);
          return;
        }

        if (isDestroyed) return;

        // =========================================================================
        // 1. LAYER: CASINO ENVIRONMENT & AMBIENCE
        // =========================================================================
        const bgLayer = new PIXI.Container();
        app.stage.addChild(bgLayer);

        // Dark casino floor background
        const envBg = new PIXI.Graphics();
        envBg.rect(0, 0, 1024, 507);
        envBg.fill({ color: 0x090503 });
        bgLayer.addChild(envBg);

        // Subtle damask gold pattern dots / sparkles on background
        const bgSparkles = new PIXI.Graphics();
        for (let i = 0; i < 40; i++) {
          const sx = (i * 73) % 1024;
          const sy = (i * 37) % 507;
          bgSparkles.circle(sx, sy, 1);
        }
        bgSparkles.fill({ color: 0x6b4a1b, alpha: 0.25 });
        bgLayer.addChild(bgSparkles);

        // =========================================================================
        // 2. LAYER: THE GIANT 3D CASINO TABLE (OVAL FELT & BUMPERS)
        // =========================================================================
        const tableLayer = new PIXI.Container();
        app.stage.addChild(tableLayer);

        const tableCenterX = 512;
        const tableCenterY = 270;

        // 2.1 Table Shadow on floor
        const tableFloorShadow = new PIXI.Graphics();
        tableFloorShadow.ellipse(tableCenterX, tableCenterY + 24, 492, 232);
        tableFloorShadow.fill({ color: 0x000000, alpha: 0.85 });
        tableLayer.addChild(tableFloorShadow);

        // 2.2 Outer Wood/Bronze Table Rim
        const tableWoodRim = new PIXI.Graphics();
        tableWoodRim.ellipse(tableCenterX, tableCenterY, 482, 222);
        tableWoodRim.fill({ color: 0x381e0d });
        tableWoodRim.stroke({ color: 0xb58b38, width: 6 });
        tableLayer.addChild(tableWoodRim);

        // 2.3 Padded Armrest Stitching
        const armrestStitch = new PIXI.Graphics();
        armrestStitch.ellipse(tableCenterX, tableCenterY, 458, 204);
        armrestStitch.stroke({ color: 0x784a1a, width: 2, alpha: 0.8 });
        tableLayer.addChild(armrestStitch);

        // 2.4 Center Deep Brown Table Felt
        const tableFelt = new PIXI.Graphics();
        tableFelt.ellipse(tableCenterX, tableCenterY, 444, 192);
        tableFelt.fill({ color: 0x221106 });
        tableFelt.stroke({ color: 0xd4a34b, width: 1.5, alpha: 0.35 });
        tableLayer.addChild(tableFelt);

        // 2.5 Dragon Felt Watermark (Center Crest)
        if (textures["dragon_felt.png"]) {
          const dragon = new PIXI.Sprite(textures["dragon_felt.png"]);
          dragon.anchor.set(0.5);
          dragon.x = tableCenterX;
          dragon.y = tableCenterY - 20;
          dragon.scale.set(1.9);
          dragon.alpha = 0.24;
          dragon.tint = 0xffba42;
          tableLayer.addChild(dragon);
        }

        // =========================================================================
        // 3. LAYER: DEALER IN ROYAL THRONE & TOP CONTROLS
        // =========================================================================
        const topLayer = new PIXI.Container();
        app.stage.addChild(topLayer);

        // 3.1 Dealer Royal Throne Backrest
        const throneArch = new PIXI.Graphics();
        throneArch.roundRect(tableCenterX - 65, 0, 130, 95, 20);
        throneArch.fill({ color: 0x540d18 });
        throneArch.stroke({ color: 0xd4a34b, width: 3 });
        topLayer.addChild(throneArch);

        // 3.2 Dealer Sprite
        let dealerSprite: PIXI.Sprite | null = null;
        if (textures["dealer_clean.png"]) {
          dealerSprite = new PIXI.Sprite(textures["dealer_clean.png"]);
          dealerSprite.anchor.set(0.5);
          dealerSprite.x = tableCenterX;
          dealerSprite.y = 48;
          topLayer.addChild(dealerSprite);
        }

        // 3.3 JACKPOT PLAQUE (Left of Dealer)
        const jackpotBox = new PIXI.Container();
        jackpotBox.x = 340;
        jackpotBox.y = 30;
        topLayer.addChild(jackpotBox);

        const jackpotBg = new PIXI.Graphics();
        jackpotBg.roundRect(-68, -20, 136, 40, 10);
        jackpotBg.fill({ color: 0x1f0d04 });
        jackpotBg.stroke({ color: 0xf59e0b, width: 2.5 });
        jackpotBox.addChild(jackpotBg);

        const jackpotLabel = new PIXI.Text({
          text: "JACKPOT",
          style: {
            fontFamily: "Arial, sans-serif",
            fontSize: 10,
            fontWeight: "bold",
            fill: 0xffe066,
          },
        });
        jackpotLabel.anchor.set(0.5, 1);
        jackpotLabel.y = -2;
        jackpotBox.addChild(jackpotLabel);

        const jackpotValText = new PIXI.Text({
          text: gameStateRef.current.jackpot.toLocaleString(),
          style: {
            fontFamily: "monospace, Courier, sans-serif",
            fontSize: 13,
            fontWeight: "bold",
            fill: 0xffea79,
          },
        });
        jackpotValText.anchor.set(0.5, 0);
        jackpotValText.y = 0;
        jackpotBox.addChild(jackpotValText);

        // 3.4 DICE HISTORY BOX (Right of Dealer)
        const diceBox = new PIXI.Container();
        diceBox.x = 635;
        diceBox.y = 30;
        topLayer.addChild(diceBox);

        const diceBg = new PIXI.Graphics();
        diceBg.roundRect(-68, -18, 136, 36, 18);
        diceBg.fill({ color: 0x1f0d04 });
        diceBg.stroke({ color: 0xd4a34b, width: 2 });
        diceBox.addChild(diceBg);

        const topDiceSprites: PIXI.Graphics[] = [];
        for (let i = 0; i < 4; i++) {
          const d = new PIXI.Graphics();
          d.x = -48 + i * 32;
          d.y = 0;
          diceBox.addChild(d);
          topDiceSprites.push(d);
        }

        // 3.5 Top Bar Interactive Icons (Back, Trophy, Rules, Sound)
        const drawIconBtn = (x: number, y: number, label: string, onClick: () => void) => {
          const btn = new PIXI.Container();
          btn.x = x;
          btn.y = y;
          btn.eventMode = "static";
          btn.cursor = "pointer";

          const bg = new PIXI.Graphics();
          bg.circle(0, 0, 16);
          bg.fill({ color: 0x2b1406 });
          bg.stroke({ color: 0xf59e0b, width: 2 });
          btn.addChild(bg);

          const txt = new PIXI.Text({
            text: label,
            style: {
              fontFamily: "Arial, sans-serif",
              fontSize: 12,
              fontWeight: "bold",
              fill: 0xffea79,
            },
          });
          txt.anchor.set(0.5);
          btn.addChild(txt);

          btn.on("pointerdown", () => {
            playSound("chip");
            onClick();
          });
          topLayer.addChild(btn);
          return btn;
        };

        drawIconBtn(32, 26, "◀", () => router.push("/"));
        drawIconBtn(72, 26, "🏆", () => onOpenTopWins?.());
        drawIconBtn(112, 26, "❓", () => onOpenRules?.());

        const soundBtn = drawIconBtn(888, 26, "🔊", () => {
          gameStateRef.current.soundEnabled = !gameStateRef.current.soundEnabled;
          const sText = soundBtn.getChildAt(1) as PIXI.Text;
          sText.text = gameStateRef.current.soundEnabled ? "🔊" : "🔇";
        });

        // Online players badge
        const onlineBadge = new PIXI.Container();
        onlineBadge.x = 948;
        onlineBadge.y = 26;
        topLayer.addChild(onlineBadge);

        const obg = new PIXI.Graphics();
        obg.roundRect(-24, -12, 48, 24, 12);
        obg.fill({ color: 0x1f0d04 });
        obg.stroke({ color: 0xd4a34b, width: 1.5 });
        onlineBadge.addChild(obg);

        const otxt = new PIXI.Text({
          text: "👥 242",
          style: { fontFamily: "Arial", fontSize: 9, fontWeight: "bold", fill: 0xffea79 },
        });
        otxt.anchor.set(0.5);
        onlineBadge.addChild(otxt);

        // =========================================================================
        // 4. LAYER: SURROUNDING ACTIVE PLAYERS & HERO USER
        // =========================================================================
        const playersLayer = new PIXI.Container();
        app.stage.addChild(playersLayer);

        const playerList = [
          { key: "p1.png", name: "xyz11", bal: "$48.32M", x: 195, y: 75 },
          { key: "p2.png", name: "haizichoqua", bal: "$388.9K", x: 125, y: 155 },
          { key: "p3.png", name: "hoang277056", bal: "$67.2M", x: 75, y: 255 },
          { key: "p4.png", name: "jdhkak", bal: "$53.57M", x: 70, y: 390 },
          { key: "p5.png", name: "lmnhgfyy", bal: "$2.95M", x: 760, y: 75 },
          { key: "p6.png", name: "azxfffvvvc", bal: "$162.1K", x: 835, y: 155 },
          { key: "p7.png", name: "sangcute", bal: "$62.7M", x: 885, y: 255 },
          { key: "p8.png", name: "halamlaicuocdo", bal: "$77.2M", x: 880, y: 390 },
        ];

        playerList.forEach((p) => {
          const pCont = new PIXI.Container();
          pCont.x = p.x;
          pCont.y = p.y;
          playersLayer.addChild(pCont);

          if (textures[p.key]) {
            const sp = new PIXI.Sprite(textures[p.key]);
            sp.anchor.set(0.5);
            sp.scale.set(46 / sp.width);
            pCont.addChild(sp);

            const ring = new PIXI.Graphics();
            ring.circle(0, 0, 23);
            ring.stroke({ color: 0xf59e0b, width: 2 });
            pCont.addChild(ring);
          }

          const nameTxt = new PIXI.Text({
            text: p.name,
            style: { fontFamily: "Arial", fontSize: 8, fontWeight: "bold", fill: 0xffffff },
          });
          nameTxt.anchor.set(0.5, 0);
          nameTxt.y = 25;
          pCont.addChild(nameTxt);

          const balTxt = new PIXI.Text({
            text: p.bal,
            style: { fontFamily: "Arial", fontSize: 8, fontWeight: "bold", fill: 0xffea79 },
          });
          balTxt.anchor.set(0.5, 0);
          balTxt.y = 35;
          pCont.addChild(balTxt);
        });

        // Hero Player (Bottom-Left)
        const heroCont = new PIXI.Container();
        heroCont.x = 180;
        heroCont.y = 440;
        playersLayer.addChild(heroCont);

        if (textures["hero.png"]) {
          const hsp = new PIXI.Sprite(textures["hero.png"]);
          hsp.anchor.set(0.5);
          hsp.scale.set(50 / hsp.width);
          heroCont.addChild(hsp);

          const hring = new PIXI.Graphics();
          hring.circle(0, 0, 25);
          hring.stroke({ color: 0xf59e0b, width: 2.5 });
          heroCont.addChild(hring);
        }

        const heroBalText = new PIXI.Text({
          text: `$${gameStateRef.current.balance.toLocaleString()}`,
          style: { fontFamily: "monospace, Arial", fontSize: 11, fontWeight: "bold", fill: 0xffea79 },
        });
        heroBalText.anchor.set(0.5, 0);
        heroBalText.y = 28;
        heroCont.addChild(heroBalText);

        // =========================================================================
        // 5. LAYER: CHẴN & LẺ MAIN BETTING CARDS
        // =========================================================================
        const betsLayer = new PIXI.Container();
        app.stage.addChild(betsLayer);

        // 5.1 BOX CHẴN (Even) at (312, 175)
        const chanCard = new PIXI.Container();
        chanCard.x = 312;
        chanCard.y = 175;
        chanCard.eventMode = "static";
        chanCard.cursor = "pointer";
        betsLayer.addChild(chanCard);

        const chanBg = new PIXI.Graphics();
        chanBg.roundRect(-117, -40, 234, 80, 16);
        chanBg.fill({ color: 0x422608 });
        chanBg.stroke({ color: 0xf59e0b, width: 3 });
        chanCard.addChild(chanBg);

        const chanTitle = new PIXI.Text({
          text: "CHẴN",
          style: {
            fontFamily: "Impact, Arial Black, sans-serif",
            fontSize: 28,
            fontWeight: "bold",
            fill: 0xffea79,
            stroke: { color: 0x000000, width: 4 },
          },
        });
        chanTitle.anchor.set(0.5, 0.5);
        chanTitle.y = -10;
        chanCard.addChild(chanTitle);

        // Dice symbol for Even
        const chanDie = new PIXI.Graphics();
        chanDie.roundRect(50, -22, 22, 22, 4);
        chanDie.fill({ color: 0xdc2626 });
        chanDie.stroke({ color: 0xffffff, width: 1.5 });
        chanDie.circle(56, -16, 2.5);
        chanDie.circle(66, -6, 2.5);
        chanDie.fill({ color: 0xffffff });
        chanCard.addChild(chanDie);

        // Pill slot for bet amount
        const chanPill = new PIXI.Graphics();
        chanPill.roundRect(-80, 12, 160, 20, 10);
        chanPill.fill({ color: 0x110904 });
        chanPill.stroke({ color: 0x7d581e, width: 1.5 });
        chanCard.addChild(chanPill);

        const chanAmountText = new PIXI.Text({
          text: `${gameStateRef.current.serverBets.even}M`,
          style: { fontFamily: "monospace, Arial", fontSize: 11, fontWeight: "bold", fill: 0xffea79 },
        });
        chanAmountText.anchor.set(0.5);
        chanAmountText.y = 22;
        chanCard.addChild(chanAmountText);

        const chanUserBetBadge = new PIXI.Text({
          text: "",
          style: { fontFamily: "Arial", fontSize: 10, fontWeight: "bold", fill: 0xffffff },
        });
        chanUserBetBadge.anchor.set(1, 0);
        chanUserBetBadge.x = 100;
        chanUserBetBadge.y = -35;
        chanCard.addChild(chanUserBetBadge);

        // 5.2 BOX LẺ (Odd) at (657, 175)
        const leCard = new PIXI.Container();
        leCard.x = 657;
        leCard.y = 175;
        leCard.eventMode = "static";
        leCard.cursor = "pointer";
        betsLayer.addChild(leCard);

        const leBg = new PIXI.Graphics();
        leBg.roundRect(-117, -40, 234, 80, 16);
        leBg.fill({ color: 0x400c1e });
        leBg.stroke({ color: 0xf43f5e, width: 3 });
        leCard.addChild(leBg);

        const leTitle = new PIXI.Text({
          text: "LẺ",
          style: {
            fontFamily: "Impact, Arial Black, sans-serif",
            fontSize: 28,
            fontWeight: "bold",
            fill: 0xffb4d6,
            stroke: { color: 0x000000, width: 4 },
          },
        });
        leTitle.anchor.set(0.5, 0.5);
        leTitle.y = -10;
        leCard.addChild(leTitle);

        // Dice symbol for Odd
        const leDie = new PIXI.Graphics();
        leDie.roundRect(38, -22, 22, 22, 4);
        leDie.fill({ color: 0xdc2626 });
        leDie.stroke({ color: 0xffffff, width: 1.5 });
        leDie.circle(49, -11, 3);
        leDie.fill({ color: 0xffffff });
        leCard.addChild(leDie);

        // Pill slot for bet amount
        const lePill = new PIXI.Graphics();
        lePill.roundRect(-80, 12, 160, 20, 10);
        lePill.fill({ color: 0x110904 });
        lePill.stroke({ color: 0x802040, width: 1.5 });
        leCard.addChild(lePill);

        const leAmountText = new PIXI.Text({
          text: `${gameStateRef.current.serverBets.odd}M`,
          style: { fontFamily: "monospace, Arial", fontSize: 11, fontWeight: "bold", fill: 0xffb4d6 },
        });
        leAmountText.anchor.set(0.5);
        leAmountText.y = 22;
        leCard.addChild(leAmountText);

        const leUserBetBadge = new PIXI.Text({
          text: "",
          style: { fontFamily: "Arial", fontSize: 10, fontWeight: "bold", fill: 0xffffff },
        });
        leUserBetBadge.anchor.set(1, 0);
        leUserBetBadge.x = 100;
        leUserBetBadge.y = -35;
        leCard.addChild(leUserBetBadge);

        // =========================================================================
        // 6. LAYER: 4 VỊ SIDE BET PANELS (1:16, 1:4)
        // =========================================================================
        const viPanels = [
          { zone: "XOC_DIA_FOUR_RED" as keyof BetState, label: "4 ĐỎ", odds: "1:16", dots: [1, 1, 1, 1], x: 265, y: 285 },
          { zone: "XOC_DIA_FOUR_WHITE" as keyof BetState, label: "4 TRẮNG", odds: "1:16", dots: [0, 0, 0, 0], x: 410, y: 285 },
          { zone: "XOC_DIA_THREE_WHITE" as keyof BetState, label: "3 TRẮNG 1 ĐỎ", odds: "1:4", dots: [0, 0, 0, 1], x: 555, y: 285 },
          { zone: "XOC_DIA_THREE_RED" as keyof BetState, label: "3 ĐỎ 1 TRẮNG", odds: "1:4", dots: [1, 1, 1, 0], x: 700, y: 285 },
        ];

        const viBoxes: PIXI.Container[] = [];
        const viAmountTexts: PIXI.Text[] = [];
        const viUserBetBadges: PIXI.Text[] = [];

        viPanels.forEach((vi) => {
          const vCont = new PIXI.Container();
          vCont.x = vi.x;
          vCont.y = vi.y;
          vCont.eventMode = "static";
          vCont.cursor = "pointer";
          betsLayer.addChild(vCont);
          viBoxes.push(vCont);

          const vbg = new PIXI.Graphics();
          vbg.roundRect(-65, -36, 130, 72, 12);
          vbg.fill({ color: 0x221208 });
          vbg.stroke({ color: 0x784e1b, width: 2 });
          vCont.addChild(vbg);

          // Render 4 mini dice dots
          vi.dots.forEach((dot, idx) => {
            const dx = -36 + idx * 24;
            const dg = new PIXI.Graphics();
            dg.roundRect(dx, -28, 18, 18, 4);
            dg.fill({ color: dot === 1 ? 0xdc2626 : 0xf1f5f9 });
            dg.stroke({ color: dot === 1 ? 0xffffff : 0x000000, width: 1 });
            dg.circle(dx + 9, -19, 2.5);
            dg.fill({ color: dot === 1 ? 0xffffff : 0xdc2626 });
            vCont.addChild(dg);
          });

          const oddsTxt = new PIXI.Text({
            text: vi.odds,
            style: { fontFamily: "Georgia, serif", fontSize: 13, fontStyle: "italic", fontWeight: "bold", fill: 0xffea79 },
          });
          oddsTxt.anchor.set(0.5);
          oddsTxt.y = 0;
          vCont.addChild(oddsTxt);

          // Pill for bet volume
          const vPill = new PIXI.Graphics();
          vPill.roundRect(-45, 14, 90, 16, 8);
          vPill.fill({ color: 0x110904 });
          vPill.stroke({ color: 0x553010, width: 1 });
          vCont.addChild(vPill);

          const amtTxt = new PIXI.Text({
            text: "3.44M",
            style: { fontFamily: "monospace, Arial", fontSize: 9, fontWeight: "bold", fill: 0xffea79 },
          });
          amtTxt.anchor.set(0.5);
          amtTxt.y = 22;
          vCont.addChild(amtTxt);
          viAmountTexts.push(amtTxt);

          const uBadge = new PIXI.Text({
            text: "",
            style: { fontFamily: "Arial", fontSize: 8, fontWeight: "bold", fill: 0xffffff },
          });
          uBadge.anchor.set(1, 0);
          uBadge.x = 55;
          uBadge.y = -32;
          vCont.addChild(uBadge);
          viUserBetBadges.push(uBadge);
        });

        // =========================================================================
        // 7. LAYER: CENTER BÁT ĐĨA 3D (PORCELAIN SHAKER & NẶN BÁT)
        // Positioned at exact center (485, 230) with 0 pixel gap
        // =========================================================================
        const bowlLayer = new PIXI.Container();
        app.stage.addChild(bowlLayer);

        const bowlCenterX = 485;
        const bowlCenterY = 225;
        const bowlScale = 0.088; // Rendered width ~87px

        // 7.1 Shadow under plate
        const bowlFloorShadow = new PIXI.Graphics();
        bowlFloorShadow.ellipse(bowlCenterX, bowlCenterY + 28, 48, 18);
        bowlFloorShadow.fill({ color: 0x000000, alpha: 0.65 });
        bowlLayer.addChild(bowlFloorShadow);

        // 7.2 Closed Set Container (Betting & Shaking - 100% Sealed)
        const closedSetContainer = new PIXI.Container();
        closedSetContainer.x = bowlCenterX;
        closedSetContainer.y = bowlCenterY;
        bowlLayer.addChild(closedSetContainer);

        if (textures["set_closed.png"]) {
          const closedSetSprite = new PIXI.Sprite(textures["set_closed.png"]);
          closedSetSprite.anchor.set(0.5);
          closedSetSprite.scale.set(bowlScale);
          closedSetContainer.addChild(closedSetSprite);
        }

        // 7.3 Open Set Container (Result & Reveal)
        const openSetContainer = new PIXI.Container();
        openSetContainer.x = bowlCenterX;
        openSetContainer.y = bowlCenterY;
        openSetContainer.visible = false;
        bowlLayer.addChild(openSetContainer);

        // Plate Sprite
        if (textures["plate.png"]) {
          const plateSprite = new PIXI.Sprite(textures["plate.png"]);
          plateSprite.anchor.set(0.5);
          plateSprite.scale.set(bowlScale);
          openSetContainer.addChild(plateSprite);
        }

        // 4 Coins Container
        const coinsContainer = new PIXI.Container();
        openSetContainer.addChild(coinsContainer);

        const coinPositions = [
          { x: 0, y: -9 },
          { x: 0, y: 9 },
          { x: -14, y: 0 },
          { x: 14, y: 0 },
        ];

        const coinSprites: PIXI.Sprite[] = [];
        coinPositions.forEach((pos) => {
          const cShadow = new PIXI.Graphics();
          cShadow.ellipse(pos.x, pos.y + 2, 9, 6);
          cShadow.fill({ color: 0x000000, alpha: 0.5 });
          coinsContainer.addChild(cShadow);

          const cs = new PIXI.Sprite(textures["coin-red.png"]);
          cs.anchor.set(0.5);
          cs.scale.set(18 / 450, 14 / 450); // isometric perspective ratio
          cs.x = pos.x;
          cs.y = pos.y;
          coinsContainer.addChild(cs);
          coinSprites.push(cs);
        });

        // Lifted Bowl Container (Draggable / Nặn Bát)
        const liftedBowlContainer = new PIXI.Container();
        liftedBowlContainer.eventMode = "static";
        liftedBowlContainer.cursor = "grab";
        openSetContainer.addChild(liftedBowlContainer);

        if (textures["bowl_fitted.png"]) {
          const bowlSprite = new PIXI.Sprite(textures["bowl_fitted.png"]);
          bowlSprite.anchor.set(0.5);
          bowlSprite.scale.set(bowlScale);
          liftedBowlContainer.addChild(bowlSprite);
        }

        // Draggable Nặn Bát handlers
        let isPointerDown = false;
        let startY = 0;
        let startX = 0;

        liftedBowlContainer.on("pointerdown", (event: PIXI.FederatedPointerEvent) => {
          if (gameStateRef.current.phase === "RESULT" || gameStateRef.current.phase === "SETTLE") {
            isPointerDown = true;
            gameStateRef.current.isDragging = true;
            startY = event.globalY;
            startX = event.globalX;
            liftedBowlContainer.cursor = "grabbing";
          }
        });

        const onGlobalMove = (event: PointerEvent) => {
          if (!isPointerDown) return;
          const dy = event.clientY - startY;
          const dx = event.clientX - startX;
          gameStateRef.current.manualOffset = {
            x: Math.min(Math.max(dx, -50), 50),
            y: Math.min(Math.max(dy, -90), 10),
          };
        };

        const onGlobalUp = () => {
          if (isPointerDown) {
            isPointerDown = false;
            gameStateRef.current.isDragging = false;
            liftedBowlContainer.cursor = "grab";
          }
        };

        window.addEventListener("pointermove", onGlobalMove);
        window.addEventListener("pointerup", onGlobalUp);

        // 7.4 Floating Countdown & Status Badge below bowl
        const statusBadge = new PIXI.Container();
        statusBadge.x = bowlCenterX;
        statusBadge.y = bowlCenterY + 48;
        bowlLayer.addChild(statusBadge);

        const sbg = new PIXI.Graphics();
        sbg.roundRect(-45, -10, 90, 20, 10);
        sbg.fill({ color: 0x000000, alpha: 0.85 });
        sbg.stroke({ color: 0xf59e0b, width: 1.5 });
        statusBadge.addChild(sbg);

        const statusText = new PIXI.Text({
          text: "CƯỢC: 15s",
          style: { fontFamily: "Arial", fontSize: 9, fontWeight: "bold", fill: 0xffea79 },
        });
        statusText.anchor.set(0.5);
        statusBadge.addChild(statusText);

        // =========================================================================
        // 8. LAYER: BOTTOM CHIP TRAY & CHIP SELECTOR
        // =========================================================================
        const chipsLayer = new PIXI.Container();
        app.stage.addChild(chipsLayer);

        const chipCoords = [
          { val: 1000, key: "chip_1k.png", x: 350 },
          { val: 5000, key: "chip_5k.png", x: 435 },
          { val: 10000, key: "chip_10k.png", x: 520 },
          { val: 50000, key: "chip_50k.png", x: 605 },
        ];

        // Chip selection neon halo
        const chipHalo = new PIXI.Graphics();
        chipHalo.circle(0, 0, 31);
        chipHalo.stroke({ color: 0x84cc16, width: 3.5 });
        chipHalo.x = 520;
        chipHalo.y = 395;
        chipsLayer.addChild(chipHalo);

        const chipContainers: PIXI.Container[] = [];
        chipCoords.forEach((c) => {
          const cc = new PIXI.Container();
          cc.x = c.x;
          cc.y = 395;
          cc.eventMode = "static";
          cc.cursor = "pointer";
          chipsLayer.addChild(cc);
          chipContainers.push(cc);

          if (textures[c.key]) {
            const csp = new PIXI.Sprite(textures[c.key]);
            csp.anchor.set(0.5);
            csp.scale.set(56 / csp.width);
            cc.addChild(csp);
          }

          cc.on("pointerdown", () => {
            playSound("chip");
            gameStateRef.current.selectedChip = c.val;
            chipHalo.x = c.x;
          });
        });

        // Quick Action Buttons (Hủy cược, Cược lại, Gấp đôi) at y = 460
        const drawActionBtn = (x: number, y: number, label: string, onClick: () => void) => {
          const btn = new PIXI.Container();
          btn.x = x;
          btn.y = y;
          btn.eventMode = "static";
          btn.cursor = "pointer";

          const bg = new PIXI.Graphics();
          bg.roundRect(-36, -11, 72, 22, 11);
          bg.fill({ color: 0x221208 });
          bg.stroke({ color: 0x784a1a, width: 1.5 });
          btn.addChild(bg);

          const txt = new PIXI.Text({
            text: label,
            style: { fontFamily: "Arial", fontSize: 9, fontWeight: "bold", fill: 0xffea79 },
          });
          txt.anchor.set(0.5);
          btn.addChild(txt);

          btn.on("pointerdown", () => {
            playSound("chip");
            onClick();
          });
          chipsLayer.addChild(btn);
        };

        drawActionBtn(415, 460, "HỦY CƯỢC", () => {
          if (gameStateRef.current.phase !== "BETTING_OPEN") return;
          const totalBet = Object.values(gameStateRef.current.bets).reduce((a, b) => a + b, 0);
          if (totalBet > 0) {
            gameStateRef.current.balance += totalBet;
            gameStateRef.current.bets = {
              XOC_DIA_EVEN: 0,
              XOC_DIA_ODD: 0,
              XOC_DIA_FOUR_RED: 0,
              XOC_DIA_FOUR_WHITE: 0,
              XOC_DIA_THREE_RED: 0,
              XOC_DIA_THREE_WHITE: 0,
            };
          }
        });

        drawActionBtn(495, 460, "CƯỢC LẠI", () => {
          if (gameStateRef.current.phase !== "BETTING_OPEN" || !gameStateRef.current.prevBets) return;
          const totalPrev = Object.values(gameStateRef.current.prevBets).reduce((a, b) => a + b, 0);
          if (totalPrev > 0 && gameStateRef.current.balance >= totalPrev) {
            gameStateRef.current.balance -= totalPrev;
            gameStateRef.current.bets = { ...gameStateRef.current.prevBets };
          }
        });

        drawActionBtn(575, 460, "GẤP ĐÔI", () => {
          if (gameStateRef.current.phase !== "BETTING_OPEN") return;
          const totalBet = Object.values(gameStateRef.current.bets).reduce((a, b) => a + b, 0);
          if (totalBet > 0 && gameStateRef.current.balance >= totalBet) {
            gameStateRef.current.balance -= totalBet;
            gameStateRef.current.bets.XOC_DIA_EVEN *= 2;
            gameStateRef.current.bets.XOC_DIA_ODD *= 2;
            gameStateRef.current.bets.XOC_DIA_FOUR_RED *= 2;
            gameStateRef.current.bets.XOC_DIA_FOUR_WHITE *= 2;
            gameStateRef.current.bets.XOC_DIA_THREE_RED *= 2;
            gameStateRef.current.bets.XOC_DIA_THREE_WHITE *= 2;
          }
        });

        // =========================================================================
        // 9. LAYER: BẢNG SOI CẦU / ROADMAP (BOTTOM-RIGHT)
        // =========================================================================
        const roadmapLayer = new PIXI.Container();
        app.stage.addChild(roadmapLayer);

        const rmapX = 872;
        const rmapY = 415;

        const rbg = new PIXI.Graphics();
        rbg.roundRect(rmapX - 95, rmapY - 50, 190, 100, 12);
        rbg.fill({ color: 0x150c05 });
        rbg.stroke({ color: 0x553010, width: 2 });
        roadmapLayer.addChild(rbg);

        const rmapSummaryText = new PIXI.Text({
          text: "CHẴN 35   17 LẺ",
          style: { fontFamily: "Arial", fontSize: 10, fontWeight: "bold", fill: 0xffea79 },
        });
        rmapSummaryText.anchor.set(0.5);
        rmapSummaryText.x = rmapX;
        rmapSummaryText.y = rmapY - 38;
        roadmapLayer.addChild(rmapSummaryText);

        const beadGridGraphics = new PIXI.Graphics();
        roadmapLayer.addChild(beadGridGraphics);

        // Function to redraw roadmap beads
        const redrawRoadmap = () => {
          beadGridGraphics.clear();
          const hist = gameStateRef.current.history;
          const countEven = hist.filter((h) => h.isEven).length;
          const countOdd = hist.length - countEven;
          rmapSummaryText.text = `CHẴN ${countEven}   ${countOdd} LẺ`;

          hist.forEach((h, idx) => {
            const col = Math.floor(idx / 6);
            const row = idx % 6;
            const bx = rmapX - 85 + col * 9;
            const by = rmapY - 20 + row * 9;
            beadGridGraphics.circle(bx, by, 3.8);
            beadGridGraphics.fill({ color: h.isEven ? 0xdc2626 : 0xfacc15 });
            beadGridGraphics.stroke({ color: 0x000000, width: 0.8 });
          });
        };
        redrawRoadmap();

        // =========================================================================
        // 10. FLYING CHIPS ANIMATION HELPER
        // =========================================================================
        const spawnFlyingChipTo = (toX: number, toY: number) => {
          if (!textures["chip_10k.png"]) return;
          const chipSp = new PIXI.Sprite(textures["chip_10k.png"]);
          chipSp.anchor.set(0.5);
          chipSp.scale.set(24 / chipSp.width);
          chipSp.x = 180;
          chipSp.y = 440;
          app.stage.addChild(chipSp);

          gameStateRef.current.flyingChips.push({
            sprite: chipSp,
            fromX: 180,
            fromY: 440,
            toX,
            toY,
            progress: 0,
          });
        };

        // Click handlers for bets
        const handlePlaceBet = (zone: keyof BetState, toX: number, toY: number) => {
          if (gameStateRef.current.phase !== "BETTING_OPEN") return;
          const chipVal = gameStateRef.current.selectedChip;
          if (gameStateRef.current.balance < chipVal) {
            alert("Số dư không đủ để đặt cược!");
            return;
          }
          playSound("chip");
          gameStateRef.current.balance -= chipVal;
          gameStateRef.current.bets[zone] += chipVal;
          spawnFlyingChipTo(toX, toY);
        };

        chanCard.on("pointerdown", () => handlePlaceBet("XOC_DIA_EVEN", 312, 175));
        leCard.on("pointerdown", () => handlePlaceBet("XOC_DIA_ODD", 657, 175));
        viBoxes[0].on("pointerdown", () => handlePlaceBet("XOC_DIA_FOUR_RED", 265, 285));
        viBoxes[1].on("pointerdown", () => handlePlaceBet("XOC_DIA_FOUR_WHITE", 410, 285));
        viBoxes[2].on("pointerdown", () => handlePlaceBet("XOC_DIA_THREE_WHITE", 555, 285));
        viBoxes[3].on("pointerdown", () => handlePlaceBet("XOC_DIA_THREE_RED", 700, 285));

        // =========================================================================
        // 11. GAME STATE LOOP (TIMERS & STATE MACHINE)
        // =========================================================================
        const gameInterval = setInterval(() => {
          const s = gameStateRef.current;
          s.jackpot += Math.floor(Math.random() * 150 + 20);

          if (s.timeLeft > 1) {
            if (s.phase === "BETTING_OPEN" && s.timeLeft <= 5) playSound("tick");
            s.timeLeft -= 1;
          } else {
            // Transitions
            if (s.phase === "BETTING_OPEN") {
              s.phase = "BETTING_CLOSED";
              playSound("bell");
              s.timeLeft = 2;
            } else if (s.phase === "BETTING_CLOSED") {
              s.phase = "SPINNING";
              playSound("shake");
              s.timeLeft = 4;
            } else if (s.phase === "SPINNING") {
              const c1 = Math.random() > 0.5 ? 1 : 0;
              const c2 = Math.random() > 0.5 ? 1 : 0;
              const c3 = Math.random() > 0.5 ? 1 : 0;
              const c4 = Math.random() > 0.5 ? 1 : 0;
              s.prevCoins = [...s.coins];
              s.coins = [c1, c2, c3, c4];
              const redCount = c1 + c2 + c3 + c4;
              const isEven = redCount % 2 === 0;

              s.phase = "RESULT";

              // Payout calculation
              let totalWin = 0;
              if (isEven && s.bets.XOC_DIA_EVEN > 0) totalWin += s.bets.XOC_DIA_EVEN * 1.98;
              if (!isEven && s.bets.XOC_DIA_ODD > 0) totalWin += s.bets.XOC_DIA_ODD * 1.98;
              if (redCount === 4 && s.bets.XOC_DIA_FOUR_RED > 0) totalWin += s.bets.XOC_DIA_FOUR_RED * 16;
              if (redCount === 0 && s.bets.XOC_DIA_FOUR_WHITE > 0) totalWin += s.bets.XOC_DIA_FOUR_WHITE * 16;
              if (redCount === 3 && s.bets.XOC_DIA_THREE_RED > 0) totalWin += s.bets.XOC_DIA_THREE_RED * 4;
              if (redCount === 1 && s.bets.XOC_DIA_THREE_WHITE > 0) totalWin += s.bets.XOC_DIA_THREE_WHITE * 4;

              if (totalWin > 0) {
                s.balance += Math.round(totalWin);
                s.lastWinAmount = Math.round(totalWin);
                playSound("win");
              } else {
                s.lastWinAmount = null;
              }

              s.history.push({ seq: s.roundSeq, redCount, isEven });
              if (s.history.length > 24) s.history.shift();
              redrawRoadmap();

              s.timeLeft = 5;
            } else if (s.phase === "RESULT") {
              s.phase = "SETTLE";
              s.timeLeft = 2;
            } else {
              s.prevBets = { ...s.bets };
              s.bets = {
                XOC_DIA_EVEN: 0,
                XOC_DIA_ODD: 0,
                XOC_DIA_FOUR_RED: 0,
                XOC_DIA_FOUR_WHITE: 0,
                XOC_DIA_THREE_RED: 0,
                XOC_DIA_THREE_WHITE: 0,
              };
              s.lastWinAmount = null;
              s.roundSeq += 1;
              s.phase = "BETTING_OPEN";
              playSound("bell");
              s.timeLeft = 15;
            }
          }
        }, 1000);

        // =========================================================================
        // 12. 60 FPS MAIN RENDER & ANIMATION TICKER
        // =========================================================================
        app.ticker.add((ticker) => {
          const delta = ticker.deltaTime;
          const s = gameStateRef.current;
          s.time += delta * 0.05;
          const t = s.time;

          // 12.1 Update text values
          jackpotValText.text = s.jackpot.toLocaleString();
          heroBalText.text = `$${s.balance.toLocaleString()}`;

          // Update dice in history capsule
          s.prevCoins.forEach((c, idx) => {
            const dg = topDiceSprites[idx];
            dg.clear();
            dg.roundRect(-9, -9, 18, 18, 4);
            dg.fill({ color: c === 1 ? 0xdc2626 : 0xf1f5f9 });
            dg.stroke({ color: c === 1 ? 0xffffff : 0x000000, width: 1 });
            dg.circle(0, 0, 3);
            dg.fill({ color: c === 1 ? 0xffffff : 0xdc2626 });
          });

          // Update user bet badges
          chanUserBetBadge.text = s.bets.XOC_DIA_EVEN > 0 ? `$${(s.bets.XOC_DIA_EVEN / 1000).toLocaleString()}K` : "";
          leUserBetBadge.text = s.bets.XOC_DIA_ODD > 0 ? `$${(s.bets.XOC_DIA_ODD / 1000).toLocaleString()}K` : "";
          viUserBetBadges[0].text = s.bets.XOC_DIA_FOUR_RED > 0 ? `$${(s.bets.XOC_DIA_FOUR_RED / 1000).toLocaleString()}K` : "";
          viUserBetBadges[1].text = s.bets.XOC_DIA_FOUR_WHITE > 0 ? `$${(s.bets.XOC_DIA_FOUR_WHITE / 1000).toLocaleString()}K` : "";
          viUserBetBadges[2].text = s.bets.XOC_DIA_THREE_WHITE > 0 ? `$${(s.bets.XOC_DIA_THREE_WHITE / 1000).toLocaleString()}K` : "";
          viUserBetBadges[3].text = s.bets.XOC_DIA_THREE_RED > 0 ? `$${(s.bets.XOC_DIA_THREE_RED / 1000).toLocaleString()}K` : "";

          // Update 4 coins in the open plate
          s.coins.forEach((c, idx) => {
            coinSprites[idx].texture = c === 1 ? textures["coin-red.png"] : textures["coin-white.png"];
          });

          // Status Badge Text
          if (s.phase === "BETTING_OPEN") {
            statusText.text = `CƯỢC: ${s.timeLeft}s`;
            statusText.style.fill = 0xffea79;
          } else if (s.phase === "BETTING_CLOSED") {
            statusText.text = "KHÓA CƯỢC";
            statusText.style.fill = 0xf87171;
          } else if (s.phase === "SPINNING") {
            statusText.text = "ĐANG XÓC...";
            statusText.style.fill = 0xfbbf24;
          } else if (s.phase === "RESULT") {
            const rCount = s.coins.reduce((a, b) => a + b, 0);
            statusText.text = `${rCount % 2 === 0 ? "CHẴN" : "LẺ"} (${rCount}Đ ${4 - rCount}T)`;
            statusText.style.fill = 0x34d399;
          } else {
            statusText.text = "TRẢ THƯỞNG";
            statusText.style.fill = 0xe2e8f0;
          }

          // 12.2 Bát Đĩa Animation & Physics
          if (s.phase === "SPINNING") {
            closedSetContainer.visible = true;
            openSetContainer.visible = false;
            const shakeX = Math.sin(t * 36) * 7;
            const shakeY = Math.sin(t * 28) * 4;
            closedSetContainer.x = bowlCenterX + shakeX;
            closedSetContainer.y = bowlCenterY + shakeY;
            closedSetContainer.rotation = Math.sin(t * 32) * 0.06;
            s.bowlLiftProgress = 0;
            s.manualOffset = { x: 0, y: 0 };
          } else if (s.phase === "BETTING_OPEN" || s.phase === "BETTING_CLOSED") {
            closedSetContainer.visible = true;
            openSetContainer.visible = false;
            closedSetContainer.x += (bowlCenterX - closedSetContainer.x) * 0.2;
            closedSetContainer.y += (bowlCenterY - closedSetContainer.y) * 0.2;
            closedSetContainer.rotation += (0 - closedSetContainer.rotation) * 0.2;
            s.bowlLiftProgress = 0;
            s.manualOffset = { x: 0, y: 0 };
          } else if (s.phase === "RESULT" || s.phase === "SETTLE") {
            closedSetContainer.visible = false;
            openSetContainer.visible = true;

            if (!s.isDragging) {
              s.bowlLiftProgress = Math.min(1, s.bowlLiftProgress + delta * 0.03);
            }
            const p = s.bowlLiftProgress;
            const easeP = 1 - Math.pow(1 - p, 3);
            const autoLiftY = -55 * easeP;

            liftedBowlContainer.x = s.manualOffset.x;
            liftedBowlContainer.y = autoLiftY + s.manualOffset.y;
            liftedBowlContainer.rotation = -0.12 * easeP;
          }

          // 12.3 Winning pulse highlights on cards
          const isCurrentEven = s.coins.reduce((a, b) => a + b, 0) % 2 === 0;
          if (s.phase === "RESULT" || s.phase === "SETTLE") {
            if (isCurrentEven) {
              chanCard.scale.set(1 + Math.sin(t * 10) * 0.02);
              leCard.scale.set(1);
            } else {
              leCard.scale.set(1 + Math.sin(t * 10) * 0.02);
              chanCard.scale.set(1);
            }
          } else {
            chanCard.scale.set(1);
            leCard.scale.set(1);
          }

          // 12.4 Dealer breathing micro-motion
          if (dealerSprite) {
            dealerSprite.scale.set(1 + Math.sin(t * 2.5) * 0.015);
          }

          // 12.5 Flying chips animation
          for (let i = s.flyingChips.length - 1; i >= 0; i--) {
            const fc = s.flyingChips[i];
            fc.progress += delta * 0.045;
            if (fc.progress >= 1) {
              app.stage.removeChild(fc.sprite);
              fc.sprite.destroy();
              s.flyingChips.splice(i, 1);
            } else {
              const curX = fc.fromX + (fc.toX - fc.fromX) * fc.progress;
              const curY = fc.fromY + (fc.toY - fc.fromY) * fc.progress - Math.sin(fc.progress * Math.PI) * 45;
              fc.sprite.x = curX;
              fc.sprite.y = curY;
              fc.sprite.rotation += 0.2;
            }
          }
        });

        // Cleanup
        return () => {
          clearInterval(gameInterval);
          window.removeEventListener("pointermove", onGlobalMove);
          window.removeEventListener("pointerup", onGlobalUp);
        };
      })
      .catch((err) => {
        console.error("PIXI initialization error in XocDiaPixiGame", err);
      });

    return () => {
      isDestroyed = true;
      if (appRef.current) {
        try {
          appRef.current.destroy(true, { children: true, texture: false });
        } catch {
          // ignore
        }
        appRef.current = null;
      }
    };
  }, []);

  return (
    <div className="relative w-full h-full min-h-dvh bg-[#070503] flex items-center justify-center select-none overflow-hidden font-sans">
      <div
        ref={canvasContainerRef}
        style={{
          width: 1024,
          height: 507,
          transform: `scale(${scale})`,
          transformOrigin: "center center",
        }}
        className="relative overflow-hidden shadow-[0_0_80px_rgba(0,0,0,0.95)]"
      />
    </div>
  );
};

export default XocDiaPixiGame;
