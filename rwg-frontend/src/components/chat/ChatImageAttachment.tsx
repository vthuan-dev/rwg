"use client";

import React, { useEffect, useRef, useState } from "react";
import { ImageOff, Loader2 } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Ảnh đính kèm trong một bong bóng chat.
 *
 * TẢI QUA FETCH rồi dựng blob URL, không gán thẳng đường dẫn vào `src`: ảnh nằm sau
 * endpoint yêu cầu header `Authorization`, mà thẻ `<img>` không gửi được header nào.
 * Hàm tải được truyền từ ngoài vì người chơi và nhân sự dùng hai token khác nhau.
 */
export const ChatImageAttachment: React.FC<{
  /** Đường dẫn lưu trong tin nhắn, vd "/api/v1/chat/attachments/<uuid>.jpg". */
  attachmentUrl: string;
  attachmentName: string | null;
  /** Tải tệp và trả về blob URL. */
  load: (attachmentUrl: string) => Promise<string>;
  onOpen: (src: string, alt: string) => void;
  /**
   * Ảnh có sẵn ở máy, dùng thẳng và BỎ QUA bước tải.
   *
   * Dành cho tin vừa gửi: ngay sau khi gửi, ảnh mới chỉ được tải lên chứ CHƯA gắn vào
   * tin nào trong cơ sở dữ liệu, mà quyền xem lại suy ra từ tin nhắn chứa ảnh — nên
   * endpoint sẽ trả 404 và bong bóng hiện "Không tải được ảnh" ngay sau khi gửi thành
   * công. Blob cục bộ đã có trong tay nên vừa đúng vừa nhanh hơn.
   */
  localSrc?: string | null;
}> = ({ attachmentUrl, attachmentName, load, onOpen, localSrc }) => {
  const { t } = useTranslation();
  const [src, setSrc] = useState<string | null>(localSrc ?? null);
  const [failed, setFailed] = useState(false);

  /**
   * URL hiện tại, để thu hồi khi component tháo bỏ.
   *
   * Blob giữ toàn bộ ảnh trong bộ nhớ tới khi được thu hồi tường minh. Một lịch sử chat
   * dài với hàng chục ảnh, mỗi ảnh vài MB, sẽ ăn hết bộ nhớ của tab nếu không dọn.
   */
  const urlRef = useRef<string | null>(null);

  useEffect(() => {
    // Đã có ảnh ở máy: không gọi mạng, và KHÔNG ghi vào urlRef — URL này do nơi khác
    // sở hữu (trang chat giữ nó trong danh sách tin), thu hồi ở đây sẽ làm ảnh biến mất
    // khỏi bong bóng khi component vẽ lại.
    if (localSrc) {
      setSrc(localSrc);
      setFailed(false);
      return;
    }

    let cancelled = false;

    load(attachmentUrl)
      .then((blobUrl) => {
        // Component đã tháo bỏ trong lúc tải: thu hồi ngay chứ không setState. Không có
        // nhánh này thì mỗi ảnh người dùng cuộn qua trước khi tải xong là một blob bị
        // giữ mãi trong bộ nhớ.
        if (cancelled) {
          URL.revokeObjectURL(blobUrl);
          return;
        }
        urlRef.current = blobUrl;
        setSrc(blobUrl);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });

    return () => {
      cancelled = true;
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
        urlRef.current = null;
      }
    };
  }, [attachmentUrl, load, localSrc]);

  const alt = attachmentName ?? t("chat.attachment.image_alt");

  // KHUNG CỐ ĐỊNH cho cả ba trạng thái (đang tải / lỗi / đã tải).
  //
  // Ảnh không có kích cỡ định trước sẽ chiếm 0px lúc chưa tải rồi bung ra khi tải xong,
  // đẩy toàn bộ hội thoại bên dưới nhảy xuống — đúng lúc người dùng đang đọc. Giữ khung
  // cố định thì bố cục ổn định từ đầu.
  const frameClass =
    "flex h-40 w-60 items-center justify-center overflow-hidden rounded-xl bg-black/20";

  if (failed) {
    return (
      <div className={`${frameClass} flex-col gap-1.5 text-[#8b8b93]`}>
        <ImageOff className="size-6" />
        <span className="text-[0.6875rem]">{t("chat.attachment.load_failed")}</span>
      </div>
    );
  }

  if (!src) {
    return (
      <div className={frameClass}>
        <Loader2 className="size-5 animate-spin text-white/60" />
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={() => onOpen(src, alt)}
      className={`${frameClass} cursor-zoom-in transition-opacity hover:opacity-90`}
      aria-label={t("chat.attachment.open")}
    >
      {/* `object-cover` cắt phần thừa thay vì bóp méo: ảnh chụp màn hình điện thoại rất
          cao, và co cho vừa khung sẽ làm chữ trong ảnh nhỏ đến mức vô nghĩa. Bấm vào để
          xem đầy đủ. */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={src} alt={alt} className="size-full object-cover" />
    </button>
  );
};
