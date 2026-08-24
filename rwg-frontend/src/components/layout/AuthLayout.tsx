"use client";

import React from "react";
import { MobileShell } from "@/components/layout/MobileShell";

/**
 * Khung của các trang xác thực (đăng nhập / tạo tài khoản).
 *
 * Chỉ là lớp mỏng trên `MobileShell` với hai khác biệt so với khu người chơi:
 * KHÔNG có thanh điều hướng dưới (người chưa đăng nhập không có gì để điều hướng)
 * và bật font IBM Plex Sans của trang gốc.
 */
export const AuthLayout: React.FC<{
  children: React.ReactNode;
  /**
   * `login` dùng ảnh nền toà nhà; `plain` dùng nền đen thuần như trang tạo tài
   * khoản của bản gốc.
   */
  variant?: "login" | "plain";
  /** Thanh tiêu đề dán trên (trang tạo tài khoản có, trang đăng nhập không). */
  header?: React.ReactNode;
}> = ({ children, variant = "plain", header }) => {
  return (
    <MobileShell
      authFont
      background={variant === "login" ? "image" : "plain"}
      header={header}
    >
      {children}
    </MobileShell>
  );
};
