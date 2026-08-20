import { PlayerLayout } from "@/components/layout/PlayerLayout";
import { Header } from "@/components/layout/Header";
import { BannerCarousel } from "@/components/home/BannerCarousel";
import { MarqueeNotice } from "@/components/home/MarqueeNotice";
import { QuickActions } from "@/components/home/QuickActions";
import { GameGrid } from "@/components/home/GameGrid";
import { LanguageBar } from "@/components/home/LanguageBar";
import { FooterInfo } from "@/components/home/FooterInfo";

export default function Home() {
  return (
    <PlayerLayout>
      <main className="flex flex-col w-full min-h-screen">
        {/* Header */}
        <Header username="jinbao01" />

        {/* Hero Banner Carousel */}
        <BannerCarousel />

        {/* Marquee Announcement Ticker */}
        <MarqueeNotice />

        {/* Quick Action Buttons 2x2 Grid */}
        <QuickActions />

        {/* Game List Section 2x2 Grid */}
        <GameGrid />

        {/* Language Switcher Bar */}
        <LanguageBar />

        {/* Partner & Certification Footer Info */}
        <FooterInfo />
      </main>
    </PlayerLayout>
  );
}
