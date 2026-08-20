import type { Metadata } from "next";
import "./globals.css";
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
      <body className="bg-[#070709] min-h-screen text-white antialiased">
        <LanguageProvider>{children}</LanguageProvider>
      </body>
    </html>
  );
}
