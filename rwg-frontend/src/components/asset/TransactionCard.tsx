"use client";

import React from "react";
import { formatMoney } from "@/lib/money";

/**
 * Thẻ một dòng lịch sử, dùng chung cho ba trang tài sản.
 *
 * CẤU TRÚC LẤY TỪ BUNDLE BẢN GỐC (`asset/history`, `asset/deposit/history`,
 * `asset/withdraw/history`). Bản gốc viết ba component riêng gần giống nhau; gộp lại một
 * chỗ vì khác biệt duy nhất là CÓ hay KHÔNG phần thân dưới và viên trạng thái.
 *
 * Các chi tiết nhìn ảnh sẽ làm sai:
 * 1. Nền `#1f1f1f`, KHÔNG bo góc.
 * 2. Vạch chia giữa hai phần là một `<div className="h-px bg-[#373737]">` — KHÔNG phải
 *    `border`. Màu này SÁNG HƠN nền thẻ, khác với vạch `#1f1f1f` ở danh sách menu.
 * 3. Số tiền luôn có dấu `+` hoặc `-` rồi MỘT dấu cách trước con số.
 * 4. Ba màu trạng thái đều là nền mờ 10% cùng tông với chữ:
 *    duyệt `#01BD8B`, từ chối `#FE1616`, chờ `#007AFF`.
 * 5. Viên trạng thái chỉ có `px-3`, KHÔNG có padding dọc và KHÔNG bo góc.
 */
export type TransactionStatus = "approved" | "rejected" | "pending";

const STATUS_STYLE: Record<TransactionStatus, string> = {
  approved: "bg-[rgba(1,189,139,0.1)] text-[#01BD8B]",
  rejected: "bg-[rgba(255,22,22,0.1)] text-[#FE1616]",
  pending: "bg-[rgba(0,122,255,0.1)] text-[#007AFF]",
};

export interface TransactionCardProps {
  /** Nhãn loại giao dịch, đã dịch. */
  title: string;
  /**
   * Số tiền dạng CHUỖI thập phân, có thể mang dấu trừ.
   *
   * Là chuỗi chứ không phải số vì backend dùng BigDecimal scale 8: đọc qua `Number` làm
   * mất chính xác ở số lớn. Dấu được suy ra từ chuỗi, không qua phép so sánh số.
   */
  amount: string;
  /** Thời gian đã định dạng sẵn. */
  date: string;
  /** Viên trạng thái góc phải hàng dưới. Bỏ trống thì không hiện. */
  status?: TransactionStatus;
  /** Nhãn trạng thái, đã dịch. Bắt buộc khi có `status`. */
  statusLabel?: string;
  /**
   * Các dòng ở phần thân dưới, dạng nhãn-giá trị.
   *
   * Rỗng thì KHÔNG vẽ cả phần dưới lẫn vạch chia — trang lịch sử nạp tiền của bản gốc chỉ
   * có một phần, còn hai trang kia có hai phần.
   */
  rows?: { label: string; value: string }[];
}

export const TransactionCard: React.FC<TransactionCardProps> = ({
  title,
  amount,
  date,
  status,
  statusLabel,
  rows = [],
}) => {
  // Suy ra dấu từ CHUỖI, không dùng `Number(amount) > 0`. Với scale 8 của backend, một số
  // vượt Number.MAX_SAFE_INTEGER sẽ bị làm tròn âm thầm và dấu hiện ra sai.
  const negative = amount.trim().startsWith("-");
  const magnitude = amount.trim().replace(/^[-+]/, "");

  return (
    <div className="flex flex-col bg-[#1f1f1f]">
      <div className="flex flex-col gap-y-0.5 px-5 py-4">
        <div className="flex items-center justify-between">
          <span className="text-[1rem] font-normal leading-[1.35rem] text-[#d0d5da]">
            {title}
          </span>
          <span className="text-[0.875rem] font-medium leading-[1.35rem] text-[#d0d5da]">
            {negative ? "-" : "+"} {formatMoney(magnitude)} USD
          </span>
        </div>

        <div className="flex items-center justify-between">
          <span className="text-[0.6875rem] font-normal leading-[1.1813rem] text-[#8b8b8b]">
            {date}
          </span>
          {status && statusLabel ? (
            <div
              className={`px-3 text-[0.75rem] font-bold leading-normal ${STATUS_STYLE[status]}`}
            >
              {statusLabel}
            </div>
          ) : null}
        </div>
      </div>

      {rows.length > 0 ? (
        <>
          <div className="h-px bg-[#373737]" />
          <div className="flex flex-col gap-y-2 px-5 py-4">
            {rows.map((row) => (
              <div className="flex items-center justify-between" key={row.label}>
                <span className="text-[0.6875rem] font-normal leading-normal text-[#8b8b8b]">
                  {row.label}
                </span>
                <span className="text-[0.75rem] font-medium leading-normal text-white">
                  {row.value}
                </span>
              </div>
            ))}
          </div>
        </>
      ) : null}
    </div>
  );
};
