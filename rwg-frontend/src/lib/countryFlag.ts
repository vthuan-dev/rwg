/**
 * Chuyển mã quốc gia thành emoji cờ.
 *
 * VÌ SAO EMOJI, KHÔNG DÙNG ẢNH CỜ: một ảnh cờ nghĩa là thêm một request mạng cho mỗi
 * dòng hộp thư, và nếu lấy từ dịch vụ ngoài (flagcdn.com) thì màn hình quản trị phụ thuộc
 * vào một tên miền thứ ba — nó sập là cờ mất. Emoji thì nằm sẵn trong phông của hệ điều
 * hành, không tải gì và không phụ thuộc ai.
 *
 * Đổi lại, Windows không vẽ emoji cờ mà hiện HAI CHỮ CÁI ("VN", "KH") thay cho hình. Điều
 * đó vẫn đọc được và đúng thông tin, nên chấp nhận được cho một chỉ dấu phụ trợ — và phần
 * chữ tên nước bên cạnh mới là thứ mang thông tin chính.
 *
 * CƠ CHẾ: mỗi cờ trong Unicode là hai "Regional Indicator Symbol", tức hai chữ cái của mã
 * ISO dịch lên vùng mã U+1F1E6..U+1F1FF. Chênh lệch 127397 chính là khoảng cách từ 'A'
 * (65) tới U+1F1E6 (127462).
 */
const REGIONAL_INDICATOR_OFFSET = 127397;

/**
 * Emoji cờ của một mã quốc gia ISO 3166-1 alpha-2.
 *
 * @returns chuỗi rỗng khi mã không hợp lệ — chỗ gọi chỉ cần kiểm chuỗi rỗng thay vì
 *          phải tự xác thực mã trước.
 */
export function countryFlagEmoji(code: string | null | undefined): string {
  if (!code) return "";

  const normalized = code.trim().toUpperCase();

  // Đúng hai chữ cái A–Z. Kiểm chặt vì mã sai sẽ tạo ra ký tự Unicode vô nghĩa
  // (hoặc ký tự thay thế hình hộp) chứ không phải một ô trống dễ nhận ra.
  if (!/^[A-Z]{2}$/.test(normalized)) return "";

  return String.fromCodePoint(
    ...[...normalized].map((char) => char.charCodeAt(0) + REGIONAL_INDICATOR_OFFSET)
  );
}
