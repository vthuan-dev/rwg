"use client";

import React from "react";
import { useTranslation } from "@/context/LanguageContext";
import { ChatBubble, type PendingChatMessage } from "./ChatBubble";

/**
 * Đường dẫn ảnh khuyến mãi, đặt trong `public/` nên KHÔNG cần xác thực.
 *
 * Khác hoàn toàn với ảnh đính kèm của chat: ảnh đó là biên lai và giấy tờ cá nhân nên
 * nằm sau endpoint có kiểm tra quyền. Ảnh này là tài liệu quảng bá gửi cho mọi khách,
 * nên để nó đi qua đường xác thực chỉ tạo thêm một lượt gọi mạng không mua được gì.
 */
const PROMO_IMAGE = "/images/chat-promo-tich-luy.jpg";

/**
 * Id giả của hai bong bóng khuyến mãi.
 *
 * Tiền tố `promo-` để phân biệt rõ với id thật (UUID) trong log và trong React DevTools.
 * Hai bong bóng này KHÔNG nằm trong state `messages`, nên id chỉ dùng làm `key`.
 */
const TEXT_ID = "promo-text";
const IMAGE_ID = "promo-image";

/**
 * Lời chào khuyến mãi tự động, hiện MỖI LẦN khách mở khung chat.
 *
 * VÌ SAO KHÔNG LƯU VÀO CƠ SỞ DỮ LIỆU:
 * - Yêu cầu là khách thấy nó ở mọi lần mở. Ghi một tin thật mỗi lần mở thì sau một
 *   tuần hội thoại có vài chục bản sao của cùng một lời chào, và lịch sử khiếu nại
 *   thật bị chôn giữa chúng.
 * - Đoạn xem trước trong hộp thư quản trị lấy từ tin CUỐI CÙNG. Ghi tin thật thì mọi
 *   dòng trong hàng đợi đều hiện nội dung quảng cáo thay vì câu hỏi của khách — đúng
 *   thứ nhân sự cần đọc để biết chọn luồng nào trước.
 * - Bộ đếm chưa đọc sẽ nhảy lên chỉ vì khách mở trang.
 *
 * Vẽ ở phía giao diện giải quyết cả ba mà vẫn đáp ứng đúng yêu cầu.
 *
 * HAI BONG BÓNG RIÊNG, không phải một bong bóng có cả chữ và ảnh: yêu cầu là "gửi chữ
 * này xong đến ảnh", và {@code ChatBubble} luôn vẽ ảnh TRƯỚC chữ trong cùng một bong
 * bóng. Tách ra cũng giống cách một nhân viên thật sẽ gửi.
 */
export const ChatPromoMessages: React.FC<{
  /** Ngày giờ hiển thị; dùng mốc của tin cuối để hai bong bóng không nhảy giờ khi vẽ lại. */
  timestamp: string;
  /** Hiện avatar và tên ở bong bóng đầu — bỏ khi tin ngay trên đó cũng của nhân sự. */
  showSender: boolean;
  onOpenImage: (src: string, alt: string) => void;
}> = ({ timestamp, showSender, onOpenImage }) => {
  const { t } = useTranslation();

  const base = {
    conversationId: "",
    senderType: "STAFF" as const,
    senderId: null,
    // null để ChatBubble tự dùng t("chat.staff") — tên hiển thị theo ngôn ngữ đang chọn.
    senderUsername: null,
    attachmentUrl: null,
    attachmentType: null,
    attachmentName: null,
    attachmentSize: null,
    readAt: null,
    clientMsgId: null,
    createdAt: timestamp,
  };

  const textMessage: PendingChatMessage = {
    ...base,
    id: TEXT_ID,
    body: t("chat.promo.text"),
  };

  const imageMessage: PendingChatMessage = {
    ...base,
    id: IMAGE_ID,
    body: "",
    attachmentUrl: PROMO_IMAGE,
    attachmentType: "IMAGE",
    attachmentName: t("chat.promo.image_alt"),
    // `localAttachmentUrl` khiến ChatImageAttachment dùng thẳng đường dẫn này thay vì
    // gọi endpoint có xác thực — ảnh nằm trong public/ nên gán trực tiếp là đủ.
    localAttachmentUrl: PROMO_IMAGE,
  };

  return (
    <>
      <ChatBubble message={textMessage} showSender={showSender} />
      <ChatBubble
        message={imageMessage}
        showSender={false}
        // `load` không bao giờ được gọi vì đã có `localAttachmentUrl`, nhưng ChatBubble
        // đòi có mặt để quyết định việc vẽ ảnh. Trả về Promise bị từ chối rõ ràng thay
        // vì một hàm rỗng: nếu vì lý do nào đó nó ĐƯỢC gọi, ta muốn thấy ô "không tải
        // được ảnh" chứ không phải một Promise treo vĩnh viễn.
        loadAttachment={() => Promise.reject(new Error("promo image is local"))}
        onOpenImage={onOpenImage}
      />
    </>
  );
};
