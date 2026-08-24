/**
 * Định dạng tiền để HIỂN THỊ.
 *
 * Backend dùng BigDecimal scale 8 (xem Money.SCALE) nên "500" trả về là
 * "500.00000000". Đưa nguyên chuỗi đó lên giao diện thì rất khó đọc, nhất là trong
 * bảng nhiều dòng.
 *
 * NGUYÊN TẮC: các hàm ở đây CHỈ dùng cho hiển thị. Khi gửi tiền lên server phải
 * giữ nguyên chuỗi gốc — số thực dấu phẩy động của JS làm tròn sai ở các số lẻ,
 * và toàn bộ sổ sách bên backend tính bằng BigDecimal.
 */

/** Số chữ số thập phân hiển thị. Phần lẻ nhỏ hơn mức này bị ẩn, KHÔNG bị mất. */
const DISPLAY_DECIMALS = 2;

/**
 * Định dạng một giá trị tiền hoặc tỷ lệ để hiển thị.
 *
 * Nhận cả `number` dù mọi API của dự án trả tiền dạng chuỗi: kiểu TypeScript chỉ là khai
 * báo, không kiểm được dữ liệu JSON thật lúc chạy. Đã gặp thật — một DTO backend khai
 * `BigDecimal` khiến Jackson trả SỐ JSON, và `raw.trim()` làm sập cả trang đặt cược thay
 * vì hiện sai một ô. Chuyển qua `String()` để lỗi kiểu ở tầng API không đánh sập giao diện.
 *
 * `number` vẫn KHÔNG phải kiểu đúng cho tiền ở phía server — xem ghi chú đầu tệp. Chỗ nào
 * thấy API trả số thì sửa ở DTO, đừng dựa vào hàm này.
 */
export function formatMoney(raw: string | number | null | undefined): string {
  if (raw === null || raw === undefined || raw === "") return "0.00";

  const text = String(raw).trim();
  if (text === "") return "0.00";

  const negative = text.startsWith("-");
  const digits = text.replace(/^[-+]/, "");
  const [whole, fraction = ""] = digits.split(".");

  // Nhóm hàng nghìn theo chuẩn dấu phẩy để dễ đọc số lớn.
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  const shownFraction = fraction.padEnd(DISPLAY_DECIMALS, "0").slice(0, DISPLAY_DECIMALS);

  return `${negative ? "-" : ""}${grouped}.${shownFraction}`;
}

export function isValidPositiveAmount(raw: string): boolean {
  const trimmed = raw.trim();
  if (!/^\d+(\.\d+)?$/.test(trimmed)) return false;
  return Number(trimmed) > 0;
}

/**
 * So sánh hai chuỗi tiền. Trả về số âm / 0 / dương như hàm so sánh thông thường.
 *
 * So theo từng phần chữ số thay vì `Number()`: với scale 8 của backend, một số dư như
 * "9007199254740993.00000000" vượt quá `Number.MAX_SAFE_INTEGER` và bị làm tròn âm
 * thầm, khiến phép so sánh cho kết quả sai mà không có dấu hiệu gì.
 */
export function compareMoney(a: string, b: string): number {
  const parse = (v: string) => {
    const t = v.trim();
    const negative = t.startsWith("-");
    const [i = "0", f = ""] = t.replace(/^[-+]/, "").split(".");
    return { negative, int: i.replace(/^0+(?=\d)/, "") || "0", frac: f };
  };

  const x = parse(a);
  const y = parse(b);

  if (x.negative !== y.negative) return x.negative ? -1 : 1;
  // Với số âm, chuỗi chữ số lớn hơn nghĩa là giá trị NHỎ hơn.
  const flip = x.negative ? -1 : 1;

  if (x.int.length !== y.int.length) {
    return (x.int.length < y.int.length ? -1 : 1) * flip;
  }
  if (x.int !== y.int) return (x.int < y.int ? -1 : 1) * flip;

  const len = Math.max(x.frac.length, y.frac.length);
  const xf = x.frac.padEnd(len, "0");
  const yf = y.frac.padEnd(len, "0");
  if (xf === yf) return 0;
  return (xf < yf ? -1 : 1) * flip;
}

/**
 * Nhân chuỗi tiền với một số nguyên không âm, giữ nguyên độ chính xác.
 *
 * Dùng cho tổng tiền cược = tiền mỗi lựa chọn × số lựa chọn. Nhân bằng `BigInt` trên
 * chuỗi chữ số nên không có sai số dấu phẩy động, và không giới hạn độ lớn.
 *
 * Kết quả giữ đúng số chữ số thập phân của đầu vào — dùng `formatMoney` khi hiển thị.
 */
export function multiplyMoney(value: string, times: number): string {
  if (!Number.isInteger(times) || times < 0) {
    throw new Error(`multiplyMoney: times phải là số nguyên không âm, nhận ${times}`);
  }
  if (times === 0) return "0";

  const trimmed = value.trim();
  if (trimmed === "") return "0";

  const negative = trimmed.startsWith("-");
  const [i = "0", f = ""] = trimmed.replace(/^[-+]/, "").split(".");

  // Ghép thành số nguyên (bỏ dấu phẩy), nhân, rồi chèn lại dấu phẩy đúng vị trí.
  const digits = (i + f).replace(/^0+(?=\d)/, "") || "0";
  const product = (BigInt(digits) * BigInt(times)).toString();

  if (f.length === 0) return (negative ? "-" : "") + product;

  const padded = product.padStart(f.length + 1, "0");
  const cut = padded.length - f.length;
  return `${negative ? "-" : ""}${padded.slice(0, cut)}.${padded.slice(cut)}`;
}
