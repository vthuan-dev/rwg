"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Download,
  Loader2,
  Search,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import { adminFetch, getAdminToken } from "@/lib/adminApi";
import { ADMIN_API_BASE_URL } from "@/lib/constants";
import { useTranslation } from "@/context/LanguageContext";
import { formatMoney } from "@/lib/money";

/** Một dòng trong bảng tổng quan. */
interface OverviewRow {
  userId: string;
  username: string;
  currency: string;
  betCount: number;
  stake: string;
  net: string;
  deposit: string;
  adminCredit: string;
  adminDebit: string;
  withdrawal: string;
  balance: string;
}

interface LedgerOverview {
  periodFrom: string;
  periodTo: string;
  timezone: string;
  rows: OverviewRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  totalDeposit: string;
  totalAdminCredit: string;
  totalAdminDebit: string;
  totalWithdrawal: string;
  totalNet: string;
}

/** Một dòng thắng/thua của một loại game. */
interface GameLine {
  gameType: string;
  betCount: number;
  stake: string;
  payout: string;
  net: string;
  pendingStake: string;
}

/** Sổ sách chi tiết một người chơi. */
interface PlayerLedger {
  userId: string;
  username: string;
  periodFrom: string;
  periodTo: string;
  timezone: string;
  currency: string;
  openingBalance: string;
  closingBalance: string;
  depositViaGateway: string;
  adminCredit: string;
  adminDebit: string;
  withdrawalSettled: string;
  games: GameLine[];
  totalStake: string;
  totalPayout: string;
  totalNet: string;
  totalPending: string;
}

type SortKey = "stake" | "net" | "deposit";

const PAGE_SIZE = 20;

/**
 * Tháng hiện tại dạng `yyyy-MM`.
 *
 * Dùng giờ máy của người vận hành: đây chỉ là giá trị khởi tạo, còn việc cắt kỳ chính
 * xác do backend làm theo múi giờ báo cáo.
 */
const currentMonth = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
};

/**
 * Số tiền có dấu tường minh, kèm màu.
 *
 * DẤU LÀ BẮT BUỘC, KHÔNG CHỈ DỰA VÀO MÀU: khoảng 8% nam giới khó phân biệt đỏ với
 * xanh, và đây là bảng để làm sổ sách — đọc sai dấu một dòng là sai cả báo cáo.
 */
const SignedAmount: React.FC<{ value: string; currency?: string }> = ({
  value,
  currency,
}) => {
  const num = Number(value);
  const tone =
    num > 0 ? "text-emerald-600" : num < 0 ? "text-rose-600" : "text-slate-500";
  // Số âm đã có dấu trừ trong chính chuỗi; số dương phải tự thêm dấu cộng.
  return (
    <span className={`font-semibold tabular-nums ${tone}`}>
      {num > 0 ? "+" : ""}
      {formatMoney(value)}
      {currency ? ` ${currency}` : ""}
    </span>
  );
};

/** Thẻ một con số trong khối dòng tiền. */
const MoneyCard: React.FC<{
  label: string;
  value: string;
  currency: string;
  tone: "in" | "out";
}> = ({ label, value, currency, tone }) => (
  <div
    className={`rounded-xl border p-4 ${
      tone === "in"
        ? "border-emerald-200 bg-emerald-50/60"
        : "border-rose-200 bg-rose-50/60"
    }`}
  >
    <p className="text-xs font-medium text-slate-500">{label}</p>
    <p className="mt-1 text-lg font-bold tabular-nums text-slate-900">
      {formatMoney(value)}{" "}
      <span className="text-xs font-medium text-slate-500">{currency}</span>
    </p>
  </div>
);

export default function LedgerPage() {
  const { t } = useTranslation();

  const [month, setMonth] = useState(currentMonth);
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [sort, setSort] = useState<SortKey>("stake");
  const [page, setPage] = useState(0);

  const [overview, setOverview] = useState<LedgerOverview | null>(null);
  const [loadingOverview, setLoadingOverview] = useState(true);

  /** null = đang xem bảng tổng quan; có giá trị = đang xem chi tiết một người. */
  const [detailUserId, setDetailUserId] = useState<string | null>(null);
  const [detail, setDetail] = useState<PlayerLedger | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);

  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * Tên hiển thị của một loại game.
   *
   * BẢNG ÁNH XẠ TƯỜNG MINH thay vì dựng khoá từ `gameType`: mã trong DB
   * (`BRITISH_LUCKY28`, `KL28`) không trùng với khoá locale (`british28`, `korean28`),
   * nên dựng khoá động sẽ cho ra `games.british_lucky28` trên bảng sổ sách.
   */
  const gameLabel = useCallback(
    (gameType: string): string => {
      const keys: Record<string, string> = {
        LUCKY28: "games.lucky28",
        BRITISH_LUCKY28: "games.british28",
        KL28: "games.korean28",
        TAIWAN_TIMES: "games.taiwantimes",
      };
      const key = keys[gameType];
      if (!key) return gameType;
      const label = t(key);
      return label === key ? gameType : label;
    },
    [t]
  );

  const loadOverview = useCallback(async () => {
    setLoadingOverview(true);
    setError(null);
    try {
      const params = new URLSearchParams({
        month,
        sort,
        page: String(page),
        size: String(PAGE_SIZE),
      });
      if (appliedKeyword) params.set("keyword", appliedKeyword);
      setOverview(
        await adminFetch<LedgerOverview>(`/admin/reports/ledger/overview?${params}`)
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : t("admin.ledger.err_load"));
      setOverview(null);
    } finally {
      setLoadingOverview(false);
    }
  }, [month, sort, page, appliedKeyword, t]);

  useEffect(() => {
    if (!getAdminToken()) return;
    void loadOverview();
  }, [loadOverview]);

  /**
   * Áp dụng từ khoá sau khi người dùng ngừng gõ 400ms.
   *
   * DEBOUNCE LÀ BẮT BUỘC, KHÔNG PHẢI TRANG TRÍ: mỗi lần gọi endpoint này backend
   * chạy bốn truy vấn tổng hợp trên toàn bộ giao dịch của kỳ. Gọi theo từng ký tự
   * thì gõ một tên 8 chữ là 8 lần tính lại, và các phản hồi có thể về không đúng
   * thứ tự khiến bảng hiện kết quả của từ khoá cũ.
   *
   * VỀ TRANG ĐẦU khi từ khoá đổi: giữ trang 5 với một bộ lọc hẹp hơn sẽ ra bảng
   * trống và trông như hệ thống hỏng.
   */
  useEffect(() => {
    const next = keyword.trim();
    if (next === appliedKeyword) return;

    const timer = setTimeout(() => {
      setPage(0);
      setAppliedKeyword(next);
    }, 400);
    return () => clearTimeout(timer);
  }, [keyword, appliedKeyword]);


  const openDetail = async (userId: string) => {
    setDetailUserId(userId);
    setLoadingDetail(true);
    setError(null);
    try {
      setDetail(
        await adminFetch<PlayerLedger>(
          `/admin/reports/players/${userId}/ledger?month=${month}`
        )
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : t("admin.ledger.err_load"));
      // XOÁ dữ liệu cũ khi lỗi: giữ lại sẽ khiến người vận hành đọc số của người
      // trước mà tưởng là của người vừa bấm.
      setDetail(null);
    } finally {
      setLoadingDetail(false);
    }
  };

  const closeDetail = () => {
    setDetailUserId(null);
    setDetail(null);
    setError(null);
  };

  /**
   * Tải CSV.
   *
   * KHÔNG dùng thẻ `<a download>` trỏ thẳng tới API: endpoint cần header
   * `Authorization`, mà điều hướng của trình duyệt không mang header nào. Phải gọi
   * bằng `fetch` rồi tạo blob URL.
   */
  const downloadCsv = async () => {
    if (!detail) return;
    setDownloading(true);
    try {
      const token = getAdminToken();
      const res = await fetch(
        `${ADMIN_API_BASE_URL}/admin/reports/players/${detail.userId}/ledger.csv?month=${month}`,
        { headers: token ? { Authorization: `Bearer ${token}` } : {} }
      );
      if (!res.ok) throw new Error(String(res.status));

      const blobUrl = URL.createObjectURL(await res.blob());
      const link = document.createElement("a");
      link.href = blobUrl;
      link.download = `so-sach-${detail.username}-${month}.csv`;
      link.click();
      // Thu hồi ngay sau khi bấm: giữ lại thì blob nằm trong bộ nhớ đến khi tải lại
      // trang, và một buổi làm sổ có thể xuất vài chục tệp.
      URL.revokeObjectURL(blobUrl);
    } catch {
      setError(t("admin.ledger.err_csv"));
    } finally {
      setDownloading(false);
    }
  };

  /** Nhà cái lãi/lỗ là số đối của người chơi. */
  const houseNet = useMemo(
    () => (detail ? String(-Number(detail.totalNet)) : "0"),
    [detail]
  );

  const currency = detail?.currency ?? "USD";

  /**
   * Kỳ này CÓ đồng nào nạp qua cổng thanh toán hay không.
   *
   * VÌ SAO PHẢI ẨN CÓ ĐIỀU KIỆN, KHÔNG XOÁ HẲN: tính năng nạp tự động đã được gỡ ở
   * phía người chơi — giờ admin cộng tiền tay trong khung chat. Nhưng dữ liệu cũ vẫn
   * còn (2.696 từ cổng giả lập hồi test), và sổ sách phải cân đối theo đẳng thức
   * `dư cuối = dư đầu + nạp + admin cộng − admin trừ − rút + lãi/lỗ`. Bỏ hẳn số nạp
   * khỏi phép tính thì đẳng thức vỡ và người vận hành thấy số dư lệch mà không hiểu
   * vì sao.
   *
   * Cách này cũng tự đúng nếu sau này bật lại cổng thanh toán: cột hiện lại mà không
   * cần sửa mã.
   */
  const hasGatewayDeposit =
    detailUserId === null
      ? Number(overview?.totalDeposit ?? 0) > 0
      : Number(detail?.depositViaGateway ?? 0) > 0;


  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        subtitle={t("admin.ledger.subtitle")}
        title={t("admin.ledger.title")}
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Bộ lọc: chỉ hiện ở chế độ tổng quan. Ở chế độ chi tiết, đổi tháng mà không
            tải lại chi tiết sẽ làm số trên trang không khớp với tháng đang chọn. */}
        {detailUserId === null ? (
          <section className="rounded-2xl border border-slate-200 bg-white p-5">
            <div className="flex flex-wrap items-end gap-4">
              <div>
                <label
                  className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500"
                  htmlFor="ledger-month"
                >
                  {t("admin.ledger.filter_month")}
                </label>
                <input
                  className="rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
                  id="ledger-month"
                  onChange={(e) => {
                    setPage(0);
                    setMonth(e.target.value);
                  }}
                  type="month"
                  value={month}
                />
              </div>

              <div>
                <label
                  className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500"
                  htmlFor="ledger-sort"
                >
                  {t("admin.ledger.sort_by")}
                </label>
                <select
                  className="rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
                  id="ledger-sort"
                  onChange={(e) => {
                    setPage(0);
                    setSort(e.target.value as SortKey);
                  }}
                  value={sort}
                >
                  <option value="stake">{t("admin.ledger.sort_stake")}</option>
                  <option value="net">{t("admin.ledger.sort_net")}</option>
                  <option value="deposit">{t("admin.ledger.sort_deposit")}</option>
                </select>
              </div>
            </div>

            {overview ? (
              <p className="mt-3 text-xs text-slate-500">
                {t("admin.ledger.timezone_note", { timezone: overview.timezone })}
              </p>
            ) : null}
          </section>
        ) : null}

        {error ? (
          <div
            className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
            role="alert"
          >
            {error}
          </div>
        ) : null}

        {/* ================= CHẾ ĐỘ TỔNG QUAN ================= */}
        {detailUserId === null ? (
          loadingOverview ? (
            <div className="flex justify-center py-16">
              <Loader2 aria-hidden="true" className="size-6 animate-spin text-slate-400" />
            </div>
          ) : overview ? (
            <>
              {/* Tổng của TOÀN KỲ, không phải tổng của trang đang xem. */}
              <section className="rounded-2xl border border-slate-200 bg-white p-5">
                <h2 className="mb-4 text-sm font-bold uppercase tracking-wide text-slate-500">
                  {t("admin.ledger.period_total")}
                </h2>
                {/* Lưới tự co theo số thẻ thật: cố định lg:grid-cols-4 khi chỉ còn 3
                    thẻ sẽ để lại một ô trống lệch ở cuối hàng. */}
                <div
                  className={`grid gap-3 sm:grid-cols-2 ${
                    hasGatewayDeposit ? "lg:grid-cols-4" : "lg:grid-cols-3"
                  }`}
                >
                  {hasGatewayDeposit ? (
                    <MoneyCard
                      currency="USD"
                      label={t("admin.ledger.deposit_gateway")}
                      tone="in"
                      value={overview.totalDeposit}
                    />
                  ) : null}
                  <MoneyCard
                    currency="USD"
                    label={t("admin.ledger.admin_credit")}
                    tone="in"
                    value={overview.totalAdminCredit}
                  />
                  <MoneyCard
                    currency="USD"
                    label={t("admin.ledger.admin_debit")}
                    tone="out"
                    value={overview.totalAdminDebit}
                  />
                  <MoneyCard
                    currency="USD"
                    label={t("admin.ledger.withdrawal_settled")}
                    tone="out"
                    value={overview.totalWithdrawal}
                  />
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4 text-sm">
                  <span className="text-slate-500">{t("admin.ledger.house_net_all")}</span>
                  <SignedAmount currency="USD" value={String(-Number(overview.totalNet))} />
                </div>
              </section>

              <section className="rounded-2xl border border-slate-200 bg-white p-5">
                <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                  <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">
                    {t("admin.ledger.active_players", {
                      count: String(overview.totalElements),
                    })}
                  </h2>

                  {/* Ô TÌM KIẾM NẰM NGAY TRÊN BẢNG, không ở khối bộ lọc phía trên:
                      nó lọc chính bảng này, nên đặt cạnh bảng thì thấy ngay kết quả
                      thay đổi. Chỉ có MỘT ô tìm kiếm trên trang — hai ô cùng lọc một
                      thứ ở hai chỗ khác nhau là cách chắc chắn làm người dùng bối rối. */}
                  <div className="relative w-full sm:w-72">
                    <Search
                      aria-hidden="true"
                      className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400"
                    />
                    <label className="sr-only" htmlFor="ledger-search">
                      {t("admin.ledger.filter_player")}
                    </label>
                    <input
                      className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-9 text-sm outline-none focus:border-slate-900"
                      id="ledger-search"
                      onChange={(e) => setKeyword(e.target.value)}
                      placeholder={t("admin.ledger.search_placeholder")}
                      type="search"
                      value={keyword}
                    />
                    {/* Vòng xoay chỉ hiện khi ĐANG GÕ DỞ (từ khoá chưa kịp áp dụng),
                        để người dùng biết hệ thống đã nhận ký tự vừa nhập. */}
                    {keyword.trim() !== appliedKeyword ? (
                      <Loader2
                        aria-hidden="true"
                        className="absolute right-3 top-1/2 size-4 -translate-y-1/2 animate-spin text-slate-400"
                      />
                    ) : null}
                  </div>
                </div>

                {overview.rows.length === 0 ? (
                  <p className="py-12 text-center text-sm text-slate-500">
                    {/* Phân biệt "không ai hoạt động" với "không ai khớp từ khoá":
                        gộp hai thông báo sẽ khiến người vận hành tưởng cả tháng không
                        có giao dịch, trong khi thực ra chỉ là gõ sai tên. */}
                    {appliedKeyword
                      ? t("admin.ledger.no_match", { keyword: appliedKeyword })
                      : t("admin.ledger.no_activity")}
                  </p>
                ) : (
                  <>
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                            <th className="pb-2 pr-4 font-semibold">
                              {t("admin.ledger.col_player")}
                            </th>
                            <th className="pb-2 pr-4 text-right font-semibold">
                              {t("admin.ledger.col_rounds")}
                            </th>
                            <th className="pb-2 pr-4 text-right font-semibold">
                              {t("admin.ledger.col_stake")}
                            </th>
                            <th className="pb-2 pr-4 text-right font-semibold">
                              {t("admin.ledger.col_net")}
                            </th>
                            {hasGatewayDeposit ? (
                              <th className="pb-2 pr-4 text-right font-semibold">
                                {t("admin.ledger.deposit_gateway")}
                              </th>
                            ) : null}
                            <th className="pb-2 pr-4 text-right font-semibold">
                              {t("admin.ledger.admin_credit")}
                            </th>
                            <th className="pb-2 pr-4 text-right font-semibold">
                              {t("admin.ledger.withdrawal_settled")}
                            </th>
                            <th className="pb-2 text-right font-semibold">
                              {t("admin.ledger.col_balance")}
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          {overview.rows.map((r) => (
                            <tr
                              className="cursor-pointer border-b border-slate-100 last:border-0 hover:bg-slate-50"
                              key={r.userId}
                              onClick={() => void openDetail(r.userId)}
                            >
                              <td className="py-3 pr-4">
                                {/* NÚT thay vì chỉ hàng bấm được: hàng bấm được không
                                    tới được bằng bàn phím và trình đọc màn hình
                                    không thông báo là có thể mở. */}
                                <button
                                  className="font-medium text-slate-900 underline-offset-2 hover:underline"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    void openDetail(r.userId);
                                  }}
                                  type="button"
                                >
                                  {r.username}
                                </button>
                              </td>
                              <td className="py-3 pr-4 text-right tabular-nums text-slate-600">
                                {r.betCount}
                              </td>
                              <td className="py-3 pr-4 text-right tabular-nums text-slate-600">
                                {formatMoney(r.stake)}
                              </td>
                              <td className="py-3 pr-4 text-right">
                                <SignedAmount value={r.net} />
                              </td>
                              {hasGatewayDeposit ? (
                                <td className="py-3 pr-4 text-right tabular-nums text-slate-600">
                                  {formatMoney(r.deposit)}
                                </td>
                              ) : null}
                              <td className="py-3 pr-4 text-right tabular-nums text-slate-600">
                                {formatMoney(r.adminCredit)}
                              </td>
                              <td className="py-3 pr-4 text-right tabular-nums text-slate-600">
                                {formatMoney(r.withdrawal)}
                              </td>
                              <td className="py-3 text-right tabular-nums font-semibold text-slate-900">
                                {formatMoney(r.balance)} {r.currency}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    {overview.totalPages > 1 ? (
                      <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-4">
                        <span className="text-xs text-slate-500">
                          {t("admin.ledger.page_of", {
                            page: String(overview.page + 1),
                            total: String(overview.totalPages),
                          })}
                        </span>
                        <div className="flex gap-2">
                          <button
                            className="inline-flex items-center gap-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-40"
                            disabled={overview.page === 0}
                            onClick={() => setPage((p) => Math.max(0, p - 1))}
                            type="button"
                          >
                            <ChevronLeft aria-hidden="true" className="size-4" />
                            {t("admin.ledger.prev")}
                          </button>
                          <button
                            className="inline-flex items-center gap-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-40"
                            disabled={overview.page + 1 >= overview.totalPages}
                            onClick={() => setPage((p) => p + 1)}
                            type="button"
                          >
                            {t("admin.ledger.next")}
                            <ChevronRight aria-hidden="true" className="size-4" />
                          </button>
                        </div>
                      </div>
                    ) : null}
                  </>
                )}
              </section>
            </>
          ) : null
        ) : (
          /* ================= CHẾ ĐỘ CHI TIẾT ================= */
          <>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <button
                className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
                id="ledger-back"
                onClick={closeDetail}
                type="button"
              >
                <ArrowLeft aria-hidden="true" className="size-4" />
                {t("admin.ledger.back_to_list")}
              </button>

              <button
                className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-slate-800 disabled:bg-slate-300"
                disabled={!detail || downloading}
                id="ledger-export-csv"
                onClick={() => void downloadCsv()}
                type="button"
              >
                {downloading ? (
                  <Loader2 aria-hidden="true" className="size-4 animate-spin" />
                ) : (
                  <Download aria-hidden="true" className="size-4" />
                )}
                {t("admin.ledger.export_csv")}
              </button>
            </div>

            {loadingDetail ? (
              <div className="flex justify-center py-16">
                <Loader2 aria-hidden="true" className="size-6 animate-spin text-slate-400" />
              </div>
            ) : detail ? (
              <>
                <section className="rounded-2xl border border-slate-200 bg-white p-5">
                  <div className="mb-4 flex flex-wrap items-baseline gap-x-3 gap-y-1">
                    <h2 className="text-lg font-bold text-slate-900">{detail.username}</h2>
                    <span className="text-xs text-slate-500">
                      {detail.periodFrom} &rarr; {detail.periodTo} ({detail.timezone})
                    </span>
                  </div>

                  <h3 className="mb-3 text-sm font-bold uppercase tracking-wide text-slate-500">
                    {t("admin.ledger.cash_flow")}
                  </h3>

                  <div
                    className={`grid gap-3 sm:grid-cols-2 ${
                      hasGatewayDeposit ? "lg:grid-cols-4" : "lg:grid-cols-3"
                    }`}
                  >
                    {hasGatewayDeposit ? (
                      <MoneyCard
                        currency={currency}
                        label={t("admin.ledger.deposit_gateway")}
                        tone="in"
                        value={detail.depositViaGateway}
                      />
                    ) : null}
                    <MoneyCard
                      currency={currency}
                      label={t("admin.ledger.admin_credit")}
                      tone="in"
                      value={detail.adminCredit}
                    />
                    <MoneyCard
                      currency={currency}
                      label={t("admin.ledger.admin_debit")}
                      tone="out"
                      value={detail.adminDebit}
                    />
                    <MoneyCard
                      currency={currency}
                      label={t("admin.ledger.withdrawal_settled")}
                      tone="out"
                      value={detail.withdrawalSettled}
                    />
                  </div>

                  <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-2 border-t border-slate-100 pt-4 text-sm">
                    <span className="text-slate-500">
                      {t("admin.ledger.opening_balance")}{" "}
                      <span className="font-semibold tabular-nums text-slate-900">
                        {formatMoney(detail.openingBalance)} {currency}
                      </span>
                    </span>
                    <span aria-hidden="true" className="text-slate-300">
                      &rarr;
                    </span>
                    <span className="text-slate-500">
                      {t("admin.ledger.closing_balance")}{" "}
                      <span className="font-semibold tabular-nums text-slate-900">
                        {formatMoney(detail.closingBalance)} {currency}
                      </span>
                    </span>
                  </div>
                </section>

                <section className="rounded-2xl border border-slate-200 bg-white p-5">
                  <h3 className="mb-4 text-sm font-bold uppercase tracking-wide text-slate-500">
                    {t("admin.ledger.win_loss")}
                  </h3>

                  {detail.games.length === 0 ? (
                    <p className="py-8 text-center text-sm text-slate-500">
                      {t("admin.ledger.no_bets")}
                    </p>
                  ) : (
                    <>
                      <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                              <th className="pb-2 pr-4 font-semibold">
                                {t("admin.ledger.col_game")}
                              </th>
                              <th className="pb-2 pr-4 text-right font-semibold">
                                {t("admin.ledger.col_rounds")}
                              </th>
                              <th className="pb-2 pr-4 text-right font-semibold">
                                {t("admin.ledger.col_stake")}
                              </th>
                              <th className="pb-2 pr-4 text-right font-semibold">
                                {t("admin.ledger.col_payout")}
                              </th>
                              <th className="pb-2 pr-4 text-right font-semibold">
                                {t("admin.ledger.col_net")}
                              </th>
                              <th className="pb-2 text-right font-semibold">
                                {t("admin.ledger.col_pending")}
                              </th>
                            </tr>
                          </thead>
                          <tbody>
                            {detail.games.map((g) => (
                              <tr
                                className="border-b border-slate-100 last:border-0"
                                key={g.gameType}
                              >
                                <td className="py-3 pr-4 font-medium text-slate-900">
                                  {gameLabel(g.gameType)}
                                </td>
                                <td className="py-3 pr-4 text-right tabular-nums text-slate-600">
                                  {g.betCount}
                                </td>
                                <td className="py-3 pr-4 text-right tabular-nums text-slate-600">
                                  {formatMoney(g.stake)}
                                </td>
                                <td className="py-3 pr-4 text-right tabular-nums text-slate-600">
                                  {formatMoney(g.payout)}
                                </td>
                                <td className="py-3 pr-4 text-right">
                                  <SignedAmount currency={currency} value={g.net} />
                                </td>
                                <td className="py-3 text-right tabular-nums text-slate-600">
                                  {formatMoney(g.pendingStake)}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                          <tfoot>
                            <tr className="border-t-2 border-slate-300 font-bold">
                              <td className="pt-3 pr-4 text-slate-900">
                                {t("admin.ledger.total")}
                              </td>
                              <td className="pt-3 pr-4 text-right tabular-nums text-slate-900">
                                {detail.games.reduce((sum, g) => sum + g.betCount, 0)}
                              </td>
                              <td className="pt-3 pr-4 text-right tabular-nums text-slate-900">
                                {formatMoney(detail.totalStake)}
                              </td>
                              <td className="pt-3 pr-4 text-right tabular-nums text-slate-900">
                                {formatMoney(detail.totalPayout)}
                              </td>
                              <td className="pt-3 pr-4 text-right">
                                <SignedAmount currency={currency} value={detail.totalNet} />
                              </td>
                              <td className="pt-3 text-right tabular-nums text-slate-900">
                                {formatMoney(detail.totalPending)}
                              </td>
                            </tr>
                          </tfoot>
                        </table>
                      </div>

                      {/* Góc nhìn nhà cái: ngược dấu với người chơi, nên nói RÕ ra
                          thay vì để người đọc tự đảo dấu trong đầu. */}
                      <div className="mt-4 flex items-center gap-2 border-t border-slate-100 pt-4 text-sm">
                        {Number(houseNet) >= 0 ? (
                          <TrendingUp aria-hidden="true" className="size-4 text-emerald-600" />
                        ) : (
                          <TrendingDown aria-hidden="true" className="size-4 text-rose-600" />
                        )}
                        <span className="text-slate-500">{t("admin.ledger.house_net")}</span>
                        <SignedAmount currency={currency} value={houseNet} />
                      </div>
                    </>
                  )}
                </section>
              </>
            ) : null}
          </>
        )}
      </div>
    </div>
  );
}
