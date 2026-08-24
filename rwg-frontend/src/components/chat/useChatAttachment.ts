"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { ChatAttachment } from "@/lib/playerApi";

/**
 * Loại ảnh được chấp nhận, PHẢI khớp với `CHAT_IMAGE_EXTENSIONS` ở backend.
 *
 * Kiểm tra ở client là để người dùng biết ngay, KHÔNG phải để bảo vệ server: mọi
 * kiểm tra ở đây đều bỏ qua được bằng cách gọi API trực tiếp, nên backend vẫn xác
 * thực lại bằng chữ ký byte của tệp.
 */
const ACCEPTED_TYPES = ["image/png", "image/jpeg", "image/webp"];

/** Trần dung lượng, khớp `rwg.media.chat-max-file-size-bytes` = 10MB. */
const MAX_SIZE_BYTES = 10 * 1024 * 1024;

export interface UseChatAttachmentResult {
  /** Tệp đã tải lên xong, sẵn sàng gửi kèm tin. */
  attachment: ChatAttachment | null;
  /** URL xem trước cục bộ, hiện ngay khi chọn tệp chứ không chờ tải lên xong. */
  previewUrl: string | null;
  /**
   * Tên tệp đã chọn, có NGAY từ lúc chọn.
   *
   * Không đọc từ `attachment.name`: trường đó chỉ có sau khi server trả về, nên ô xem
   * trước sẽ trống tên trong suốt thời gian tải lên rồi đột ngột hiện chữ — đúng lúc
   * người dùng đang muốn xác nhận mình chọn đúng ảnh.
   *
   * Ảnh dán từ khay nhớ tạm thường có tên vô nghĩa ("image.png") nên có thể null.
   */
  fileName: string | null;
  fileSize: number | null;
  uploading: boolean;
  /** Khoá dịch của lỗi, hoặc null. */
  errorKey: string | null;
  /** Nhận tệp từ ô chọn tệp, dán, hoặc kéo-thả. */
  accept: (file: File) => void;
  /** Nhận nội dung dán; trả true nếu đã lấy được một ảnh từ đó. */
  acceptPaste: (items: DataTransferItemList | null) => boolean;
  clear: () => void;
  /**
   * Dọn trạng thái nhưng CHUYỂN quyền sở hữu URL xem trước cho người gọi.
   *
   * DÙNG KHI GỬI TIN, thay cho {@link clear}. Lý do: bong bóng tin vừa gửi cần hiện
   * ảnh ngay, và blob cục bộ là thứ duy nhất có sẵn — tải lại từ server thì phải chờ
   * một vòng mạng nữa, và ngay sau khi gửi thì ảnh còn CHƯA được gắn vào tin nào nên
   * endpoint còn trả 404 (quyền xem được suy ra từ tin nhắn chứa ảnh).
   *
   * Gọi {@link clear} ở đó sẽ thu hồi URL và ảnh trong tin vừa gửi thành ô vỡ.
   *
   * NGƯỜI GỌI PHẢI thu hồi URL nhận được khi không cần nữa.
   */
  detach: () => string | null;
}

/**
 * Quản lý một ảnh đính kèm đang chờ gửi.
 *
 * MỘT TỆP MỖI TIN, không phải danh sách: gửi nhiều ảnh thì mỗi ảnh là một tin nhắn
 * riêng (đúng cách Telegram và Messenger làm). Cho phép chọn nhiều tệp cùng lúc sẽ
 * kéo theo giao diện quản lý danh sách, thanh tiến trình từng tệp, và xử lý trường
 * hợp một tệp lỗi giữa lô — trong khi nhu cầu thật gần như luôn là một ảnh.
 *
 * @param upload hàm tải tệp lên; khác nhau giữa người chơi và nhân sự (hai API khác
 *        nhau, hai token khác nhau), nên nhận từ ngoài thay vì gọi thẳng.
 */
export function useChatAttachment(
  upload: (file: File) => Promise<ChatAttachment>
): UseChatAttachmentResult {
  const [attachment, setAttachment] = useState<ChatAttachment | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [fileName, setFileName] = useState<string | null>(null);
  const [fileSize, setFileSize] = useState<number | null>(null);
  const [uploading, setUploading] = useState(false);
  const [errorKey, setErrorKey] = useState<string | null>(null);

  /**
   * Giữ URL xem trước hiện tại để thu hồi khi thay thế hoặc khi component tháo bỏ.
   *
   * `URL.createObjectURL` giữ tệp trong bộ nhớ tới khi được thu hồi tường minh. Một
   * ảnh 10MB bị bỏ quên mỗi lần người dùng đổi ý là rò rỉ bộ nhớ thấy được ngay trên
   * điện thoại sau vài lần thao tác.
   */
  const previewRef = useRef<string | null>(null);

  const releasePreview = useCallback(() => {
    if (previewRef.current) {
      URL.revokeObjectURL(previewRef.current);
      previewRef.current = null;
    }
  }, []);

  useEffect(() => releasePreview, [releasePreview]);

  const clear = useCallback(() => {
    releasePreview();
    setPreviewUrl(null);
    setFileName(null);
    setFileSize(null);
    setAttachment(null);
    setUploading(false);
    setErrorKey(null);
  }, [releasePreview]);

  const detach = useCallback(() => {
    // LẤY URL ra rồi XÓA REF mà KHÔNG thu hồi: effect dọn dẹp lúc tháo component đọc
    // chính ref này, nên để nguyên thì URL đã chuyển cho người khác vẫn bị thu hồi khi
    // người dùng rời trang.
    const url = previewRef.current;
    previewRef.current = null;

    setPreviewUrl(null);
    setFileName(null);
    setFileSize(null);
    setAttachment(null);
    setUploading(false);
    setErrorKey(null);
    return url;
  }, []);

  const accept = useCallback(
    (file: File) => {
      if (!ACCEPTED_TYPES.includes(file.type)) {
        setErrorKey("chat.attachment.error_type");
        return;
      }
      if (file.size > MAX_SIZE_BYTES) {
        setErrorKey("chat.attachment.error_size");
        return;
      }

      // Ảnh xem trước hiện NGAY, trước khi tải lên xong: người dùng thấy đúng ảnh
      // mình vừa chọn và biết thao tác đã được ghi nhận. Chờ server trả về mới hiện
      // thì trên mạng chậm sẽ có vài giây giao diện không phản hồi gì.
      releasePreview();
      const localUrl = URL.createObjectURL(file);
      previewRef.current = localUrl;
      setPreviewUrl(localUrl);
      setFileName(file.name || null);
      setFileSize(file.size);
      setAttachment(null);
      setErrorKey(null);
      setUploading(true);

      upload(file)
        .then((uploaded) => setAttachment(uploaded))
        .catch(() => {
          // Giữ ảnh xem trước lại: xoá đi thì người dùng không biết vừa xảy ra gì với
          // tệp nào. Họ bỏ chọn hoặc chọn lại tệp khác.
          setErrorKey("chat.attachment.error_upload");
        })
        .finally(() => setUploading(false));
    },
    [releasePreview, upload]
  );

  /**
   * Lấy ảnh từ nội dung dán.
   *
   * Đọc `DataTransferItemList` chứ không đọc `files`: ảnh chụp màn hình dán từ khay
   * nhớ tạm của Windows không xuất hiện trong `files` trên mọi trình duyệt, nhưng
   * luôn có trong `items` dưới dạng một item kind="file".
   *
   * @return true nếu đã lấy được ảnh — người gọi cần chặn hành vi dán mặc định, không
   *         thì trình duyệt chèn thêm chuỗi tên tệp vào ô nhập.
   */
  const acceptPaste = useCallback(
    (items: DataTransferItemList | null) => {
      if (!items) return false;
      for (const item of Array.from(items)) {
        if (item.kind !== "file") continue;
        const file = item.getAsFile();
        if (file && ACCEPTED_TYPES.includes(file.type)) {
          accept(file);
          return true;
        }
      }
      return false;
    },
    [accept]
  );

  return {
    attachment,
    previewUrl,
    fileName,
    fileSize,
    uploading,
    errorKey,
    accept,
    acceptPaste,
    clear,
    detach,
  };
}
