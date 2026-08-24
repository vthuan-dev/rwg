"use client";

import React, { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AssetHistoryShell } from "@/components/asset/AssetHistoryShell";
import { TransactionCard, type TransactionStatus } from "@/components/asset/TransactionCard";
import { useTranslation } from "@/context/LanguageContext";
import {
  ApiError,
  getPlayerToken,
  paymentOrders,
  type PaymentOrder,
} from "@/lib/playerApi";

const PAGE_SIZE = 20;

/**
 * Ánh xạ trạng thái lệnh sang màu viên trạng thái của thẻ.
 *
 * Ba màu của bản gốc không đủ cho năm trạng thái backend, nên gộp theo Ý NGHĨA với người
 * chơi chứ không theo tên trạng thái:
 * - `SUCCESS` (nạp xong) và `SETTLED` (rút đã chuyển) → xanh: tiền đã tới đích.
 * - `FAILED` (cổng từ chối) và `VOIDED` (admin từ chối, đã hoàn tiền) → đỏ: không thành.
 * - `PENDING` → xanh dương: còn treo.
 *
 * Trạng thái lạ trả về `undefined` để thẻ không vẽ viên nào, thay vì tô một màu đoán bừa.
 */
const STATUS_TONE: Record<string, TransactionStatus> = {
  SUCCESS: "approved",
  SETTLED: "approved",
  FAILED: "rejected",
  VOIDED: "rejected",
  PENDING: "pending",
};

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

export interface PaymentOrderHistoryProps {
  /** Lọc Ở SERVER qua `?type=`, không lọc sau khi lấy về. */
  type: "DEPOSIT" | "WITHDRAWAL";
  title: string;
  backHref: string;
  /** Nhãn tiêu đề mỗi thẻ, ví dụ "Nạp tiền". */
  itemTitle: string;
  /**
   * True thì số tiền hiện dấu trừ.
   *
   * Lệnh rút làm giảm số dư nên hiện dấu trừ, dù `amount` trong dữ liệu là số dương —
   * backend lưu độ lớn của lệnh, chiều tiền nằm ở `type`.
   */
  negative: boolean;
}

/**
 * Thân dùng chung của hai trang lịch sử nạp và lịch sử rút.
 *
 * Hai trang chỉ khác nhau ba thứ: loại lệnh lọc, tiêu đề, và chiều dấu số tiền. Viết hai
 * bản gần giống nhau thì mỗi lần sửa cách hiện trạng thái phải nhớ sửa cả hai nơi.
 */
export const PaymentOrderHistory: React.FC<PaymentOrderHistoryProps> = ({
  type,
  title,
  backHref,
  itemTitle,
  negative,
}) => {
  const router = useRouter();
  const { t } = useTranslation();

  const [items, setItems] = useState<PaymentOrder[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchPage = useCallback(
    async (p: number): Promise<{ items: PaymentOrder[]; totalPages: number } | null> => {
      try {
        const data = await paymentOrders(p, PAGE_SIZE, type);
        setError("");
        // Trang rỗng khiến backend trả totalPages = 0; kẹp lên 1 để không hiện "Trang 1/0".
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
    [router, t, type]
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

  /** Nhãn trạng thái; trạng thái lạ hiện nguyên mã thay vì để trống. */
  const statusLabel = (status: string): string => {
    const key = `asset.status_${status.toLowerCase()}`;
    const label = t(key);
    return label === key ? status : label;
  };

  return (
    <AssetHistoryShell
      backHref={backHref}
      empty={items.length === 0}
      error={error}
      loading={loading}
      onPageChange={changePage}
      page={page}
      title={title}
      totalPages={totalPages}
    >
      {items.map((order) => (
        <TransactionCard
          amount={negative ? `-${order.amount}` : order.amount}
          date={formatTime(order.createdAt)}
          key={order.id}
          rows={[
            {
              label: t("asset.order_no"),
              // Tám ký tự đầu của UUID là đủ để người chơi đối chiếu khi nhắn cho hỗ trợ,
              // và ngắn gọn hơn nhiều so với cả 36 ký tự trên màn hình điện thoại.
              value: order.id.slice(0, 8).toUpperCase(),
            },
          ]}
          status={STATUS_TONE[order.status]}
          statusLabel={STATUS_TONE[order.status] ? statusLabel(order.status) : undefined}
          title={itemTitle}
        />
      ))}
    </AssetHistoryShell>
  );
};
