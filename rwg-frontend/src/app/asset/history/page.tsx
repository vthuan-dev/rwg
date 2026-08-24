"use client";

import React, { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AssetHistoryShell } from "@/components/asset/AssetHistoryShell";
import { TransactionCard } from "@/components/asset/TransactionCard";
import { useTranslation } from "@/context/LanguageContext";
import {
  ApiError,
  getPlayerToken,
  walletTransactions,
  type WalletTransaction,
} from "@/lib/playerApi";

const PAGE_SIZE = 20;

/**
 * Chuỗi tiền chỉ gồm số 0 và dấu chấm nghĩa là dòng này không có giá trị.
 *
 * Mỗi bút toán chỉ điền MỘT trong hai cột `debit`/`credit`, cột còn lại là "0.00000000".
 * Kiểm bằng biểu thức chính quy trên chuỗi thay vì `Number(x) === 0`: với scale 8 của
 * backend, số lớn qua `Number` bị làm tròn âm thầm.
 */
const isZero = (raw: string | null | undefined): boolean =>
  !raw || /^0*(\.0*)?$/.test(raw);

/** Giữ theo định dạng của bản gốc: 22-08-2026 5:11 PM. */
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
 * Lịch sử giao dịch ví — MỌI bút toán, không chỉ nạp/rút.
 *
 * Dùng `/wallet/me/transactions` (sổ cái ví) chứ không `/wallet/me/orders`: trang này phải
 * thấy cả cược, tiền thắng, hoa hồng và điều chỉnh của quản trị. Hai trang lịch sử nạp và
 * rút riêng mới dùng danh sách lệnh.
 */
export default function AssetHistoryPage() {
  const router = useRouter();
  const { t } = useTranslation();

  const [items, setItems] = useState<WalletTransaction[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchPage = useCallback(
    async (
      p: number
    ): Promise<{ items: WalletTransaction[]; totalPages: number } | null> => {
      try {
        const data = await walletTransactions(p, PAGE_SIZE);
        setError("");
        // Trang rỗng khiến backend trả totalPages = 0; kẹp lên 1 để phân trang không
        // hiện "Trang 1/0".
        return { items: data.content, totalPages: Math.max(1, data.totalPages) };
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) {
          router.replace("/login");
          return null;
        }
        setError(err instanceof ApiError ? err.message : t("asset.load_failed"));
        return null;
      }
    },
    [router, t]
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
        setItems(result.items);
        setTotalPages(result.totalPages);
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

  /**
   * Nhãn loại bút toán.
   *
   * ADJUSTMENT tách thành hai nhãn theo chiều tiền: một dòng "Điều chỉnh số dư" không
   * nói cho người chơi biết họ được cộng hay bị trừ. Các loại còn lại đã tự rõ chiều
   * (BET luôn trừ, WIN luôn cộng...) nên không cần tách.
   *
   * Loại lạ hiện NGUYÊN MÃ thay vì để trống: backend có thể thêm giá trị vào
   * `WalletRefType` bất cứ lúc nào, và một dòng tiền không có nhãn còn tệ hơn một dòng
   * hiện mã kỹ thuật.
   */
  const typeLabel = (refType: string, isDebit: boolean): string => {
    const base = refType.toLowerCase();
    const key =
      base === "adjustment"
        ? `asset.trx_adjustment_${isDebit ? "debit" : "credit"}`
        : `asset.trx_${base}`;
    const label = t(key);
    return label === key ? refType : label;
  };

  return (
    <AssetHistoryShell
      backHref="/profile"
      empty={items.length === 0}
      error={error}
      loading={loading}
      onPageChange={changePage}
      page={page}
      title={t("asset.transaction_history")}
      totalPages={totalPages}
    >
      {items.map((tx) => {
        // Dòng ghi nợ hiện dấu trừ. Chỉ một trong hai cột có giá trị nên chọn cột nào
        // khác 0; trường hợp cả hai đều 0 (không nên xảy ra) coi như ghi có 0.
        const debit = !isZero(tx.debit);
        const amount = debit ? `-${tx.debit}` : tx.credit;

        return (
          <TransactionCard
            amount={amount}
            date={formatTime(tx.createdAt)}
            key={tx.id}
            rows={[
              {
                label: t("asset.balance_after"),
                value: tx.balanceAfter,
              },
            ]}
            title={typeLabel(tx.refType, debit)}
          />
        );
      })}
    </AssetHistoryShell>
  );
}
