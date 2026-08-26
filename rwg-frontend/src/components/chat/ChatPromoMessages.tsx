"use client";

import React, { useEffect, useState } from "react";
import { useTranslation } from "@/context/LanguageContext";
import { USER_API_BASE_URL, USER_BASE_URL } from "@/lib/constants";
import { ChatBubble, type PendingChatMessage } from "./ChatBubble";

/**
 * Ảnh dự phòng, nằm trong `public/` nên KHÔNG cần xác thực.
 *
 * VẪN GIỮ dù ảnh giờ đã cấu hình được từ khu quản trị: bỏ hẳn thì khi backend chết hoặc
 * chưa ai đặt ảnh nào, bong bóng chào ĐẦU TIÊN khách thấy sẽ là ô "không tải được ảnh" —
 * ấn tượng tệ hơn nhiều so với việc hiện một ảnh hơi cũ.
 *
 * Khác hoàn toàn với ảnh đính kèm của chat: ảnh đó là biên lai và giấy tờ cá nhân nên
 * nằm sau endpoint có kiểm tra quyền. Ảnh này là tài liệu quảng bá gửi cho mọi khách.
 */
const FALLBACK_PROMO_IMAGE = "/images/chat-promo-tich-luy.jpg";

/**
 * Id giả của hai bong bóng khuyến mãi.
 *
 * Tiền tố `promo-` để phân biệt rõ với id thật (UUID) trong log và trong React DevTools.
 * Hai bong bóng này KHÔNG nằm trong state `messages`, nên id chỉ dùng làm `key`.
 */
const TEXT_ID = "promo-text";
const IMAGE_ID = "promo-image";

/** Hình dạng tối giản của `BannerResponse` — chỉ hai trường được dùng ở đây. */
interface ChatPromoBanner {
  mediaUrl: string;
  title: string;
}

/**
 * Lời chào khuyến mãi tự động, hiện MỖI LẦN khách mở khung chat.
 *
 * VÌ SAO KHÔNG LƯU VÀO CƠ SỞ DỮ LIỆU (như một tin nhắn thật):
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
 *
 * ẢNH LẤY TỪ KHU QUẢN TRỊ (`/banners/chat-promo`) thay vì gán cứng: trước đây mỗi lần
 * đổi ảnh là một lần sửa mã, build và triển khai lại toàn bộ frontend.
 */
export const ChatPromoMessages: React.FC<{
  /** Ngày giờ hiển thị; dùng mốc của tin cuối để hai bong bóng không nhảy giờ khi vẽ lại. */
  timestamp: string;
  /** Hiện avatar và tên ở bong bóng đầu — bỏ khi tin ngay trên đó cũng của nhân sự. */
  showSender: boolean;
  onOpenImage: (src: string, alt: string) => void;
}> = ({ timestamp, showSender, onOpenImage }) => {
  const { t } = useTranslation();

  /**
   * Ảnh do khu quản trị cấu hình. `null` = dùng ảnh dự phòng.
   *
   * KHÔNG có trạng thái "đang tải" riêng: ảnh dự phòng hiện ngay từ lần vẽ đầu, rồi
   * được thay khi API trả về. Chờ API xong mới vẽ thì bong bóng chào xuất hiện trễ hơn
   * phần còn lại của hội thoại — trông như khung chat bị giật.
   */
  const [promo, setPromo] = useState<ChatPromoBanner | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const res = await fetch(`${USER_API_BASE_URL}/banners/chat-promo`);
        // 204 = chưa ai cấu hình ảnh nào. Đây là trạng thái HỢP LỆ, không phải lỗi, nên
        // im lặng dùng ảnh dự phòng. `res.json()` trên body rỗng sẽ ném lỗi nên phải
        // chặn trước.
        if (!res.ok || res.status === 204) return;
        const data: ChatPromoBanner = await res.json();
        if (!cancelled && data?.mediaUrl) setPromo(data);
      } catch {
        // Mạng lỗi hoặc backend chết: giữ ảnh dự phòng, không ghi gì ra console —
        // khách mở chat không phải lúc để đổ lỗi kỹ thuật vào bảng điều khiển.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  /**
   * Đường dẫn ảnh cuối cùng.
   *
   * Tệp do khu quản trị tải lên nằm dưới `/uploads` và do BACKEND phục vụ, nên cần ghép
   * tiền tố domain của backend. Ảnh dự phòng nằm trong `public/` của frontend nên giữ
   * nguyên đường dẫn tương đối.
   */
  const promoSrc = promo
    ? promo.mediaUrl.startsWith("/uploads")
      ? `${USER_BASE_URL}${promo.mediaUrl}`
      : promo.mediaUrl
    : FALLBACK_PROMO_IMAGE;

  const promoAlt = promo?.title || t("chat.promo.image_alt");

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
    attachmentUrl: promoSrc,
    attachmentType: "IMAGE",
    attachmentName: promoAlt,
    // `localAttachmentUrl` khiến ChatImageAttachment dùng thẳng đường dẫn này thay vì
    // gọi endpoint có xác thực — cả ảnh trong public/ và ảnh dưới /uploads/media đều
    // phục vụ công khai nên gán trực tiếp là đủ.
    localAttachmentUrl: promoSrc,
  };

  return (
    <>
      <ChatBubble message={textMessage} showSender={showSender} />
      <ChatBubble
        // `key` theo đường dẫn ảnh: không có nó thì khi API trả về ảnh mới, React dùng
        // lại đúng thẻ <img> cũ và ChatImageAttachment không nhận ra cần đổi nguồn.
        key={promoSrc}
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
