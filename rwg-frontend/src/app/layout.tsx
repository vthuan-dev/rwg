import type { Metadata } from "next";
import "./globals.css";
import { BottomNav } from "@/components/layout/BottomNav";
import { LanguageProvider } from "@/context/LanguageContext";

export const metadata: Metadata = {
  title: "Resorts World Genting - Sảnh Casino Trực Tuyến",
  description:
    "Trải nghiệm casino đẳng cấp với sảnh Lucky 28, British Lucky 28, Korean Lucky 28 & Taiwan Times.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi">
      <body className="bg-[#070709] min-h-screen flex justify-center text-white antialiased">
        <LanguageProvider>
          <div className="w-full max-w-[500px] bg-[#0d0d0f] min-h-screen flex flex-col relative shadow-2xl pb-20 border-x border-[#1a1a1e]">
            {children}
            <BottomNav />
          </div>
        </LanguageProvider>
      </body>
    </html>
  );
}
