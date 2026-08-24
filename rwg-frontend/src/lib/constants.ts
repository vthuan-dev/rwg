/**
 * Constants & Env-Driven API Configuration for RWG Frontend
 */

export const USER_API_BASE_URL =
  process.env.NEXT_PUBLIC_USER_API_URL || "http://localhost:8080/api/v1";

export const ADMIN_API_BASE_URL =
  process.env.NEXT_PUBLIC_ADMIN_API_URL || "http://localhost:8081/api/v1";

export const USER_BASE_URL =
  process.env.NEXT_PUBLIC_USER_BASE_URL || "http://localhost:8080";

export const ADMIN_BASE_URL =
  process.env.NEXT_PUBLIC_ADMIN_BASE_URL || "http://localhost:8081";

export const WS_BASE_URL =
  process.env.NEXT_PUBLIC_WS_URL || "http://localhost:8080/ws";

/**
 * WebSocket của khu QUẢN TRỊ (cổng 8081).
 *
 * Endpoint riêng, không dùng chung WS_BASE_URL: mỗi app chạy broker STOMP riêng
 * trong bộ nhớ của nó, và mỗi app chỉ nhận token của đúng nhóm người dùng
 * (rwg.websocket.audience). Nhân sự nối vào cổng 8080 sẽ bị từ chối ngay ở frame
 * CONNECT, và cũng sẽ không nhận được gói nào từ hộp thư hỗ trợ.
 */
export const ADMIN_WS_URL =
  process.env.NEXT_PUBLIC_ADMIN_WS_URL || "http://localhost:8081/ws";

export const ADMIN_SECRET_PATH =
  process.env.NEXT_PUBLIC_ADMIN_SECRET_PATH || "2026";

export const ADMIN_URL_PREFIX = `/admin/${ADMIN_SECRET_PATH}`;

/**
 * Tên miền công khai của chính frontend, dùng khi cần một URL TUYỆT ĐỐI để gửi ra
 * ngoài — liên kết giới thiệu, mã QR, thẻ chia sẻ.
 *
 * KHÔNG CÓ GIÁ TRỊ MẶC ĐỊNH GÁN CỨNG. Một chuỗi như "http://localhost:3000" đặt ở
 * đây sẽ đi theo bản build lên máy thật nếu ai đó quên khai báo biến môi trường, và
 * người chơi sẽ gửi cho bạn bè một liên kết trỏ về MÁY CỦA CHÍNH HỌ — không ai mở
 * được, và không có gì báo lỗi.
 *
 * Backend KHÔNG thể tự ghép giá trị này: nó nằm sau reverse proxy nên chỉ thấy tên
 * miền nội bộ. Đó là lý do `ReferralCodeResponse.registerPath` chỉ trả đường dẫn
 * tương đối.
 */
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "";

/**
 * Ghép một đường dẫn tương đối thành URL tuyệt đối.
 *
 * Dự phòng bằng `window.location.origin` khi thiếu biến môi trường: đúng hơn mọi
 * chuỗi gán cứng vì nó là tên miền người dùng ĐANG thật sự truy cập. Nhưng đây
 * chỉ là dự phòng: sau reverse proxy hoặc khi trang được mở qua nhiều tên miền,
 * `origin` sẽ khác nhau giữa các người dùng — luôn khai báo NEXT_PUBLIC_SITE_URL
 * trên môi trường thật.
 *
 * Trả "" khi chạy trên server và không có env, thay vì trả riêng đường dẫn tương
 * đối: một liên kết dở dang bị copy đi thì người nhận mở ra trang lỗi, còn ô trống
 * thì nhìn ra ngay là chưa tải xong.
 */
export function siteUrl(relativePath: string): string {
  const origin =
    SITE_URL || (typeof window !== "undefined" ? window.location.origin : "");
  if (!origin) return "";
  // Bo dau / cuoi origin de khong sinh URL co "//" o giua.
  return origin.replace(/\/+$/, "") + relativePath;
}

export const KL28_ODDS = {
  BIG: "1.98",
  SMALL: "1.98",
  SINGLE: "1.98",
  DOUBLE: "1.98",
  NUMBER_ODDS: [
    "999", "332", "165", "99", "65", "46", "34", "26", "21", "17",
    "14", "13", "12", "12", "12", "12", "13", "14", "17", "21",
    "26", "34", "46", "65", "99", "165", "332", "999"
  ]
} as const;
