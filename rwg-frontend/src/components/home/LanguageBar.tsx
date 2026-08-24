"use client";

import React from "react";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Tên ngôn ngữ viết bằng CHÍNH ngôn ngữ đó, cố tình không dịch: người dùng mở
 * trang thấy tiếng mình không đọc được vẫn phải tìm ra dòng của mình.
 *
 * Nhãn sao lại ĐÚNG bảng của trang gốc (trích từ `app.js`):
 *   en: "English", zh-CN: "中文", vi: "Tiếng Việt", ms: "Malay", ja: "日本語", ko: "한국인"
 *
 * Nên `zh` là "中文" chứ không phải "简体中文", `ms` là "Malay" chứ không phải
 * "Bahasa Melayu", và `ko` là "한국인" chứ không phải "한국어". Tên ngắn cũng là điều
 * kiện để cả sáu vừa một hàng ở màn hình 375px.
 *
 * Mã `zh` khớp với khoá của LanguageContext (trang gốc dùng `zh-CN`).
 */
const LANGUAGES = [
  { code: "vi", name: "Tiếng Việt" },
  { code: "en", name: "English" },
  { code: "zh", name: "中文" },
  { code: "ms", name: "Malay" },
  { code: "ja", name: "日本語" },
  { code: "ko", name: "한국인" },
];

/**
 * Chọn ngôn ngữ ở cuối trang chủ: sáu mục trên MỘT hàng, phân cách bằng dấu gạch
 * dọc, đúng như trang gốc.
 *
 * Vùng bấm vẫn giữ 44px chiều cao (`min-h-11`) nhưng bị triệt tiêu bằng `-my-3` nên
 * khoảng cách NHÌN THẤY vẫn gọn như thiết kế. Bỏ hẳn min-h-11 sẽ cho một dải chỉ cao
 * ~18px, quá nhỏ để bấm bằng ngón tay.
 *
 * `flex-wrap` để dự phòng: nếu sau này thêm ngôn ngữ hoặc người dùng phóng to chữ,
 * hàng tự xuống dòng thay vì tràn ngang gây trượt cả trang.
 */
export const LanguageBar: React.FC = () => {
  const { locale, setLocale } = useTranslation();

  return (
    <section className="w-full px-4 my-4">
      <ul className="flex flex-wrap items-center justify-center gap-x-1 border-t border-[#1a1a1e] pt-2">
        {LANGUAGES.map((lang, index) => {
          const isActive = locale === lang.code;
          return (
            <li className="flex items-center" key={lang.code}>
              {/* Dấu gạch phân cách là phần TRANG TRÍ: để trong `aria-hidden` và nằm
                  ngoài nút, không thì trình đọc màn hình đọc "gạch dọc" giữa từng
                  ngôn ngữ. */}
              {index > 0 ? (
                <span aria-hidden="true" className="px-1 text-[0.6875rem] text-[#3a3a40]">
                  |
                </span>
              ) : null}
              <button
                aria-pressed={isActive}
                // min-h-11 = 44px cho vùng bấm bằng ngón tay; -my-3 triệt tiêu phần
                // chiều cao thêm ra để hàng nhìn vẫn gọn.
                className={`min-h-11 -my-3 px-1 flex items-center whitespace-nowrap text-[0.6875rem] transition-colors active:opacity-70 ${
                  isActive ? "text-primary font-bold" : "text-[#83888c]"
                }`}
                onClick={() => setLocale(lang.code)}
                type="button"
              >
                {lang.name}
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
};
