"use client";

import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Tên hiển thị của từng ngôn ngữ, viết bằng CHÍNH ngôn ngữ đó.
 *
 * Cố tình không đưa vào file dịch: danh sách này phải giống nhau ở mọi ngôn ngữ.
 * Người đang xem giao diện tiếng Việt mà muốn chuyển sang tiếng Nhật cần thấy
 * chữ "日本語", không phải chữ "Tiếng Nhật" — nếu dịch theo locale hiện tại thì
 * người không đọc được ngôn ngữ đang bật sẽ không tìm ra ngôn ngữ của mình.
 */
const LOCALE_LABELS: Record<string, string> = {
  en: "English",
  "zh": "简体中文",
  vi: "Tiếng Việt",
  ms: "Bahasa Melayu",
  ja: "日本語",
  ko: "한국어",
};

/** Thứ tự hiển thị, giữ đúng thứ tự của trang gốc. */
const LOCALE_ORDER = ["en", "zh", "vi", "ms", "ja", "ko"];

export const LanguagePopover: React.FC = () => {
  const { locale, setLocale } = useTranslation();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Bấm ra ngoài thì đóng. Không dùng onBlur trên khối bọc: onBlur bắn cả khi
  // focus chuyển sang một mục BÊN TRONG danh sách, khiến danh sách tự đóng ngay
  // trước khi cú bấm chọn ngôn ngữ kịp chạy.
  useEffect(() => {
    if (!open) return;

    const onPointerDown = (event: MouseEvent | TouchEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };

    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("touchstart", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("touchstart", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <div className="relative flex flex-col items-center" ref={containerRef}>
      <button
        type="button"
        className="flex justify-center items-center gap-x-2 cursor-pointer"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        aria-haspopup="listbox"
      >
        <i className="icon-icon50 size-5 text-[1.25rem] text-primary" />
        <div className="text-white">{LOCALE_LABELS[locale] ?? locale}</div>
      </button>

      {open ? (
        <div
          className="absolute top-full mt-2 w-72 p-4 flex flex-col gap-y-4 border-none bg-[#1F1F1F] outline-none rounded-none shadow-xl z-20"
          role="listbox"
        >
          {LOCALE_ORDER.map((code) => {
            const active = code === locale;
            return (
              <button
                key={code}
                type="button"
                role="option"
                aria-selected={active}
                className={`flex justify-between items-center text-[0.875rem] cursor-pointer ${
                  active ? "text-primary" : "text-[#83888C]"
                }`}
                onClick={() => {
                  setLocale(code);
                  setOpen(false);
                }}
              >
                {LOCALE_LABELS[code]}
                {active ? (
                  <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true">
                    <path
                      d="M20 6 9 17l-5-5"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                ) : null}
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
};
