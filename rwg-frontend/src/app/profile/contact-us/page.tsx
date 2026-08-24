"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { MessageSquareDashed, Loader2, ChevronUp } from "lucide-react";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { ChatBubble, type PendingChatMessage } from "@/components/chat/ChatBubble";
import { ChatComposer } from "@/components/chat/ChatComposer";
import { ChatPromoMessages } from "@/components/chat/ChatPromoMessages";
import { ImageLightbox } from "@/components/chat/ImageLightbox";
import { useChatAttachment } from "@/components/chat/useChatAttachment";
import { useChatAutoScroll } from "@/components/chat/useChatAutoScroll";
import { useTranslation } from "@/context/LanguageContext";
import { CHAT_EVENT, useNotification } from "@/context/NotificationContext";
import {
  ApiError,
  clearPlayerTokens,
  getPlayerToken,
  getChatConversation,
  getChatMessages,
  sendChatMessage,
  markChatRead,
  newClientMsgId,
  uploadChatAttachment,
  fetchAuthedBlobUrl,
  chatAttachmentPath,
  type ChatConversation,
  type ChatMessage,
} from "@/lib/playerApi";

/**
 * Trang trò chuyện trực tiếp với bộ phận hỗ trợ.
 *
 * Đường dẫn `/profile/contact-us` là đường dẫn ô "Trò chuyện trực tiếp" ở trang chủ
 * đã trỏ tới từ trước (xem `QuickActions.tsx`) — nó vốn dẫn tới một trang chưa tồn
 * tại và trả về 404.
 *
 * THỨ TỰ DỮ LIỆU: API trả về MỚI NHẤT TRƯỚC (phân trang theo mốc thời gian). Giao
 * diện cần cũ trên mới dưới như mọi ứng dụng nhắn tin, nên state giữ danh sách đã
 * đảo chiều: cuối mảng là tin mới nhất. Nhờ vậy thêm tin mới chỉ là `push`, và tải
 * trang cũ hơn là `unshift` — không phải sắp xếp lại gì.
 */
export default function ContactUsPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const { refreshChatUnreadCount } = useNotification();

  const [checked, setChecked] = useState(false);
  const [conversation, setConversation] = useState<ChatConversation | null>(null);
  const [messages, setMessages] = useState<PendingChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [hasOlder, setHasOlder] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Ảnh đang xem toàn màn hình; null = không mở. */
  const [lightbox, setLightbox] = useState<{ src: string; alt: string } | null>(null);

  const attachment = useChatAttachment(uploadChatAttachment);

  /**
   * Tải ảnh đính kèm qua endpoint có xác thực.
   *
   * `useCallback` không phải để tối ưu mà là điều kiện đúng đắn: hàm này nằm trong
   * mảng phụ thuộc của `useEffect` bên {@code ChatImageAttachment}. Tạo mới mỗi lần
   * vẽ lại thì effect đó chạy lại liên tục và mọi ảnh bị tải lại sau mỗi tin nhắn mới.
   */
  const loadAttachment = useCallback(
    (attachmentUrl: string) => fetchAuthedBlobUrl(chatAttachmentPath(attachmentUrl)),
    []
  );

  /**
   * Khung cuộn + các hàm điều khiển việc dán đáy.
   *
   * Dùng hook chung thay vì tự gọi `scrollTo` một lần sau khi tải: chiều cao vùng tin
   * nhắn còn đổi sau đó (ô nhập tự giãn, ảnh khuyến mãi vẽ xong, bàn phím ảo mở), nên
   * cuộn một lần là không đủ — xem chú thích trong {@code useChatAutoScroll}.
   */
  const { scrollRef, scrollToBottom, suspendAutoScroll } = useChatAutoScroll(!loading);

  /**
   * Giờ hiển thị của lời chào khuyến mãi, chốt MỘT LẦN lúc mở trang.
   *
   * Không gọi `new Date()` trực tiếp trong phần render: mỗi lần vẽ lại (gõ một chữ, có
   * tin mới đến) sẽ cho một giá trị khác và giờ dưới bong bóng nhảy liên tục.
   */
  const [promoTimestamp] = useState(() => new Date().toISOString());

  /**
   * Blob URL của các ảnh đã gửi trong phiên này, để thu hồi khi rời trang.
   *
   * Quyền sở hữu những URL này được `attachment.detach()` chuyển ra khỏi hook, nên hook
   * không còn dọn chúng nữa. Không có tập này thì mỗi ảnh gửi đi là vài MB bị giữ lại
   * trong bộ nhớ tab cho tới khi tải lại trang.
   */
  const localBlobUrls = useRef<Set<string>>(new Set());

  useEffect(
    () => () => {
      for (const url of localBlobUrls.current) {
        URL.revokeObjectURL(url);
      }
      localBlobUrls.current.clear();
    },
    []
  );

  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }
    setChecked(true);
  }, [router]);


  /**
   * Tải luồng và trang tin nhắn đầu tiên.
   *
   * TRẢ VỀ dữ liệu thay vì gọi setState bên trong: đây là quy ước của dự án ở các
   * trang khu quản trị, giúp effect kiểm soát được việc bỏ kết quả khi component đã
   * bị tháo.
   */
  const loadInitial = useCallback(async () => {
    const [conv, page] = await Promise.all([
      getChatConversation(),
      getChatMessages(),
    ]);
    return { conv, page };
  }, []);

  useEffect(() => {
    if (!checked) return;
    let cancelled = false;

    setLoading(true);
    loadInitial()
      .then(({ conv, page }) => {
        if (cancelled) return;
        setConversation(conv);
        // API trả mới nhất trước; đảo lại để cũ ở trên, mới ở dưới.
        setMessages([...page].reverse());
        // Đủ một trang đầy nghĩa là RẤT CÓ THỂ còn tin cũ hơn. Không thể biết chắc mà
        // không gọi thêm một request, nên chọn hiện nút và để lần bấm đó tự trả lời.
        setHasOlder(page.length >= 30);
        setError(null);
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 401) {
          clearPlayerTokens();
          router.replace("/login");
          return;
        }
        setError(t("chat.load_failed"));
        console.error("Lỗi tải cuộc trò chuyện:", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [checked, loadInitial, router, t]);

  /**
   * Đánh dấu đã đọc khi mở trang.
   *
   * Tách khỏi effect tải dữ liệu: đây là lệnh GHI, và gộp vào đường đọc sẽ khiến mọi
   * lần thử lại việc tải cũng gửi kèm một lệnh ghi.
   */
  useEffect(() => {
    if (!conversation) return;
    void markChatRead()
      .then(() => refreshChatUnreadCount())
      .catch(() => {
        // Không cứu được gì và cũng không ảnh hưởng nội dung đang xem.
      });
  }, [conversation, refreshChatUnreadCount]);


  /**
   * Nhận gói realtime từ WebSocket dùng chung của `NotificationContext`.
   *
   * Nghe CustomEvent thay vì mở kết nối STOMP riêng: cả ứng dụng chỉ có MỘT client
   * WebSocket, mở thêm ở đây sẽ nhân đôi số phiên trên server.
   */
  useEffect(() => {
    if (!conversation) return;

    const handler = (event: Event) => {
      const payload = (event as CustomEvent).detail;
      if (!payload || payload.conversationId !== conversation.id) return;

      if (payload.type === "READ") {
        // Nhân sự vừa xem: đóng dấu mọi tin của mình để đổi sang hai dấu tích.
        const now = new Date().toISOString();
        setMessages((prev) =>
          prev.map((m) =>
            m.senderType === "PLAYER" && !m.readAt ? { ...m, readAt: now } : m
          )
        );
        return;
      }

      if (payload.type === "CONVERSATION") {
        setConversation((prev) => (prev ? { ...prev, status: payload.status } : prev));
        return;
      }

      if (payload.type !== "MESSAGE" || !payload.message) return;
      const incoming: ChatMessage = payload.message;

      setMessages((prev) => {
        // Đã có (do response HTTP về trước gói WebSocket) thì bỏ qua.
        if (prev.some((m) => m.id === incoming.id)) return prev;

        // Gói WebSocket của tin CHÍNH MÌNH vừa gửi: thay thế bản đang chờ theo
        // clientMsgId thay vì thêm mới, nếu không sẽ hiện hai lần.
        if (incoming.clientMsgId) {
          const pendingIndex = prev.findIndex(
            (m) => m.pending && m.clientMsgId === incoming.clientMsgId
          );
          if (pendingIndex >= 0) {
            const next = [...prev];
            next[pendingIndex] = incoming;
            return next;
          }
        }

        return [...prev, incoming];
      });

      if (incoming.senderType === "STAFF") {
        // Có tin mới của nhân sự và người dùng đang xem trang này, nên đánh dấu đã
        // đọc ngay: để nguyên thì viên đỏ vẫn sáng dù họ vừa đọc xong.
        void markChatRead()
          .then(() => refreshChatUnreadCount())
          .catch(() => {});
      }
      scrollToBottom(true);
    };

    window.addEventListener(CHAT_EVENT, handler);
    return () => window.removeEventListener(CHAT_EVENT, handler);
  }, [conversation, refreshChatUnreadCount, scrollToBottom]);

  /** Tải thêm tin cũ hơn, giữ nguyên vị trí đang đọc. */
  const loadOlder = async () => {
    const oldest = messages.find((m) => !m.pending);
    if (!oldest || loadingOlder) return;

    const el = scrollRef.current;
    const heightBefore = el?.scrollHeight ?? 0;

    // Ngưng ghim đáy trong lúc chèn: chèn vào ĐẦU danh sách làm scrollHeight tăng vọt,
    // và bộ theo dõi của hook sẽ hiểu đó là "có nội dung mới" rồi kéo thẳng xuống cuối —
    // đúng lúc người dùng vừa bấm để xem đoạn cũ hơn.
    const resume = suspendAutoScroll();

    setLoadingOlder(true);
    try {
      const page = await getChatMessages(oldest.createdAt);
      setMessages((prev) => [...[...page].reverse(), ...prev]);
      setHasOlder(page.length >= 30);

      // Giữ nguyên tin người dùng đang xem: chèn nội dung vào ĐẦU danh sách sẽ đẩy
      // mọi thứ xuống, nên phải bù lại đúng phần chiều cao vừa thêm. Không bù thì
      // màn hình nhảy về đầu và người dùng mất chỗ đang đọc.
      requestAnimationFrame(() => {
        const target = scrollRef.current;
        if (target) {
          target.scrollTop = target.scrollHeight - heightBefore;
        }
        resume();
      });
    } catch (err) {
      console.warn("Lỗi tải tin cũ hơn:", err);
      resume();
    } finally {
      setLoadingOlder(false);
    }
  };

  /** Gửi một tin, hiện ngay ở trạng thái chờ rồi thay bằng bản ghi thật. */
  const handleSend = async (body: string) => {
    if (!conversation) return;

    // Chụp lại ảnh đính kèm TRƯỚC khi dọn: `attachment.detach()` bên dưới chạy đồng
    // thời với lời gọi mạng, và đọc lại sau đó sẽ thấy null.
    const pendingAttachment = attachment.attachment;
    if (!body && !pendingAttachment) return;

    // `detach` chứ không phải `clear`: nó trả lại blob URL và giao quyền sở hữu cho ta,
    // để bong bóng vừa tạo hiện được ảnh ngay. `clear` sẽ thu hồi URL đó và ảnh trong
    // tin vừa gửi thành ô vỡ.
    const localUrl = pendingAttachment ? attachment.detach() : null;
    if (localUrl) {
      localBlobUrls.current.add(localUrl);
    }

    const clientMsgId = newClientMsgId();
    const optimistic: PendingChatMessage = {
      id: `pending-${clientMsgId}`,
      conversationId: conversation.id,
      senderType: "PLAYER",
      senderId: null,
      senderUsername: null,
      body,
      attachmentUrl: pendingAttachment?.url ?? null,
      attachmentType: pendingAttachment ? "IMAGE" : null,
      attachmentName: pendingAttachment?.name ?? null,
      attachmentSize: pendingAttachment?.size ?? null,
      readAt: null,
      clientMsgId,
      createdAt: new Date().toISOString(),
      pending: true,
      localAttachmentUrl: localUrl,
    };

    setMessages((prev) => [...prev, optimistic]);
    setSending(true);
    // Không gọi `clear()` ở đây: `detach()` phía trên đã dọn trạng thái, chỉ khác là nó
    // không thu hồi URL. Gọi cả hai thì ảnh trong bong bóng sẽ vỡ.
    if (!pendingAttachment) {
      attachment.clear();
    }
    // Cuộn ngay, không chờ server: người dùng phải thấy tin mình vừa gửi.
    requestAnimationFrame(() => scrollToBottom(true));

    try {
      const saved = await sendChatMessage(body, clientMsgId, pendingAttachment);
      // GIỮ blob cục bộ trên bản ghi thật: bản ghi từ server chỉ có đường dẫn API, mà
      // tải theo đường đó thì phải chờ thêm một vòng mạng cho một ảnh đang có sẵn.
      const savedWithLocal: PendingChatMessage = { ...saved, localAttachmentUrl: localUrl };
      setMessages((prev) => {
        // Gói WebSocket có thể đã thay thế bản chờ trước khi response HTTP về.
        if (prev.some((m) => m.id === saved.id)) {
          return prev.filter((m) => m.id !== optimistic.id);
        }
        return prev.map((m) => (m.id === optimistic.id ? savedWithLocal : m));
      });
      setError(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearPlayerTokens();
        router.replace("/login");
        return;
      }
      // GIỮ tin đã gõ trên màn hình và đánh dấu lỗi, KHÔNG xoá: xoá đi là mất nội
      // dung người dùng vừa viết, và họ phải gõ lại từ đầu.
      setMessages((prev) =>
        prev.map((m) =>
          m.id === optimistic.id ? { ...m, pending: false, failed: true } : m
        )
      );
      setError(
        err instanceof ApiError && err.message ? err.message : t("chat.send_failed")
      );
    } finally {
      setSending(false);
    }
  };

  const isClosed = conversation?.status === "CLOSED";

  return (
    <MobileShell
      header={
        <TopNavigationBar
          backHref="/profile"
          title={t("chat.title")}
          rightSlot={
            <div className="flex items-center gap-1.5 text-[0.625rem] font-semibold text-[#8b8b93]">
              <span className="size-1.5 rounded-full bg-emerald-500" />
              {t("chat.online_hint")}
            </div>
          }
        />
      }
    >
      {/* `min-h-0` là bắt buộc trên phần tử cuộn nằm trong flex column: thiếu nó thì
          flex item lấy chiều cao theo nội dung và cả trang cuộn thay vì chỉ vùng tin
          nhắn — lúc đó ô nhập trôi khỏi màn hình. */}
      <main className="flex min-h-0 grow flex-col bg-[#0d0d0f]">
        <div ref={scrollRef} className="flex min-h-0 grow flex-col overflow-y-auto py-3">
          {loading ? (
            <div className="flex grow flex-col items-center justify-center gap-y-3">
              <Loader2 className="size-6 animate-spin text-primary" />
              <span className="text-xs font-bold text-primary">{t("chat.loading")}</span>
            </div>
          ) : error && messages.length === 0 ? (
            <div className="flex grow flex-col items-center justify-center px-8 text-center">
              <p className="text-xs font-semibold text-rose-500">{error}</p>
            </div>
          ) : messages.length === 0 ? (
            // Hội thoại trống: vẫn hiện lời chào khuyến mãi, KHÔNG hiện ô “chưa có tin
            // nhắn” — hai thứ cùng lúc thì màn hình vừa nói trống vừa có hai bong bóng.
            <ul className="flex flex-col pt-1">
              <ChatPromoMessages
                timestamp={promoTimestamp}
                showSender
                onOpenImage={(src, alt) => setLightbox({ src, alt })}
              />
            </ul>
          ) : (
            <>
              {hasOlder && (
                <div className="mb-2 flex justify-center px-4">
                  <button
                    type="button"
                    onClick={() => void loadOlder()}
                    disabled={loadingOlder}
                    className="flex items-center gap-1.5 rounded-full border border-[#28282e] bg-[#16161a] px-3.5 py-1.5 text-[0.6875rem] font-semibold text-[#8b8b93] transition-colors active:bg-[#1f1f24]"
                  >
                    {loadingOlder ? (
                      <Loader2 className="size-3 animate-spin" />
                    ) : (
                      <ChevronUp className="size-3" />
                    )}
                    {t("chat.load_more")}
                  </button>
                </div>
              )}

              <ul className="flex flex-col">
                {messages.map((message, index) => {
                  // Chỉ hiện avatar + tên ở tin ĐẦU của một chuỗi liên tiếp cùng người
                  // gửi: lặp lại ở mọi bong bóng làm cột trái rối và tốn chỗ ngang.
                  const prev = index > 0 ? messages[index - 1] : null;
                  const showSender =
                    !prev ||
                    prev.senderType !== message.senderType ||
                    prev.senderUsername !== message.senderUsername;

                  return (
                    <ChatBubble
                      key={message.id}
                      message={message}
                      showSender={showSender}
                      loadAttachment={loadAttachment}
                      onOpenImage={(src, alt) => setLightbox({ src, alt })}
                    />
                  );
                })}

                {/* Lời chào khuyến mãi ở CUỐI danh sách, không phải đầu: nó phải nằm
                    trong tầm nhìn mỗi lần khách mở trang, mà màn hình chat luôn cuộn
                    xuống đáy. Đầu danh sách thì sau vài câu trò chuyện nó trôi mất và
                    khách không bao giờ thấy. */}
                <ChatPromoMessages
                  timestamp={promoTimestamp}
                  showSender={messages[messages.length - 1]?.senderType !== "STAFF"}
                  onOpenImage={(src, alt) => setLightbox({ src, alt })}
                />
              </ul>
            </>
          )}
        </div>

        {isClosed && (
          <div className="flex items-center gap-2 border-t border-[#1f1f24] bg-[#16161a] px-4 py-2.5">
            <MessageSquareDashed className="size-3.5 shrink-0 text-[#7a7a82]" />
            <span className="text-[0.6875rem] leading-snug text-[#7a7a82]">
              {t("chat.closed_notice")}
            </span>
          </div>
        )}

        {error && messages.length > 0 && (
          <div className="border-t border-rose-500/20 bg-rose-500/[0.07] px-4 py-2 text-[0.6875rem] font-semibold text-rose-400">
            {error}
          </div>
        )}

        {/* Ô nhập KHÔNG bị khoá khi luồng đã đóng: gửi tin mới sẽ tự mở lại luồng
            (backend xử lý), nên khoá ở đây là chặn người dùng hỏi tiếp một cách vô cớ. */}
        <ChatComposer
          onSend={(body) => void handleSend(body)}
          sending={sending}
          // Chờ tải xong mới focus: focus sớm hơn sẽ tranh với lần cuộn xuống đáy.
          focusWhenReady={!loading}
          onPickFile={attachment.accept}
          onPasteFiles={attachment.acceptPaste}
          attachmentPreviewUrl={attachment.previewUrl}
          attachmentFileName={attachment.fileName}
          attachmentFileSize={attachment.fileSize}
          attachmentUploading={attachment.uploading}
          attachmentErrorKey={attachment.errorKey}
          attachmentReady={Boolean(attachment.attachment)}
          onRemoveAttachment={attachment.clear}
          onOpenAttachment={(src, alt) => setLightbox({ src, alt })}
        />
      </main>

      {lightbox && (
        <ImageLightbox
          src={lightbox.src}
          alt={lightbox.alt}
          onClose={() => setLightbox(null)}
        />
      )}
    </MobileShell>
  );
}
