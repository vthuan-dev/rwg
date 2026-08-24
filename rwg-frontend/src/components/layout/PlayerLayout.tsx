"use client";

import React from "react";
import { MobileShell } from "@/components/layout/MobileShell";
import { BottomNav } from "@/components/layout/BottomNav";

/**
 * Khung các trang khu người chơi đã đăng nhập: dùng chung `MobileShell` với hai
 * trang xác thực, cộng thêm thanh điều hướng dưới.
 *
 * Khung tự chừa chỗ cho thanh dưới qua `.pb-bottom-nav` (xem MobileShell), nên
 * các trang con KHÔNG cần tự thêm padding đáy.
 *
 * `header` chuyển thẳng xuống `MobileShell` để thanh tiêu đề nằm NGOÀI vùng nội dung
 * cuộn. Đặt thanh bên trong `children` thì `sticky top-0` của nó dính theo vùng cuộn của
 * trang chứ không theo khung, nên khi cuộn thanh sẽ trôi lên mất.
 */
export const PlayerLayout: React.FC<{
  children: React.ReactNode;
  header?: React.ReactNode;
}> = ({ children, header }) => {
  return (
    <MobileShell bottomNav={<BottomNav />} header={header}>
      {children}
    </MobileShell>
  );
};
