"use client";

import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useRef,
} from "react";
import { Client } from "@stomp/stompjs";
import { useTranslation } from "@/context/LanguageContext";
import {
  ApiError,
  clearPlayerTokens,
  getPlayerToken,
  getUnreadNotificationsCount,
  getChatUnreadCount,
  DbNotification,
} from "@/lib/playerApi";
import { WS_BASE_URL } from "@/lib/constants";
import {
  playNotificationChime,
  primeNotificationSound,
} from "@/lib/notificationSound";
import { Bell, Trophy, AlertCircle, X, Check } from "lucide-react";
import { formatMoney } from "@/lib/money";

export interface ToastItem {
  id: string;
  type: "info" | "success" | "win" | "loss" | "error";
  title: string;
  message: string;
}

interface NotificationContextType {
  unreadCount: number;
  refreshUnreadCount: () => Promise<void>;
  /** Số tin nhắn hỗ trợ chưa đọc — cho viên đỏ trên ô "Trò chuyện trực tiếp". */
  chatUnreadCount: number;
  refreshChatUnreadCount: () => Promise<void>;
  toasts: ToastItem[];
  addToast: (type: ToastItem["type"], title: string, message: string) => void;
  removeToast: (id: string) => void;
}

const NotificationContext = createContext<NotificationContextType>({
  unreadCount: 0,
  refreshUnreadCount: async () => {},
  chatUnreadCount: 0,
  refreshChatUnreadCount: async () => {},
  toasts: [],
  addToast: () => {},
  removeToast: () => {},
});

export const useNotification = () => useContext(NotificationContext);

/**
 * Sự kiện chuyển gói chat từ WebSocket tới trang đang mở.
 *
 * Dùng CustomEvent trên `window` thay vì đặt danh sách tin nhắn vào context: để
 * trong context thì MỌI trang bọc bởi provider này sẽ render lại mỗi lần có tin
 * mới, kể cả khi người dùng đang ở bàn cược. Đây đúng là cách
 * `wallet_balance_updated` đã làm ở trên.
 */
export const CHAT_EVENT = "chat_event";

/**
 * Đường dẫn của khu quản trị.
 *
 * KHÔNG dùng `ADMIN_URL_PREFIX` (`/admin/${secret}`): đường dẫn bí mật đổi được qua
 * biến môi trường, còn tiền tố `/admin` thì không. So với tiền tố ngắn nên đúng với
 * mọi giá trị secret.
 */
const ADMIN_PATH_PREFIX = "/admin";

/**
 * Trang hiện tại có thuộc khu quản trị không.
 *
 * Provider này nằm ở layout gốc nên chạy trên MỌI trang, kể cả `/admin/*`. Nhân sự
 * quản trị KHÔNG có token người chơi (họ dùng `rwg_admin_token` riêng), nên mở
 * WebSocket ở đây hoặc không nối được, hoặc nối bằng token người chơi còn sót trong
 * localStorage rồi bị interceptor từ chối vì lệch `audience`. Cả hai đều vô ích và đều
 * để lại lỗi đỏ trong Console của người vận hành.
 *
 * Khu quản trị có socket riêng: `useAdminChatSocket` nối vào app 8081.
 */
const onAdminArea = (): boolean =>
  typeof window !== "undefined" &&
  window.location.pathname.startsWith(ADMIN_PATH_PREFIX);

const getWsUrl = () => {
  const base = WS_BASE_URL;
  return base.replace(/^http/, "ws");
};

export const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const { t } = useTranslation();
  const [unreadCount, setUnreadCount] = useState(0);
  const [chatUnreadCount, setChatUnreadCount] = useState(0);
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const stompClientRef = useRef<Client | null>(null);

  const addToast = useCallback(
    (type: ToastItem["type"], title: string, message: string) => {
      const id = Math.random().toString(36).substring(2, 9);
      setToasts((prev) => [...prev, { id, type, title, message }]);
      setTimeout(() => {
        removeToast(id);
      }, 5000);
    },
    []
  );

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const refreshUnreadCount = useCallback(async () => {
    if (!getPlayerToken()) return;
    try {
      const res = await getUnreadNotificationsCount();
      setUnreadCount(res.count);
    } catch (err) {
      // 401 = chưa đăng nhập hoặc phiên đã hết hạn. Đây là trạng thái BÌNH THƯỜNG với
      // một badge đếm chạy nền, nên không ghi log: mỗi lần tải trang khi chưa đăng nhập
      // sẽ để lại một dòng cảnh báo vàng vô nghĩa trong console.
      if (err instanceof ApiError && err.status === 401) {
        setUnreadCount(0);
        return;
      }
      console.warn("Lỗi đọc số thông báo chưa đọc:", err);
    }
  }, []);

  const refreshChatUnreadCount = useCallback(async () => {
    if (!getPlayerToken()) return;
    try {
      const res = await getChatUnreadCount();
      setChatUnreadCount(res.messages);
    } catch (err) {
      // Cùng lý do như trên: 401 là trạng thái bình thường của một badge chạy nền.
      if (err instanceof ApiError && err.status === 401) {
        setChatUnreadCount(0);
        return;
      }
      console.warn("Lỗi đọc số tin chat chưa đọc:", err);
    }
  }, []);

  /**
   * Dịch nội dung thông báo từ khoá + tham số backend gửi kèm.
   *
   * PHẢI định dạng lại {amount}: backend lưu BigDecimal scale 8 nên "40" thành
   * "40.00000000", chèn nguyên vào câu sẽ ra "đã cộng $40.00000000".
   *
   * Giủ giống hệt bản ở trang danh sách thông báo: cùng một thông báo hiện ở hai
   * chỗ (toast lúc tới, danh sách lúc xem lại) nên hai nơi lệch nhau sẽ thành lỗi
   * khó hiểu cho người đọc.
   */
  const formatNotificationText = useCallback(
    (titleKey: string, paramsJson: string | null) => {
      let params: Record<string, string | number> = {};
      if (paramsJson) {
        try {
          params = JSON.parse(paramsJson);
        } catch {
          // Tham số hỏng thì vẫn dịch được câu, chỉ còn lại placeholder "{amount}".
        }
      }
      if (params.amount !== undefined) {
        params = { ...params, amount: formatMoney(String(params.amount)) };
      }
      return t(titleKey, params);
    },
    [t]
  );

  // Connect / Disconnect WebSocket
  const setupWebSocket = useCallback(() => {
    if (typeof window === "undefined") return;

    // Khu quản trị: không mở socket người chơi. Xem `onAdminArea`.
    if (onAdminArea()) return;

    // Disconnect old client if exists
    if (stompClientRef.current) {
      stompClientRef.current.deactivate();
      stompClientRef.current = null;
    }

    const token = getPlayerToken();
    if (!token) return;

    const wsUrl = getWsUrl();
    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: () => {},
    });

    client.onConnect = () => {
      // 1) Subscribe notifications
      client.subscribe("/user/queue/notifications", (msg) => {
        try {
          const notification: DbNotification = JSON.parse(msg.body);
          const messageText = notification.broadcast
            ? notification.body || ""
            : formatNotificationText(notification.titleKey, notification.paramsJson);

          // Tin bị từ chối dùng màu lỗi — cả RÚT và NẠP, không chỉ rút: một yêu cầu
          // nạp bị từ chối hiện màu xanh "thành công" là sai hẳn ý nghĩa.
          const isRejection =
            notification.type === "WITHDRAWAL_REJECTED" ||
            notification.type === "DEPOSIT_REJECTED";

          // Tiêu đề RIÊNG theo loại thay vì chuỗi chung "Thông báo": với tiêu đề chung,
          // người chơi phải đọc hết dong nội dung mới biết chuyện gì xảy ra. Dùng cùng
          // khoá với trang danh sách thông báo để hai chỗ không lệch chứ.
          const toastTitle = notification.broadcast
            ? t("notification.announcement")
            : t(notification.titleKey + "_title");

          addToast(isRejection ? "error" : "success", toastTitle, messageText);
          playNotificationChime();
          void refreshUnreadCount();
        } catch (err) {
          console.error("Lỗi xử lý websocket notification:", err);
        }
      });

      // 2) Subscribe game results
      client.subscribe("/user/queue/game/results", (msg) => {
        try {
          const payload = JSON.parse(msg.body);
          const payout = Number(payload.payout || "0");
          if (payout > 0) {
            addToast(
              "win",
              t("notification.game_win").split(":")[0],
              t("notification.game_win", { amount: payload.payout })
            );
          } else {
            addToast(
              "loss",
              t("notification.game_loss").split("!")[0] + "!",
              t("notification.game_loss")
            );
          }
        } catch (err) {
          console.error("Lỗi xử lý websocket game result:", err);
        }
      });

      // 3) Subscribe wallet updates
      client.subscribe("/user/queue/wallet", (msg) => {
        try {
          const payload = JSON.parse(msg.body);
          window.dispatchEvent(
            new CustomEvent("wallet_balance_updated", { detail: payload.balance })
          );
        } catch (err) {
          console.error("Lỗi xử lý websocket wallet update:", err);
        }
      });

      // 3b) Subscribe game odds updates
      client.subscribe("/user/queue/game/odds-updated", (msg) => {
        try {
          const payload = JSON.parse(msg.body);
          window.dispatchEvent(
            new CustomEvent("game_odds_updated", { detail: payload })
          );
        } catch (err) {
          console.error("Lỗi xủ lý websocket odds update:", err);
        }
      });

      // 3c) Phiên bị thu hồi (admin khóa tài khoản, đổi quyền, hoặc đặt lại mật khẩu).
      //
      // KHÔNG hiện thông báo gì: đây là yêu cầu nghiệp vụ. Người bị khóa chỉ thấy mình
      // đang ở trang đăng nhập; lý do khóa không được tiết lộ ra giao diện.
      client.subscribe("/user/queue/session", () => {
        clearPlayerTokens();

        // Ngắt socket TRƯỚC khi điều hướng: token đã bị xoá nên lần kết nối lại tự động
        // của stompjs (reconnectDelay 5000) sẽ thất bại và lặp mãi, mỗi lần ghi một dòng lỗi
        // vào console của trang đăng nhập.
        stompClientRef.current?.deactivate();
        stompClientRef.current = null;

        // `location.replace` chứ không router của Next: cần Bỏ HẲN toàn bộ state đang có
        // trong trang. Điều hướng bằng router giữ nguyên cây React, nên các trang đang mở
        // vẫn còn dữ liệu cũ trong bộ nhớ và các vòng gọi API của chúng vẫn chạy tiếp một
        // lúc. `replace` cũng không để lại mục lịch sử, nên bấm nút quay lại không đưa họ
        // về trang đã bị tước quyền.
        window.location.replace("/login");
      });

      // 4) Subscribe chat hỗ trợ.
      //
      // ĐĂNG KÝ TRÊN CHÍNH client này, KHÔNG mở kết nối thứ hai: một kết nối
      // WebSocket thứ hai sẽ nhân đôi số phiên STOMP trên server cho mọi người
      // đang online, chỉ để nghe thêm một đích.
      client.subscribe("/user/queue/chat", (msg) => {
        try {
          const payload = JSON.parse(msg.body);

          // Chuyển nguyên gói cho trang chat đang mở (nếu có) để nó tự chèn bong
          // bóng mới mà không phải gọi lại API.
          window.dispatchEvent(new CustomEvent(CHAT_EVENT, { detail: payload }));

          // Chỉ tin của NHÂN SỰ mới làm tăng viên đỏ. Gói MESSAGE cũng được gửi
          // về cho chính người vừa gửi (để đồng bộ giữa nhiều tab), nên không
          // lọc thì viên đỏ sẽ tăng mỗi lần chính người dùng gửi tin.
          const isStaffMessage =
            payload?.type === "MESSAGE" && payload?.message?.senderType === "STAFF";
          if (!isStaffMessage) {
            return;
          }

          // Đang ở chính trang chat thì không tăng: trang đó tự đánh dấu đã đọc, nên
          // tăng rồi xóa ngay sau đó chỉ làm viên đỏ nháy một cái.
          const onChatPage = window.location.pathname.startsWith("/profile/contact-us");
          if (onChatPage) {
            return;
          }

          setChatUnreadCount((prev) => prev + 1);
          addToast(
            "info",
            t("chat.toast_title"),
            // Tin chỉ có ảnh không có chữ nào để hiện — để trống thì thông báo trông như
            // bị lỗi. Dùng cùng khoá mô tả với đoạn xem trước trong hộp thư.
            payload.message.body || t("chat.preview.image")
          );
          // Tiếng chuông ĐẦU TIÊN, trước cả ô thông báo: người dùng có thể đang ở tab khác
          // hoặc không nhìn màn hình, lúc đó âm thanh là thứ duy nhất đọng đến họ.
          playNotificationChime();
        } catch (err) {
          console.error("Lỗi xử lý websocket chat:", err);
        }
      });
    };

    client.onStompError = (frame) => {
      console.warn("STOMP error:", frame.body);
    };

    client.activate();
    stompClientRef.current = client;
  }, [addToast, formatNotificationText, refreshUnreadCount, t]);

  useEffect(() => {
    // Khu quản trị không dùng gì trong provider này: không đếm chưa đọc, không socket.
    // Hai lần gọi API bên dưới chỉ trả 401 và làm ồn Network tab của nhân sự.
    if (onAdminArea()) return;

    // Tải đếm chưa đọc lúc đầu
    void refreshUnreadCount();
    void refreshChatUnreadCount();
    // Thiết lập kết nối WS
    setupWebSocket();

    // Mở khoá âm thanh: trình duyệt treo AudioContext cho tới khi người dùng có tương tác
    // thật đầu tiên. Không làm bước này thì tiếng chuông im lặng mà không báo lỗi gì.
    const detachSoundPrimer = primeNotificationSound();

    // Lắng nghe sự kiện đăng nhập/đăng xuất
    const handleAuthChange = () => {
      void refreshUnreadCount();
      void refreshChatUnreadCount();
      setupWebSocket();
    };

    window.addEventListener("auth_changed", handleAuthChange);
    return () => {
      window.removeEventListener("auth_changed", handleAuthChange);
      detachSoundPrimer();
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
      }
    };
  }, [refreshUnreadCount, refreshChatUnreadCount, setupWebSocket]);

  return (
    <NotificationContext.Provider
      value={{
        unreadCount,
        refreshUnreadCount,
        chatUnreadCount,
        refreshChatUnreadCount,
        toasts,
        addToast,
        removeToast,
      }}
    >
      {children}

      {/* Container hiển thị toast popups ở góc màn hình */}
      <div className="fixed top-4 right-4 z-50 flex flex-col gap-y-3 w-full max-w-[340px] px-4 pointer-events-none">
        {toasts.map((toast) => {
          let Icon = Bell;
          let iconColor = "text-amber-500";
          let bgBorder = "bg-[#121212]/95 border-amber-500/20";
          if (toast.type === "success") {
            Icon = Check;
            iconColor = "text-emerald-500";
            bgBorder = "bg-[#121212]/95 border-emerald-500/20";
          } else if (toast.type === "win") {
            Icon = Trophy;
            iconColor = "text-yellow-500 animate-bounce";
            bgBorder = "bg-[#121212]/95 border-yellow-500/30 ring-1 ring-yellow-500/10";
          } else if (toast.type === "loss" || toast.type === "error") {
            Icon = AlertCircle;
            iconColor = "text-rose-500";
            bgBorder = "bg-[#121212]/95 border-rose-500/20";
          }

          return (
            <div
              key={toast.id}
              className={`flex items-start gap-x-3 p-4 rounded-xl border backdrop-blur-md shadow-2xl pointer-events-auto transition-all duration-300 transform translate-x-0 ${bgBorder}`}
            >
              <div className={`p-1.5 rounded-lg bg-white/5 shrink-0 ${iconColor}`}>
                <Icon className="w-5 h-5" />
              </div>
              <div className="flex grow flex-col min-w-0">
                <span className="text-[0.875rem] font-bold text-white leading-tight">
                  {toast.title}
                </span>
                <span className="text-[0.75rem] text-[#8b8b8b] mt-1 leading-normal break-words">
                  {toast.message}
                </span>
              </div>
              <button
                className="text-[#5b5b5b] hover:text-white shrink-0 p-0.5 rounded-lg hover:bg-white/5 transition-all"
                onClick={() => removeToast(toast.id)}
                type="button"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          );
        })}
      </div>
    </NotificationContext.Provider>
  );
};
