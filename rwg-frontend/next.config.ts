import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /**
   * `lucide-react` xuất hơn 1600 icon từ một file index. Không có mục này, việc
   * `import { Home } from "lucide-react"` khiến bundler phải đọc và phân tích toàn
   * bộ cây icon rồi mới loại bỏ phần không dùng — chậm cả lúc build lẫn lúc dev,
   * và ở chế độ dev thì không loại bỏ gì cả.
   *
   * `optimizePackageImports` chuyển các import kiểu này thành import trực tiếp tới
   * từng file icon, nên chỉ icon được dùng mới đi vào bundle.
   */
  experimental: {
    optimizePackageImports: ["lucide-react"],
  },

  /**
   * Bật nén gzip cho phản hồi từ server Next.
   *
   * Ở môi trường thật thường có reverse proxy (nginx/CDN) lo việc này, nhưng bật ở
   * đây để `next start` một mình cũng đã nén — nếu không, mỗi lượt tải trang gửi
   * nguyên khối JS chưa nén.
   */
  compress: true,

  images: {
    /**
     * Chỉ giữ các bề rộng khớp với bố cục thật, thay cho danh sách mặc định của Next
     * (640/750/828/1080/1200/1920/2048/3840).
     *
     * Khung nội dung tối đa 640px, nên:
     *   320 = ô lưới 2 cột trên máy hẹp
     *   640 = ô lưới ở màn hình retina 2x, hoặc banner rộng hết khung
     *   828 = logo (giữ vì logo1.png đang dùng bề rộng này)
     *  1280 = banner ở retina 2x
     *
     * Bỏ 1920/2048/3840: không có phần tử nào rộng tới mức đó, giữ lại chỉ khiến
     * Next sinh thêm các biến thể ảnh không bao giờ được yêu cầu.
     */
    deviceSizes: [320, 640, 828, 1280],
    imageSizes: [64, 128, 256],

    /** AVIF trước WebP: cùng chất lượng thì AVIF nhỏ hơn khoảng 20%. */
    formats: ["image/avif", "image/webp"],

    /**
     * Ảnh trong public/ là ảnh tĩnh của bố cục, không phải nội dung do người dùng
     * tải lên, nên cache lâu là an toàn: đổi ảnh thì đổi luôn tên file.
     */
    minimumCacheTTL: 60 * 60 * 24 * 30,
  },
};

export default nextConfig;
