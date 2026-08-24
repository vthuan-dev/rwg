/**
 * Kiểm tra một đường dẫn nội bộ trước khi dùng để điều hướng.
 *
 * Trang đặt cược nhận đích quay lại từ tham số `?ref=` trên URL — nghĩa là giá trị đó
 * do BÊN NGOÀI cung cấp và ai cũng gửi được. Điều hướng thẳng tới nó là lỗ hổng
 * chuyển hướng mở: kẻ tấn công gửi liên kết `/bet/detail?id=x&ref=https://trang-gia.com`,
 * người chơi thấy tên miền thật của mình nên tin, bấm nút quay lại và bị đưa sang trang
 * giả mạo để nhập lại mật khẩu.
 *
 * Chỉ nhận đường dẫn tương đối bắt đầu bằng một dấu `/`. Các dạng bị chặn:
 * - `https://...`, `http://...` — tên miền khác
 * - `//trang-khac.com` — trình duyệt hiểu là giao thức tương đối, vẫn ra ngoài
 * - `javascript:...`, `data:...` — thực thi mã
 * - `/\trang-khac.com` — một số trình duyệt coi `\` như `/`
 */
export function safeInternalPath(raw: string | null, fallback: string): string {
  if (!raw) return fallback;

  const value = raw.trim();
  if (value === "") return fallback;

  // Phải bắt đầu bằng đúng MỘT dấu gạch chéo.
  if (!value.startsWith("/")) return fallback;
  if (value.startsWith("//")) return fallback;

  // Chặn `/\host` và `/\\host`: một số trình duyệt chuẩn hoá `\` thành `/`, biến đây
  // thành `//host` tức là một tên miền khác.
  if (value.startsWith("/\\")) return fallback;

  // Ký tự điều khiển có thể dùng để cắt chuỗi và lách các phép kiểm ở trên.
  if (/[\u0000-\u001f\u007f]/.test(value)) return fallback;

  return value;
}
