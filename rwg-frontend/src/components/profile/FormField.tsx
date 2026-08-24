"use client";

import React, { useId, useState } from "react";

/**
 * Ô nhập dùng chung cho các trang biểu mẫu của khu người chơi.
 *
 * Theo đúng bản gốc: nhãn nhỏ màu xám phía trên, ô nhập nền `#1f1f1f` cao `h-12`,
 * KHÔNG bo góc, KHÔNG viền.
 *
 * Ô bị vô hiệu có nền SÁNG HƠN (`#2a2a2a`) chứ không mờ đi bằng `opacity`: giảm độ mờ làm
 * chữ tụt tương phản xuống dưới mức đọc được, mà đây là chỗ hiển thị tên đăng nhập —
 * thông tin người dùng cần đọc rõ dù không sửa được.
 */

const LABEL_CLASS = "text-[0.8125rem] leading-[1.125rem] text-[#8b8b8b]";
const INPUT_BASE =
  "h-12 w-full px-4 text-[0.9375rem] text-[#d0d5da] outline-none " +
  "placeholder:text-[#5b5b5b] focus:ring-1 focus:ring-[#fe1616]/40";

interface BaseProps {
  label: string;
  /** Thông báo lỗi cho riêng ô này; hiện ngay dưới ô. */
  error?: string;
}

interface TextFieldProps extends BaseProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  /**
   * `inputMode` gợi ý bàn phím trên điện thoại. Với ô số điện thoại phải là `"tel"` để
   * người dùng không phải tự chuyển sang bàn phím số.
   */
  inputMode?: "text" | "tel" | "numeric";
  maxLength?: number;
  autoComplete?: string;
}

/** Ô nhập chữ một dòng. */
export function TextField({
  label,
  value,
  onChange,
  placeholder,
  disabled,
  inputMode = "text",
  maxLength,
  autoComplete,
  error,
}: TextFieldProps) {
  const id = useId();

  return (
    <div className="flex flex-col gap-y-2">
      <label className={LABEL_CLASS} htmlFor={id}>
        {label}
      </label>
      <input
        aria-invalid={error ? true : undefined}
        autoComplete={autoComplete}
        className={`${INPUT_BASE} ${disabled ? "bg-[#2a2a2a] text-[#8b8b8b]" : "bg-[#1f1f1f]"}`}
        disabled={disabled}
        id={id}
        inputMode={inputMode}
        maxLength={maxLength}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        type="text"
        value={value}
      />
      {error ? <FieldError message={error} /> : null}
    </div>
  );
}

interface PasswordFieldProps extends BaseProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  maxLength?: number;
  autoComplete?: string;
}

/**
 * Ô nhập mật khẩu, có nút hiện/ẩn như bản gốc.
 *
 * Nút hiện/ẩn không phải để trang trí: mật khẩu rút tiền là mã PIN, gõ sai mà không xem
 * lại được thì người dùng tốn một lần thử sai vào bộ đếm chống dò, và quá số lần là bị
 * khoá tạm.
 */
export function PasswordField({
  label,
  value,
  onChange,
  placeholder,
  maxLength,
  autoComplete = "current-password",
  error,
}: PasswordFieldProps) {
  const id = useId();
  const [visible, setVisible] = useState(false);

  return (
    <div className="flex flex-col gap-y-2">
      <label className={LABEL_CLASS} htmlFor={id}>
        {label}
      </label>
      <div className="relative">
        <input
          aria-invalid={error ? true : undefined}
          autoComplete={autoComplete}
          className={`${INPUT_BASE} bg-[#1f1f1f] pe-12`}
          id={id}
          inputMode="text"
          maxLength={maxLength}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          type={visible ? "text" : "password"}
          value={value}
        />
        <button
          // Vùng bấm 44px theo hướng dẫn tiếp cận của cả iOS và Android; icon nhỏ hơn
          // nhưng vùng bấm phải đủ rộng cho ngón tay.
          className="absolute end-0 top-0 flex size-12 items-center justify-center text-[#8b8b8b]"
          // Nhãn đổi theo trạng thái để trình đọc màn hình báo đúng việc nút sẽ làm.
          aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
          onClick={() => setVisible((current) => !current)}
          type="button"
        >
          <EyeIcon open={visible} />
        </button>
      </div>
      {error ? <FieldError message={error} /> : null}
    </div>
  );
}

interface SelectFieldProps extends BaseProps {
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
  placeholder?: string;
  disabled?: boolean;
}

/**
 * Ô chọn.
 *
 * Dùng `<select>` gốc của trình duyệt chứ không tự vẽ danh sách: trên điện thoại nó mở ra
 * bộ chọn của hệ điều hành — quen tay hơn, dùng được với trình đọc màn hình, và cuộn mượt
 * với danh sách dài mà không cần một dòng JavaScript nào.
 */
export function SelectField({
  label,
  value,
  onChange,
  options,
  placeholder,
  disabled,
  error,
}: SelectFieldProps) {
  const id = useId();

  return (
    <div className="flex flex-col gap-y-2">
      <label className={LABEL_CLASS} htmlFor={id}>
        {label}
      </label>
      <div className="relative">
        <select
          aria-invalid={error ? true : undefined}
          // `appearance-none` để bỏ mũi tên mặc định (mỗi hệ điều hành vẽ một kiểu) rồi
          // tự vẽ chevron cho khớp bản gốc.
          className={`${INPUT_BASE} appearance-none pe-10 ${
            disabled ? "bg-[#2a2a2a] text-[#8b8b8b]" : "bg-[#1f1f1f]"
          } ${value ? "" : "text-[#5b5b5b]"}`}
          disabled={disabled}
          id={id}
          onChange={(event) => onChange(event.target.value)}
          value={value}
        >
          {placeholder ? (
            <option value="">{placeholder}</option>
          ) : null}
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <span className="pointer-events-none absolute end-4 top-1/2 -translate-y-1/2 text-[#8b8b8b]">
          <ChevronDownIcon />
        </span>
      </div>
      {error ? <FieldError message={error} /> : null}
    </div>
  );
}

/** Nút Lưu đỏ, rộng hết dòng, theo đúng bản gốc. */
export function SubmitButton({
  children,
  disabled,
  onClick,
}: {
  children: React.ReactNode;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      className="h-12 w-full bg-[#fe1616] text-[0.9375rem] font-bold text-white
                 transition-opacity active:opacity-80 disabled:opacity-50"
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      {children}
    </button>
  );
}

/**
 * Thông báo lỗi của một ô.
 *
 * `role="alert"` để trình đọc màn hình đọc ngay khi lỗi xuất hiện; không có thuộc tính này
 * thì người dùng khiếm thị bấm Lưu mà không biết vì sao không có gì xảy ra.
 */
function FieldError({ message }: { message: string }) {
  return (
    <p className="text-[0.75rem] leading-[1rem] text-[#fe1616]" role="alert">
      {message}
    </p>
  );
}

function EyeIcon({ open }: { open: boolean }) {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      height="20"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.5"
      viewBox="0 0 24 24"
      width="20"
    >
      <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z" />
      <circle cx="12" cy="12" r="3" />
      {open ? null : <path d="m3 3 18 18" />}
    </svg>
  );
}

function ChevronDownIcon() {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      height="16"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      viewBox="0 0 24 24"
      width="16"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}
