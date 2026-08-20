import type { Metadata } from "next";
import { Roboto } from "next/font/google";
import "./globals.css";
import { LanguageProvider } from "@/context/LanguageContext";

const roboto = Roboto({
  weight: ["300", "400", "500", "700", "900"],
  subsets: ["latin", "vietnamese"],
  display: "swap",
});

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
    <html lang="vi" className={roboto.className}>
      <body className={`${roboto.className} bg-[#070709] min-h-screen text-white antialiased`}>
        <LanguageProvider>{children}</LanguageProvider>
      </body>
    </html>
  );
}
