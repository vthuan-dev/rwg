"use client";

import React, { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Loader2,
  Percent,
  RotateCcw,
  ShieldOff,
} from "lucide-react";
import { adminFetch, AdminApiError } from "@/lib/adminApi";
import { getAdminIdentity, isSuperAdmin } from "@/lib/adminIdentity";
import { useTranslation } from "@/context/LanguageContext";

/** Khớp UserOddsOptionResponse của backend. */
interface OddsOption {
  betType: string;
  defaultOdds: string;
  effectiveOdds: string;
  /** Hệ số người chơi thực nhận, đã trừ hoa hồng nếu cửa đó có thu. */
  netMultiplier: string;
  /** Tỷ lệ hoa hồng, ví dụ "0.05"; null nếu cửa này không chịu hoa hồng. */
  commissionRate: string | null;
  overridden: boolean;
  reason: string | null;
  updatedAt: string | null;
}

/** Khớp UserTableOddsResponse. */
interface TableOddsRow {
  tableId: string;
  gameType: string;
  nameI18n: string;
  options: OddsOption[];
}

interface UserOddsData {
  userId: string;
  username: string;
  tables: TableOddsRow[];
}

interface Props {
  userId: string;
  username: string;
}

/**
 * Biên an toàn, khớp `OddsResolver.MIN_RATIO` và `MAX_RATIO` của backend.
 *
 * Kiểm ở đây để người vận hành biết ngay khi gõ, thay vì bấm lưu rồi mới nhận lỗi. Server
 * vẫn là nơi quyết định — hai bên cùng chặn.
 */
const MIN_RATIO = 0.5;
const MAX_RATIO = 3;

/** Lệch quá mức này so với mức chung thì cảnh báo, dù vẫn trong biên cho phép. */
const WARN_RATIO_DELTA = 0.2;

/** Tiền cược mẫu để quy tỷ lệ thành số tiền cụ thể trên biểu đồ. */
const SAMPLE_STAKE = 100;

/**
 * Bước nhảy của nút mũi tên.
 *
 * 0.01 vì cột `user_game_odds.odds` là `DECIMAL(10,4)` và hệ số hiển thị hai chữ số thập
 * phân — bước nhỏ hơn sẽ tạo ra thay đổi không nhìn thấy được trên màn hình.
 */
const STEP = 0.01;

/**
 * Đọc tên bàn từ cột `name_i18n`.
 *
 * Backend trả nguyên chuỗi JSON của cột, không phải đối tượng đã phân tích. Bọc trong
 * try/catch vì một hàng dữ liệu hỏng không nên làm trắng cả hộp thoại.
 */
function tableName(nameI18n: string, locale: string, fallback: string): string {
  try {
    const parsed = JSON.parse(nameI18n) as Record<string, string>;
    return parsed[locale] ?? parsed.en ?? fallback;
  } catch {
    return fallback;
  }
}

/** Hệ số hiển thị (gồm tiền gốc) từ odds lợi. */
function toMultiplier(odds: string | number): string {
  const value = Number(odds);
  if (!Number.isFinite(value)) return String(odds);
  return (value + 1).toFixed(2);
}

/**
 * Ảnh nền sảnh của từng loại game, TRÙNG với ảnh người chơi thấy ở trang `/bet`.
 *
 * Khoá theo `gameType` chứ KHÔNG theo vị trí trong danh sách. Trang người chơi tính ảnh
 * bằng `index % 4` trên thứ tự DB (Roulette, Baccarat, KL28, Lucky28, British, Taiwan),
 * còn bảng quản trị đã sắp lại cho Lucky 28 lên đầu — dùng chỉ số ở đây sẽ cho ra ảnh khác
 * với ảnh người chơi đang xem, đúng điều cần tránh khi mục đích là để hình dung bàn nào.
 *
 * Bảng này chốt lại kết quả `index % 4` của trang người chơi theo từng loại game:
 * Roulette→1, Baccarat→2, KL28→3, Lucky28→4, British→1 (chỉ số 4), Taiwan→2 (chỉ số 5).
 */
const ROOM_IMAGE_BY_GAME_TYPE: Record<string, string> = {
  ROULETTE: "/element/room1.webp",
  BACCARAT: "/element/room2.webp",
  KL28: "/element/room3.webp",
  LUCKY28: "/element/room4.webp",
  BRITISH_LUCKY28: "/element/room1.webp",
  TAIWAN_TIMES: "/element/room2.webp",
};

/**
 * Ảnh nền của một bàn, hoặc null nếu loại game chưa có ảnh.
 *
 * Trả null thay vì rơi về một ảnh mặc định: một loại game mới mà dùng ảnh của game khác thì
 * gây hiểu sai bàn, còn không có ảnh thì chỉ là thiếu trang trí.
 */
function roomImageFor(gameType: string): string | null {
  return ROOM_IMAGE_BY_GAME_TYPE[gameType] ?? null;
}

/**
 * Điền sẵn ô nhập bằng hệ số ĐANG ÁP của từng loại cược.
 *
 * Vì sao không để trống: ô trống buộc người vận hành phải đọc con số ở chỗ khác rồi gõ lại
 * toàn bộ, trong khi việc thường làm là nhích một chút từ mức hiện tại. Điền sẵn cũng làm
 * rõ ĐƠN VỊ của ô — thấy 1.98 thì không ai gõ 0.98 vào.
 */
function seedDrafts(data: UserOddsData): Record<string, string> {
  const seeded: Record<string, string> = {};
  data.tables.forEach((table) => {
    table.options.forEach((option) => {
      seeded[`${table.tableId}:${option.betType}`] = toMultiplier(option.effectiveOdds);
    });
  });
  return seeded;
}

/**
 * Điền sẵn ô "cả cặp" của từng bàn.
 *
 * Điền MỨC ĐANG ÁP nếu hai cửa đang BẰNG NHAU; để TRỐNG nếu đang lệch.
 *
 * Vì sao không điền sẵn khi hai cửa lệch nhau: lúc đó phải chọn một trong hai giá trị, và
 * người vận hành thấy một con số có sẵn rất dễ bấm áp dụng mà không nhận ra mình vừa ghi
 * đè cửa kia. Ô trống buộc họ tự gõ con số muốn áp cho cả hai.
 */
function seedPairDrafts(data: UserOddsData): Record<string, string> {
  const seeded: Record<string, string> = {};
  data.tables.forEach((table) => {
    const values = table.options.map((option) => option.effectiveOdds);
    const allSame = values.length > 0 && values.every((v) => v === values[0]);
    seeded[table.tableId] = allSame ? toMultiplier(values[0]) : "";
  });
  return seeded;
}

/**
 * Bảng điều chỉnh tỷ lệ cược riêng theo từng bàn.
 *
 * Chỉ ADMIN được ghi — cùng lý do với hạn mức cược: nâng tỷ lệ của một tài khoản rồi để
 * tài khoản đó cược và thắng là một đường rút tiền không đi qua sổ điều chỉnh ví nào.
 *
 * Mỗi bàn chỉ có CẶP HAI CHIỀU chính (lớn/nhỏ, thấp/cao, người chơi/nhà băng) — xem
 * `TableOddsService.adjustableBetTypesFor` ở backend để biết vì sao hẹp hơn lưới cược của
 * người chơi. Lưới của người chơi KHÔNG đổi, họ vẫn cược đủ mọi loại.
 */
export const UserGameOddsPanel: React.FC<Props> = ({ userId, username }) => {
  const { t, locale } = useTranslation();
  const identity = getAdminIdentity();
  const canEdit = isSuperAdmin(identity);

  /** Backend chặn tự sửa cho mình; ẩn ô nhập trước để không bấm rồi mới báo lỗi. */
  const isSelf = identity.userId === userId;

  const [data, setData] = useState<UserOddsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  /**
   * Giá trị đang gõ, khoá theo `${tableId}:${betType}`.
   *
   * ĐƠN VỊ LÀ HỆ SỐ (gồm tiền gốc, ví dụ 1.98), KHÔNG phải odds lợi (0.98). Đây là con số
   * người chơi nhìn thấy trên lưới cược, nên lấy nó làm đơn vị nhập thì người vận hành và
   * người chơi nói cùng một thứ tiếng. Trừ 1 khi gửi lên server.
   */
  const [drafts, setDrafts] = useState<Record<string, string>>({});

  /**
   * Giá trị đang gõ ở ô "cả cặp", khoá theo `tableId`.
   *
   * Giữ RIÊNG khỏi {@link drafts}: hai ô lẻ và ô cả cặp là ba ô nhập độc lập. Gộp chung một
   * bản đồ thì phải đặt ra quy ước khoá để phân biệt, và một `tableId` trùng dạng khoá của
   * ô lẻ sẽ làm hai ô đổi theo nhau.
   */
  const [pairDrafts, setPairDrafts] = useState<Record<string, string>>({});

  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [savedKey, setSavedKey] = useState<string | null>(null);

  /**
   * Gọi API, trả dữ liệu hoặc null nếu lỗi.
   *
   * Hàm này KHÔNG đặt `loading`. Việc đó tách ra ngoài vì `setState` gọi đồng bộ trong
   * thân effect gây chuỗi render liên tiếp, và luật lint của dự án chặn.
   */
  const fetchOdds = useCallback(async (): Promise<UserOddsData | null> => {
    try {
      return await adminFetch<UserOddsData>(`/admin/users/${userId}/game-odds`);
    } catch (err) {
      setError(
        err instanceof AdminApiError ? err.message : t("admin.states.load_failed")
      );
      return null;
    }
  }, [t, userId]);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể đóng hộp thoại trước khi request xong, và ghi state
    // vào component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      const result = await fetchOdds();
      if (cancelled) return;
      if (result) {
        setData(result);
        setDrafts(seedDrafts(result));
        setPairDrafts(seedPairDrafts(result));
      }
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchOdds]);

  /**
   * Tải lại sau khi ghi. Gọi từ trình xử lý sự kiện nên đặt state trực tiếp là được.
   *
   * Gieo lại ô nhập theo dữ liệu vừa tải: server làm tròn về 4 chữ số thập phân nên con số
   * lưu có thể khác con số vừa gõ, và sau khi hoàn tác thì ô phải trở về mức chung.
   */
  const reload = useCallback(async () => {
    const result = await fetchOdds();
    if (result) {
      setData(result);
      setDrafts(seedDrafts(result));
      setPairDrafts(seedPairDrafts(result));
    }
  }, [fetchOdds]);

  const save = async (row: TableOddsRow, option: OddsOption) => {
    const key = `${row.tableId}:${option.betType}`;
    const raw = drafts[key] ?? "";

    // Ô nhập chứa HỆ SỐ, API nhận ODDS LỢI — trừ 1. Làm tròn 4 chữ số cho khớp định nghĩa
    // cột `DECIMAL(10,4)`, tránh chuỗi như "0.9800000000000001" do phép trừ dấu phẩy động.
    const profitOdds = (Number(raw.trim()) - 1).toFixed(4);

    setError("");
    setSavedKey(null);
    setSavingKey(key);

    try {
      // Không gửi `reason`: backend cho phép để trống, và audit đã ghi người thực hiện,
      // thời điểm, IP, tỷ lệ trước/sau — đủ để truy vết mà không cần người vận hành gõ thêm.
      await adminFetch(`/admin/users/${userId}/game-odds`, {
        method: "PUT",
        body: JSON.stringify({
          tableId: row.tableId,
          betType: option.betType,
          odds: profitOdds,
        }),
      });
      setSavedKey(key);
      // Không xoá ô nhập: `reload` gieo lại theo giá trị server vừa chốt, nên ô luôn hiện
      // đúng mức đang áp thay vì trống.
      await reload();
    } catch (err) {
      setError(
        err instanceof AdminApiError ? err.message : t("admin.states.save_failed")
      );
    } finally {
      setSavingKey(null);
    }
  };

  const reset = async (row: TableOddsRow, option: OddsOption) => {
    const key = `${row.tableId}:${option.betType}`;
    setError("");
    setSavingKey(key);
    try {
      await adminFetch(
        `/admin/users/${userId}/game-odds/${row.tableId}/${option.betType}`,
        { method: "DELETE" }
      );
      // `reload` đã gieo lại ô nhập về mức chung. Xoá thêm ở đây sẽ để ô trống.
      await reload();
    } catch (err) {
      setError(
        err instanceof AdminApiError ? err.message : t("admin.states.save_failed")
      );
    } finally {
      setSavingKey(null);
    }
  };

  /**
   * Đặt CÙNG một hệ số cho cả hai cửa của một bàn, trong MỘT lần gọi.
   *
   * Gọi endpoint riêng `/pair` chứ KHÔNG lặp lại `save` hai lần: lần thứ hai có thể thất bại
   * (mất mạng, token hết hạn giữa hai lượt), để lại LỆCH giữa hai cửa mà người vận hành
   * tin là đã đặt cân. Server bọc cả hai lần ghi trong một transaction.
   */
  const savePair = async (row: TableOddsRow) => {
    // Tiền tố "pair:" để khoá không trùng dạng `${tableId}:${betType}` của hai ô lẻ —
    // trùng thì vòng xoay chờ sẽ hiện trên sai nút.
    const key = `pair:${row.tableId}`;
    const raw = pairDrafts[row.tableId] ?? "";

    // Cùng phép đổi đơn vị như `save`: ô nhập chứa HỆ SỐ, API nhận ODDS LỢI.
    const profitOdds = (Number(raw.trim()) - 1).toFixed(4);

    setError("");
    setSavedKey(null);
    setSavingKey(key);

    try {
      await adminFetch(`/admin/users/${userId}/game-odds/pair`, {
        method: "PUT",
        body: JSON.stringify({
          tableId: row.tableId,
          odds: profitOdds,
        }),
      });
      setSavedKey(key);
      await reload();
    } catch (err) {
      setError(
        err instanceof AdminApiError ? err.message : t("admin.states.save_failed")
      );
    } finally {
      setSavingKey(null);
    }
  };

  const betLabel = useCallback(
    (betType: string) => {
      const key = `admin.bet_types.${betType}`;
      const label = t(key);
      return label === key ? betType : label;
    },
    [t]
  );

  /**
   * Tăng/giảm hệ số một bước.
   *
   * KẸP trong biên an toàn của chính loại cược đó, thay vì để chạy tự do rồi báo lỗi: nút
   * mũi tên là thao tác nhích dần, mà nhích tới một giá trị server sẽ từ chối thì người
   * dùng chỉ biết khi bấm Lưu. Kẹp ở đây làm nút tự dừng ở biên.
   *
   * Làm tròn 2 chữ số sau mỗi bước vì cộng số dấu phẩy động dồn sai số: 1.98 + 0.01 cho
   * 1.9900000000000002, và chuỗi đó sẽ hiện nguyên trong ô nhập.
   */
  const stepDraft = useCallback(
    (tableId: string, option: OddsOption, direction: 1 | -1) => {
      const key = `${tableId}:${option.betType}`;
      const defaultOdds = Number(option.defaultOdds);

      const min = defaultOdds * MIN_RATIO + 1;
      const max = defaultOdds * MAX_RATIO + 1;

      setSavedKey(null);
      setDrafts((prev) => {
        // Ô rỗng hoặc đang gõ dở thì lấy mức đang áp làm điểm khởi đầu.
        const parsed = Number((prev[key] ?? "").trim());
        const current = Number.isFinite(parsed) && parsed > 1
          ? parsed
          : Number(option.effectiveOdds) + 1;

        const next = Math.min(max, Math.max(min, current + direction * STEP));
        return { ...prev, [key]: next.toFixed(2) };
      });
    },
    []
  );

  /**
   * Tăng/giảm hệ số ở ô "cả cặp" một bước.
   *
   * KẸP trong biên CHẬT NHẤT của cả hai cửa: giá trị này sẽ áp cho cả hai, nên một con số
   * chỉ hợp lệ với một cửa thì cả lượt ghi sẽ bị server từ chối. Hiện hai cửa trong mỗi
   * cặp luôn cùng mức chung nên hai biên trùng nhau; lấy giao để đúng cả khi sau này có
   * loại game phá giả định đó.
   */
  const stepPairDraft = useCallback(
    (row: TableOddsRow, direction: 1 | -1) => {
      if (row.options.length === 0) return;

      const min = Math.max(
        ...row.options.map((o) => Number(o.defaultOdds) * MIN_RATIO + 1)
      );
      const max = Math.min(
        ...row.options.map((o) => Number(o.defaultOdds) * MAX_RATIO + 1)
      );

      setSavedKey(null);
      setPairDrafts((prev) => {
        const parsed = Number((prev[row.tableId] ?? "").trim());
        // Ô trống (hai cửa đang lệch) thì khởi đầu từ mức chung của bàn, không phải tỷ lệ
        // đang áp của một trong hai cửa — chọn một trong hai sẽ là lựa chọn tùy ý.
        const current = Number.isFinite(parsed) && parsed > 1
          ? parsed
          : Number(row.options[0].defaultOdds) + 1;

        const next = Math.min(max, Math.max(min, current + direction * STEP));
        return { ...prev, [row.tableId]: next.toFixed(2) };
      });
    },
    []
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center gap-2 py-8 text-xs font-semibold text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" />
        {t("admin.states.loading")}
      </div>
    );
  }

  if (isSelf) {
    return (
      <div className="flex items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 p-3.5">
        <ShieldOff className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
        <span className="text-xs font-semibold text-amber-900">
          {t("admin.users.odds.err_self")}
        </span>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3.5">
        <Percent className="mt-0.5 h-4 w-4 shrink-0 text-slate-500" />
        <div className="flex flex-col gap-1">
          <span className="text-xs font-bold text-slate-700">
            {t("admin.users.odds.intro", { username })}
          </span>
          <span className="text-[11px] font-medium text-slate-500">
            {t("admin.users.odds.intro_note")}
          </span>
        </div>
      </div>

      {!canEdit && (
        <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-3.5">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
          <span className="text-[11px] font-semibold text-slate-500">
            {t("admin.users.odds.read_only")}
          </span>
        </div>
      )}

      {error && (
        <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-3.5">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
          <span className="text-xs font-semibold text-red-700">{error}</span>
        </div>
      )}

      {data?.tables.map((row) => {
        const roomImage = roomImageFor(row.gameType);

        return (
        <div
          key={row.tableId}
          className="flex flex-col gap-2.5 overflow-hidden rounded-xl border border-slate-200 bg-white"
        >
          {/* Dải tiêu đề dựng lại thẻ sảnh của người chơi: nền #1D1D1D, ảnh căn phải,
              `contain` chứ không `cover`.
              Mục đích là để người vận hành nhận ra bàn bằng hình giống hệt cái người chơi
              đang xem, nên mọi thông số nền phải khớp trang `/bet` — `cover` sẽ phóng ảnh
              tràn ra và cắt mất phần nhận dạng của sảnh.
              Ảnh đặt qua `style` vì đường dẫn là giá trị chạy lúc thực thi; Tailwind không
              sinh class từ giá trị động. */}
          <div
            className="relative flex items-baseline justify-between gap-2 bg-[#1D1D1D] bg-contain bg-right-top bg-no-repeat px-3 py-2.5"
            style={roomImage ? { backgroundImage: `url("${roomImage}")` } : undefined}
          >
            {/* Lớp phủ tối dần từ trái: ảnh sảnh có vùng sáng, chữ trắng đặt thẳng lên sẽ
                mất nét ở những bàn ảnh nhạt. */}
            <span
              aria-hidden="true"
              className="pointer-events-none absolute inset-0 bg-gradient-to-r from-[#1D1D1D] via-[#1D1D1D]/85 to-transparent"
            />
            <span className="relative text-xs font-extrabold text-white drop-shadow-sm">
              {tableName(row.nameI18n, locale, row.gameType)}
            </span>
            <span className="relative text-[10px] font-bold uppercase tracking-wide text-white/50">
              {row.gameType}
            </span>
          </div>

          {/* HÀNG "ÁP CẢ CẶP".
              Đặt ở phạm vi BÀN, phía trên hai thẻ lẻ, vì nó tác động lên cả hai cửa —
              nhét vào trong một thẻ sẽ khiến nút trông như chỉ thuộc cửa đó.
              Hai thẻ lẻ bên dưới vẫn giữ nguyên để chỉnh lệch khi cần. */}
          {canEdit && row.options.length > 1 && (() => {
            const pairKey = `pair:${row.tableId}`;
            const pairDraft = pairDrafts[row.tableId] ?? "";

            // Biên CHẶT NHẤT của cả hai cửa: giá trị này áp cho cả hai, nên chỉ hợp lệ khi
            // nằm trong biên của từng cửa. Hiện hai biên trùng nhau vì hai cửa cùng mức
            // chung; lấy giao để vẫn đúng nếu sau này có loại game phá giả định đó.
            const pairMin = Math.max(
              ...row.options.map((o) => Number(o.defaultOdds) * MIN_RATIO + 1)
            );
            const pairMax = Math.min(
              ...row.options.map((o) => Number(o.defaultOdds) * MAX_RATIO + 1)
            );

            const pairValue = Number(pairDraft.trim());
            const pairValid =
              pairDraft.trim() !== "" && Number.isFinite(pairValue) && pairValue > 1;
            const pairOutOfRange =
              pairValid && (pairValue < pairMin || pairValue > pairMax);

            // Mọi cửa đã ở đúng giá trị này thì không có gì để ghi.
            const pairUnchanged =
              pairValid &&
              row.options.every(
                (o) => Math.abs(Number(o.effectiveOdds) + 1 - pairValue) < 1e-9
              );

            const pairCanSubmit =
              pairValid && !pairOutOfRange && !pairUnchanged && savingKey === null;

            const pairAtMax = pairValid && pairValue >= pairMax;
            const pairAtMin = pairValid && pairValue <= pairMin;

            return (
              <div className="flex flex-col gap-1.5 border-b border-slate-100 px-3 pt-2.5 pb-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[10px] font-bold uppercase tracking-wide text-slate-500">
                    {t("admin.users.odds.pair_label")}
                  </span>
                  {savedKey === pairKey && (
                    <span className="flex items-center gap-1 text-[10px] font-bold text-emerald-600">
                      <CheckCircle2 className="h-3 w-3" />
                      {t("admin.users.odds.saved")}
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-1">
                  <div className="flex min-w-0 flex-1 items-stretch overflow-hidden rounded-lg border border-slate-200 bg-white focus-within:border-slate-900">
                    <input
                      aria-label={t("admin.users.odds.pair_label")}
                      className="min-w-0 flex-1 bg-transparent px-2 py-1.5 text-[11px] font-bold tabular-nums text-slate-900 outline-none placeholder-slate-400"
                      inputMode="decimal"
                      onChange={(e) => {
                        setSavedKey(null);
                        setPairDrafts((prev) => ({
                          ...prev,
                          [row.tableId]: e.target.value,
                        }));
                      }}
                      onKeyDown={(e) => {
                        if (e.key === "ArrowUp") {
                          e.preventDefault();
                          stepPairDraft(row, 1);
                        } else if (e.key === "ArrowDown") {
                          e.preventDefault();
                          stepPairDraft(row, -1);
                        } else if (e.key === "Enter" && pairCanSubmit) {
                          // Enter lưu luôn: đây là ô một-dòng-một-việc, và người vận hành
                          // gõ số rồi với tay sang chuột chỉ để bấm một nút ngay bên cạnh.
                          e.preventDefault();
                          void savePair(row);
                        }
                      }}
                      placeholder={toMultiplier(row.options[0].defaultOdds)}
                      type="text"
                      value={pairDraft}
                    />
                    <div className="flex shrink-0 flex-col border-l border-slate-200">
                      <button
                        aria-label={t("admin.users.odds.step_up")}
                        className="flex flex-1 items-center justify-center px-1 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent"
                        disabled={pairAtMax}
                        onClick={() => stepPairDraft(row, 1)}
                        tabIndex={-1}
                        title={t("admin.users.odds.step_up")}
                        type="button"
                      >
                        <ChevronUp className="h-3 w-3" />
                      </button>
                      <button
                        aria-label={t("admin.users.odds.step_down")}
                        className="flex flex-1 items-center justify-center border-t border-slate-200 px-1 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent"
                        disabled={pairAtMin}
                        onClick={() => stepPairDraft(row, -1)}
                        tabIndex={-1}
                        title={t("admin.users.odds.step_down")}
                        type="button"
                      >
                        <ChevronDown className="h-3 w-3" />
                      </button>
                    </div>
                  </div>

                  <button
                    className="flex shrink-0 items-center gap-1 rounded-lg bg-slate-900 px-2.5 py-1.5 text-[10px] font-bold text-white transition-colors disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
                    disabled={!pairCanSubmit}
                    onClick={() => void savePair(row)}
                    type="button"
                  >
                    {savingKey === pairKey ? (
                      <Loader2 className="h-3 w-3 animate-spin" />
                    ) : (
                      t("admin.users.odds.apply_pair")
                    )}
                  </button>
                </div>

                {pairOutOfRange ? (
                  <span className="text-[10px] font-bold text-red-600">
                    {t("admin.users.odds.err_range", {
                      min: pairMin.toFixed(2),
                      max: pairMax.toFixed(2),
                    })}
                  </span>
                ) : (
                  <span className="text-[10px] font-medium text-slate-400">
                    {t("admin.users.odds.pair_hint", {
                      options: row.options.map((o) => betLabel(o.betType)).join(" · "),
                    })}
                  </span>
                )}
              </div>
            );
          })()}

          {/* Hai cột: các loại điều chỉnh được LUÔN đi theo cặp hai chiều (Thấp/Cao,
              Lớn/Nhỏ, Người chơi/Nhà băng). Đặt cạnh nhau thì so hai mức bằng mắt được
              ngay, còn xếp dọc thì mỗi bàn chiếm gần một trang và phải cuộn để đối chiếu.
              Đệm đặt ở đây chứ không ở thẻ ngoài: thẻ bỏ padding để dải tiêu đề chạm sát
              viền, nếu không sẽ hở một khung trắng quanh ảnh. */}
          <div className="grid grid-cols-2 gap-2 px-3 pb-3">
            {row.options.map((option) => {
              const key = `${row.tableId}:${option.betType}`;
              const draft = drafts[key] ?? "";

              const defaultOdds = Number(option.defaultOdds);
              const effectiveOdds = Number(option.effectiveOdds);

              // Ô nhập là HỆ SỐ. Quy biên an toàn của backend (odds lợi trong
              // 0.5×–3× mức chung) sang cùng đơn vị để so trực tiếp với con số đang gõ.
              const minMultiplier = defaultOdds * MIN_RATIO + 1;
              const maxMultiplier = defaultOdds * MAX_RATIO + 1;

              const draftMultiplier = Number(draft.trim());
              const draftValid =
                draft.trim() !== "" &&
                Number.isFinite(draftMultiplier) &&
                draftMultiplier > 1;

              const outOfRange =
                draftValid &&
                (draftMultiplier < minMultiplier || draftMultiplier > maxMultiplier);

              const draftOdds = draftValid ? draftMultiplier - 1 : effectiveOdds;

              const farFromDefault =
                draftValid &&
                !outOfRange &&
                Math.abs(draftOdds - defaultOdds) / defaultOdds > WARN_RATIO_DELTA;

              // Ô nhập luôn có sẵn mức đang áp, nên trạng thái thường gặp là "chưa đổi gì".
              // Khoá nút Lưu trong trường hợp đó: gửi lại đúng con số cũ vẫn tạo một dòng
              // audit và một lần ghi bảng, làm loãng lịch sử điều chỉnh thật.
              const unchanged = draftValid && Math.abs(draftOdds - effectiveOdds) < 0.00005;

              // Đã tới biên thì khoá nút mũi tên tương ứng, thay vì để bấm mà con số không
              // nhích — nút bấm được nhưng không có tác dụng là tín hiệu sai.
              const atMax = draftValid && draftMultiplier >= maxMultiplier - 0.00005;
              const atMin = draftValid && draftMultiplier <= minMultiplier + 0.00005;

              const canSubmit =
                canEdit && draftValid && !outOfRange && !unchanged && savingKey === null;

              // Chênh lệch tiền nhận về so với mức chung, tính trên tiền cược mẫu.
              const deltaMoney = (draftOdds - defaultOdds) * SAMPLE_STAKE;
              const sameAsBase = Math.abs(deltaMoney) < 0.005;

              // Hệ số người chơi THỰC NHẬN ở cửa có hoa hồng; null nếu cửa không thu.
              //
              // Tính từ `draftOdds` chứ không đọc `option.netMultiplier` của server: con số
              // phải nhảy theo từng lần bấm mũi tên, còn giá trị server chỉ đổi sau khi lưu.
              const rate = option.commissionRate ? Number(option.commissionRate) : 0;
              const netReceive =
                rate > 0 ? (1 + draftOdds * (1 - rate)).toFixed(2) : null;
              const commissionPercent = String(rate * 100);

              return (
                <div
                  key={option.betType}
                  className="flex flex-col gap-1.5 rounded-lg border border-slate-100 bg-slate-50/60 p-2"
                >
                  <div className="flex items-center justify-between gap-1">
                    <span className="flex min-w-0 items-center gap-1">
                      {/* Chấm thay cho nhãn chữ "riêng": layout hai cột không đủ rộng cho một
                          nhãn, mà bỏ hẳn thì không phân biệt được ô đang ở mức chung với ô
                          đã bị đổi. Kèm `title` và `sr-only` để không chỉ dựa vào màu. */}
                      {option.overridden && (
                        <>
                          <span
                            aria-hidden="true"
                            className="h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500"
                            title={t("admin.users.odds.custom")}
                          />
                          <span className="sr-only">{t("admin.users.odds.custom")}</span>
                        </>
                      )}
                      <span className="truncate text-[11px] font-bold text-slate-700">
                        {betLabel(option.betType)}
                      </span>
                    </span>
                    <span className="shrink-0 text-[11px] font-bold tabular-nums text-slate-400">
                      {toMultiplier(option.defaultOdds)}
                    </span>
                  </div>

                  {canEdit ? (
                    <div className="flex items-center gap-1">
                      {/* Ô nhập ghép liền hai nút mũi tên.
                          Không dùng `type="number"` của trình duyệt: nút mũi tên mặc định
                          rất nhỏ, chỉ hiện khi trỏ chuột vào, và không kẹp được theo biên
                          riêng của từng loại cược. */}
                      <div className="flex min-w-0 flex-1 items-stretch overflow-hidden rounded-lg border border-slate-200 bg-white focus-within:border-slate-900">
                        <input
                          aria-label={t("admin.users.odds.field_multiplier")}
                          className="min-w-0 flex-1 bg-transparent px-2 py-1.5 text-[11px] font-bold tabular-nums text-slate-900 outline-none placeholder-slate-400"
                          inputMode="decimal"
                          onChange={(e) => {
                            setSavedKey(null);
                            setDrafts((prev) => ({ ...prev, [key]: e.target.value }));
                          }}
                          onKeyDown={(e) => {
                            // Phím Lên/Xuống làm cùng việc với nút mũi tên. `type="text"`
                            // không có hành vi này sẵn, mà đây là thao tác người dùng bàn
                            // phím mong đợi ở một ô số.
                            if (e.key === "ArrowUp") {
                              e.preventDefault();
                              stepDraft(row.tableId, option, 1);
                            } else if (e.key === "ArrowDown") {
                              e.preventDefault();
                              stepDraft(row.tableId, option, -1);
                            }
                          }}
                          placeholder={toMultiplier(option.defaultOdds)}
                          type="text"
                          value={draft}
                        />
                        <div className="flex shrink-0 flex-col border-l border-slate-200">
                          <button
                            aria-label={t("admin.users.odds.step_up")}
                            className="flex flex-1 items-center justify-center px-1 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent"
                            disabled={atMax}
                            onClick={() => stepDraft(row.tableId, option, 1)}
                            tabIndex={-1}
                            title={t("admin.users.odds.step_up")}
                            type="button"
                          >
                            <ChevronUp className="h-3 w-3" />
                          </button>
                          <button
                            aria-label={t("admin.users.odds.step_down")}
                            className="flex flex-1 items-center justify-center border-t border-slate-200 px-1 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 disabled:cursor-not-allowed disabled:text-slate-300 disabled:hover:bg-transparent"
                            disabled={atMin}
                            onClick={() => stepDraft(row.tableId, option, -1)}
                            tabIndex={-1}
                            title={t("admin.users.odds.step_down")}
                            type="button"
                          >
                            <ChevronDown className="h-3 w-3" />
                          </button>
                        </div>
                      </div>

                      <button
                        className="shrink-0 rounded-lg bg-slate-900 px-2 py-1.5 text-[10px] font-bold text-white transition-colors disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400"
                        disabled={!canSubmit}
                        onClick={() => void save(row, option)}
                        type="button"
                      >
                        {savingKey === key ? (
                          <Loader2 className="h-3 w-3 animate-spin" />
                        ) : (
                          t("admin.users.odds.save")
                        )}
                      </button>

                      {option.overridden && (
                        <button
                          aria-label={t("admin.users.odds.reset")}
                          className="shrink-0 rounded-lg border border-slate-200 bg-white p-1.5 text-slate-500 transition-colors hover:border-slate-300 disabled:opacity-40"
                          disabled={savingKey !== null}
                          onClick={() => void reset(row, option)}
                          title={t("admin.users.odds.reset")}
                          type="button"
                        >
                          <RotateCcw className="h-3 w-3" />
                        </button>
                      )}
                    </div>
                  ) : (
                    <span className="text-[11px] font-bold tabular-nums text-slate-900">
                      {toMultiplier(option.effectiveOdds)}
                    </span>
                  )}

                  {/* Một dòng trạng thái duy nhất, ưu tiên từ nặng đến nhẹ: lỗi biên trước,
                      rồi cảnh báo lệch xa, rồi tác động tiền. Xếp chồng nhiều dòng như trước
                      làm ô cao lên gấp đôi ngay khi vừa gõ. */}
                  {outOfRange ? (
                    <span className="text-[10px] font-bold text-red-600">
                      {t("admin.users.odds.err_range", {
                        min: minMultiplier.toFixed(2),
                        max: maxMultiplier.toFixed(2),
                      })}
                    </span>
                  ) : savedKey === key && !error ? (
                    <span className="flex items-center gap-1 text-[10px] font-bold text-emerald-700">
                      <CheckCircle2 className="h-3 w-3" />
                      {t("admin.users.odds.saved")}
                    </span>
                  ) : farFromDefault ? (
                    <span className="text-[10px] font-bold text-amber-700">
                      {t("admin.users.odds.warn_far")}
                    </span>
                  ) : (
                    <span
                      className={`text-[10px] font-semibold ${
                        sameAsBase
                          ? "text-slate-400"
                          : deltaMoney > 0
                            ? "text-amber-700"
                            : "text-blue-700"
                      }`}
                    >
                      {sameAsBase
                        ? t("admin.users.odds.impact_same")
                        : t(
                            deltaMoney > 0
                              ? "admin.users.odds.impact_more"
                              : "admin.users.odds.impact_less",
                            { amount: Math.abs(deltaMoney).toFixed(2) }
                          )}
                    </span>
                  )}

                  {/* Cửa có hoa hồng: ô nhập dùng hệ số GỘP cho mọi bàn để đơn vị không đổi
                      giữa các bàn, nên ở đây con số gõ vào KHÁC con số người chơi nhận. Dòng
                      này hiện rõ khoảng chênh, thay vì để người vận hành tự nhớ mà trừ. */}
                  {netReceive !== null && (
                    <span className="text-[10px] font-semibold text-slate-500">
                      {t("admin.users.odds.net_receive", {
                        value: netReceive,
                        percent: commissionPercent,
                      })}
                    </span>
                  )}
                </div>
              );
            })}
          </div>
        </div>
        );
      })}

      {data?.tables.length === 0 && (
        <p className="py-6 text-center text-xs font-semibold text-slate-400">
          {t("admin.states.empty")}
        </p>
      )}
    </div>
  );
};
