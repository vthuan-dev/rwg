"use client";

import React, { Suspense, useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { HistoryShell } from "@/components/game/HistoryShell";
import { useTranslation } from "@/context/LanguageContext";
import { safeInternalPath } from "@/lib/safePath";
import {
  ApiError,
  gameTables,
  getPlayerToken,
  roundsHistory,
  type GameRound,
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
 * Kết quả một ván, hiển thị gọn trong một ô.
 *
 * Ba loại game trả kết quả ở ba trường khác nhau: Lucky 28 dùng `kl28Numbers`, Roulette
 * dùng `winningNumber`, Baccarat dùng `baccaratResult`. Trả `null` cho ván bị huỷ vì
 * không có kết quả nào để hiện.
 */
function resultText(round: GameRound): string | null {
  if (round.status === "VOIDED") return null;
  if (round.kl28Numbers) return round.kl28Numbers.split(",").join(" + ");
  if (round.winningNumber != null) return String(round.winningNumber);
  if (round.baccaratResult) return round.baccaratResult;
  return null;
}

function DrawHistoryContent() {
  const { t } = useTranslation();
  const router = useRouter();
  const searchParams = useSearchParams();

  const idParam = searchParams.get("id") ?? "";
  // Mặc định về `/draw`: trang này giờ vào từ nút "Kết quả khác" ở danh sách sảnh, nên
  // thiếu `?ref=` thì quay lại đúng chỗ vừa rời đi. Trang `/bet/detail` vẫn truyền
  // `?ref=/bet` tường minh nên luồng cũ không đổi.
  const backHref = safeInternalPath(searchParams.get("ref"), "/draw");

  const [rounds, setRounds] = useState<GameRound[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  /**
   * Tải một trang lịch sử.
   *
   * Phải đổi tham số `?id=` (có thể là gameType như "lucky28") sang UUID thật của bàn: API
   * lịch sử chỉ nhận UUID. Cùng cách làm với trang đặt cược.
   */
  const fetchPage = useCallback(
    async (p: number): Promise<{ rounds: GameRound[]; totalPages: number } | null> => {
      try {
        const tables = await gameTables();
        const key = idParam.trim().toLowerCase();
        const table =
          tables.find((x) => x.gameType.toLowerCase() === key) ??
          tables.find((x) => x.id.toLowerCase() === key);

        if (!table) {
          setError(t("bet.table_not_found"));
          return null;
        }

        const data = await roundsHistory(table.id, p, PAGE_SIZE);
        return { rounds: data.content, totalPages: Math.max(1, data.totalPages) };
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
        setRounds(result.rounds);
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

  return (
    <HistoryShell
      backHref={backHref}
      empty={rounds.length === 0}
      error={error}
      loading={loading}
      onPageChange={changePage}
      page={page}
      title={t("bet.draw_history_title")}
      totalPages={totalPages}
    >
      <table className="w-full text-[0.6875rem]">
        <thead>
          <tr className="text-[#979797]">
            <th className="text-start font-normal">{t("bet.draw_no")}</th>
            <th className="text-end font-normal">{t("bet.result_column")}</th>
            <th className="text-end font-normal">{t("bet.sum_column")}</th>
            <th className="text-end font-normal">{t("bet.time_column")}</th>
          </tr>
        </thead>
        <tbody>
          {rounds.map((round) => {
            const result = resultText(round);
            return (
              <tr className="border-b border-[#1D1D1D]" key={round.roundId}>
                <td className="py-2.5 text-start font-bold text-white">
                  #{round.roundSeq}
                </td>
                <td className="py-2.5 text-end text-primary">
                  {result ?? (
                    <span className="text-[#828282]">{t("bet.round_voided")}</span>
                  )}
                </td>
                <td className="py-2.5 text-end font-bold text-white">
                  {round.kl28Sum ?? "--"}
                </td>
                <td className="py-2.5 text-end text-[#d0d5da]">
                  {formatTime(round.startedAt)}
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
export default function DrawHistoryPage() {
  return (
    <Suspense fallback={null}>
      <DrawHistoryContent />
    </Suspense>
  );
}
