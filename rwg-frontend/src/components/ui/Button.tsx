"use client";

import React from "react";

/**
 * Nút chính của khu xác thực.
 *
 * Kích thước sao lại đúng biến thể `md` của trang gốc: rộng hết dòng, cao
 * `h-11`, KHÔNG bo góc, chữ `1rem` in đậm.
 */
export const Button: React.FC<
  React.ButtonHTMLAttributes<HTMLButtonElement> & {
    loading?: boolean;
    /**
     * React 19 cho phép nhận `ref` như một prop thường ở function component
     * (không cần forwardRef nữa), nhưng vẫn phải khai báo trong kiểu để
     * TypeScript chấp nhận.
     */
    ref?: React.Ref<HTMLButtonElement>;
  }
> = ({ children, className = "", loading = false, disabled, ...rest }) => {
  return (
    <button
      className={`inline-flex justify-center items-center gap-2 w-full h-11 rounded-none bg-primary text-[1rem] text-white font-bold leading-normal shadow-lg transition-all hover:brightness-110 active:brightness-95 disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white ${className}`}
      // Đang gửi thì phải chặn bấm lần hai: người dùng bấm đôi vào nút tạo tài
      // khoản sẽ gửi hai lần đăng ký, lần sau nhận lỗi trùng tên và hiện hộp
      // thoại lỗi ngay sau khi vừa tạo thành công.
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? (
        <span
          className="size-4 border-2 border-white/40 border-t-white rounded-full animate-spin"
          aria-hidden="true"
        />
      ) : null}
      {children}
    </button>
  );
};
