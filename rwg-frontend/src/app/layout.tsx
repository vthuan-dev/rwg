import type { Metadata, Viewport } from "next";
import { Roboto, IBM_Plex_Sans } from "next/font/google";
import "./globals.css";
import { LanguageProvider } from "@/context/LanguageContext";
import { NotificationProvider } from "@/context/NotificationContext";

/**
 * Font chính của toàn app.
 *
 * MỖI độ đậm là MỘT file woff2 riêng phải tải về. Danh sách này đã lược theo mức
 * dùng thật trong mã: `font-light` (300) không xuất hiện ở đâu nên bỏ. Bốn mức còn
 * lại đều đang dùng: 400 (mặc định), 500, 700, 900.
 */
const roboto = Roboto({
  weight: ["400", "500", "700", "900"],
  subsets: ["latin", "vietnamese"],
  variable: "--font-roboto",
  display: "swap",
});

/**
 * Font của khu đăng nhập/đăng ký, khớp với trang gốc.
 *
 * Nạp qua next/font nên file font được self-host trong bundle: không có request
 * sang Google lúc người dùng mở trang, và không bị nhảy layout vì Next tự tính
 * số liệu font dự phòng.
 *
 * CHỈ ba độ đậm: khu xác thực dùng đúng `font-normal`, `font-medium` và `font-bold`
 * (đã rà toàn bộ 10 file của khu này). Trước đây nạp năm mức, tức là hai file woff2
 * tải về mà không ký tự nào dùng tới.
 *
 * Có subset "vietnamese": giao diện mặc định là tiếng Việt, thiếu subset này thì
 * các chữ có dấu sẽ rơi về font dự phòng và trông lệch hẳn so với phần còn lại.
 */
const ibmPlexSans = IBM_Plex_Sans({
  weight: ["400", "500", "700"],
  subsets: ["latin", "vietnamese"],
  variable: "--font-ibm-plex-sans",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Resorts World Genting - Sảnh Casino Trực Tuyến",
  description:
    "Trải nghiệm casino đẳng cấp với sảnh Lucky 28, British Lucky 28, Korean Lucky 28 & Taiwan Times.",
};

/**
 * Cấu hình khung nhìn cho giao diện mobile-first.
 *
 * `viewportFit: "cover"` là ĐIỀU KIỆN BẮT BUỘC để `env(safe-area-inset-*)` trong
 * CSS trả về giá trị khác 0. Thiếu nó thì mọi xử lý vùng an toàn (thanh điều
 * hướng dưới không bị thanh gesture iPhone che, lề khi xoay ngang) hoàn toàn
 * không có tác dụng — mà trên máy tính thì không thấy gì bất thường, nên lỗi này
 * chỉ lộ ra khi mở trên điện thoại thật.
 *
 * CỐ TÌNH KHÔNG đặt `maximumScale: 1` hay `userScalable: false`: chặn phóng to là
 * rào cản với người mắt kém, và Safari iOS cũng bỏ qua từ lâu nên chỉ còn tác
 * dụng phụ mà không đạt mục đích.
 */
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#070709",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="vi"
      className={`${roboto.variable} ${ibmPlexSans.variable} ${roboto.className}`}
    >
      {/* min-h-dvh chứ không min-h-screen: `100vh` trên Safari iOS tính theo chiều
          cao khi thanh địa chỉ đã thu gọn nên trang bị dư một khoảng trượt. */}
      <body className={`${roboto.className} bg-[#070709] min-h-dvh text-white antialiased`}>
        <LanguageProvider>
          <NotificationProvider>{children}</NotificationProvider>
        </LanguageProvider>
      </body>
    </html>
  );
}
