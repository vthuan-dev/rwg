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
      {/* KHÔNG đặt min-h ở đây: MobileShell đã giữ min-h-dvh. Thêm nữa sẽ cộng dồn
          chiều cao và sinh ra một khoảng trượt rỗng bằng chiều cao thanh dưới. */}
      <main className="flex flex-col w-full grow">
        <Header />
        <BannerCarousel />
        <MarqueeNotice />
        <QuickActions />
        <GameGrid />
        <LanguageBar />
        <FooterInfo />
      </main>
    </PlayerLayout>
  );
}
