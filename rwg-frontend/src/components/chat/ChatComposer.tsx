"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { SendHorizonal, Loader2, ImagePlus } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";
import { ChatAttachmentPreview } from "./ChatAttachmentPreview";

const MAX_LENGTH = 2000;

/**
 * Ô nhập tin nhắn, dán đáy màn hình.
 *
 * Tự giãn theo số dòng thay vì dùng `<input>` một dòng: người chơi khiếu nại thường
 * gõ vài câu, và một ô một dòng buộc họ phải cuộn ngang trong lúc gõ.
 *
 * Nhận ảnh theo BA đường: nút chọn tệp, dán (Ctrl+V), và kéo-thả. Cả ba dẫn về cùng
 * một hàm `onPickFile` — người dùng khác nhau có thói quen khác nhau, và ảnh chụp màn
 * hình thì đường tự nhiên nhất luôn là dán.
 */
export const ChatComposer: React.FC<{
  onSend: (body: string) => void;
  disabled?: boolean;
  /** Đang gửi: vẫn cho gõ tiếp, chỉ khoá nút. */
  sending?: boolean;
  /**
   * Đặt con trỏ vào ô nhập khi giá trị này chuyển thành true.
   *
   * LÀ MỘT CỜ, không dùng thuộc tính `autoFocus` của HTML: lúc component được gắn vào
   * DOM thì lịch sử chat còn đang tải, và `autoFocus` sẽ cuộn trang để đưa ô nhập vào
   * tầm nhìn ngay lúc đó — xung đột với việc cuộn xuống tin mới nhất sau khi tải xong.
   * Chờ tải xong rồi mới focus thì hai việc không tranh nhau.
   */
  focusWhenReady?: boolean;

  // ===== đính kèm (tuỳ chọn: bỏ trống thì ô nhập chỉ nhận chữ) =====

  /** Nhận một tệp từ nút chọn tệp hoặc kéo-thả. */
  onPickFile?: (file: File) => void;
  /** Nhận nội dung dán; trả true nếu đã lấy được ảnh (để chặn dán mặc định). */
  onPasteFiles?: (items: DataTransferItemList | null) => boolean;
  /** URL xem trước ảnh đang chờ gửi; null = chưa chọn gì. */
  attachmentPreviewUrl?: string | null;
  /** Tên tệp đã chọn, hiện trong ô xem trước. */
  attachmentFileName?: string | null;
  attachmentFileSize?: number | null;
  attachmentUploading?: boolean;
  attachmentErrorKey?: string | null;
  /** Đã tải lên xong và sẵn sàng gửi — quyết định nút gửi có bật hay không. */
  attachmentReady?: boolean;
  onRemoveAttachment?: () => void;
  /** Mở lớp xem to ảnh đang chờ gửi. Bỏ trống thì ô xem trước không bấm được. */
  onOpenAttachment?: (src: string, alt: string) => void;
}> = ({
  onSend,
  disabled = false,
  sending = false,
  focusWhenReady = false,
  onPickFile,
  onPasteFiles,
  attachmentPreviewUrl = null,
  attachmentFileName = null,
  attachmentFileSize = null,
  attachmentUploading = false,
  attachmentErrorKey = null,
  attachmentReady = false,
  onRemoveAttachment,
  onOpenAttachment,
}) => {
  const { t } = useTranslation();
  const [value, setValue] = useState("");
  const [dragging, setDragging] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const attachmentsEnabled = Boolean(onPickFile);

  /**
   * Giãn chiều cao theo nội dung, chặn trên ở 120px.
   *
   * Đặt lại `height = "auto"` TRƯỚC khi đọc `scrollHeight`: không làm vậy thì
   * scrollHeight vẫn giữ chiều cao cũ và ô chỉ giãn ra chứ không bao giờ co lại khi
   * người dùng xoá chữ.
   */
  const autoGrow = useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 120)}px`;
  }, []);

  useEffect(() => {
    autoGrow();
  }, [value, autoGrow]);

  /**
   * Đặt con trỏ vào ô nhập một lần, khi trang đã sẵn sàng.
   *
   * `focused` chặn việc giành lại con trỏ: không có nó thì mỗi lần `focusWhenReady`
   * đổi (ví dụ tải thêm tin cũ) ô nhập lại kéo con trỏ về, kể cả khi người dùng
   * đang bấm ở chỗ khác.
   *
   * `preventScroll` vì việc cuộn đã có người lo: trang chat tự cuộn xuống tin mới
   * nhất, còn focus kèm cuộn sẽ đẩy vị trí đó lệch đi.
   */
  const focused = useRef(false);

  useEffect(() => {
    if (!focusWhenReady || disabled || focused.current) return;
    focused.current = true;
    textareaRef.current?.focus({ preventScroll: true });
  }, [focusWhenReady, disabled]);

  const submit = () => {
    const trimmed = value.trim();
    // Cho gửi khi CHỈ có ảnh: gửi ảnh chụp màn hình lỗi không kèm chữ là trường hợp
    // phổ biến nhất của tính năng này.
    if (disabled) return;
    if (!trimmed && !attachmentReady) return;
    onSend(trimmed);
    setValue("");
  };

  /**
   * Enter gửi, Shift+Enter xuống dòng.
   *
   * CHỈ trên màn hình rộng (matchMedia pointer: fine). Trên bàn phím ảo điện thoại,
   * Enter là phím xuống dòng duy nhất người dùng có, nên chiếm nó để gửi sẽ khiến họ
   * không thể gõ nổi một tin nhiều dòng.
   */
  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key !== "Enter" || e.shiftKey) return;
    const hasFinePointer =
      typeof window !== "undefined" && window.matchMedia("(pointer: fine)").matches;
    if (!hasFinePointer) return;
    e.preventDefault();
    submit();
  };

  /**
   * Dán ảnh trực tiếp vào ô nhập.
   *
   * `preventDefault` CHỈ khi thực sự lấy được ảnh: dán chặn vô điều kiện sẽ làm người
   * dùng không dán được văn bản nữa — mà dán văn bản (mã giao dịch, thông báo lỗi)
   * là việc họ làm thường xuyên hơn dán ảnh.
   */
  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    if (!onPasteFiles || disabled) return;
    if (onPasteFiles(e.clipboardData?.items ?? null)) {
      e.preventDefault();
    }
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    if (!onPickFile || disabled) return;
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer?.files?.[0];
    if (file) onPickFile(file);
  };

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && onPickFile) onPickFile(file);
    // Xoá giá trị để chọn LẠI CÙNG một tệp vẫn kích hoạt onChange: không xoá thì
    // người dùng bỏ ảnh rồi chọn lại đúng ảnh đó sẽ không thấy gì xảy ra.
    e.target.value = "";
  };

  // Đang tải ảnh lên thì chưa cho gửi: gửi lúc này thì tin đi mà không có ảnh.
  const canSend =
    (value.trim().length > 0 || attachmentReady) &&
    !disabled &&
    !sending &&
    !attachmentUploading;

  return (
    <div
      // `sticky bottom-0` + `pb-safe`: ô nhập luôn nằm trên vùng cử chỉ của iPhone,
      // nếu không thì nút gửi bị thanh home che một nửa.
      className={`sticky bottom-0 z-10 border-t bg-[#0d0d0f]/95 backdrop-blur-sm transition-colors ${
        dragging ? "border-primary/60 bg-primary/5" : "border-[#1f1f24]"
      }`}
      style={{ paddingBottom: "max(0.75rem, var(--safe-bottom, 0px))" }}
      onDragOver={(e) => {
        if (!attachmentsEnabled || disabled) return;
        e.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
    >
      {attachmentPreviewUrl && onRemoveAttachment && (
        <ChatAttachmentPreview
          previewUrl={attachmentPreviewUrl}
          uploading={attachmentUploading}
          errorKey={attachmentErrorKey}
          onRemove={onRemoveAttachment}
          fileName={attachmentFileName}
          fileSize={attachmentFileSize}
          onOpen={onOpenAttachment}
        />
      )}

      <div className="flex items-end gap-2 px-4 pt-3">
        {attachmentsEnabled && (
          <>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={handleFileInput}
              className="hidden"
              // Ô nhập tệp thật bị ẩn và thao tác qua nút bên dưới: giao diện mặc định
              // của <input type="file"> không thể tạo kiểu và trông lệch hẳn so với
              // phần còn lại của ô nhập.
              aria-hidden="true"
              tabIndex={-1}
              id="chat-composer-file"
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={disabled || attachmentUploading}
              aria-label={t("chat.attachment.add")}
              className="flex size-11 shrink-0 items-center justify-center rounded-full text-[#8b8b93] transition-colors hover:bg-[#1f1f24] hover:text-[#e4e4e7] disabled:opacity-40"
            >
              <ImagePlus className="size-[1.125rem]" />
            </button>
          </>
        )}

        <label className="sr-only" htmlFor="chat-composer-input">
          {t("chat.placeholder")}
        </label>
        <textarea
          id="chat-composer-input"
          ref={textareaRef}
          rows={1}
          value={value}
          maxLength={MAX_LENGTH}
          disabled={disabled}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          onPaste={handlePaste}
          placeholder={t("chat.placeholder")}
          className="max-h-[120px] min-h-11 grow resize-none rounded-2xl border border-[#28282e] bg-[#16161a] px-4 py-3 text-[0.8125rem] leading-relaxed text-[#e4e4e7] outline-none transition-colors placeholder:text-[#5b5b62] focus:border-primary/60 disabled:opacity-50"
        />
        <button
          type="button"
          onClick={submit}
          disabled={!canSend}
          aria-label={t("chat.send")}
          // Vùng bấm 44px theo hướng dẫn của Apple và Google, dù icon chỉ 18px.
          className="flex size-11 shrink-0 items-center justify-center rounded-full bg-primary text-white transition-all active:scale-95 disabled:bg-[#28282e] disabled:text-[#6b6b73]"
        >
          {sending ? (
            <Loader2 className="size-[1.125rem] animate-spin" />
          ) : (
            <SendHorizonal className="size-[1.125rem]" />
          )}
        </button>
      </div>

      {/* Đếm ký tự CHỈ hiện khi gần chạm giới hạn: hiện thường trực sẽ làm người dùng
          để ý tới con số thay vì nội dung họ đang viết. */}
      {value.length > MAX_LENGTH - 200 && (
        <div className="px-4 pt-1 text-end text-[0.625rem] text-[#6b6b73]">
          {value.length} / {MAX_LENGTH}
        </div>
      )}
    </div>
  );
};
