"use client";

import React, { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { getPlayerToken, isGuestSupportOnly } from "@/lib/playerApi";

/**
 * Cổng kiểm tra đăng nhập cho TOÀN BỘ khu người chơi.
 *
 * VÌ SAO PHẢI TẬP TRUNG Ở MỘT CHỖ: trước đây mỗi trang tự gọi `getPlayerToken()`
 * trong `useEffect` của riêng nó. Cách đó bỏ sót ngay khi thêm trang mới — trang chủ
 * `/` chưa từng có bước kiểm này, nên khách chưa đăng nhập vẫn xem được banner, ô
 * nạp/rút và lưới trò chơi. Đặt cổng ở layout gốc thì MỌI đường dẫn đều đi qua đây,
 * kể cả các trang thêm về sau.
 *
 * KHÔNG dùng middleware của Next: token nằm trong `localStorage` (xem `storeTokens`),
 * mà middleware chạy trên server nên không đọc được. Chuyển token sang cookie chỉ để
 * middleware đọc được sẽ mở thêm bề mặt CSRF cho toàn bộ API.
 */

/** Các đường dẫn khách CHƯA đăng nhập được phép mở. */
const PUBLIC_PATHS = ["/login", "/new-account"];

/** Khu quản trị có phiên đăng nhập riêng (`rwg_admin_token`), không dùng cổng này. */
const ADMIN_PREFIX = "/admin";

/** Đích duy nhất của phiên hỗ trợ khách quên mật khẩu. */
const GUEST_SUPPORT_PATH = "/profile/contact-us";

function isPublicPath(pathname: string): boolean {
  return PUBLIC_PATHS.some(
    (path) => pathname === path || pathname.startsWith(`${path}/`)
  );
}

export const AuthGate: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const pathname = usePathname() ?? "/";
  const router = useRouter();

  const openToGuests = pathname.startsWith(ADMIN_PREFIX) || isPublicPath(pathname);

  /**
   * `null` = chưa kiểm xong. Phân biệt với `false` để KHÔNG nháy hiện nội dung trang
   * trong khoảnh khắc trước khi chuyển hướng: lần render đầu chạy trên server, ở đó
   * không có `localStorage` nên không thể biết trạng thái đăng nhập.
   */
  const [allowed, setAllowed] = useState<boolean | null>(openToGuests ? true : null);

  useEffect(() => {
    /** Kiểm quyền vào đường dẫn hiện tại; chuyển hướng nếu không được phép. */
    const check = () => {
      if (openToGuests) {
        setAllowed(true);
        return;
      }

      if (!getPlayerToken()) {
        setAllowed(false);
        router.replace("/login");
        return;
      }

      // Phiên hỗ trợ (khách chỉ nhập tên đăng nhập, KHÔNG nhập mật khẩu) chỉ được ở
      // đúng khung chat — mọi trang khác của người chơi đều bị đẩy về đó.
      if (isGuestSupportOnly() && pathname !== GUEST_SUPPORT_PATH) {
        setAllowed(false);
        router.replace(GUEST_SUPPORT_PATH);
        return;
      }

      setAllowed(true);
    };

    check();

    // Đăng xuất ở tab này (`clearPlayerTokens` phát `auth_changed`) hoặc ở tab khác
    // (`storage`) đều phải đẩy người dùng ra khỏi trang cần đăng nhập ngay, chứ không
    // đợi tới lần điều hướng kế tiếp.
    window.addEventListener("auth_changed", check);
    window.addEventListener("storage", check);
    return () => {
      window.removeEventListener("auth_changed", check);
      window.removeEventListener("storage", check);
    };
  }, [openToGuests, pathname, router]);

  if (allowed !== true) {
    return (
      <div
        className="flex min-h-dvh w-full items-center justify-center bg-[#070709]"
        role="status"
        aria-live="polite"
      >
        <Loader2 aria-hidden="true" className="size-6 animate-spin text-primary" />
      </div>
    );
  }

  return <>{children}</>;
};
