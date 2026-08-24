/**
 * Bộ lựa chọn cược theo từng loại bàn.
 *
 * Bảng dưới đây là giá trị DỰ PHÒNG, dùng khi chưa tải được tỷ lệ thật từ server. Tỷ lệ
 * thật đến từ `GET /games/tables/{id}/odds`, vì mỗi người chơi có thể được đặt tỷ lệ riêng
 * ở từng bàn.
 *
 * CẢNH BÁO: đừng dùng `multiplier` ở đây để hiển thị nếu đã có dữ liệu từ server. Người
 * chơi có tỷ lệ riêng sẽ thấy con số chung trong khi được trả theo con số khác — nghĩa là
 * thu tiền theo một tỷ lệ và trả theo tỷ lệ khác.
 *
 * Các giá trị dự phòng đã đối chiếu với engine tương ứng, và phải đối chiếu lại mỗi khi
 * engine đổi.
 */

/** Một lựa chọn cược trong lưới. */
export interface BetOption {
  /** Giá trị `betType` gửi cho backend. Phải khớp `BetType` enum. */
  betType: string;
  /** Khoá i18n của nhãn. */
  labelKey: string;
  /**
   * Hệ số nhận về, ĐÃ GỒM tiền cược (quy ước stake-inclusive của backend) và ĐÃ trừ hoa
   * hồng nếu cửa đó có thu.
   *
   * Ví dụ 1.98 nghĩa là cược 100 thắng thì nhận 198, tức lãi 98. Trang gốc cũng
   * hiện con số kiểu này (1.98), không phải "0.98:1".
   */
  multiplier: string;
  /**
   * Tỷ lệ hoa hồng bị trừ khỏi tiền lời, ví dụ "0.05"; bỏ trống nếu không chịu hoa hồng.
   *
   * `multiplier` Ở TRÊN ĐÃ trừ khoản này. Trường này chỉ để chú thích cho người chơi biết
   * vì sao hệ số thấp hơn tỷ lệ niêm yết thông thường — ĐỪNG trừ lần nữa.
   */
  commissionRate?: string | null;
  /**
   * True nếu tỷ lệ này khác mức chung vì người chơi được đặt riêng.
   *
   * Giao diện dùng để nói rõ cho người chơi, thay vì đổi con số mà không giải thích.
   */
  personalized?: boolean;
}

/**
 * Lucky 28 và ba biến thể: British, Korean, Taiwan Times.
 *
 * Bốn lựa chọn này khớp đúng lưới 2×2 của trang mẫu. Odds mặc định 0.98 → hệ số 1.98.
 *
 * Ngưỡng Lớn/Nhỏ là 14: `Kl28Engine` dùng `sum >= 14` cho `KL28_BIG`.
 *
 * Nhãn dùng lại nhóm `draw` — bốn chữ này đã có sẵn ở đó từ trang danh sách sảnh và cùng
 * một ý nghĩa, nên không dịch lại thành `bet.big` để tránh hai bản dịch lệch nhau.
 */
const KL28_OPTIONS: BetOption[] = [
  { betType: "KL28_BIG", labelKey: "draw.big", multiplier: "1.98" },
  { betType: "KL28_SMALL", labelKey: "draw.small", multiplier: "1.98" },
  { betType: "KL28_SINGLE", labelKey: "draw.single", multiplier: "1.98" },
  { betType: "KL28_DOUBLE", labelKey: "draw.double", multiplier: "1.98" },
];

/**
 * Roulette châu Âu.
 *
 * Chỉ liệt kê các cược KHÔNG cần chọn số cụ thể. `STRAIGHT` (35:1), `SPLIT`,
 * `STREET`, `CORNER`, `SIX_LINE`, `COLUMN`, `DOZEN` đều cần `selection` chỉ ra ô nào
 * trên bàn — chúng cần một bàn cược vẽ riêng, không nhét được vào lưới 2 cột.
 */
const ROULETTE_OPTIONS: BetOption[] = [
  { betType: "RED", labelKey: "bet.red", multiplier: "2" },
  { betType: "BLACK", labelKey: "bet.black", multiplier: "2" },
  { betType: "ODD", labelKey: "bet.odd", multiplier: "2" },
  { betType: "EVEN", labelKey: "bet.even", multiplier: "2" },
  { betType: "LOW", labelKey: "bet.low", multiplier: "2" },
  { betType: "HIGH", labelKey: "bet.high", multiplier: "2" },
];

/**
 * Baccarat.
 *
 * `BANKER` bị trừ 5% hoa hồng nên hệ số thực là 1.95, không phải 2 — đây là lý do
 * không thể suy tỷ lệ từ tên loại cược mà phải tra engine.
 */
const BACCARAT_OPTIONS: BetOption[] = [
  { betType: "PLAYER", labelKey: "bet.player", multiplier: "2" },
  {
    betType: "BANKER",
    labelKey: "bet.banker",
    multiplier: "1.95",
    commissionRate: "0.05",
  },
  { betType: "TIE", labelKey: "bet.tie", multiplier: "9" },
  { betType: "PLAYER_PAIR", labelKey: "bet.player_pair", multiplier: "12" },
  { betType: "BANKER_PAIR", labelKey: "bet.banker_pair", multiplier: "12" },
];

/** Số lựa chọn tối đa một lần, theo đúng bản gốc. */
export const MAX_BET_SELECTIONS = 7;

/** Bộ lựa chọn của một loại bàn. Loại lạ trả mảng rỗng thay vì ném lỗi. */
export function betOptionsFor(gameType: string): BetOption[] {
  switch (gameType) {
    case "KL28":
    case "LUCKY28":
    case "BRITISH_LUCKY28":
    case "TAIWAN_TIMES":
      return KL28_OPTIONS;
    case "ROULETTE":
      return ROULETTE_OPTIONS;
    case "BACCARAT":
      return BACCARAT_OPTIONS;
    default:
      return [];
  }
}

/** Nhãn i18n theo `betType`, dựng từ ba bảng trên. */
const LABEL_BY_BET_TYPE: Record<string, string> = Object.fromEntries(
  [...KL28_OPTIONS, ...ROULETTE_OPTIONS, ...BACCARAT_OPTIONS].map((o) => [
    o.betType,
    o.labelKey,
  ])
);

/**
 * Gộp tỷ lệ thật từ server vào bộ lựa chọn để hiển thị.
 *
 * Giữ THỨ TỰ của bảng khai báo, không dùng thứ tự server trả về: thứ tự quyết định vị trí
 * ô trên lưới, và người chơi quen bấm theo vị trí. Nếu thứ tự đổi giữa hai lần tải thì
 * người chơi bấm nhầm ô.
 *
 * Loại cược server trả về mà bảng khai báo không có sẽ bị BỎ QUA, không tự thêm vào lưới:
 * một ô cược không có nhãn dịch sẽ hiện chuỗi thô, và không có gì bảo đảm nó vừa lưới 2
 * cột. `KL28_NUMBER` là ví dụ — nó cần chọn số 0–27 nên thuộc tab Special Code.
 *
 * @param gameType loại bàn
 * @param serverOdds dữ liệu từ `GET /games/tables/{id}/odds`, hoặc null nếu chưa tải được
 */
export function mergeServerOdds(
  gameType: string,
  serverOdds:
    | {
        betType: string;
        multiplier: string;
        commissionRate: string | null;
        personalized: boolean;
      }[]
    | null
): BetOption[] {
  const fallback = betOptionsFor(gameType);
  if (!serverOdds || serverOdds.length === 0) {
    return fallback;
  }

  const byType = new Map(serverOdds.map((o) => [o.betType, o]));

  return fallback.map((option) => {
    const fromServer = byType.get(option.betType);
    if (!fromServer) {
      return option;
    }
    return {
      ...option,
      labelKey: LABEL_BY_BET_TYPE[option.betType] ?? option.labelKey,
      multiplier: fromServer.multiplier,
      commissionRate: fromServer.commissionRate,
      personalized: fromServer.personalized,
    };
  });
}
