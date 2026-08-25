"use client";

import React from "react";
import { Check, CheckCheck, Clock, Headphones } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";
import type { ChatMessage } from "@/lib/playerApi";
import { ChatImageAttachment } from "./ChatImageAttachment";

/**
 * Một tin nhắn đang chờ server xác nhận.
 *
 * Hiện tin NGAY lúc bấm gửi thay vì chờ phản hồi: trên mạng 3G, chờ 800ms mới thấy
 * chữ mình vừa gõ khiến người dùng tưởng nút không ăn và bấm lại. `pending` cho biết
 * phải vẽ ở trạng thái mờ kèm icon đồng hồ.
 */
export interface PendingChatMessage extends ChatMessage {
  pending?: boolean;
  failed?: boolean;
  /**
   * Blob URL của ảnh ở máy, chỉ có với tin do chính phiên này gửi.
   *
   * Dùng để hiện ảnh ngay mà không gọi lại server — xem {@code localSrc} bên
   * {@code ChatImageAttachment} để biết vì sao tải lại sẽ thất bại ở thời điểm đó.
   */
  localAttachmentUrl?: string | null;
}

/**
 * Dòng thông báo của hệ thống.
 *
 * Tách khỏi bong bóng chat: đây không phải lời của ai cả, nên nếu vẽ thành bong bóng
 * thì người chơi sẽ đọc nó như một câu nhân viên nói.
 */
const SystemLine: React.FC<{ body: string; time: string }> = ({ body, time }) => {
  const { t } = useTranslation();
  // body là KHOÁ DỊCH do backend trả về (vd "chat.system.assigned"). `t()` trả lại
  // chính khoá khi không tìm thấy, nên chuỗi lạ vẫn hiện ra để phát hiện được.
  return (
    <li className="flex justify-center px-4 py-1.5">
      <span className="max-w-[85%] rounded-full bg-white/[0.04] px-3.5 py-1.5 text-center text-[0.6875rem] leading-snug text-[#7a7a82]">
        {t(body)}
        <span className="ms-1.5 text-[#5b5b62]">{time}</span>
      </span>
    </li>
  );
};

/**
 * Một bong bóng tin nhắn.
 *
 * Tin của mình dạt phải với màu nhấn, tin của nhân sự dạt trái trên nền tối. Đây là
 * quy ước mọi ứng dụng nhắn tin đều dùng, nên người dùng không phải học gì mới.
 */
export const ChatBubble: React.FC<{
  message: PendingChatMessage;
  /** Hiện avatar và tên: chỉ với tin ĐẦU của một chuỗi cùng người gửi. */
  showSender: boolean;
  /**
   * Tải ảnh đính kèm, trả về blob URL.
   *
   * Truyền từ ngoài vì người chơi và nhân sự dùng hai token khác nhau trên hai
   * cổng khác nhau. Bỏ trống thì ảnh không được vẽ — dùng ở nơi chỉ có chữ.
   */
  loadAttachment?: (attachmentUrl: string) => Promise<string>;
  onOpenImage?: (src: string, alt: string) => void;
}> = ({ message, showSender, loadAttachment, onOpenImage }) => {
  const { t } = useTranslation();

  const time = new Date(message.createdAt).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });

  if (message.senderType === "SYSTEM") {
    return <SystemLine body={message.body} time={time} />;
  }

  const isMine = message.senderType === "PLAYER";
  const hasText = Boolean(message.body && message.body.trim().length > 0);
  const hasImage = Boolean(
    message.attachmentUrl && message.attachmentType === "IMAGE" && loadAttachment && onOpenImage
  );

  return (
    <li className={`flex px-4 py-0.5 ${isMine ? "justify-end" : "justify-start"}`}>
      {/* Cột avatar giữ chỗ cố định 32px cả khi không vẽ avatar, để các bong bóng
          trong cùng một chuỗi thẳng lề nhau thay vì so le. */}
      {!isMine && (
        <div className="me-2 w-8 shrink-0">
          {showSender && (
            <div className="flex size-8 items-center justify-center rounded-full border border-primary/25 bg-primary/10">
              <Headphones className="size-4 text-primary" />
            </div>
          )}
        </div>
      )}

      {/* Bong bong CHI CO ANH duoc rong hon bong bong chu.
          78% la gioi han hop ly cho CHU: dong qua dai thi mat kho doi dong. Anh
          khong co van de do - cang rong cang de xem, dac biet la anh khuyen mai co
          bang so lieu chu nho. 92% vua du chua cho cot avatar 32px. */}
      <div
        className={[
          "flex flex-col",
          hasImage && !hasText ? "max-w-[92%]" : "max-w-[78%]",
          isMine ? "items-end" : "items-start",
        ].join(" ")}
      >
        {showSender && !isMine && (
          <span className="mb-1 ps-0.5 text-[0.625rem] font-semibold tracking-wide text-[#8b8b93]">
            {t("chat.staff")}
          </span>
        )}

        <div
          className={[
            // Ảnh cần đệm hẹp hơn để không có viền dày quanh nó; chữ thì cần đệm đủ để
            // dễ đọc. Một giá trị chung cho cả hai sẽ làm một trong hai trông sai.
            hasImage && !hasText ? "p-1" : "px-3.5 py-2.5",
            "text-[0.8125rem] leading-relaxed",
            // Góc nhọn ở phía người gửi tạo cảm giác bong bóng "trỏ" về đúng người,
            // không cần vẽ thêm mũi nhọn tam giác.
            isMine
              ? "rounded-2xl rounded-br-md bg-primary text-white"
              : "rounded-2xl rounded-bl-md border border-[#28282e] bg-[#1b1b1f] text-[#e4e4e7]",
            message.pending ? "opacity-60" : "",
            message.failed ? "border border-rose-500/50" : "",
          ].join(" ")}
        >
          {hasImage && (
            <div className={hasText ? "mb-2" : ""}>
              <ChatImageAttachment
                attachmentUrl={message.attachmentUrl!}
                attachmentName={message.attachmentName}
                load={loadAttachment!}
                onOpen={onOpenImage!}
                localSrc={message.localAttachmentUrl}
              />
            </div>
          )}

          {/* `whitespace-pre-wrap` giữ lại các dòng người dùng tự xuống: mất chúng thì
              một danh sách nhiều dòng bị dồn thành một khối chữ không đọc được.
              `break-words` chặn một chuỗi dài không khoảng trắng (link, mã giao dịch)
              làm bong bóng tràn ra ngoài khung.

              CHỈ vẽ khi có chữ: tin chỉ có ảnh sẽ có một thẻ <p> rỗng chiếm thêm một
              dòng chiều cao ngay dưới ảnh. */}
          {hasText && (
            <p className="whitespace-pre-wrap break-words">{message.body}</p>
          )}
        </div>

        <div className="mt-1 flex items-center gap-1 px-0.5 text-[0.5625rem] text-[#6b6b73]">
          <span>{time}</span>
          {isMine && (
            <>
              {message.pending ? (
                <Clock className="size-3" aria-label={t("chat.sending")} />
              ) : message.readAt ? (
                // Hai dấu tích = phía kia ĐÃ XEM. Thông tin này giảm hẳn số lần người
                // chơi gửi lại cùng một câu hỏi vì tưởng không ai đọc.
                <CheckCheck className="size-3 text-sky-400" aria-label={t("chat.seen")} />
              ) : (
                <Check className="size-3" aria-label={t("chat.delivered")} />
              )}
            </>
          )}
        </div>
      </div>
    </li>
  );
};
