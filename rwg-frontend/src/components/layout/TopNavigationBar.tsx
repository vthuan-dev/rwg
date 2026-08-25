"use client";

import React from "react";
import Link from "next/link";

/**
 * Thanh tiêu đề trên cùng, dựng theo đúng thanh của trang gốc: sticky, cao
 * `h-15`, nền `#0d0d0d`, nút quay lại màu nhấn và tiêu đề lệch phải `ms-7`.
 *
 * KHÔNG tự giới hạn chiều rộng: `MobileShell` đã giới hạn `sm:max-w-[640px]` ở
 * khối cha. Đặt thêm `max-w` ở đây sẽ khiến nền của thanh hẹp hơn nội dung trên
 * màn hình rộng, tạo hai mép hở hai bên.
 *
 * Vùng bấm của nút quay lại được nới lên 44px (`size-11`) dù icon chỉ 20px: đây
 * là mức tối thiểu để bấm bằng ngón tay theo hướng dẫn của cả Apple và Google.
 * Icon vẫn hiển thị đúng kích thước gốc, chỉ vùng nhận cú bấm rộng ra.
 */
export const TopNavigationBar: React.FC<{
  title: string;
  /** Có đường dẫn thì hiện nút quay lại; không có thì tiêu đề nằm sát lề. */
  backHref?: string;
  /**
   * Hành động chạy khi bấm quay lại, dùng khi việc rời trang cần làm thêm gì đó
   * trước — ví dụ trang chat CSKH phải KẾT THÚC phiên hỗ trợ, không thì cổng
   * `AuthGate` đẩy người dùng trở lại ngay và họ mắc kẹt.
   *
   * Có `onBack` thì render `<button>`, ngược lại render `<Link>`. CỐ TÌNH dùng
   * đúng thẻ theo từng việc thay vì gắn `onClick` lên `<Link>`: bấm giữa chuột
   * hoặc "mở trong tab mới" trên một `<Link>` BỎ QUA `onClick` và đi thẳng tới
   * href — tức là rời trang mà việc dọn dẹp chưa chạy.
   *
   * Ưu tiên hơn `backHref` khi cả hai được truyền.
   */
  onBack?: () => void;
  rightSlot?: React.ReactNode;
}> = ({ title, backHref, onBack, rightSlot }) => {
  // -ms-3 để icon vẫn thẳng lề 20px như bản gốc dù vùng bấm rộng 44px.
  const backClassName = "-ms-3 flex items-center justify-center size-11 shrink-0";
  const backIcon = (
    <i aria-hidden="true" className="icon-icon76 size-5 text-[1.25rem] text-primary" />
  );

  return (
    <header className="sticky top-0 z-10 flex items-center w-full h-15 px-5 bg-[#0d0d0d]">
      {onBack ? (
        <button
          type="button"
          onClick={onBack}
          className={backClassName}
          aria-label={title}
        >
          {backIcon}
        </button>
      ) : backHref ? (
        <Link href={backHref} className={backClassName} aria-label={title}>
          {backIcon}
        </Link>
      ) : null}
      <h1
        className={`${
          onBack || backHref ? "ms-4" : ""
        } text-[1.25rem] text-[#d0d5da] leading-[1.75rem] font-normal truncate`}
      >
        {title}
      </h1>
      {rightSlot ? <div className="ms-auto">{rightSlot}</div> : null}
    </header>
  );
};
