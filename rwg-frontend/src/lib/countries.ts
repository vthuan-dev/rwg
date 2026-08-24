/**
 * Danh sách quốc gia cho ô chọn ở trang chỉnh sửa hồ sơ.
 *
 * VÌ SAO NHÚNG SẴN THAY VÌ GỌI API NGOÀI: ô chọn quốc gia không đáng để trang phụ thuộc
 * thêm một dịch vụ mạng. Danh sách này gần như không bao giờ đổi, và khi dịch vụ ngoài
 * sập thì người dùng không lưu được hồ sơ chỉ vì không tải được tên nước.
 *
 * LƯU MÃ ISO 3166-1 alpha-2, không lưu tên: cột `users.country_code` là CHAR(2) và tên
 * hiển thị phụ thuộc ngôn ngữ đang xem. Xem chú thích ở `UpdateProfileRequest`.
 *
 * Phạm vi: các nước Đông Nam Á và Đông Á — nơi có người dùng thật — cộng vài thị trường
 * lớn khác. Cần thêm nước nào thì thêm vào đây; danh sách xếp theo tên tiếng Anh để tìm
 * bằng mắt cho nhanh.
 */
export interface Country {
  /** Mã ISO 3166-1 alpha-2, chữ in hoa. */
  code: string;
  /** Tên tiếng Anh — dùng làm nhãn dự phòng khi ngôn ngữ hiện tại chưa có bản dịch. */
  name: string;
}

export const COUNTRIES: Country[] = [
  { code: "AU", name: "Australia" },
  { code: "BN", name: "Brunei" },
  { code: "KH", name: "Cambodia" },
  { code: "CN", name: "China" },
  { code: "HK", name: "Hong Kong" },
  { code: "IN", name: "India" },
  { code: "ID", name: "Indonesia" },
  { code: "JP", name: "Japan" },
  { code: "LA", name: "Laos" },
  { code: "MO", name: "Macau" },
  { code: "MY", name: "Malaysia" },
  { code: "MM", name: "Myanmar" },
  { code: "NZ", name: "New Zealand" },
  { code: "PH", name: "Philippines" },
  { code: "SG", name: "Singapore" },
  { code: "KR", name: "South Korea" },
  { code: "TW", name: "Taiwan" },
  { code: "TH", name: "Thailand" },
  { code: "GB", name: "United Kingdom" },
  { code: "US", name: "United States" },
  { code: "VN", name: "Vietnam" },
];

/**
 * Tên nước theo mã; trả về chính mã đó nếu không có trong danh sách.
 *
 * Trả lại mã thay vì chuỗi rỗng: tài khoản cũ có thể mang mã hợp lệ mà danh sách này chưa
 * liệt kê, và hiện "ZA" vẫn hữu ích hơn hiện một ô trống.
 */
export function countryName(code: string | null | undefined): string {
  if (!code) return "";
  const found = COUNTRIES.find((c) => c.code === code.toUpperCase());
  return found ? found.name : code;
}
