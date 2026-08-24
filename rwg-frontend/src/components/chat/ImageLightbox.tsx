"use client";

import React, { useEffect } from "react";
import { X } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Xem ảnh toàn màn hình.
 *
 * Ảnh trong bong bóng chat bị giới hạn khoảng 240px để không chiếm hết luồng hội thoại,
 * mà nội dung thường là ảnh chụp màn hình có chữ nhỏ — ở kích cỡ đó không đọc được.
 * Không có lớp này thì người dùng phải tải ảnh về mới đọc được thứ họ vừa được gửi.
 */
export const ImageLightbox: React.FC<{
  src: string;
  alt: string;
  onClose: () => void;
}> = ({ src, alt, onClose }) => {
  const { t } = useTranslation();

  /**
   * Escape để đóng, và KHOÁ CUỘN của trang bên dưới.
   *
   * Không khoá cuộn thì lăn chuột trên lớp phủ sẽ cuộn danh sách tin nhắn phía sau, và
   * khi đóng lại người dùng thấy mình đã ở một chỗ khác trong hội thoại.
   */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [onClose]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={alt}
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 p-4 backdrop-blur-sm"
    >
      <button
        type="button"
        onClick={onClose}
        aria-label={t("chat.attachment.close")}
        className="absolute end-4 top-4 flex size-11 items-center justify-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/20"
      >
        <X className="size-5" />
      </button>

      {/* `stopPropagation` trên chính ảnh: bấm vào lớp phủ thì đóng, nhưng bấm vào ảnh
          thì không — người dùng thường bấm vào ảnh để phóng to hoặc kéo xem. */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={src}
        alt={alt}
        onClick={(e) => e.stopPropagation()}
        className="max-h-full max-w-full rounded-lg object-contain"
      />
    </div>
  );
};
