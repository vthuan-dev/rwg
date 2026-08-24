"use client";

import React from "react";
import Link from "next/link";

/**
 * Một dòng trong danh sách tuỳ chọn của trang hồ sơ.
 *
 * CẤU TRÚC LẤY TỪ BUNDLE CỦA TRANG GỐC (module `1141` trong
 * `pages/profile-663e3d4a0f7a413c.js`), không phải phỏng theo ảnh chụp. Ba chi tiết dưới
 * đây nhìn ảnh sẽ làm sai:
 *
 * 1. Đường kẻ chỉ ở ĐÁY (`border-b`), màu `#1f1f1f`. Không có kẻ trên, nên hai dòng liền
 *    nhau chỉ có MỘT vạch giữa chúng.
 * 2. Icon trái cách chữ bằng `ms-5` đặt trên KHỐI CHỮ, không phải `gap-x` trên hàng —
 *    khác nhau ở chỗ mũi nhọn phải được `ms-auto` đẩy ra mép mà không chịu ảnh hưởng của
 *    gap.
 * 3. Cả icon trái và mũi nhọn phải đều màu `primary`, cỡ `1.25rem`.
 *
 * `href` NHẬN CẢ HÀM: bản gốc dùng đúng component này cho cả dòng điều hướng
 * (`/profile/invitation`) và dòng mở hộp thoại chọn ngôn ngữ. Truyền hàm thì render thành
 * `<button>` chứ không phải `<a>` — dùng `<a href="#">` cho một hành động sẽ làm thay đổi
 * URL và trình đọc màn hình đọc sai là liên kết.
 */
export interface ProfileMenuRowProps {
  /** Đường dẫn để điều hướng, HOẶC hàm xử lý khi bấm. */
  href: string | (() => void);
  /** Tên lớp icon icomoon, ví dụ `icon-icon17`. */
  icon: string;
  title: string;
  subtitle: string;
  /** Badge hiển thị thêm (ví dụ số lượng tin chưa đọc) */
  badge?: React.ReactNode;
}

/** Phần bên trong, dùng chung cho cả hai nhánh `<Link>` và `<button>`. */
const RowContent: React.FC<Omit<ProfileMenuRowProps, "href">> = ({
  icon,
  title,
  subtitle,
  badge,
}) => (
  <>
    <i aria-hidden="true" className={`${icon} size-5 text-[1.25rem] text-primary`} />
    <div className="ms-5 flex flex-col gap-y-1 text-start">
      <div className="text-[0.8125rem] text-white flex items-center gap-x-1.5">
        <span>{title}</span>
        {badge}
      </div>
      <div className="text-[0.6875rem] text-[#83888c]">{subtitle}</div>
    </div>
    <i
      aria-hidden="true"
      className="ms-auto icon-icon77 size-5 text-[1.25rem] text-primary"
    />
  </>
);

const ROW_CLASS =
  "px-5 py-4 flex items-center border-b border-b-[#1f1f1f] transition-colors hover:bg-[#1a1a1a]/40 active:bg-[#1a1a1a]/60";

export const ProfileMenuRow: React.FC<ProfileMenuRowProps> = ({
  href,
  icon,
  title,
  subtitle,
  badge,
}) => {
  if (typeof href === "function") {
    return (
      // `w-full` vì <button> không tự trải hết chiều ngang như <a> ở đây, thiếu nó thì
      // vùng bấm chỉ bằng bề rộng nội dung và mũi nhọn phải không còn ở mép.
      <button className={`w-full ${ROW_CLASS}`} onClick={href} type="button">
        <RowContent icon={icon} subtitle={subtitle} title={title} badge={badge} />
      </button>
    );
  }

  return (
    <Link className={ROW_CLASS} href={href}>
      <RowContent icon={icon} subtitle={subtitle} title={title} badge={badge} />
    </Link>
  );
};
