"use client";

import { useEffect, useRef } from "react";
import { Client } from "@stomp/stompjs";
import { getAdminToken } from "@/lib/adminApi";
import { ADMIN_WS_URL } from "@/lib/constants";

/**
 * Gói sự kiện chat, khớp `ChatEventPayload` của backend.
 *
 * `message` chỉ có với type MESSAGE; `status` chỉ có nghĩa với type CONVERSATION.
 */
export interface AdminChatEvent {
  type: "MESSAGE" | "READ" | "CONVERSATION" | "MESSAGES_DELETED";
  conversationId: string;
  targetUserId: string;
  message: {
    id: string;
    conversationId: string;
    senderType: "PLAYER" | "STAFF" | "SYSTEM";
    senderId: string | null;
    senderUsername: string | null;
    /** Có thể rỗng khi tin chỉ có ảnh. */
    body: string;
    /**
     * Ảnh đính kèm. PHẢI có trong kiểu này, không chỉ trong response HTTP: tin đến qua
     * WebSocket được đưa thẳng vào danh sách đang hiển thị, nên thiếu bốn trường này
     * thì ảnh người chơi vừa gửi hiện thành bong bóng trống — và chỉ hiện ra sau khi
     * nhân sự tải lại trang.
     */
    attachmentUrl: string | null;
    attachmentType: "IMAGE" | null;
    attachmentName: string | null;
    attachmentSize: number | null;
    readAt: string | null;
    clientMsgId: string | null;
    createdAt: string;
  } | null;
  /**
   * Gói chỉ dành cho nhân sự (hiện chỉ có thẻ duyệt lệnh rút).
   *
   * Người chơi không bao giờ nhận gói này — backend chặn ở
   * `ChatEventPublisher.deliverLocally`.
   *
   * KIỂU NÀY CỐ TÌNH KHÔNG CÓ trường `withdrawal`: backend không đính dữ liệu lệnh vào
   * gói realtime (thông tin ngân hàng nằm ở package khác, đi lấy nó sẽ biến một hàm ghi
   * chat thành phụ thuộc vào hai package nữa). Nên gói này KHÔNG đủ để vẽ một thẻ, và
   * chỗ nhận phải tải lại luồng thay vì chèn thẳng vào danh sách đang hiển thị.
   */
  staffOnly: boolean;
  status: string | null;
  /** Danh sách id của tin bị xóa, chỉ có với type = MESSAGES_DELETED. */
  deletedMessageIds?: string[] | null;
  serverTime: string;
}

/**
 * Kết nối WebSocket của khu quản trị và nghe kênh hộp thư hỗ trợ.
 *
 * VÌ SAO là một client RIÊNG, không dùng lại client của `NotificationContext`:
 * khu quản trị chạy trên backend cổng 8081 với broker STOMP riêng, và token quản
 * trị chỉ được app đó chấp nhận (`rwg.websocket.audience: STAFF`). Nối token quản
 * trị vào cổng 8080 sẽ bị từ chối ngay ở frame CONNECT.
 *
 * `/topic/admin/chat` là kênh CHUNG cho toàn bộ nhân sự, không phải kênh riêng
 * từng người: một luồng có thể do bất kỳ ai nhận, nên mọi người cần thấy tin mới
 * để hàng đợi không bị bỏ sót. Quyền đăng ký kênh này được kiểm ở frame SUBSCRIBE
 * trong `WsAuthChannelInterceptor` — người chơi không vào được.
 */
export function useAdminChatSocket(onEvent: (event: AdminChatEvent) => void) {
  // Giữ callback trong ref để việc đổi hàm xử lý KHÔNG làm dựng lại kết nối.
  // Truyền `onEvent` thẳng vào mảng phụ thuộc của effect sẽ khiến mỗi lần trang
  // render lại là một lần ngắt và mở lại WebSocket.
  const handlerRef = useRef(onEvent);
  handlerRef.current = onEvent;

  useEffect(() => {
    const token = getAdminToken();
    if (!token) return;

    const client = new Client({
      brokerURL: ADMIN_WS_URL.replace(/^http/, "ws"),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: () => {},
    });

    client.onConnect = () => {
      client.subscribe("/topic/admin/chat", (msg) => {
        try {
          handlerRef.current(JSON.parse(msg.body) as AdminChatEvent);
        } catch (err) {
          // Một gói lỗi định dạng không được làm chết cả kênh: các gói sau vẫn phải
          // tới được, nếu không thì hộp thư đứng im mà không ai biết vì sao.
          console.error("Lỗi xử lý gói chat từ WebSocket:", err);
        }
      });
    };

    client.onStompError = (frame) => {
      console.warn("STOMP error (admin chat):", frame.body);
    };

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, []);
}
