/**
 * Hiển thị thời gian cho khu quản trị.
 *
 * VÌ SAO NẰM Ở `lib/` CHỨ KHÔNG NHÚNG TRONG TRANG: bảng danh sách người dùng và mục lịch sử
 * đăng nhập trong modal chi tiết đều cần cùng một cách hiển thị. Nhúng trong trang thì phải
 * viết hai lần, và hai bản sẽ trôi khỏi nhau ngay lần sửa đầu tiên.
 */

/** Các mốc quy đổi, đơn vị lớn trước để vòng lặp dừng ở đơn vị đọc được nhất. */
const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ["day", 86_400_000],
  ["hour", 3_600_000],
  ["minute", 60_000],
];

/** Quá mốc này thì thời gian tương đối mất nghĩa ("42 ngày trước" không giúp gì). */
const ABSOLUTE_AFTER_DAYS = 7;

/**
 * Ngày giờ đầy đủ, dùng cho tooltip và cho các mốc đã quá xa.
 *
 * Luôn hiện CẢ giờ phút: với việc điều tra một tài khoản, "hôm qua" và "hôm qua 3 giờ sáng"
 * là hai thông tin khác nhau về mức đáng ngờ.
 */
export function formatAbsoluteDateTime(iso: string, locale: string): string {
  return new Date(iso).toLocaleString(locale, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * Thời gian tương đối theo ngôn ngữ đang chọn: "5 phút trước", "3 giờ trước", "2 ngày trước".
 *
 * Quá {@link ABSOLUTE_AFTER_DAYS} ngày thì trả về ngày giờ tuyệt đối.
 *
 * DÙNG `Intl.RelativeTimeFormat` THAY VÌ TỰ GHÉP CHUỖI: khu quản trị có 6 ngôn ngữ, và tự
 * ghép sẽ cần 6 bộ quy tắc số nhiều khác nhau (tiếng Nga/Ả Rập còn nhiều dạng hơn). Trình
 * duyệt đã có sẵn dữ liệu đó.
 *
 * Mốc TƯƠNG LAI (lệch giờ giữa máy khách và máy chủ) được xử lý bằng `Math.abs` rồi luôn
 * hiển thị ở thể quá khứ: một lần đăng nhập không thể xảy ra ở tương lai, nên "trong 2 phút
 * nữa" chỉ là đồng hồ lệch chứ không phải thông tin thật.
 */
export function formatRelativeTime(iso: string, locale: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";

  const elapsed = Math.abs(Date.now() - then);

  if (elapsed >= ABSOLUTE_AFTER_DAYS * 86_400_000) {
    return formatAbsoluteDateTime(iso, locale);
  }

  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });

  for (const [unit, ms] of UNITS) {
    const value = Math.floor(elapsed / ms);
    if (value >= 1) return rtf.format(-value, unit);
  }

  // Dưới một phút: "vài giây trước" thay vì đếm từng giây, vì con số chính xác ở mức đó
  // không đổi quyết định nào của người vận hành.
  return rtf.format(-1, "minute");
}

/**
 * Mốc thời gian có "còn mới" không (trong vòng 24 giờ).
 *
 * Dùng để chấm màu trên bảng: người vận hành quét mắt xuống cột và cần thấy ngay tài khoản
 * nào còn hoạt động, trước khi kịp đọc chữ.
 */
export function isWithinLast24Hours(iso: string): boolean {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return false;
  return Date.now() - then < 86_400_000;
}
