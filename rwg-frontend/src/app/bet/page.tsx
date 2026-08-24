"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { PlayerLayout } from "@/components/layout/PlayerLayout";
import { GameHallCard } from "@/components/games/GameHallCard";
import { useTranslation } from "@/context/LanguageContext";
import {
  ApiError,
  currentRound,
  gameTables,
  getPlayerToken,
  type GameRound,
  type GameTable,
} from "@/lib/playerApi";

/**
 * Danh sách bàn chơi kèm kết quả vòng mới nhất.
 *
 * Đường dẫn `/bet` theo đúng trang gốc. Trang gốc còn có `?platform=` để lọc theo nhà
 * cung cấp; backend RWG chưa có khái niệm đó nên tham số bị bỏ qua, và trang luôn hiện
 * mọi bàn đang mở.
 *
 * BẮT BUỘC ĐĂNG NHẬP: cả `/games/tables` và `/rounds/current` đều nằm sau
 * `@SecurityRequirement(name = "bearerAuth")` ở `GameController`. Khác trang chủ vốn
 * xem được mà không cần tài khoản.
 *
 * SỐ LỜI GỌI: backend chưa có endpoint tổng hợp (trang gốc có
 * `/platforms/latest-result-v2` trả cả danh sách kèm kết quả trong một lượt), nên
 * phải gọi 1 lần lấy danh sách rồi N lần lấy vòng — với 6 bàn là 7 request. Gọi
 * SONG SONG bằng `Promise.allSettled` để tổng thời gian bằng lời gọi chậm nhất chứ
 * không phải tổng tất cả. Chặng sau nên thay bằng WebSocket: backend đã có sẵn
 * `/topic/game/table/{tableId}` phát sự kiện đổi pha và kết quả.
 */
export default function BetListPage() {
  const router = useRouter();
  const { t } = useTranslation();

  const [tables, setTables] = useState<GameTable[]>([]);
  const [rounds, setRounds] = useState<Record<string, GameRound | null>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /** Chặn setState sau khi component đã rời khỏi cây DOM. */
  const aliveRef = useRef(true);
  useEffect(() => {
    aliveRef.current = true;
    return () => {
      aliveRef.current = false;
    };
  }, []);

  /**
   * Tải vòng hiện tại của một bàn.
   *
   * 404 `ROUND_NOT_FOUND` là chuyện BÌNH THƯỜNG, không phải lỗi: giữa hai vòng có
   * một khoảnh khắc vòng cũ đã đóng mà vòng mới chưa kịp tạo. Lúc đó đặt `null` và
   * thẻ hiện dấu gạch, chứ không hiện thông báo lỗi.
   */
  const loadRound = useCallback(async (tableId: string) => {
    try {
      const round = await currentRound(tableId);
      if (aliveRef.current) {
        setRounds((prev) => ({ ...prev, [tableId]: round }));
      }
    } catch {
      if (aliveRef.current) {
        setRounds((prev) => ({ ...prev, [tableId]: null }));
      }
    }
  }, []);

  useEffect(() => {
    // Kiểm token TRƯỚC khi gọi API: không có token thì mọi lời gọi đều 401 và người
    // dùng chỉ thấy một thông báo lỗi vô nghĩa thay vì được đưa tới trang đăng nhập.
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
        setLoading(false);

        // Song song, và `allSettled` chứ không `all`: một bàn lỗi thì các bàn còn
        // lại vẫn phải hiện được.
        await Promise.allSettled(list.map((table) => loadRound(table.id)));
      } catch (err) {
        if (cancelled || !aliveRef.current) return;
        if (err instanceof ApiError && err.status === 401) {
          router.replace("/login");
          return;
        }
        setError(t("draw.load_failed"));
        setLoading(false);
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [loadRound, router, t]);

  return (
    <PlayerLayout>
      <main className="flex w-full grow flex-col bg-[#0d0d0d]">
        <header className="flex h-15 items-center px-5">
          <h1 className="text-[1.25rem] leading-[1.75rem] text-[#d0d5da]">
            {t("draw.title")}
          </h1>
        </header>

        <div className="flex grow flex-col gap-y-4 bg-ss2 px-5 py-6">
          {loading ? (
            <div className="mt-[50px] flex flex-col items-center gap-y-2">
              <Loader2 aria-hidden="true" className="size-6 animate-spin text-primary" />
              <p className="text-[0.75rem] font-bold leading-normal text-primary">
                {t("draw.loading")}
              </p>
            </div>
          ) : error ? (
            <p
              className="mt-[50px] text-center text-[0.75rem] font-bold text-[#ff4355]"
              role="alert"
            >
              {error}
            </p>
          ) : tables.length === 0 ? (
            <p className="mt-[50px] text-center text-[0.75rem] font-bold text-[#828282]">
              {t("draw.no_records")}
            </p>
          ) : (
            tables.map((table, index) => (
              <GameHallCard
                index={index}
                key={table.id}
                onRoundExpired={loadRound}
                round={rounds[table.id] ?? null}
                table={table}
              />
            ))
          )}
        </div>
      </main>
    </PlayerLayout>
  );
}
