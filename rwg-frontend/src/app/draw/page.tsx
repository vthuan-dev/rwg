"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { PlayerLayout } from "@/components/layout/PlayerLayout";
import { DrawHistoryHallCard } from "@/components/game/DrawHistoryHallCard";
import { useTranslation } from "@/context/LanguageContext";
import {
  ApiError,
  gameTables,
  getPlayerToken,
  roundsHistory,
  type GameRound,
  type GameTable,
} from "@/lib/playerApi";

/**
 * Số ván hiện trong mỗi thẻ sảnh. Bản gốc hiện đúng 4 dòng.
 *
 * Đây cũng là `size` gửi cho API, không phải cắt ở phía client: kéo về 20 ván rồi bỏ 16
 * là tốn băng thông cho dữ liệu không bao giờ được vẽ.
 */
const ROUNDS_PER_HALL = 4;

/**
 * Trang lịch sử quay số — danh sách sảnh, mỗi sảnh kèm 4 ván gần nhất.
 *
 * Đường dẫn `/draw` theo đúng bản gốc, và đây là mục thứ ba của thanh điều hướng dưới.
 * KHÁC trang `/bet`: trang đó hiện ván ĐANG chạy kèm đồng hồ đếm ngược để vào cược, còn
 * trang này chỉ tra kết quả đã xong. Bản gốc cũng có cả hai đường dẫn riêng biệt.
 *
 * SỐ LỜI GỌI — chỗ này lệch so với bản gốc:
 * Bản gốc gọi MỘT lượt `/platforms/home-latest-result`, trả sẵn mọi sảnh kèm kết quả và
 * cả tổng/Nhỏ-Lớn/Chẵn-Lẻ đã tính phía server. Backend mình chưa có endpoint gộp như vậy,
 * nên phải 1 lượt lấy danh sách bàn rồi N lượt lấy lịch sử từng bàn — với 6 bàn là 7
 * request. Gọi SONG SONG bằng `Promise.allSettled` để tổng thời gian bằng lượt chậm nhất
 * chứ không phải tổng tất cả, và một bàn lỗi thì các bàn còn lại vẫn hiện được.
 *
 * BẮT BUỘC ĐĂNG NHẬP: cả `/games/tables` và `/games/tables/{id}/rounds` đều nằm sau
 * `bearerAuth`. Kiểm token TRƯỚC khi gọi, không thì người dùng chỉ thấy một thông báo lỗi
 * vô nghĩa thay vì được đưa tới trang đăng nhập.
 */
export default function DrawHistoryListPage() {
  const router = useRouter();
  const { t } = useTranslation();

  const [tables, setTables] = useState<GameTable[]>([]);
  const [roundsByTable, setRoundsByTable] = useState<Record<string, GameRound[]>>({});
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
   * Tải lịch sử của một bàn.
   *
   * Lỗi ở một bàn KHÔNG được làm cả trang thất bại: đặt mảng rỗng để thẻ hiện "chưa có
   * dữ liệu" thay vì biến mất khỏi danh sách.
   */
  const loadRounds = useCallback(async (tableId: string) => {
    try {
      const data = await roundsHistory(tableId, 0, ROUNDS_PER_HALL);
      if (aliveRef.current) {
        setRoundsByTable((prev) => ({ ...prev, [tableId]: data.content }));
      }
    } catch {
      if (aliveRef.current) {
        setRoundsByTable((prev) => ({ ...prev, [tableId]: [] }));
      }
    }
  }, []);

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
        setLoading(false);

        await Promise.allSettled(list.map((table) => loadRounds(table.id)));
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
  }, [loadRounds, router, t]);

  return (
    <PlayerLayout>
      <main className="flex w-full grow flex-col bg-[#0d0d0d]">
        <header className="flex h-15 items-center px-5">
          <h1 className="text-[1.25rem] leading-[1.75rem] text-[#d0d5da]">
            {t("draw.history_title")}
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
              <DrawHistoryHallCard
                index={index}
                key={table.id}
                rounds={roundsByTable[table.id] ?? []}
                table={table}
              />
            ))
          )}
        </div>
      </main>
    </PlayerLayout>
  );
}
