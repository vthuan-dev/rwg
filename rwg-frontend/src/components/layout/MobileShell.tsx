"use client";

import React from "react";

/**
 * Khung mobile-first dùng chung cho MỌI trang khu người chơi.
 *
 * MỘT chỗ duy nhất giữ định nghĩa khung (chiều rộng tối đa, chiều cao tối thiểu,
 * vùng an toàn). Trước đây trang chủ và các trang xác thực mỗi bên tự khai báo
 * khung riêng với hai con số khác nhau (500px và 640px), nên chuyển giữa hai
 * trang thấy nội dung nhảy ngang.
 *
 * VÌ SAO `min-h-dvh` chứ không `min-h-screen`: `min-h-screen` dịch thành `100vh`,
 * mà trên Safari/Chrome iOS `100vh` tính theo chiều cao khi thanh địa chỉ ĐÃ thu
 * gọn. Hệ quả là phần cuối trang bị đẩy xuống dưới vùng nhìn thấy và trang có một
 * khoảng trượt vô nghĩa. `100dvh` co giãn theo vùng nhìn thấy thật.
 */
export interface MobileShellProps {
  children: React.ReactNode;
  /** Nội dung dán trên đầu (thanh tiêu đề). Tự sticky, không cần bọc thêm. */
  header?: React.ReactNode;
  /** Thanh điều hướng dưới. Truyền vào thì khung tự chừa chỗ cho nó. */
  bottomNav?: React.ReactNode;
  /**
   * `plain` nền đen thuần; `image` dùng ảnh nền `bg-ss2` của trang đăng nhập.
   */
  background?: "plain" | "image";
  /** Bật font IBM Plex Sans của trang gốc (dùng cho khu xác thực). */
  authFont?: boolean;
  className?: string;
}

export const MobileShell: React.FC<MobileShellProps> = ({
  children,
  header,
  bottomNav,
  background = "plain",
  authFont = false,
  className = "",
}) => {
  return (
    // Khối bọc ngoài LUÔN đen. Trang gốc chỉ đặt ảnh nền bên trong cột 640px, nên
    // trên màn hình rộng hai bên là nền đen thuần. Đặt ảnh ở đây sẽ làm ảnh trải
    // hết chiều ngang và khung mất hẳn cảm giác là một cột nội dung trên điện thoại.
    <div
      className={`mobile-shell w-full flex justify-center min-h-dvh bg-black ${
        authFont ? "font-auth" : ""
      }`}
    >
      <div
        className={`relative w-full sm:max-w-[640px] min-h-dvh flex flex-col ${
          background === "image" ? "bg-ss2" : "bg-[#0d0d0f]"
        } ${bottomNav ? "pb-bottom-nav" : ""} ${className}`}
      >
        {header ?? null}
        {children}
        {bottomNav ?? null}
      </div>
    </div>
  );
};
