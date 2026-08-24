"use client";

import React from "react";

/**
 * Dòng gợi ý điều kiện của mật khẩu, đổi màu khi giá trị đã thoả.
 *
 * Sao lại dòng gợi ý của trang gốc: icon tròn có dấu tick, `#B2B2B2` khi chưa
 * đạt và `#FF4355` khi đã đạt, chữ `0.6875rem` màu `#707070`.
 */
export const PasswordRule: React.FC<{
  /** Điều kiện cần thoả, ví dụ `/^.{8,}$/` hoặc `/^\d{6}$/`. */
  pattern: RegExp;
  value: string;
  label: string;
}> = ({ pattern, value, label }) => {
  const satisfied = pattern.test(value);

  return (
    <div className="flex items-center gap-x-2.5">
      {/* SVG nội tuyến thay vì font icon: icon này phải ĐỔI MÀU theo trạng thái,
          mà tô màu qua thuộc tính fill thì rõ ràng hơn là dựa vào màu chữ. */}
      <svg
        className="shrink-0"
        width="16"
        height="16"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="12" fill={satisfied ? "#FF4355" : "#B2B2B2"} />
        <path
          d="M17 9l-6.5 6.5L7 12"
          fill="none"
          stroke="#000000"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <div className="text-[0.6875rem] text-[#707070] leading-[21px]">{label}</div>
    </div>
  );
};
