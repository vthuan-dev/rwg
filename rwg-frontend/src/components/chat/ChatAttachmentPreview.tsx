"use client";

import React from "react";
import { X, Loader2, AlertCircle, Check } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

/** Đổi số byte thành chuỗi người đọc được. */
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Ô xem trước ảnh đang chờ gửi, nằm ngay trên ô nhập.
 *
 * KÍCH CỠ CỐ ĐỊNH (80px): ảnh người dùng chọn có tỷ lệ bất kỳ, và để nó tự co giãn
 * thì mỗi lần chọn ảnh mới ô nhập lại nhảy một chiều cao khác. `object-cover` cắt
 * phần thừa thay vì bóp méo ảnh.
 *
 * Ảnh BẤM ĐƯỢC để xem to: ô 80px không đủ để kiểm tra xem có dán đúng ảnh chưa, nhất
 * là với ảnh chụp màn hình dài mà phần cần gửi nằm ở giữa.
 */
export const ChatAttachmentPreview: React.FC<{
  previewUrl: string;
  uploading: boolean;
  errorKey: string | null;
  onRemove: () => void;
  /** Tên tệp, để người dùng biết chắc mình chọn đúng. Ảnh dán từ khay nhớ tạm không có. */
  fileName?: string | null;
  fileSize?: number | null;
  /** Mở lớp xem toàn màn hình. Bỏ trống thì ảnh không bấm được. */
  onOpen?: (src: string, alt: string) => void;
}> = ({
  previewUrl,
  uploading,
  errorKey,
  onRemove,
  fileName = null,
  fileSize = null,
  onOpen,
}) => {
  const { t } = useTranslation();
  const alt = fileName ?? t("chat.attachment.preview_alt");

  const thumbClass =
    "relative size-20 shrink-0 overflow-hidden rounded-xl border border-[#28282e] bg-[#16161a]";

  // Dùng <img> chứ không dùng next/image: nguồn là một blob URL cục bộ, mà trình tối ưu
  // ảnh của Next chỉ làm việc với đường dẫn thật trên server.
  const thumbInner = (
    <>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={previewUrl} alt={alt} className="size-full object-cover" />

      {uploading && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/60">
          <Loader2 className="size-5 animate-spin text-white" />
        </div>
      )}
    </>
  );

  return (
    <div className="flex items-center gap-3 border-b border-[#1f1f24] px-4 py-2.5">
      {onOpen ? (
        <button
          type="button"
          onClick={() => onOpen(previewUrl, alt)}
          className={`${thumbClass} cursor-zoom-in transition-opacity hover:opacity-90`}
          aria-label={t("chat.attachment.open")}
        >
          {thumbInner}
        </button>
      ) : (
        <div className={thumbClass}>{thumbInner}</div>
      )}

      <div className="min-w-0 grow">
        {/* Tên tệp trên một dòng riêng, cắt bằng ellipsis: tên ảnh từ điện thoại rất dài
            và để nó xuống dòng sẽ đẩy chiều cao ô nhập lên. */}
        {fileName && (
          <p className="truncate text-[0.75rem] font-medium text-[#e4e4e7]">{fileName}</p>
        )}

        {errorKey ? (
          <p className="flex items-center gap-1.5 text-[0.75rem] text-red-400">
            <AlertCircle className="size-3.5 shrink-0" />
            {t(errorKey)}
          </p>
        ) : (
          <p className="flex items-center gap-1.5 text-[0.75rem] text-[#8b8b93]">
            {uploading ? (
              <>
                <Loader2 className="size-3.5 shrink-0 animate-spin" />
                {t("chat.attachment.uploading")}
              </>
            ) : (
              <>
                {/* Dấu tích xanh: người dùng cần biết ảnh đã lên xong và bấm gửi sẽ đi
                    kèm, chứ không phải vẫn đang tải. */}
                <Check className="size-3.5 shrink-0 text-emerald-400" />
                {t("chat.attachment.ready")}
              </>
            )}
            {fileSize != null && (
              <span className="text-[#6b6b73]">· {formatSize(fileSize)}</span>
            )}
          </p>
        )}
      </div>

      <button
        type="button"
        onClick={onRemove}
        aria-label={t("chat.attachment.remove")}
        className="flex size-8 shrink-0 items-center justify-center rounded-full text-[#8b8b93] transition-colors hover:bg-[#1f1f24] hover:text-[#e4e4e7]"
      >
        <X className="size-4" />
      </button>
    </div>
  );
};
