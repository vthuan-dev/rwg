"use client";

import React, { useId, useState } from "react";

/**
 * Ô nhập của khu xác thực, sao lại đúng ô nhập của trang gốc: cao `2.75rem`,
 * nền `#1F1F1F`, KHÔNG bo góc, viền chuyển sang màu nhấn khi focus.
 */
export interface TextFieldProps {
  label: string;
  name: string;
  value: string;
  onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  placeholder?: string;
  type?: "text" | "password" | "email" | "tel";
  error?: string;
  required?: boolean;
  disabled?: boolean;
  autoComplete?: string;
  inputMode?: React.HTMLAttributes<HTMLInputElement>["inputMode"];
  maxLength?: number;
  /** Nội dung nằm bên phải trong ô, ví dụ nút "Gửi mã". */
  rightSlot?: React.ReactNode;
}

export const TextField: React.FC<TextFieldProps> = ({
  label,
  name,
  value,
  onChange,
  placeholder,
  type = "text",
  error,
  required = false,
  disabled = false,
  autoComplete,
  inputMode,
  maxLength,
  rightSlot,
}) => {
  const [revealed, setRevealed] = useState(false);
  // id phải duy nhất trong trang để <label for> trỏ đúng ô; useId sinh id ổn định
  // giữa server và client nên không gây lệch khi hydrate.
  const reactId = useId();
  const inputId = `${name}-${reactId}`;
  const errorId = `${inputId}-error`;

  const isPassword = type === "password";
  const effectiveType = isPassword && revealed ? "text" : type;

  return (
    <div className="flex flex-col gap-y-1.5">
      <label
        className="text-[0.875rem] text-[#8B8B8B] leading-normal"
        htmlFor={inputId}
      >
        {label}
        {/* Bản gốc dùng lớp `text-destructive` cho dấu này, KHÁC với màu nhấn
            `--primary` của nút và liên kết. Hai trang xác thực hiện không bật
            `required` ở ô nào, nhưng giữ đúng màu để nếu sau này dùng thì không ra
            hai sắc đỏ lệch nhau cạnh nhau. */}
        {required ? <span className="text-[#ff3b30]">*</span> : null}
      </label>

      <div className="relative flex items-center">
        <input
          className="w-full px-5 h-[2.75rem] bg-[#1F1F1F] border border-[rgba(0,0,0,0.1)] text-[0.875rem] leading-[1.1875rem] text-white placeholder:text-[#8B8B8B] placeholder:text-[0.875rem] focus:outline-none focus:border-primary shadow-lg disabled:opacity-50 disabled:cursor-not-allowed"
          disabled={disabled}
          id={inputId}
          inputMode={inputMode}
          maxLength={maxLength}
          name={name}
          onChange={onChange}
          placeholder={placeholder}
          type={effectiveType}
          value={value}
          autoComplete={autoComplete}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
        />

        {isPassword ? (
          <button
            type="button"
            className="absolute right-5 flex justify-center items-center cursor-pointer"
            onClick={() => setRevealed((prev) => !prev)}
            // Nút này chỉ đổi cách hiển thị, không phải một chặng trong luồng
            // điền form — để nó nhận focus bằng Tab sẽ chen vào giữa ô mật khẩu
            // và nút gửi.
            tabIndex={-1}
            aria-label={revealed ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
          >
            <i
              className={`${
                revealed ? "icon-icon2" : "icon-icon1"
              } size-5 text-[1.25rem] text-primary`}
            />
          </button>
        ) : (
          rightSlot ?? null
        )}
      </div>

      {error ? (
        <span id={errorId} className="text-[0.6875rem] text-primary" role="alert">
          {error}
        </span>
      ) : null}
    </div>
  );
};
