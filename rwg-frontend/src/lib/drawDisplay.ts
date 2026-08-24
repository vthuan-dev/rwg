/**
 * Phần hiển thị dùng chung của các thẻ sảnh.
 *
 * VÌ SAO TÁCH RA: ba thứ dưới đây trước đó nằm trùng lặp ở `GameHallCard` và
 * `CurrentHallCard`, và giờ thẻ thứ ba (`DrawHistoryHallCard`) cũng cần đúng chúng.
 * Ba bản sao của cùng một bảng màu là ba chỗ phải nhớ sửa khi trang gốc đổi tông,
 * mà quên một chỗ thì hai trang hiện hai màu khác nhau cho cùng một sảnh.
 *
 * KHÔNG đặt trong `components/` vì đây là dữ liệu và hàm thuần, không phải phần tử
 * giao diện — trang cũng cần dùng, không riêng thẻ.
 */

import type { GameRound } from "@/lib/playerApi";

export interface DrawTier {
  /** Màu viền VÀ màu chữ của nhãn hạng — bản gốc dùng cùng một màu cho cả hai. */
  color: string;
  labelKey: string;
}

/**
 * Bốn hạng sảnh, đúng thứ tự và đúng mã màu của trang gốc.
 *
 * Đã đối chiếu với bundle production của bản gốc: bốn màu này nằm ngay trong chuỗi
 * class (`border-[#ff4355]`, `border-[#ffc443]`, `border-[#43d0ff]`,
 * `border-[#f943ff]`), gán theo `id` sảnh 1–4.
 */
export const DRAW_TIERS: readonly DrawTier[] = [
  { color: "#ff4355", labelKey: "draw.tier_premium" },
  { color: "#ffc443", labelKey: "draw.tier_gold" },
  { color: "#43d0ff", labelKey: "draw.tier_platinum" },
  { color: "#f943ff", labelKey: "draw.tier_diamond" },
] as const;

/**
 * Hạng của sảnh thứ `index` trong danh sách.
 *
 * Bản gốc gán hạng theo `id` bàn là số 1–4. Bàn của mình dùng UUID nên không so được;
 * thay vào đó gán theo THỨ TỰ trong danh sách, vốn do backend trả về ổn định.
 *
 * Quay vòng bằng phép chia lấy dư: backend trả 6 bàn mà chỉ có 4 hạng.
 */
export function drawTier(index: number): DrawTier {
  return DRAW_TIERS[index % DRAW_TIERS.length];
}

/**
 * Số thứ tự ảnh nền (1–4) cho lớp `bg-room-{n}`.
 *
 * Trang gốc chỉ có 4 ảnh nền, backend lại trả 6 bàn — nên quay vòng lại từ đầu. Thà
 * lặp ảnh còn hơn để hai thẻ cuối trơ nền đen giữa bốn thẻ có ảnh.
 */
export function drawRoomNumber(index: number): number {
  return (index % 4) + 1;
}

/**
 * Định dạng thời gian `DD-MM-YYYY h:mm A`.
 *
 * Khớp ĐÚNG bản gốc: bundle của họ gọi `dayjs(draw_datetime).format("DD-MM-YYYY h:mm A")`.
 *
 * Tự ghép thay vì thêm dayjs: chỉ cần đúng một định dạng, kéo cả một thư viện ngày
 * tháng vào bundle là không xứng. Cũng KHÔNG dùng `Intl`: nó sẽ tự dịch AM/PM và đổi
 * thứ tự ngày/tháng theo ngôn ngữ máy, còn ở đây cần định dạng CỐ ĐỊNH giống bản gốc.
 */
export function formatDrawTime(iso: string | null): string {
  if (!iso) return "-";

  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "-";

  const pad = (n: number) => String(n).padStart(2, "0");
  const hours24 = d.getHours();
  const meridiem = hours24 < 12 ? "AM" : "PM";
  // 0 giờ và 12 giờ đều hiện là 12 trong hệ 12 giờ.
  const hours12 = hours24 % 12 === 0 ? 12 : hours24 % 12;

  return `${pad(d.getDate())}-${pad(d.getMonth() + 1)}-${d.getFullYear()} ${hours12}:${pad(
    d.getMinutes()
  )} ${meridiem}`;
}

/**
 * Kết quả một ván, đã rút về đúng những gì màn hình cần vẽ.
 *
 * Mỗi trường có thể là null, và chỗ gọi PHẢI xử lý null chứ không thay bằng 0: ba loại
 * game của mình trả ba dạng dữ liệu khác nhau, không loại nào có đủ mọi trường.
 */
export interface DrawSummary {
  /** Tổng ba số. CHỈ Lucky 28 có — Roulette và Baccarat không có khái niệm tổng. */
  sum: number | null;
  /** Các số kết quả, đã thành chuỗi để đưa thẳng vào ô. Rỗng khi ván bị huỷ. */
  numbers: string[];
  /** Khoá dịch Nhỏ/Lớn. Null khi game không có khái niệm này. */
  sizeKey: string | null;
  /** Khoá dịch Chẵn/Lẻ. Null khi game không có khái niệm này. */
  parityKey: string | null;
  /** Bên thắng Baccarat, thay cho hai nhãn Nhỏ-Lớn / Chẵn-Lẻ. */
  baccaratResult: string | null;
  /** Ván đã bị huỷ — không có kết quả nào để hiện. */
  voided: boolean;
}

const EMPTY_SUMMARY: DrawSummary = {
  sum: null,
  numbers: [],
  sizeKey: null,
  parityKey: null,
  baccaratResult: null,
  voided: false,
};

/**
 * Rút kết quả của một ván ra dạng màn hình dùng được.
 *
 * BA LOẠI GAME, BA DẠNG DỮ LIỆU — bản gốc chỉ có một loại (Lucky 28) nên bên họ luôn
 * đủ tổng + ba số + Nhỏ/Lớn + Chẵn/Lẻ. Bên mình:
 *
 * - Lucky 28: `kl28Numbers` ba số, `kl28Sum` là tổng. Suy ra được cả Nhỏ/Lớn và Chẵn/Lẻ.
 * - Roulette: `winningNumber`, MỘT số duy nhất 0–36. Không có tổng.
 * - Baccarat: điểm hai bên. Không có tổng, không có Nhỏ/Lớn hay Chẵn/Lẻ — thay bằng
 *   bên thắng.
 *
 * Ngưỡng Nhỏ/Lớn của Lucky 28 là 14: tổng ba số nằm trong 0–27, nên 0–13 là Nhỏ và
 * 14–27 là Lớn. Con số này PHẢI khớp với luật thanh toán ở backend, đừng đổi một phía.
 */
export function drawSummary(round: GameRound | null): DrawSummary {
  if (!round) return EMPTY_SUMMARY;

  if (round.status === "VOIDED") {
    return { ...EMPTY_SUMMARY, voided: true };
  }

  if (round.kl28Numbers) {
    const numbers = round.kl28Numbers
      .split(",")
      .map((n) => n.trim())
      .filter(Boolean);
    const sum = round.kl28Sum;

    return {
      sum,
      numbers,
      sizeKey: sum == null ? null : sum >= 14 ? "draw.big" : "draw.small",
      parityKey: sum == null ? null : sum % 2 === 0 ? "draw.double" : "draw.single",
      baccaratResult: null,
      voided: false,
    };
  }

  if (round.winningNumber != null) {
    return {
      ...EMPTY_SUMMARY,
      numbers: [String(round.winningNumber)],
    };
  }

  if (round.baccaratPlayerScore != null && round.baccaratBankerScore != null) {
    return {
      ...EMPTY_SUMMARY,
      numbers: [String(round.baccaratPlayerScore), String(round.baccaratBankerScore)],
      baccaratResult: round.baccaratResult,
    };
  }

  return EMPTY_SUMMARY;
}
