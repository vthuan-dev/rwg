"use client";

import React, { useEffect, useState } from "react";
import { SUPPORTED_LOCALES, useTranslation } from "@/context/LanguageContext";

/**
 * Hộp thoại chọn ngôn ngữ của trang hồ sơ.
 *
 * SAO LẠI ĐÚNG BẢN GỐC (module `9879` trong `pages/profile-663e3d4a0f7a413c.js`):
 * - Khung `bg-[#1F1F1F]`, KHÔNG bo góc, không viền.
 * - Danh sách xếp DỌC, `gap-y-5`, mỗi mục `text-[1.25rem]`; mục đang chọn màu `primary`,
 *   còn lại `#83888C`.
 * - Nút xác nhận `mt-6`.
 * - Nút đóng là một VÒNG TRÒN nằm BÊN NGOÀI khung, phía dưới (`-bottom-17`).
 *
 * KHÁC `LanguagePopover`: popover đó là danh sách nhỏ rơi xuống dưới nút ở trang chủ và
 * đổi ngôn ngữ NGAY khi bấm. Hộp thoại này chọn trước rồi phải bấm xác nhận. Giữ đúng hai
 * hành vi khác nhau vì bản gốc cũng vậy — gộp lại thì một trong hai trang sẽ sai.
 */
const LOCALE_LABELS: Record<string, string> = {
  en: "English",
  zh: "简体中文",
  vi: "Tiếng Việt",
  ms: "Bahasa Melayu",
  ja: "日本語",
  ko: "한국어",
};

export interface LanguageModalProps {
  open: boolean;
  onClose: () => void;
}

/**
 * Phần thân, CHỈ tồn tại khi hộp thoại đang mở.
 *
 * VÌ SAO TÁCH RA: lựa chọn tạm phải quay về ngôn ngữ hiện tại mỗi lần MỞ — người dùng chọn
 * một ngôn ngữ, đóng hộp thoại, mở lại thì không được thấy lựa chọn cũ còn sáng trong khi
 * giao diện chưa hề đổi.
 *
 * Cách hiển nhiên là một `useEffect` gọi `setPending(locale)` khi `open` thành true, nhưng
 * đó là setState đồng bộ trong effect: nó tạo thêm một vòng render sau mỗi lần mở, và ESLint
 * chặn đúng (`react-hooks/set-state-in-effect`). Ở đây component con bị THÁO khi đóng nên
 * `useState(locale)` tự chạy lại lúc mở — không cần effect, không có vòng render phụ.
 */
const LanguageModalPanel: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const { locale, setLocale, t } = useTranslation();

  /**
   * Lựa chọn TẠM, chưa áp dụng.
   *
   * Bấm một ngôn ngữ rồi bấm nút đóng phải KHÔNG đổi gì. Áp dụng ngay lúc bấm sẽ khiến chữ
   * trên chính hộp thoại nhảy sang ngôn ngữ khác trong lúc người dùng còn đang cân nhắc.
   */
  const [pending, setPending] = useState(locale);

  // Đăng ký nghe bàn phím — đây là đúng việc của effect: đồng bộ với một hệ thống bên
  // ngoài React, và dọn lại khi tháo.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Lớp phủ trải HẾT màn hình, kể cả ngoài cột 640px — cùng lý do như `AuthModal`:
          chỉ tối phần trong cột thì trên màn hình rộng hai dải bên cạnh vẫn sáng và hộp
          thoại trông như đang nổi trên một trang chưa bị chặn.

          Lớp phủ là phần tử EM của khung ngoài chứ không phải cha của hộp thoại, nên cú
          bấm bên trong hộp thoại không nổi bọt xuống đây — không cần `stopPropagation`. */}
      <div
        aria-hidden="true"
        className="absolute inset-0 cursor-pointer bg-black/50"
        onClick={onClose}
      />

      <div className="pointer-events-none relative mx-auto flex w-full justify-center px-4 sm:max-w-[640px]">
        <div
          aria-labelledby="language-modal-title"
          aria-modal="true"
          className="pointer-events-auto relative w-full rounded-none border-none bg-[#1F1F1F] p-0 shadow-lg outline-none sm:max-w-lg"
          role="dialog"
        >
          {/* Tiêu đề chỉ dành cho trình đọc màn hình: bản gốc KHÔNG hiện tiêu đề (họ đặt
              `AlertDialogHeader` lớp `hidden`), nhưng `role="dialog"` không có nhãn thì
              trình đọc màn hình chỉ đọc được "hộp thoại" mà không rõ để làm gì. */}
          <h2 className="sr-only" id="language-modal-title">
            {t("profile.language_settings")}
          </h2>

          <div className="flex flex-col items-center px-7 py-5">
            <div className="flex flex-col gap-y-5" role="listbox">
              {SUPPORTED_LOCALES.map((code) => (
                <button
                  aria-selected={pending === code}
                  className={`py-2 text-center text-[1.25rem] leading-normal ${
                    pending === code ? "text-primary" : "text-[#83888C]"
                  }`}
                  key={code}
                  onClick={() => setPending(code)}
                  role="option"
                  type="button"
                >
                  {LOCALE_LABELS[code]}
                </button>
              ))}
            </div>

            <button
              className="mt-6 inline-flex h-11 w-full items-center justify-center rounded-none bg-primary text-[1rem] font-bold leading-normal text-white shadow-lg transition-colors hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
              onClick={() => {
                setLocale(pending);
                onClose();
              }}
              type="button"
            >
              {t("auth.confirm")}
            </button>

            {/* Nút đóng nằm NGOÀI khung, phía dưới — đúng bản gốc (`-bottom-17`). */}
            <button
              aria-label={t("bet.back")}
              className="absolute -bottom-17 left-1/2 flex size-9 -translate-x-1/2 items-center justify-center rounded-full bg-[#1f1f1f] text-white"
              onClick={onClose}
              type="button"
            >
              <i aria-hidden="true" className="icon-icon3 size-4.5 text-[1.125rem]" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export const LanguageModal: React.FC<LanguageModalProps> = ({ open, onClose }) => {
  // Trả null khi đóng để phần thân bị THÁO HẲN, không chỉ bị ẩn: đó là điều làm lựa chọn
  // tạm tự đặt lại ở lần mở sau (xem ghi chú ở `LanguageModalPanel`).
  if (!open) return null;
  return <LanguageModalPanel onClose={onClose} />;
};
