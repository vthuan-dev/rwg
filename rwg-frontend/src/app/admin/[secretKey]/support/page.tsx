"use client";

import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import {
  Headphones,
  Loader2,
  MessageSquare,
  RefreshCw,
  Search,
  SendHorizonal,
  UserCheck,
  ChevronUp,
  Lock,
  EyeOff,
  ImagePlus,
  Bell,
  BellOff,
  Wallet as WalletIcon,
} from "lucide-react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import { AdminErrorState, AdminEmptyState } from "@/components/admin/AdminStates";
import { useAdminChatSocket, type AdminChatEvent } from "@/components/admin/useAdminChatSocket";
import { ChatAttachmentPreview } from "@/components/chat/ChatAttachmentPreview";
import { ChatImageAttachment } from "@/components/chat/ChatImageAttachment";
import { WalletAdjustPanel } from "@/components/admin/WalletAdjustPanel";
import { ChatGeoBadge } from "@/components/admin/ChatGeoBadge";
import { AdminModal } from "@/components/admin/AdminModal";
import { ImageLightbox } from "@/components/chat/ImageLightbox";
import {
  ChatWithdrawalCard,
  type ChatWithdrawalCardData,
} from "@/components/chat/ChatWithdrawalCard";
import { useChatAttachment } from "@/components/chat/useChatAttachment";
import {
  adminFetch,
  adminFetchBlobUrl,
  chatAttachmentEndpoint,
} from "@/lib/adminApi";
import { canChatReply, canAdjustWallet, getAdminIdentity } from "@/lib/adminIdentity";
import {
  isNotificationMuted,
  playNotificationChime,
  primeNotificationSound,
  setNotificationMuted,
} from "@/lib/notificationSound";
import { useTranslation } from "@/context/LanguageContext";
import type { ChatAttachment } from "@/lib/playerApi";

/** Một dòng hộp thư, khớp `AdminChatConversationRowResponse` của backend. */
interface ConversationRow {
  id: string;
  userId: string;
  username: string;
  status: "OPEN" | "CLOSED";
  assignedAdminId: string | null;
  assignedAdminUsername: string | null;
  unreadCount: number;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  createdAt: string;
  /**
   * Vi tri dia ly suy tu IP cua khach — do backend tra va luu san trong DB.
   *
   * TAT CA DEU NULLABLE: dich vu tra IP thuong chi biet quoc gia ma khong biet tinh,
   * hoac khong biet gi (IP mang noi bo, dich vu an danh). Khach KHONG can dong y chia
   * se vi tri — day la suy tu IP, khong phai Geolocation API cua trinh duyet.
   */
  lastIp: string | null;
  geoCountryCode: string | null;
  geoCountryName: string | null;
  geoRegion: string | null;
  geoCity: string | null;
  geoIsp: string | null;
}

/** Một tin nhắn, khớp `ChatMessageResponse` của backend. */
interface Message {
  id: string;
  conversationId: string;
  senderType: "PLAYER" | "STAFF" | "SYSTEM";
  senderId: string | null;
  senderUsername: string | null;
  /** Có thể rỗng khi tin chỉ có ảnh. */
  body: string;
  attachmentUrl: string | null;
  attachmentType: "IMAGE" | null;
  attachmentName: string | null;
  attachmentSize: number | null;
  readAt: string | null;
  clientMsgId: string | null;
  createdAt: string;
  /**
   * Blob URL của ảnh ở máy, chỉ có với tin do chính phiên này gửi.
   *
   * KHÔNG đến từ backend — được gán ở phía giao diện để hiện ảnh vừa gửi ngay lập
   * tức. Xem {@code localSrc} bên ChatImageAttachment để biết vì sao không tải lại.
   */
  localAttachmentUrl?: string | null;
  /**
   * Thông tin lệnh rút, chỉ có với THẺ DUYỆT — những tin chỉ nhân sự đọc được.
   *
   * Trạng thái trong đây đọc từ bảng lệnh mỗi lần tải luồng, không phải chép vào tin
   * nhắn — nên một lệnh được duyệt ở trang "Duyệt Nạp & Rút Tiền" thì thẻ ở đây cũng
   * đổi theo.
   */
  withdrawal?: ChatWithdrawalCardData | null;
}

/**
 * Tải một ảnh lên bằng token quản trị.
 *
 * ĐỂ NGOÀI component và không bọc `useCallback`: hàm này được truyền vào
 * `useChatAttachment`, mà hook đó để nó trong mảng phụ thuộc của `useCallback` bên
 * trong. Một hàm định nghĩa trong thân component sẽ là đối tượng mới sau mọi lần vẽ,
 * làm toàn bộ chuỗi phụ thuộc đó vô hiệu.
 */
async function uploadAdminAttachment(file: File): Promise<ChatAttachment> {
  const form = new FormData();
  form.append("file", file);
  // KHÔNG đặt Content-Type: `adminFetchWithStatus` đã nhận ra FormData và bỏ qua
  // header đó để trình duyệt tự sinh kèm chuỗi boundary.
  return adminFetch<ChatAttachment>("/admin/chat/attachments", {
    method: "POST",
    body: form,
  });
}

/** Tải ảnh đính kèm để hiển thị — cùng lý do để ngoài component như trên. */
function loadAdminAttachment(attachmentUrl: string): Promise<string> {
  return adminFetchBlobUrl(chatAttachmentEndpoint(attachmentUrl));
}

interface ConversationPage {
  content: ConversationRow[];
  totalElements: number;
  totalPages: number;
}

/** Bộ lọc hàng đợi. Khớp đúng ba tổ hợp tham số mà backend nhận. */
type QueueFilter = "ALL" | "UNASSIGNED" | "MINE" | "CLOSED";

/**
 * Hộp thư hỗ trợ người chơi.
 *
 * Bố cục hai cột: danh sách luồng bên trái, nội dung bên phải. Đây là dạng của mọi
 * công cụ hỗ trợ khách hàng, và nó cần thiết vì nhân sự phải nhìn được hàng đợi
 * trong lúc đang trả lời một người — nếu phải bấm quay lại mới thấy danh sách thì
 * họ sẽ không biết còn ai đang chờ.
 *
 * Vai trò RISK đọc được toàn bộ lịch sử nhưng KHÔNG có ô nhập: matcher
 * `POST /api/v1/admin/chat/**` trong SecurityConfig chỉ mở cho ADMIN/FINANCE/SUPPORT.
 * Ẩn ô nhập thay vì để họ gõ rồi nhận 403.
 */
export default function AdminSupportPage() {
  const { t } = useTranslation();

  const canReply = canChatReply();
  const myId = getAdminIdentity().userId;

  const [filter, setFilter] = useState<QueueFilter>("ALL");
  const [search, setSearch] = useState("");
  const [rows, setRows] = useState<ConversationRow[]>([]);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState("");

  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [threadLoading, setThreadLoading] = useState(false);
  const [threadError, setThreadError] = useState("");
  const [hasOlder, setHasOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);

  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [acting, setActing] = useState(false);

  /** Ảnh đang xem toàn màn hình; null = không mở. */
  const [lightbox, setLightbox] = useState<{ src: string; alt: string } | null>(null);

  const [showAdjustModal, setShowAdjustModal] = useState(false);
  const [activeBalance, setActiveBalance] = useState("0.00");
  const [activeCurrency, setActiveCurrency] = useState("USD");
  const [loadingBalance, setLoadingBalance] = useState(false);

  const [selectMode, setSelectMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const attachment = useChatAttachment(uploadAdminAttachment);
  /** Ô chọn tệp thật, bị ẩn và thao tác qua nút bên cạnh ô nhập. */
  const fileInputRef = useRef<HTMLInputElement>(null);

  /** Ô trả lời, để đặt con trỏ vào khi nhân sự mở một luồng. */
  const replyRef = useRef<HTMLTextAreaElement>(null);

  /**
   * Blob URL của các ảnh đã gửi trong phiên này, để thu hồi khi rời trang.
   *
   * Quyền sở hữu những URL này đã được `attachment.detach()` chuyển ra khỏi hook, nên
   * hook không còn dọn chúng. Nhân sự mở trang này cả ngày làm việc nên chỗ rò rỉ này
   * tích lũy đáng kể hơn ở phía người chơi.
   */
  const localBlobUrls = useRef<Set<string>>(new Set());

  /**
   * Trạng thái tắt tiếng, đọc từ localStorage SAU khi mount.
   *
   * Không đọc trong `useState(...)` khởi tạo: localStorage không tồn tại lúc server
   * render, và đọc ở lần render đầu sẽ làm HTML từ server và từ client khác nhau —
   * React báo hydration mismatch. Đây là cùng lý do được ghi ở AdminHeader.
   */
  const [muted, setMuted] = useState(false);

  useEffect(() => setMuted(isNotificationMuted()), []);

  useEffect(
    () => () => {
      for (const url of localBlobUrls.current) {
        URL.revokeObjectURL(url);
      }
      localBlobUrls.current.clear();
    },
    []
  );

  const scrollRef = useRef<HTMLDivElement>(null);
  /**
   * Luồng đang mở, đọc được từ trong các callback.
   *
   * Cần bản ref song song với state: hàm xử lý gói WebSocket được tạo một lần và sẽ
   * đọc phải giá trị `activeId` đã cũ nếu chỉ dựa vào biến state (closure).
   */
  const activeIdRef = useRef<string | null>(null);
  activeIdRef.current = activeId;

  const activeRow = rows.find((r) => r.id === activeId) ?? null;

  /** Chuỗi truy vấn của bộ lọc hiện tại. */
  const buildQuery = useCallback(() => {
    const params = new URLSearchParams({ page: "0", size: "50" });
    if (filter === "UNASSIGNED") params.set("unassigned", "true");
    if (filter === "MINE") params.set("mine", "true");
    // CLOSED là bộ lọc TRẠNG THÁI, các bộ lọc khác đều ngầm hiểu là luồng đang mở.
    // Không đặt status=OPEN cho "Tất cả" là có chủ ý: nhân sự cần tìm lại được cả
    // luồng đã đóng khi người chơi hỏi tiếp về một vụ cũ.
    if (filter === "CLOSED") params.set("status", "CLOSED");
    const q = search.trim();
    if (q) params.set("q", q);
    return `/admin/chat/conversations?${params.toString()}`;
  }, [filter, search]);

  /**
   * Tải hộp thư.
   *
   * Trả dữ liệu về thay vì tự đặt state — quy ước của các trang quản trị trong dự
   * án, để effect quyết định có bỏ kết quả hay không khi component đã tháo.
   * Trả `null` khi lỗi và KHÔNG dựng dữ liệu giả: một hộp thư trống giả tạo sẽ làm
   * nhân sự tưởng không còn ai đang chờ.
   */
  const fetchRows = useCallback(async (): Promise<ConversationRow[] | null> => {
    try {
      const data = await adminFetch<ConversationPage>(buildQuery());
      setListError("");
      return data.content ?? [];
    } catch (err) {
      setListError((err as Error).message);
      return null;
    }
  }, [buildQuery]);

  const reloadList = useCallback(async () => {
    setListLoading(true);
    const data = await fetchRows();
    setRows(data ?? []);
    setListLoading(false);
  }, [fetchRows]);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      setListLoading(true);
      const data = await fetchRows();
      if (cancelled) return;
      setRows(data ?? []);
      setListLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchRows]);

  /**
   * Mở khoá âm thanh sau tương tác đầu tiên.
   *
   * PHẢI làm ở đây chứ không dựa vào chỗ khác: khu quản trị KHÔNG bọc trong
   * `NotificationProvider` của phía người chơi (provider đó đọc token người chơi và mở
   * WebSocket tới cổng 8080). Trình duyệt treo AudioContext cho tới khi có tương tác
   * thật, nên thiếu bước này thì chuông im lặng mà không báo lỗi gì.
   */
  useEffect(() => primeNotificationSound(), []);

  const scrollToBottom = useCallback((smooth: boolean) => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? "smooth" : "auto" });
  }, []);

  /** Mở một luồng: tải lịch sử và đánh dấu đã đọc. */
  const openConversation = async (id: string) => {
    setActiveId(id);
    setMessages([]);
    setThreadError("");
    setThreadLoading(true);
    setDraft("");
    setSelectMode(false);
    setSelectedIds(new Set());

    try {
      const page = await adminFetch<Message[]>(
        `/admin/chat/conversations/${id}/messages`
      );
      // API trả mới nhất trước; đảo lại để cũ trên, mới dưới.
      setMessages([...page].reverse());
      setHasOlder(page.length >= 30);

      // Đánh dấu đã đọc CHỈ khi có quyền ghi: với RISK, endpoint này trả 403 và mỗi
      // lần mở một luồng sẽ để lại một lỗi đỏ không liên quan tới việc họ đang làm.
      if (canReply) {
        await adminFetch(`/admin/chat/conversations/${id}/read`, { method: "POST" });
        setRows((prev) =>
          prev.map((r) => (r.id === id ? { ...r, unreadCount: 0 } : r))
        );
      }
    } catch (err) {
      setThreadError((err as Error).message);
    } finally {
      setThreadLoading(false);
    }
  };

  /**
   * Tải lại lịch sử luồng đang mở, KHÔNG xoá màn hình và KHÔNG đánh dấu đã đọc lại.
   *
   * Dùng sau khi duyệt/từ chối một lệnh rút từ thẻ trong chat: trạng thái thẻ do bảng
   * lệnh quyết định, nên phải đọc lại từ server thay vì tự sửa ở máy — lệnh có thể đã bị
   * người khác xử lý giữa lúc thẻ đang mở.
   *
   * TÁCH KHỎI {@code openConversation} vì hàm đó đặt `threadLoading` và xoá mảng tin: nó
   * sẽ làm cả khung nhấp nháy thành spinner rồi cuộn lại từ đầu, cho một thay đổi chỉ
   * ảnh hưởng một thẻ.
   */
  const reloadThread = useCallback(async () => {
    const id = activeIdRef.current;
    if (!id) return;
    try {
      const page = await adminFetch<Message[]>(
        `/admin/chat/conversations/${id}/messages`
      );
      setMessages([...page].reverse());
      setHasOlder(page.length >= 30);
    } catch (err) {
      setThreadError((err as Error).message);
    }
  }, []);

  useLayoutEffect(() => {
    // KHÔNG điều kiện `messages.length > 0`: một luồng chưa có tin (khách mở khung chat
    // rồi chưa gõ gì) vẫn cần đưa khung về đáy, để ô trả lời nằm đúng chỗ mắt nhân sự
    // đang nhìn thay vì lơ lửng dưới một vùng trống.
    if (!threadLoading) {
      scrollToBottom(false);
    }
    // Chỉ cuộn khi ĐỔI luồng hoặc vừa tải xong, không phải mỗi lần mảng đổi: tin mới
    // đến đã tự cuộn ở chỗ khác, và cuộn thêm ở đây sẽ giật khi người dùng đang đọc
    // lại tin cũ.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId, threadLoading]);

  /**
   * Đặt con trỏ vào ô trả lời mỗi lần mở một luồng.
   *
   * LẶP LẠI THEO TỪNG LUỒNG, khác với phía người chơi nơi chỉ focus một lần: nhân sự
   * chuyển qua lại giữa nhiều luồng trong cùng một lần tải trang, và mỗi lần mở một
   * luồng là một lần họ sắp gõ trả lời.
   *
   * `canReply` chặn vai RISK: ô trả lời không được vẽ cho họ, nên `replyRef` là null và
   * lời gọi focus sẽ rơi vào khoảng không.
   *
   * `preventScroll` để không tranh với lần cuộn xuống đáy ở effect ngay trên.
   */
  useLayoutEffect(() => {
    if (!activeId || threadLoading || !canReply) return;
    replyRef.current?.focus({ preventScroll: true });
  }, [activeId, threadLoading, canReply]);

  /** Tải tin cũ hơn, giữ nguyên vị trí đang đọc. */
  const loadOlder = async () => {
    if (!activeId || loadingOlder || messages.length === 0) return;
    const oldest = messages[0];
    const el = scrollRef.current;
    const heightBefore = el?.scrollHeight ?? 0;

    setLoadingOlder(true);
    try {
      const page = await adminFetch<Message[]>(
        `/admin/chat/conversations/${activeId}/messages?before=${encodeURIComponent(
          oldest.createdAt
        )}`
      );
      setMessages((prev) => [...[...page].reverse(), ...prev]);
      setHasOlder(page.length >= 30);

      // Bù đúng phần chiều cao vừa chèn vào đầu, nếu không màn hình nhảy về đầu và
      // nhân sự mất chỗ đang đọc.
      requestAnimationFrame(() => {
        const target = scrollRef.current;
        if (!target) return;
        target.scrollTop = target.scrollHeight - heightBefore;
      });
    } catch (err) {
      console.warn("Lỗi tải tin cũ hơn:", err);
    } finally {
      setLoadingOlder(false);
    }
  };

  /** Nhận gói realtime. */
  const handleEvent = useCallback(
    (event: AdminChatEvent) => {
      const isActive = event.conversationId === activeIdRef.current;

      if (event.type === "MESSAGE" && event.message) {
        const incoming = event.message;

        // Gói CHỈ-NHÂN-SỰ (hiện chỉ có thẻ duyệt lệnh rút) đi đường riêng.
        //
        // KHÔNG chèn thẳng vào mảng như tin thường: gói realtime không mang dữ liệu lệnh
        // rút (backend cố tình không đính — xem `AdminChatEvent.withdrawal`), nên chèn
        // vào sẽ vẽ ra một dải chữ trống thay vì thẻ có nút. Tải lại luồng để lấy đủ dữ
        // liệu, và làm mới hộp thư để dòng xem trước đổi theo.
        //
        // KHÔNG reo chuông: đây không phải khách vừa nói gì, và viên đỏ báo tin mới phải
        // giữ nghĩa "có người đang chờ trả lời".
        if (event.staffOnly) {
          if (isActive) {
            void reloadThread();
          }
          void reloadList();
          return;
        }

        // Tiếng chuông CHỈ với tin của người chơi, và chỉ khi luồng đó không đang mở.
        //
        // Kênh /topic/admin/chat là kênh CHUNG: mọi nhân sự đều nhận cả tin do chính
        // họ vừa trả lời. Không lọc theo senderType thì chính người gửi nghe chuông
        // sau mỗi câu mình gõ. Luồng đang mở cũng không cần: họ đang nhìn thấy tin đó.
        if (incoming.senderType === "PLAYER" && !isActive) {
          playNotificationChime();
        }

        if (isActive) {
          setMessages((prev) =>
            prev.some((m) => m.id === incoming.id) ? prev : [...prev, incoming]
          );
          requestAnimationFrame(() => scrollToBottom(true));
        }

        // Cập nhật dòng hộp thư ngay tại chỗ thay vì gọi lại API: người chơi gõ liên
        // tục thì mỗi tin sẽ thành một request tải lại cả danh sách.
        setRows((prev) => {
          const index = prev.findIndex((r) => r.id === event.conversationId);
          if (index < 0) {
            // Luồng chưa có trong danh sách (người chơi mới, hoặc không khớp bộ lọc):
            // tải lại để dòng mới xuất hiện đúng thứ tự và đúng bộ lọc.
            void reloadList();
            return prev;
          }
          const row = prev[index];
          const updated: ConversationRow = {
            ...row,
            status: "OPEN",
            // Tin chỉ có ảnh không có chữ nào — dùng đúng khoá mà backend sẽ lưu vào
            // cột last_message_preview, để dòng hộp thư không bị trống rồi nhảy chữ khi
            // tải lại trang. `previewOf` sẽ dịch nó khi hiển thị.
            lastMessagePreview: incoming.body || "chat.preview.image",
            lastMessageAt: incoming.createdAt,
            unreadCount:
              incoming.senderType === "PLAYER" && !isActive
                ? row.unreadCount + 1
                : row.unreadCount,
          };
          // Đưa lên đầu: hộp thư sắp theo tin mới nhất, nên dòng vừa có tin phải nhảy
          // lên trên như ở server.
          return [updated, ...prev.filter((_, i) => i !== index)];
        });
        return;
      }

      if (event.type === "MESSAGES_DELETED" && event.deletedMessageIds) {
        const deletedSet = new Set(event.deletedMessageIds);
        if (isActive) {
          setMessages((prev) => prev.filter((m) => !deletedSet.has(m.id)));
        }
        void reloadList();
        return;
      }

      if (event.type === "CONVERSATION") {
        setRows((prev) =>
          prev.map((r) =>
            r.id === event.conversationId
              ? { ...r, status: (event.status as "OPEN" | "CLOSED") ?? r.status }
              : r
          )
        );
        return;
      }

      if (event.type === "READ" && isActive) {
        const now = new Date().toISOString();
        setMessages((prev) =>
          prev.map((m) =>
            m.senderType === "STAFF" && !m.readAt ? { ...m, readAt: now } : m
          )
        );
      }
    },
    [reloadList, reloadThread, scrollToBottom]
  );

  useAdminChatSocket(handleEvent);

  const toggleMessageSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const deleteMessages = async () => {
    if (selectedIds.size === 0 || !activeId || acting) return;
    const pin = window.prompt("Nhập mã xác nhận bảo mật để xóa tin nhắn:");
    if (pin === null) return; // Người dùng bấm Hủy
    setActing(true);
    setThreadError("");
    try {
      await adminFetch(`/admin/chat/conversations/${activeId}/messages`, {
        method: "DELETE",
        body: JSON.stringify({
          messageIds: Array.from(selectedIds),
          confirmPin: pin.trim(),
        }),
      });
      setMessages((prev) => prev.filter((m) => !selectedIds.has(m.id)));
      setSelectMode(false);
      setSelectedIds(new Set());
      void reloadList();
    } catch (err) {
      setThreadError((err as Error).message);
    } finally {
      setActing(false);
    }
  };

  const sendReply = async () => {
    const body = draft.trim();
    // Chụp lại ảnh TRƯỚC khi dọn: `attachment.detach()` chạy đồng thời với lời gọi mạng.
    const pendingAttachment = attachment.attachment;
    // Cho gửi khi CHỈ có ảnh: nhân sự gửi ảnh hướng dẫn không kèm chữ là bình thường.
    if ((!body && !pendingAttachment) || !activeId || sending) return;

    // `detach` chứ không phải `clear`: nó giao quyền sở hữu blob URL cho ta, để bong
    // bóng vừa tạo hiện được ảnh ngay. Xem `localSrc` bên ChatImageAttachment để biết vì
    // sao không tải lại từ server ở thời điểm này.
    const localUrl = pendingAttachment ? attachment.detach() : null;
    if (localUrl) {
      localBlobUrls.current.add(localUrl);
    }

    setSending(true);
    try {
      const saved = await adminFetch<Message>(
        `/admin/chat/conversations/${activeId}/messages`,
        {
          method: "POST",
          body: JSON.stringify({
            body,
            attachmentUrl: pendingAttachment?.url ?? null,
            attachmentName: pendingAttachment?.name ?? null,
            attachmentSize: pendingAttachment?.size ?? null,
          }),
        }
      );
      const savedWithLocal: Message = { ...saved, localAttachmentUrl: localUrl };
      setMessages((prev) =>
        prev.some((m) => m.id === saved.id) ? prev : [...prev, savedWithLocal]
      );
      setDraft("");
      // Dọn ô xem trước khi gửi tin KHÔNG kèm ảnh: `detach()` phía trên chỉ chạy khi có
      // ảnh đã tải lên xong, nên một lần tải thất bại sẽ để lại ô xem trước kèm thông báo
      // lỗi lơ lửng — trông như tin vẫn đang chờ gửi. Có ảnh thì KHÔNG gọi: `clear()` thu
      // hồi blob URL và ảnh trong bong bóng vừa tạo sẽ thành ô vỡ.
      if (!pendingAttachment) {
        attachment.clear();
      }
      requestAnimationFrame(() => scrollToBottom(true));

      // Trả lời sẽ TỰ nhận phụ trách ở backend, nên dòng hộp thư phải phản ánh ngay
      // điều đó — nếu không thì nút "Nhận phụ trách" vẫn hiện dù việc đã có người.
      setRows((prev) =>
        prev.map((r) =>
          r.id === activeId && !r.assignedAdminId
            ? {
                ...r,
                assignedAdminId: myId,
                assignedAdminUsername: saved.senderUsername,
              }
            : r
        )
      );
      setThreadError("");
    } catch (err) {
      setThreadError((err as Error).message);
    } finally {
      setSending(false);
    }
  };

  /**
   * Tải số dư ví hiện tại của người chơi và mở modal Cộng/Trừ tiền.
   *
   * Tránh việc mở modal với số dư cũ (hoặc mặc định 0) khiến admin nhập sai tiền
   * hoặc không đối chiếu được số dư dự kiến chính xác.
   */
  const openAdjustModal = useCallback(async () => {
    if (!activeRow) return;
    setLoadingBalance(true);
    setThreadError(""); // Xoá lỗi cũ nếu có
    try {
      const detail = await adminFetch<{ walletBalance: string; currency?: string }>(`/admin/users/${activeRow.userId}`);
      setActiveBalance(detail.walletBalance);
      setActiveCurrency(detail.currency || "USD");
      setShowAdjustModal(true);
    } catch (err) {
      setThreadError((err as Error).message);
    } finally {
      setLoadingBalance(false);
    }
  }, [activeRow]);

  /** Nhận phụ trách hoặc đóng luồng; cả hai đều trả về dòng đã cập nhật. */
  const runAction = async (action: "assign" | "close") => {
    if (!activeId || acting) return;
    setActing(true);
    try {
      const updated = await adminFetch<ConversationRow>(
        `/admin/chat/conversations/${activeId}/${action}`,
        { method: "POST" }
      );
      setRows((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
      setThreadError("");
      // Nhận việc và đóng luồng đều ghi thêm một dòng SYSTEM vào lịch sử, nên phải
      // tải lại phần thân để dòng đó hiện ra.
      const page = await adminFetch<Message[]>(
        `/admin/chat/conversations/${activeId}/messages`
      );
      setMessages([...page].reverse());
      requestAnimationFrame(() => scrollToBottom(true));
    } catch (err) {
      setThreadError((err as Error).message);
    } finally {
      setActing(false);
    }
  };

  const FILTERS: { key: QueueFilter; label: string }[] = [
    { key: "ALL", label: t("admin.chat.filter_all") },
    { key: "UNASSIGNED", label: t("admin.chat.filter_unassigned") },
    { key: "MINE", label: t("admin.chat.filter_mine") },
    { key: "CLOSED", label: t("admin.chat.filter_closed") },
  ];

  /**
   * Đoạn xem trước của một dòng hộp thư.
   *
   * Backend lưu tin SYSTEM với thân là KHOÁ DỊCH (xem `ChatMessage.fromSystem`), và
   * cột `last_message_preview` chép lại đúng thân đó. Nên sau khi nhận phụ trách hay
   * đóng luồng, dòng hộp thư sẽ in ra chữ "chat.system.closed" nếu không dịch ở đây —
   * đúng chuỗi mà thân tin nhắn đã đi qua `t()` để tránh.
   *
   * Dịch HAI tiền tố:
   * - `chat.system.` — dòng thông báo hệ thống (nhận phụ trách, đóng luồng).
   * - `chat.preview.` — tin CHỈ có ảnh, không có chữ nào để hiện.
   *
   * Chỉ đúng hai tiền tố đó: tin người thật gõ là văn bản thô và phải hiện nguyên văn,
   * kể cả khi họ tình cờ gõ một chuỗi trông giống khoá dịch.
   */
  const previewOf = (preview: string | null): string => {
    if (!preview) return "-";
    const isKey =
      preview.startsWith("chat.system.") || preview.startsWith("chat.preview.");
    return isKey ? t(preview) : preview;
  };

  const waitingCount = rows.filter((r) => r.unreadCount > 0).length;

  return (
    <div className="flex w-full min-h-screen flex-col bg-slate-50">
      <AdminHeader
        title={t("admin.chat.title")}
        subtitle={t("admin.chat.subtitle")}
      />

      <div className="flex flex-col gap-4 p-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Headphones className="h-5 w-5 text-red-600" />
            <span className="text-sm font-extrabold text-slate-900">
              {t("admin.chat.inbox_title")} ({rows.length})
            </span>
            {waitingCount > 0 && (
              <span className="rounded-full border border-red-200 bg-red-50 px-2.5 py-0.5 text-[10px] font-bold text-red-700">
                {t("admin.chat.waiting_reply", { count: waitingCount })}
              </span>
            )}
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => {
                const next = !muted;
                setNotificationMuted(next);
                setMuted(next);
                // Phát thử ngay khi BẬT tiếng: nhân sự biết chắc âm lượng máy đang ở mức
                // nghe được, thay vì phát hiện lúc bỏ sót mất một tin của khách.
                if (!next) playNotificationChime();
              }}
              aria-label={t(muted ? "admin.chat.sound_on" : "admin.chat.sound_off")}
              title={t(muted ? "admin.chat.sound_on" : "admin.chat.sound_off")}
              className={`rounded-xl border p-2 shadow-xs transition-colors ${
                muted
                  ? "border-slate-200 bg-white text-slate-400 hover:bg-slate-100"
                  : "border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100"
              }`}
            >
              {muted ? <BellOff className="h-4 w-4" /> : <Bell className="h-4 w-4" />}
            </button>

            <button
              type="button"
              onClick={() => void reloadList()}
              aria-label={t("admin.states.retry")}
              className="rounded-xl border border-slate-200 bg-white p-2 text-slate-600 shadow-xs hover:bg-slate-100"
            >
              <RefreshCw
                className={`h-4 w-4 ${listLoading ? "animate-spin text-red-600" : ""}`}
              />
            </button>
          </div>
        </div>

        {listError ? (
          <AdminErrorState message={listError} onRetry={() => void reloadList()} />
        ) : (
          // Chiều cao cố định theo viewport, KHÔNG để cả trang cuộn: hai cột phải cuộn
          // độc lập, nếu không thì kéo xem lịch sử của một luồng sẽ làm danh sách hàng
          // đợi trôi khỏi màn hình.
          <div className="grid h-[calc(100vh-13rem)] grid-cols-1 gap-4 lg:grid-cols-[22rem_1fr]">
            {/* ==================== Cột trái: hàng đợi ==================== */}
            <section className="flex min-h-0 flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
              <div className="flex flex-col gap-2.5 border-b border-slate-100 p-3">
                <div className="relative">
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" />
                  <label className="sr-only" htmlFor="chat-search">
                    {t("admin.chat.search_placeholder")}
                  </label>
                  <input
                    id="chat-search"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    placeholder={t("admin.chat.search_placeholder")}
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 pe-3 ps-9 text-xs font-medium text-slate-900 outline-none transition-colors placeholder:text-slate-400 focus:border-slate-400 focus:bg-white"
                  />
                </div>

                <div className="flex flex-wrap gap-1.5">
                  {FILTERS.map((f) => (
                    <button
                      key={f.key}
                      type="button"
                      onClick={() => setFilter(f.key)}
                      aria-pressed={filter === f.key}
                      className={`rounded-lg border px-2.5 py-1 text-[11px] font-bold transition-colors ${
                        filter === f.key
                          ? "border-red-500/30 bg-red-500/10 text-red-700"
                          : "border-slate-200 bg-white text-slate-500 hover:bg-slate-50"
                      }`}
                    >
                      {f.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="min-h-0 grow overflow-y-auto">
                {listLoading ? (
                  <div className="flex items-center justify-center py-14">
                    <Loader2 className="h-5 w-5 animate-spin text-red-600" />
                  </div>
                ) : rows.length === 0 ? (
                  <AdminEmptyState message={t("admin.chat.inbox_empty")} />
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {rows.map((row) => {
                      const isActive = row.id === activeId;
                      return (
                        <li key={row.id}>
                          <button
                            type="button"
                            onClick={() => void openConversation(row.id)}
                            aria-current={isActive ? "true" : undefined}
                            className={`flex w-full flex-col gap-1 px-3.5 py-3 text-left transition-colors ${
                              isActive ? "bg-red-50/70" : "hover:bg-slate-50"
                            }`}
                          >
                            <div className="flex items-center gap-2">
                              <span className="truncate text-xs font-extrabold text-slate-900">
                                {row.username}
                              </span>
                              {row.unreadCount > 0 && (
                                <span className="flex h-4.5 min-w-[18px] items-center justify-center rounded-full bg-red-600 px-1.5 text-[10px] font-black text-white">
                                  {row.unreadCount}
                                </span>
                              )}
                              {row.lastMessageAt && (
                                <span className="ms-auto shrink-0 text-[10px] font-medium text-slate-400">
                                  {new Date(row.lastMessageAt).toLocaleTimeString([], {
                                    hour: "2-digit",
                                    minute: "2-digit",
                                  })}
                                </span>
                              )}
                            </div>

                            <p className="truncate text-[11px] font-medium text-slate-500">
                              {previewOf(row.lastMessagePreview)}
                            </p>

                            <div className="flex items-center gap-1.5">
                              {row.status === "CLOSED" && (
                                <span className="rounded-md border border-slate-200 bg-slate-100 px-1.5 py-0.5 text-[9px] font-bold text-slate-600">
                                  {t("admin.chat.closed_badge")}
                                </span>
                              )}
                              {row.assignedAdminId ? (
                                <span className="truncate rounded-md border border-sky-200 bg-sky-50 px-1.5 py-0.5 text-[9px] font-bold text-sky-700">
                                  {t("admin.chat.assigned_to", {
                                    name:
                                      row.assignedAdminId === myId
                                        ? t("admin.chat.you")
                                        : row.assignedAdminUsername ?? "-",
                                  })}
                                </span>
                              ) : (
                                <span className="rounded-md border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-[9px] font-bold text-amber-700">
                                  {t("admin.chat.unassigned_badge")}
                                </span>
                              )}
                            </div>
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            </section>

            {/* ==================== Cột phải: nội dung luồng ==================== */}
            <section className="flex min-h-0 flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
              {!activeRow ? (
                <div className="flex grow flex-col items-center justify-center gap-3 px-8 text-center">
                  <div className="rounded-2xl border border-slate-200 bg-slate-50 p-3.5">
                    <MessageSquare className="h-6 w-6 text-slate-400" />
                  </div>
                  <span className="max-w-xs text-xs font-medium text-slate-500">
                    {t("admin.chat.select_hint")}
                  </span>
                </div>
              ) : (
                <>
                  <header className="flex items-center gap-3 border-b border-slate-100 px-4 py-3">
                    <div className="flex flex-col min-w-0">
                      <span className="truncate text-sm font-extrabold text-slate-900">
                        {activeRow.username}
                      </span>
                      {/* Nguoi phu trach va vi tri khach tren CUNG MOT DONG: header chat
                          chi cao 3 dong va them mot dong nua se day phan tin nhan xuong.
                          Dau cham giua hai phan de tach thi giac ma khong ton chieu cao. */}
                      <span className="flex min-w-0 items-center gap-1.5 text-[11px] font-medium text-slate-500">
                        <span className="truncate">
                          {activeRow.assignedAdminId
                            ? t("admin.chat.assigned_to", {
                                name:
                                  activeRow.assignedAdminId === myId
                                    ? t("admin.chat.you")
                                    : activeRow.assignedAdminUsername ?? "-",
                              })
                            : t("admin.chat.unassigned_badge")}
                        </span>
                        <span aria-hidden="true" className="text-slate-300">·</span>
                        <ChatGeoBadge
                          countryCode={activeRow.geoCountryCode}
                          countryName={activeRow.geoCountryName}
                          region={activeRow.geoRegion}
                          city={activeRow.geoCity}
                          isp={activeRow.geoIsp}
                          ip={activeRow.lastIp}
                        />
                      </span>
                    </div>

                    {canReply && (
                      <div className="ms-auto flex shrink-0 items-center gap-2">
                        {selectMode ? (
                          <>
                            <span className="text-xs font-semibold text-slate-500">
                              {t("admin.chat.selected_count", { count: selectedIds.size })}
                            </span>
                            <button
                              type="button"
                              disabled={selectedIds.size === 0 || acting}
                              onClick={deleteMessages}
                              className="flex items-center gap-1.5 rounded-xl bg-red-600 px-3 py-2 text-[11px] font-bold text-white transition-colors hover:bg-red-700 disabled:opacity-60"
                            >
                              {acting ? (
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              ) : (
                                t("admin.chat.delete_selected")
                              )}
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setSelectMode(false);
                                setSelectedIds(new Set());
                              }}
                              className="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-[11px] font-bold text-slate-600 transition-colors hover:bg-slate-100"
                            >
                              {t("admin.chat.cancel")}
                            </button>
                          </>
                        ) : (
                          <>
                            <button
                              type="button"
                              onClick={() => setSelectMode(true)}
                              className="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-[11px] font-bold text-slate-600 transition-colors hover:bg-slate-100"
                            >
                              {t("admin.chat.select_messages")}
                            </button>
                            {!activeRow.assignedAdminId && (
                              <button
                                type="button"
                                disabled={acting}
                                onClick={() => void runAction("assign")}
                                className="flex items-center gap-1.5 rounded-xl bg-slate-900 px-3 py-2 text-[11px] font-bold text-white transition-colors hover:bg-slate-800 disabled:opacity-60"
                              >
                                <UserCheck className="h-3.5 w-3.5" />
                                {t("admin.chat.assign")}
                              </button>
                            )}
                            {canAdjustWallet(getAdminIdentity()) && (
                              <button
                                type="button"
                                disabled={loadingBalance}
                                onClick={openAdjustModal}
                                id="chat-adjust-wallet"
                                className="flex items-center gap-1.5 rounded-xl bg-emerald-600 px-3 py-2 text-[11px] font-bold text-white transition-colors hover:bg-emerald-700 disabled:opacity-60"
                              >
                                {loadingBalance ? (
                                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                ) : (
                                  <WalletIcon className="h-3.5 w-3.5" />
                                )}
                                {t("admin.users.wallet.tab_adjust")}
                              </button>
                            )}
                            {activeRow.status === "OPEN" && (
                              <button
                                type="button"
                                disabled={acting}
                                onClick={() => void runAction("close")}
                                className="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-[11px] font-bold text-slate-600 transition-colors hover:bg-slate-100 disabled:opacity-60"
                              >
                                <Lock className="h-3.5 w-3.5" />
                                {t("admin.chat.close")}
                              </button>
                            )}
                          </>
                        )}
                      </div>
                    )}
                  </header>

                  <div ref={scrollRef} className="min-h-0 grow overflow-y-auto bg-slate-50/60 px-4 py-3">
                    {threadLoading ? (
                      <div className="flex items-center justify-center py-14">
                        <Loader2 className="h-5 w-5 animate-spin text-red-600" />
                      </div>
                    ) : (
                      <>
                        {hasOlder && (
                          <div className="mb-3 flex justify-center">
                            <button
                              type="button"
                              onClick={() => void loadOlder()}
                              disabled={loadingOlder}
                              className="flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[11px] font-bold text-slate-500 hover:bg-slate-100"
                            >
                              {loadingOlder ? (
                                <Loader2 className="h-3 w-3 animate-spin" />
                              ) : (
                                <ChevronUp className="h-3 w-3" />
                              )}
                              {t("admin.chat.load_more")}
                            </button>
                          </div>
                        )}

                        <ul className="flex flex-col gap-2">
                          {messages.map((m) => {
                            // Thẻ duyệt lệnh rút. Kiểm TRƯỚC nhánh SYSTEM vì thẻ cũng là
                            // tin SYSTEM — vẽ nó thành dải chữ nhỏ sẽ mất hết nút bấm.
                            if (m.withdrawal) {
                              return (
                                <li key={m.id} className="py-1.5">
                                  <ChatWithdrawalCard
                                    card={m.withdrawal}
                                    onDecided={() => {
                                      // Cả hai bảng đều lỗi thời sau một quyết định:
                                      // luồng để thẻ đổi trạng thái, hộp thư để dòng
                                      // xem trước và badge khớp lại.
                                      void reloadThread();
                                      void reloadList();
                                    }}
                                  />
                                </li>
                              );
                            }

                            // Tin SYSTEM là dòng giữa, không phải bong bóng: nó không
                            // phải lời của ai, và vẽ thành bong bóng sẽ khiến nhân sự
                            // đọc nó như câu người chơi vừa nói.
                            if (m.senderType === "SYSTEM") {
                              return (
                                <li key={m.id} className="flex justify-center py-1">
                                  <span className="rounded-full bg-slate-200/70 px-3 py-1 text-[10px] font-medium text-slate-600">
                                    {t(m.body)}
                                  </span>
                                </li>
                              );
                            }



                            const isStaff = m.senderType === "STAFF";
                            const hasText = Boolean(m.body && m.body.trim().length > 0);
                            const hasImage = Boolean(
                              m.attachmentUrl && m.attachmentType === "IMAGE"
                            );
                            const isSelected = selectedIds.has(m.id);
                            return (
                              <li
                                key={m.id}
                                onClick={() => {
                                  if (selectMode) toggleMessageSelect(m.id);
                                }}
                                className={`flex items-center gap-2.5 py-1 ${
                                  selectMode ? "cursor-pointer hover:bg-slate-100/50" : ""
                                } ${isStaff ? "justify-end" : "justify-start"}`}
                              >
                                {selectMode && !isStaff && (
                                  <input
                                    type="checkbox"
                                    checked={isSelected}
                                    onChange={() => {}} // Đã handled ở onClick của li
                                    className="h-4 w-4 shrink-0 rounded border-slate-300 text-red-600 focus:ring-red-500"
                                  />
                                )}
                                <div
                                  className={`flex max-w-[70%] flex-col ${
                                    isStaff ? "items-end" : "items-start"
                                  }`}
                                >
                                  <div
                                    className={`text-xs leading-relaxed ${
                                      // Ảnh cần đệm hẹp hơn để không có viền dày quanh nó;
                                      // chữ thì cần đệm đủ để dễ đọc.
                                      hasImage && !hasText ? "p-1" : "px-3.5 py-2.5"
                                    } ${
                                      isStaff
                                        ? "rounded-2xl rounded-br-md bg-slate-900 text-white"
                                        : "rounded-2xl rounded-bl-md border border-slate-200 bg-white text-slate-800"
                                    } ${
                                      selectMode && isSelected
                                        ? "ring-2 ring-red-500 ring-offset-1"
                                        : ""
                                    }`}
                                  >
                                    {hasImage && (
                                      <div className={hasText ? "mb-2" : ""}>
                                        <ChatImageAttachment
                                          attachmentUrl={m.attachmentUrl!}
                                          attachmentName={m.attachmentName}
                                          load={loadAdminAttachment}
                                          onOpen={(src, alt) => {
                                            // Chặn lightbox khi đang ở chế độ chọn tin nhắn
                                            if (!selectMode) setLightbox({ src, alt });
                                          }}
                                          localSrc={m.localAttachmentUrl}
                                        />
                                      </div>
                                    )}

                                    {/* Giữ các dòng người dùng tự xuống; chặn chuỗi dài
                                        không khoảng trắng làm tràn bong bóng. CHỈ vẽ khi
                                        có chữ — tin chỉ có ảnh sẽ có một dòng rỗng dưới
                                        ảnh nếu vẽ vô điều kiện. */}
                                    {hasText && (
                                      <p className="whitespace-pre-wrap break-words">{m.body}</p>
                                    )}
                                  </div>
                                  <span className="mt-1 text-[9px] font-medium text-slate-400">
                                    {isStaff
                                      ? m.senderUsername ?? t("admin.chat.you")
                                      : activeRow.username}
                                    {" · "}
                                    {new Date(m.createdAt).toLocaleTimeString([], {
                                      hour: "2-digit",
                                      minute: "2-digit",
                                    })}
                                  </span>
                                </div>
                                {selectMode && isStaff && (
                                  <input
                                    type="checkbox"
                                    checked={isSelected}
                                    onChange={() => {}} // Đã handled ở onClick của li
                                    className="h-4 w-4 shrink-0 rounded border-slate-300 text-red-600 focus:ring-red-500"
                                  />
                                )}
                              </li>
                            );
                          })}
                        </ul>
                      </>
                    )}
                  </div>

                  {threadError && (
                    <div className="border-t border-red-200 bg-red-50 px-4 py-2 text-[11px] font-bold text-red-700">
                      {threadError}
                    </div>
                  )}

                  {canReply ? (
                    <div className="border-t border-slate-100">
                      {attachment.previewUrl && (
                        <ChatAttachmentPreview
                          previewUrl={attachment.previewUrl}
                          uploading={attachment.uploading}
                          errorKey={attachment.errorKey}
                          onRemove={attachment.clear}
                          fileName={attachment.fileName}
                          fileSize={attachment.fileSize}
                          onOpen={(src, alt) => setLightbox({ src, alt })}
                        />
                      )}

                      <div className="flex items-end gap-2 p-3">
                        <input
                          ref={fileInputRef}
                          type="file"
                          accept="image/png,image/jpeg,image/webp"
                          onChange={(e) => {
                            const file = e.target.files?.[0];
                            if (file) attachment.accept(file);
                            // Xóa giá trị để chọn LẠI CÙNG một tệp vẫn kích hoạt
                            // onChange: không xóa thì bỏ ảnh rồi chọn lại đúng ảnh đó
                            // sẽ không thấy gì xảy ra.
                            e.target.value = "";
                          }}
                          className="hidden"
                          aria-hidden="true"
                          tabIndex={-1}
                          id="chat-reply-file"
                        />
                        <button
                          type="button"
                          onClick={() => fileInputRef.current?.click()}
                          disabled={attachment.uploading}
                          aria-label={t("chat.attachment.add")}
                          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600 disabled:opacity-40"
                        >
                          <ImagePlus className="h-4 w-4" />
                        </button>

                        <label className="sr-only" htmlFor="chat-reply">
                          {t("admin.chat.reply_placeholder")}
                        </label>
                        <textarea
                          id="chat-reply"
                          ref={replyRef}
                          rows={2}
                          value={draft}
                          maxLength={2000}
                          onChange={(e) => setDraft(e.target.value)}
                          onPaste={(e) => {
                            // `preventDefault` CHỈ khi thực sự lấy được ảnh: chặn vô điều
                            // kiện sẽ làm nhân sự không dán được văn bản nữa — việc họ
                            // làm thường xuyên hơn dán ảnh.
                            if (attachment.acceptPaste(e.clipboardData?.items ?? null)) {
                              e.preventDefault();
                            }
                          }}
                          onKeyDown={(e) => {
                            // Enter gửi, Shift+Enter xuống dòng. Khu quản trị là giao
                            // diện máy tính có bàn phím thật, nên không cần dò con trỏ
                            // như bên người chơi.
                            if (e.key === "Enter" && !e.shiftKey) {
                              e.preventDefault();
                              void sendReply();
                            }
                          }}
                          placeholder={t("admin.chat.reply_placeholder")}
                          className="max-h-32 min-h-11 grow resize-none rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-xs font-medium leading-relaxed text-slate-900 outline-none transition-colors placeholder:text-slate-400 focus:border-slate-400 focus:bg-white"
                        />
                        <button
                          type="button"
                          onClick={() => void sendReply()}
                          // Cho gửi khi chỉ có ảnh; chặn trong lúc ảnh đang tải lên, vì
                          // gửi lúc đó thì tin đi mà không có ảnh.
                          disabled={
                            (draft.trim().length === 0 && !attachment.attachment) ||
                            sending ||
                            attachment.uploading
                          }
                          className="flex h-11 shrink-0 items-center gap-1.5 rounded-xl bg-red-600 px-4 text-xs font-bold text-white transition-colors hover:bg-red-700 disabled:bg-slate-200 disabled:text-slate-400"
                        >
                          {sending ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <SendHorizonal className="h-3.5 w-3.5" />
                          )}
                          {t("admin.chat.send")}
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2 border-t border-slate-100 bg-slate-50 px-4 py-3">
                      <EyeOff className="h-3.5 w-3.5 shrink-0 text-slate-400" />
                      <span className="text-[11px] font-medium text-slate-500">
                        {t("admin.chat.read_only")}
                      </span>
                    </div>
                  )}
                </>
              )}
            </section>
          </div>
        )}
      </div>

      {lightbox && (
        <ImageLightbox
          src={lightbox.src}
          alt={lightbox.alt}
          onClose={() => setLightbox(null)}
        />
      )}

      {showAdjustModal && activeRow && (
        <AdminModal
          isOpen={showAdjustModal}
          onClose={() => setShowAdjustModal(false)}
          title={
            <div className="flex items-center gap-2 font-extrabold text-slate-900">
              <WalletIcon className="h-5 w-5 text-red-600" />
              <span>{t("admin.users.wallet.tab_adjust")}</span>
            </div>
          }
        >
          <WalletAdjustPanel
            userId={activeRow.userId}
            username={activeRow.username}
            currentBalance={activeBalance}
            currency={activeCurrency}
            onAdjusted={() => {
              setShowAdjustModal(false);
              // Làm mới dòng hộp thư để cập nhật preview/unread
              void fetchRows().then((data) => {
                if (data) setRows(data);
              });
            }}
          />
        </AdminModal>
      )}
    </div>
  );
}
