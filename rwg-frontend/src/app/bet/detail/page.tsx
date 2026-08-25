"use client";

import React, { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { PlayerLayout } from "@/components/layout/PlayerLayout";
import { WalletBalanceBar } from "@/components/game/WalletBalanceBar";
import { CurrentHallCard } from "@/components/game/CurrentHallCard";
import { MatchInfoPanel } from "@/components/game/MatchInfoPanel";
import { BetOptionGrid } from "@/components/game/BetOptionGrid";
import { BetSubmitBar } from "@/components/game/BetSubmitBar";
import { BetTabs } from "@/components/game/BetTabs";
import { NumberOddsGrid } from "@/components/game/NumberOddsGrid";
import { BettorsTable } from "@/components/game/BettorsTable";
import { BetBlockingOverlay } from "@/components/game/BetBlockingOverlay";
import { useTranslation } from "@/context/LanguageContext";
import { MAX_BET_SELECTIONS, mergeServerOdds } from "@/lib/betOptions";
import { safeInternalPath } from "@/lib/safePath";
import {
  ApiError,
  currentRound,
  gameTables,
  getPlayerToken,
  myBets,
  placeBet,
  roundsHistory,
  tableOdds,
  walletMe,
  type GameRound,
  type GameTable,
  type PlayerBet,
  type TableOdds,
  type Wallet,
} from "@/lib/playerApi";

/**
 * Độ dài một vòng dùng khi chưa tải được dự liệu vòng.
 *
 * Chỉ là giá trị DỰ PHÒNG cho lần render đầu. Con số thật đến từ `round.roundSeconds`,
 * vì thời lượng các pha nằm trong cấu hình server và đổi được mà không sửa frontend.
 */
const FALLBACK_ROUND_SECONDS = 63;

/**
 * Tìm bàn theo tham số `?id=`.
 *
 * Nhận CẢ HAI dạng: `gameType` (`?id=lucky28`) và UUID (`?id=4444...`). Dạng `gameType`
 * là dạng chính vì đọc được và không đổi khi seed lại dữ liệu; dạng UUID vẫn nhận để
 * liên kết chia sẻ hoặc liên kết cũ không bị vỡ.
 *
 * Trang gốc dùng `encrypted_id` (`?id=pE`) — một mã đã mã hoá của khoá chính. Backend
 * RWG không có cột đó nên không sao lại được, mà thêm một lớp mã hoá id chỉ để giống
 * hình thức thì không đem lại lợi ích bảo mật nào: id bàn chơi không phải thông tin mật,
 * và mọi endpoint đều đã kiểm quyền qua JWT.
 */
function findTable(tables: GameTable[], param: string): GameTable | null {
  const key = param.trim().toLowerCase();
  if (key === "") return null;
  return (
    tables.find((table) => table.gameType.toLowerCase() === key) ??
    tables.find((table) => table.id.toLowerCase() === key) ??
    null
  );
}

function BetDetailContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t, locale } = useTranslation();

  const idParam = searchParams.get("id") ?? "";

  /**
   * Đích của nút quay lại, lấy từ `?ref=` như trang gốc.
   *
   * PHẢI kiểm trước khi dùng: giá trị này đến từ URL nên ai cũng gửi được. Xem
   * `safeInternalPath` để biết vì sao điều hướng thẳng tới nó là lỗ hổng.
   */
  const backHref = safeInternalPath(searchParams.get("ref"), "/bet");

  const [tables, setTables] = useState<GameTable[]>([]);
  const [table, setTable] = useState<GameTable | null>(null);
  const [round, setRound] = useState<GameRound | null>(null);
  const [lastRound, setLastRound] = useState<GameRound | null>(null);
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [bets, setBets] = useState<PlayerBet[]>([]);
  const [odds, setOdds] = useState<TableOdds | null>(null);

  const [selected, setSelected] = useState<string[]>([]);
  /**
   * Các tổng 0-27 đang chọn ở tab Special Code.
   *
   * Giữ riêng khỏi {@code selected}: hai tab gửi hai loại cược khác nhau — tab Mixing gửi
   * bốn loại kết hợp không có {@code selection}, tab này gửi {@code KL28_NUMBER} kèm số.
   * Nhồn chung một mảng thì không phân biệt được "14" là tổng hay tên loại cược.
   */
  const [selectedNumbers, setSelectedNumbers] = useState<string[]>([]);
  const [stake, setStake] = useState("");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const aliveRef = useRef(true);
  useEffect(() => {
    aliveRef.current = true;
    return () => {
      aliveRef.current = false;
    };
  }, []);

  /**
   * Bộ đếm chống trùng cho lời gọi đặt cược.
   *
   * Backend dựng khoá `BET:{roundId}:{userId}:{seq}`. Đã kiểm bằng lời gọi thật: gửi hai
   * cược KHÁC NHAU cùng `seq` thì server trả lại cược ĐẦU TIÊN kèm mã thành công, cược
   * thứ hai bị bỏ hoàn toàn mà không báo lỗi. Nên `seq` phải tăng cho từng cược, và đặt
   * lại về 0 khi sang vòng mới.
   */
  const seqRef = useRef(0);
  const roundIdRef = useRef<string | null>(null);

  const loadBets = useCallback(async (roundId: string) => {
    try {
      const list = await myBets(roundId);
      if (aliveRef.current) setBets(list);
    } catch {
      // Không lấy được thì bảng hiện "chưa có cược nào". Không đáng báo lỗi to.
    }
  }, []);

  const loadRound = useCallback(
    async (tableId: string) => {
      const [current, history] = await Promise.allSettled([
        currentRound(tableId),
        roundsHistory(tableId, 0, 1),
      ]);

      if (!aliveRef.current) return;

      if (current.status === "fulfilled") {
        setRound(current.value);
        void loadBets(current.value.roundId);
      } else {
        // 404 giữa hai vòng là bình thường — xem ghi chú ở `currentRound`.
        setRound(null);
      }
      if (history.status === "fulfilled") {
        setLastRound(history.value.content[0] ?? null);
      }
    },
    [loadBets]
  );

  const loadWallet = useCallback(async () => {
    try {
      const w = await walletMe();
      if (aliveRef.current) setWallet(w);
    } catch {
      // Không có ví thì thanh đáy hiện "—" và nút TỐI ĐA bị khoá.
    }
  }, []);

  /**
   * Tỷ lệ cược hiệu lực của người đang đăng nhập.
   *
   * Lỗi ở đây KHÔNG chặn trang: lưới rơi về bảng tỷ lệ dự phòng để người chơi vẫn cược
   * được. Con số có thể lệch nếu người này được đặt tỷ lệ riêng, nhưng server vẫn là nơi
   * quyết định tiền trả, nên không có nguy cơ trả sai.
   */
  const loadOdds = useCallback(async (tableId: string) => {
    try {
      const data = await tableOdds(tableId);
      if (aliveRef.current) setOdds(data);
    } catch {
      if (aliveRef.current) setOdds(null);
    }
  }, []);

  // Tải lần đầu.
  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }

    let cancelled = false;

    const load = async () => {
      try {
        const list = await gameTables();
        if (cancelled || !aliveRef.current) return;

        setTables(list);
        const found = findTable(list, idParam);
        setTable(found);
        setLoading(false);

        if (!found) {
          setError(t("bet.table_not_found"));
          return;
        }

        await Promise.allSettled([loadRound(found.id), loadWallet(), loadOdds(found.id)]);
      } catch (err) {
        if (cancelled || !aliveRef.current) return;
        if (err instanceof ApiError && err.status === 401) {
          router.replace("/login");
          return;
        }
        setError(t("bet.load_failed"));
        setLoading(false);
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [idParam, loadOdds, loadRound, loadWallet, router, t]);

  // Cập nhật số dư ví real-time qua websocket
  useEffect(() => {
    const handleBalanceUpdate = (e: Event) => {
      const customEvent = e as CustomEvent<string>;
      setWallet((prev) => {
        if (prev) {
          return { ...prev, balance: customEvent.detail };
        }
        return { balance: customEvent.detail, currency: table?.currency || "USD" } as any;
      });
    };
    window.addEventListener("wallet_balance_updated", handleBalanceUpdate);
    return () => {
      window.removeEventListener("wallet_balance_updated", handleBalanceUpdate);
    };
  }, [table]);

  // Cập nhật tỷ lệ cược odds real-time qua websocket khi admin chỉnh sửa
  useEffect(() => {
    const handleOddsUpdate = (e: Event) => {
      const customEvent = e as CustomEvent<{ tableId: string }>;
      if (table && customEvent.detail.tableId === table.id) {
        void loadOdds(table.id);
      }
    };
    window.addEventListener("game_odds_updated", handleOddsUpdate);
    return () => {
      window.removeEventListener("game_odds_updated", handleOddsUpdate);
    };
  }, [table, loadOdds]);

  /**
   * Sang vòng mới thì đặt lại phiếu cược.
   *
   * Bỏ trắng các lựa chọn: giữ lại thì người chơi tưởng cược đã đặt cho vòng mới. Đặt
   * `seq` về 0 vì khoá chống trùng gồm cả `roundId` nên đếm lại từ đầu là an toàn.
   */
  useEffect(() => {
    if (!round) return;
    if (roundIdRef.current === round.roundId) return;

    roundIdRef.current = round.roundId;
    seqRef.current = 0;
    setSelected([]);
    setSelectedNumbers([]);
    setNotice(null);
  }, [round]);

  /**
   * Bộ lựa chọn cược kèm tỷ lệ hiển thị.
   *
   * Ưu tiên tỷ lệ từ server vì người chơi này có thể được đặt tỷ lệ riêng. Chưa tải được
   * thì rơi về bảng dự phòng để lưới không trống.
   */
  const options = useMemo(() => {
    if (!table) return [];
    return mergeServerOdds(table.gameType, odds?.options ?? null);
  }, [odds, table]);

  const labelForBetType = useCallback(
    (betType: string) => {
      const found = options.find((o) => o.betType === betType);
      return found ? t(found.labelKey) : betType;
    },
    [options, t]
  );

  const selectedLabels = useMemo(
    () => [
      ...selected.map(labelForBetType),
      // Tổng hiện kèm tiền tố để không lẫn với nhãn loại cược ở cùng một dòng.
      ...selectedNumbers.map((sum) => t("bet.sum_label", { sum })),
    ],
    [labelForBetType, selected, selectedNumbers, t]
  );

  /** Tổng số lựa chọn cả hai tab — giới hạn 7 tính chung. */
  const totalSelections = selected.length + selectedNumbers.length;

  const bettingClosed = round == null || round.phase !== "BETTING_OPEN";

  const toggleOption = useCallback(
    (betType: string) => {
      setNotice(null);
      setSelected((prev) => {
        if (prev.includes(betType)) return prev.filter((b) => b !== betType);
        if (prev.length + selectedNumbers.length >= MAX_BET_SELECTIONS) {
          setNotice(t("bet.max_selections_reached"));
          return prev;
        }
        return [...prev, betType];
      });
    },
    [selectedNumbers.length, t]
  );

  const toggleNumber = useCallback(
    (sum: string) => {
      setNotice(null);
      setSelectedNumbers((prev) => {
        if (prev.includes(sum)) return prev.filter((s) => s !== sum);
        if (prev.length + selected.length >= MAX_BET_SELECTIONS) {
          setNotice(t("bet.max_selections_reached"));
          return prev;
        }
        return [...prev, sum];
      });
    },
    [selected.length, t]
  );

  /**
   * Gửi phiếu cược.
   *
   * Backend nhận MỘT cược mỗi lần, nên gửi LẦN LƯỢT chứ không song song: mỗi cược cần
   * một `seq` riêng, và gửi song song thì thứ tự tăng `seq` không còn xác định.
   *
   * Nếu một cược lỗi giữa chừng thì các cược trước đó ĐÃ được ghi nhận — backend không
   * có giao dịch bao cả nhóm. Nên báo rõ đã đặt được mấy cược thay vì nói chung là thất
   * bại, để người chơi biết tiền đã trừ bao nhiêu.
   */
  const submitBets = useCallback(async () => {
    if (!table || !round || submitting) return;

    setSubmitting(true);
    setNotice(null);

    let placed = 0;
    let failure: string | null = null;

    /**
     * Gộp hai tab thành một danh sách để gửi.
     *
     * Bốn cược kết hợp PHẢI gửi {@code selection} null — {@code Kl28Engine.validSelection}
     * từ chối nếu có giá trị. Còn {@code KL28_NUMBER} bắt buộc có số.
     */
    const payloads = [
      ...selected.map((betType) => ({ betType, selection: null as string | null })),
      ...selectedNumbers.map((sum) => ({ betType: "KL28_NUMBER", selection: sum })),
    ];

    for (const payload of payloads) {
      try {
        seqRef.current += 1;
        await placeBet(table.id, {
          betType: payload.betType,
          selection: payload.selection,
          stake,
          seq: seqRef.current,
        });
        placed += 1;
      } catch (err) {
        failure = err instanceof ApiError ? err.message || err.code : t("bet.place_failed");
        break;
      }
    }

    if (!aliveRef.current) return;

    setSubmitting(false);

    if (failure) {
      setNotice(
        placed > 0
          ? t("bet.partial_placed", { count: String(placed), reason: failure })
          : failure
      );
    } else {
      setNotice(t("bet.placed_successfully"));
      setSelected([]);
      setSelectedNumbers([]);
      setStake("");
    }

    // Đọc lại số dư và danh sách cược từ server thay vì tự suy: nếu nhóm cược lỗi giữa
    // chừng thì số dư trong phản hồi của cược cuối cùng thành công đã không còn đúng.
    void loadWallet();
    void loadBets(round.roundId);
  }, [
    loadBets,
    loadWallet,
    round,
    selected,
    selectedNumbers,
    stake,
    submitting,
    t,
    table,
  ]);

  const tableName = table
    ? (table.nameI18n[locale] ?? table.nameI18n.en ?? table.gameType)
    : "";

  const tableIndex = table ? tables.findIndex((x) => x.id === table.id) : 0;

  /** Ván chưa bắt đầu: đang ở khoảng trống giữa hai vòng. */
  const preparingNextMatch = !loading && error == null && table != null && round == null;

  /**
   * Đường dẫn cho hai nút lịch sử, mang theo `ref` để nút quay lại của trang đó biết
   * đường về — đúng cách trang gốc nối chuỗi `?id=...&ref=/bet/detail?id=...`.
   */
  const selfHref = `/bet/detail?id=${encodeURIComponent(idParam)}&ref=${encodeURIComponent(backHref)}`;

  return (
    <PlayerLayout>
      <main className="flex w-full grow flex-col bg-[#0d0d0d]">
        <header className="flex h-15 items-center gap-x-3 px-5">
          <button
            aria-label={t("bet.back")}
            className="flex size-8 items-center justify-center text-primary"
            onClick={() => router.push(backHref)}
            type="button"
          >
            <i aria-hidden="true" className="icon-icon76 text-[1.25rem]" />
          </button>
          <h1 className="text-[1.25rem] leading-[1.75rem] text-[#d0d5da]">
            {tableName || t("bet.title")}
          </h1>
        </header>

        {loading ? (
          <div className="mt-[50px] flex flex-col items-center gap-y-2">
            <Loader2 aria-hidden="true" className="size-6 animate-spin text-primary" />
            <p className="text-[0.75rem] font-bold text-primary">{t("draw.loading")}</p>
          </div>
        ) : error ? (
          <p
            className="mt-[50px] px-5 text-center text-[0.875rem] font-bold text-[#ff4355]"
            role="alert"
          >
            {error}
          </p>
        ) : table ? (
          <div className="flex grow flex-col gap-y-6 p-5">
            <WalletBalanceBar
              balance={wallet?.balance ?? null}
              currency={wallet?.currency ?? table.currency}
            />

            <div className="bg-[#1D1D1D] shadow-[0_2px_5px_0_rgba(0,0,0,0.1)]">
              <CurrentHallCard
                index={tableIndex < 0 ? 0 : tableIndex}
                lastRound={lastRound}
                roundSeconds={
                  round?.roundSeconds ?? lastRound?.roundSeconds ?? FALLBACK_ROUND_SECONDS
                }
                table={table}
              />
              <hr className="border-[#373737]" />
              <MatchInfoPanel
                betHistoryHref={`/bet/history?id=${encodeURIComponent(idParam)}&ref=${encodeURIComponent(selfHref)}`}
                drawHistoryHref={`/draw/history?id=${encodeURIComponent(idParam)}&ref=${encodeURIComponent(selfHref)}`}
                onExpired={() => void loadRound(table.id)}
                round={round}
              />
            </div>

            <BettorsTable
              bets={bets}
              labelFor={labelForBetType}
              roundSeq={round?.roundSeq ?? null}
            />

            <BetTabs
              tabs={[
                {
                  value: "mixing",
                  label: t("bet.mixing_tab"),
                  content: (
                    <>
                      <div className="mt-6 bg-primary py-1 text-center">
                        <span className="text-[1rem] font-semibold text-white">
                          {t("bet.mixing")}
                        </span>
                      </div>

                      <BetOptionGrid
                        disabled={bettingClosed}
                        onToggle={toggleOption}
                        options={options}
                        selected={selected}
                      />

                      {notice ? (
                        <p
                          aria-live="polite"
                          className="mt-3 text-center text-[0.75rem] font-bold text-primary"
                        >
                          {notice}
                        </p>
                      ) : null}

                      {bettingClosed && round ? (
                        <p className="mt-3 text-center text-[0.75rem] font-bold text-[#828282]">
                          {t("bet.betting_closed")}
                        </p>
                      ) : null}
                    </>
                  ),
                },
                {
                  value: "special-code",
                  label: t("bet.special_code_tab"),
                  content: (
                    <>
                      <div className="mt-6 bg-primary py-1 text-center">
                        <span className="text-[1rem] font-semibold text-white">
                          {t("bet.special_code")}
                        </span>
                      </div>

                      <NumberOddsGrid
                        disabled={bettingClosed}
                        numberOdds={odds?.numberOdds ?? []}
                        onToggle={toggleNumber}
                        selected={selectedNumbers}
                      />

                      {notice ? (
                        <p
                          aria-live="polite"
                          className="mt-3 text-center text-[0.75rem] font-bold text-primary"
                        >
                          {notice}
                        </p>
                      ) : null}

                      {bettingClosed && round ? (
                        <p className="mt-3 text-center text-[0.75rem] font-bold text-[#828282]">
                          {t("bet.betting_closed")}
                        </p>
                      ) : null}
                    </>
                  ),
                },
              ]}
            />

            {/* Chừa chỗ cho thanh đặt cược cố định, không thì nó che mất lưới cược.
                14rem là chiều cao thực của thanh (ô nhập + hai dòng tổng + nút), cộng
                thêm `--bottom-nav-total` vì thanh đặt cược giờ nằm TRÊN thanh điều hướng
                chứ không đè lên nó. Thiếu phần cộng này thì hàng cược cuối cùng vẫn
                bị khuất đúng 56px. */}
            <div
              aria-hidden="true"
              className="h-[calc(14rem+var(--bottom-nav-total))]"
            />
          </div>
        ) : null}
      </main>

      {table ? (
        <BetSubmitBar
          balance={wallet?.balance ?? null}
          closed={bettingClosed}
          currency={wallet?.currency ?? table.currency}
          maxBet={table.maxBet}
          minBet={table.minBet}
          onStakeChange={setStake}
          onSubmit={() => void submitBets()}
          selectedCount={totalSelections}
          selectedLabels={selectedLabels}
          stake={stake}
          submitting={submitting}
        />
      ) : null}

      {submitting ? <BetBlockingOverlay variant="submitting" /> : null}
      {preparingNextMatch ? <BetBlockingOverlay variant="next_match" /> : null}
    </PlayerLayout>
  );
}

/**
 * Bọc nội dung trong `Suspense`.
 *
 * `useSearchParams` đọc tham số URL, thứ chỉ biết được lúc có yêu cầu thật, nên Next
 * không dựng sẵn trang này ở bước build được và bắt buộc phải có một ranh giới
 * `Suspense`. Thiếu nó thì build thất bại với lỗi prerender.
 *
 * Nội dung chờ dùng chính vòng xoay của trang, nên người dùng không thấy giao diện nhảy
 * giữa hai kiểu chờ khác nhau.
 */
export default function BetDetailPage() {
  return (
    <Suspense
      fallback={
        <PlayerLayout>
          <main className="flex w-full grow flex-col bg-[#0d0d0d]">
            <div className="mt-[50px] flex flex-col items-center gap-y-2">
              <Loader2 aria-hidden="true" className="size-6 animate-spin text-primary" />
            </div>
          </main>
        </PlayerLayout>
      }
    >
      <BetDetailContent />
    </Suspense>
  );
}