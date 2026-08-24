"use client";

import React, { Suspense, useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { HistoryShell } from "@/components/game/HistoryShell";
import { useTranslation } from "@/context/LanguageContext";
import { compareMoney, formatMoney } from "@/lib/money";
import { safeInternalPath } from "@/lib/safePath";
import {
  ApiError,
  betsHistory,
  gameTables,
  getPlayerToken,
  type PlayerBet,
} from "@/lib/playerApi";

const PAGE_SIZE = 20;

/** Giờ theo định dạng của trang gốc: 22-08-2026 5:11 PM. */
function formatTime(iso: string | null): string {
  if (!iso) return "--";
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  const h = d.getHours();
  const h12 = h % 12 === 0 ? 12 : h % 12;
  const meridiem = h < 12 ? "AM" : "PM";

  return `${pad(d.getDate())}-${pad(d.getMonth() + 1)}-${d.getFullYear()} ${h12}:${pad(
    d.getMinutes()
  )} ${meridiem}`;
}

/**
 * Trạng thái hiển thị của một cược.
 *
 * Backend chỉ có ba trạng thái: PENDING, SETTLED, VOIDED. "Thắng" và "thua" KHÔNG phải
 * trạng thái riêng — cả hai đều là SETTLED, phân biệt bằng tiền thắng có lớn hơn 0 hay không.
 *
 * So sánh qua `compareMoney` chứ không `Number(payout) > 0`: tiền là chuỗi thập phân
 * scale 8, đọc qua `Number` làm mất chính xác với số lớn.
 */
function statusOf(bet: PlayerBet): { key: string; color: string } {
  if (bet.status === "VOIDED") {
    return { key: "bet.status_void", color: "text-[#828282]" };
  }
  if (bet.status === "PENDING") {
    return { key: "bet.status_pending", color: "text-white" };
  }
  // SETTLED
  return compareMoney(bet.payout, "0") > 0
    ? { key: "bet.status_won", color: "text-primary" }
    : { key: "bet.status_lost", color: "text-[#828282]" };
}

function BetHistoryContent() {
  const { t } = useTranslation();
  const router = useRouter();
  const searchParams = useSearchParams();

  const idParam = searchParams.get("id") ?? "";
  const backHref = safeInternalPath(searchParams.get("ref"), "/bet");

  const [bets, setBets] = useState<PlayerBet[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchPage = useCallback(
    async (p: number): Promise<{ bets: PlayerBet[]; totalPages: number } | null> => {
      try {
        // Đổi `?id=` sang UUID thật: API chỉ nhận UUID, còn URL có thể mang gameType.
        // Thiếu bước này thì bộ lọc bị bỏ qua âm thầm và trang hiện cược của MỌI bàn.
        let tableId: string | undefined;
        if (idParam.trim() !== "") {
          const tables = await gameTables();
          const key = idParam.trim().toLowerCase();
          const table =
            tables.find((x) => x.gameType.toLowerCase() === key) ??
            tables.find((x) => x.id.toLowerCase() === key);

          if (!table) {
            setError(t("bet.table_not_found"));
            return null;
          }
          tableId = table.id;
        }

        const data = await betsHistory(tableId, p, PAGE_SIZE);
        return { bets: data.content, totalPages: Math.max(1, data.totalPages) };
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) {
          router.replace("/login");
          return null;
        }
        setError(err instanceof ApiError ? err.message : t("bet.load_failed"));
        return null;
      }
    },
    [idParam, router, t]
  );

  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }

    let cancelled = false;

    (async () => {
      const result = await fetchPage(page);
      if (cancelled) return;
      if (result) {
        setBets(result.bets);
        setTotalPages(result.totalPages);
        setError("");
      }
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchPage, page, router]);

  const changePage = (next: number) => {
    setLoading(true);
    setError("");
    setPage(next);
  };

  /** Nhãn loại cược. Loại chưa có bản dịch hiện nguyên tên enum thay vì ô trống. */
  const betTypeLabel = (betType: string) => {
    const key = `admin.bet_types.${betType}`;
    const label = t(key);
    return label === key ? betType : label;
  };

  return (
    <HistoryShell
      backHref={backHref}
      empty={bets.length === 0}
      error={error}
      loading={loading}
      onPageChange={changePage}
      page={page}
      title={t("bet.bet_history_title")}
      totalPages={totalPages}
    >
      <table className="w-full text-[0.6875rem]">
        <thead>
          <tr className="text-[#979797]">
            <th className="text-start font-normal">{t("bet.bet_no")}</th>
            <th className="text-end font-normal">{t("bet.stake_column")}</th>
            <th className="text-end font-normal">{t("bet.status_column")}</th>
            <th className="text-end font-normal">{t("bet.payout_column")}</th>
          </tr>
        </thead>
        <tbody>
          {bets.map((bet) => {
            const status = statusOf(bet);
            return (
              <tr className="border-b border-[#1D1D1D]" key={bet.id}>
                <td className="py-2.5 text-start">
                  <div className="flex flex-col gap-y-0.5">
                    <span className="font-bold text-white">
                      {betTypeLabel(bet.betType)}
                      {bet.selection ? ` · ${bet.selection}` : ""}
                    </span>
                    <span className="text-[0.625rem] text-[#828282]">
                      {formatTime(bet.createdAt)}
                    </span>
                  </div>
                </td>
                <td className="py-2.5 text-end font-bold text-[#d0d5da]">
                  {formatMoney(bet.stake)}
                </td>
                <td className={`py-2.5 text-end font-bold ${status.color}`}>
                  {t(status.key)}
                </td>
                <td className="py-2.5 text-end font-bold text-primary">
                  {formatMoney(bet.payout)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </HistoryShell>
  );
}

/**
 * `useSearchParams` bắt buộc có ranh giới `Suspense`, không thì `next build` thất bại khi
 * dựng sẵn trang này.
 */
export default function BetHistoryPage() {
  return (
    <Suspense fallback={null}>
      <BetHistoryContent />
    </Suspense>
  );
}
