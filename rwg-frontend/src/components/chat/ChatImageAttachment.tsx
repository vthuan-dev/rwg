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

  // Khung CHỈ dùng cho hai trạng thái chưa có ảnh (đang tải / lỗi).
  //
  // Lúc này chưa biết tỉ lệ ảnh nên buộc phải có kích cỡ cố định, nếu không thì
  // bong bóng cao 0px rồi bung ra khi ảnh tải xong, đẩy cả hội thoại bên dưới nhảy
  // xuống đúng lúc người dùng đang đọc.
  const placeholderClass =
    "flex h-40 w-full items-center justify-center overflow-hidden rounded-xl bg-black/20";

  if (failed) {
    return (
      <div className={`${placeholderClass} flex-col gap-1.5 text-[#8b8b93]`}>
        <ImageOff className="size-6" />
        <span className="text-[0.6875rem]">{t("chat.attachment.load_failed")}</span>
      </div>
    );
  }

  if (!src) {
    return (
      <div className={placeholderClass}>
        <Loader2 className="size-5 animate-spin text-white/60" />
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={() => onOpen(src, alt)}
      // `block w-full` chứ không phải khung có kích cỡ: nút rộng bằng cả bong bóng,
      // và chiều cao do chính ảnh quyết định. Không có `h-*` nào ở đây — thêm vào là
      // lại sinh ra viền đen trên dưới cho ảnh ngang.
      className="block w-full cursor-zoom-in overflow-hidden rounded-xl transition-opacity hover:opacity-90"
      aria-label={t("chat.attachment.open")}
    >
      {/* `h-auto` + KHÔNG `object-*`: ảnh hiện ở đúng tỉ lệ thật của nó, rộng hết
          bong bóng. Không cắt mép nào, không viền đen, không bóp méo.
          `object-cover` ban đầu cắt hai bên ảnh ngang — với ảnh khuyến mãi kèm bảng
          số liệu thì phần bị cắt lại đúng là phần cần đọc.
          `max-h-[32rem]` chỉ để một ảnh rất cao (chụp màn hình dài) không đẩy cả
          khung chat đi; ảnh như vậy vẫn bấm được để xem đầy đủ. */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={src} alt={alt} className="h-auto w-full max-h-[32rem] object-contain" />
    </button>
  );
};
