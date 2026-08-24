"use client";

import React, { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { useTranslation } from "@/context/LanguageContext";
import { useNotification } from "@/context/NotificationContext";
import {
  ApiError,
  clearPlayerTokens,
  getPlayerToken,
  getNotifications,
  markNotificationAsRead,
  markAllNotificationsAsRead,
  DbNotification,
} from "@/lib/playerApi";
import {
  Bell,
  CheckCheck,
  Trophy,
  AlertCircle,
  Check,
  Clock,
  ChevronDown,
  ChevronUp,
  Loader2,
  Megaphone,
} from "lucide-react";
import { formatMoney } from "@/lib/money";

export default function AnnouncementsPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const { refreshUnreadCount } = useNotification();

  const [checked, setChecked] = useState(false);
  const [notifications, setNotifications] = useState<DbNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Pagination
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loadingMore, setLoadingMore] = useState(false);

  // Expanded announcement IDs (for details)
  const [expandedIds, setExpandedIds] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }
    setChecked(true);
  }, [router]);

  const loadNotifications = useCallback(async (pageNum: number, append: boolean = false) => {
    try {
      if (pageNum === 0) setLoading(true);
      else setLoadingMore(true);

      const res = await getNotifications(pageNum, 15);
      if (append) {
        setNotifications((prev) => {
          // Tránh trùng id
          const existingIds = new Set(prev.map((n) => n.id));
          const newItems = res.content.filter((n) => !existingIds.has(n.id));
          return [...prev, ...newItems];
        });
      } else {
        setNotifications(res.content);
      }
      setTotalPages(res.totalPages);
      setPage(pageNum);
      setError(null);
    } catch (err) {
      // Phiên hết hạn (refresh token cũng đã hết): đưa về trang đăng nhập thay vì để lại
      // một dòng lỗi đỏ mà người dùng không có cách nào xử lý.
      if (err instanceof ApiError && err.status === 401) {
        clearPlayerTokens();
        router.replace("/login");
        return;
      }
      setError(t("profile.load_failed"));
      console.error("Lỗi tải thông báo:", err);
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, [router, t]);

  useEffect(() => {
    if (checked) {
      void loadNotifications(0);
    }
  }, [checked, loadNotifications]);

  const handleMarkAllRead = async () => {
    try {
      await markAllNotificationsAsRead();
      setNotifications((prev) =>
        prev.map((n) => ({ ...n, readAt: new Date().toISOString() }))
      );
      void refreshUnreadCount();
    } catch (err) {
      console.warn("Lỗi đánh dấu đọc tất cả:", err);
    }
  };

  const handleItemClick = async (n: DbNotification) => {
    // Expand or collapse
    setExpandedIds((prev) => ({ ...prev, [n.id]: !prev[n.id] }));

    if (n.readAt === null) {
      try {
        await markNotificationAsRead(n.id);
        setNotifications((prev) =>
          prev.map((item) =>
            item.id === n.id ? { ...item, readAt: new Date().toISOString() } : item
          )
        );
        void refreshUnreadCount();
      } catch (err) {
        console.warn("Lỗi đánh dấu đã đọc:", err);
      }
    }
  };

  /**
   * Dịch nội dung thông báo từ khoá + tham số backend gửi kèm.
   *
   * PHẢI định dạng lại {amount}: backend lưu BigDecimal scale 8 nên "40" thành
   * "40.00000000", chen nguyên vào câu sẽ ra "đã cộng $40.00000000".
   */
  const formatNotificationText = (titleKey: string, paramsJson: string | null) => {
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
  };

  const getNotificationIcon = (type: string) => {
    switch (type) {
      case "DEPOSIT_COMPLETED":
        return { Icon: Check, color: "text-emerald-500 bg-emerald-500/10 border-emerald-500/20" };
      case "DEPOSIT_REQUESTED":
        // Đồng hồ như WITHDRAWAL_REQUESTED: yêu cầu mới gửi, chưa có tiền nào vào ví.
        return { Icon: Clock, color: "text-sky-500 bg-sky-500/10 border-sky-500/20" };
      case "DEPOSIT_REJECTED":
        return { Icon: AlertCircle, color: "text-rose-500 bg-rose-500/10 border-rose-500/20" };
      case "WITHDRAWAL_REQUESTED":
        // Đồng hồ, không phải dấu tích: lệnh mới gửi CHƯA được duyệt. Dùng cùng icon với
        // WITHDRAWAL_APPROVED sẽ khiến người chơi tưởng tiền đã chuyển đi rồi.
        return { Icon: Clock, color: "text-sky-500 bg-sky-500/10 border-sky-500/20" };
      case "WITHDRAWAL_APPROVED":
        return { Icon: CheckCheck, color: "text-blue-500 bg-blue-500/10 border-blue-500/20" };
      case "WITHDRAWAL_REJECTED":
        return { Icon: AlertCircle, color: "text-rose-500 bg-rose-500/10 border-rose-500/20" };
      case "ADMIN_CREDIT":
        return { Icon: Trophy, color: "text-yellow-500 bg-yellow-500/10 border-yellow-500/20" };
      case "ADMIN_DEBIT":
        return { Icon: AlertCircle, color: "text-amber-500 bg-amber-500/10 border-amber-500/20" };
      default:
        return { Icon: Megaphone, color: "text-primary bg-primary/10 border-primary/20" };
    }
  };

  const hasUnread = notifications.some((n) => n.readAt === null);

  return (
    <MobileShell
      header={
        <TopNavigationBar
          backHref="/profile"
          title={t("notification.title")}
          rightSlot={
            hasUnread ? (
              <button
                className="text-[0.75rem] font-bold text-primary active:scale-95 transition-all"
                onClick={handleMarkAllRead}
                type="button"
              >
                {t("notification.mark_all_read")}
              </button>
            ) : undefined
          }
        />
      }
    >
      <main className="flex grow flex-col bg-black">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-20 gap-y-3">
            <Loader2 className="w-6 h-6 animate-spin text-primary" />
            <span className="text-xs text-primary font-bold">{t("draw.loading")}</span>
          </div>
        ) : error ? (
          <div className="text-center py-10 text-rose-500 font-semibold text-xs">{error}</div>
        ) : notifications.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-[#8b8b8b] text-center px-6">
            <Bell className="w-10 h-10 text-[#4b4b4b] mb-3" />
            <span className="text-[0.8125rem]">{t("notification.no_notifications")}</span>
          </div>
        ) : (
          <div className="flex flex-col grow pb-10">
            <ul className="flex flex-col">
              {notifications.map((n) => {
                const { Icon, color } = getNotificationIcon(n.type);
                const isUnread = n.readAt === null;
                const isExpanded = expandedIds[n.id];
                const messageText = n.broadcast
                  ? n.body || ""
                  : formatNotificationText(n.titleKey, n.paramsJson);

                return (
                  <li
                    key={n.id}
                    className={`border-b border-[#1f1f1f] transition-all duration-200 cursor-pointer ${
                      isUnread ? "bg-white/[0.02]" : "hover:bg-white/[0.01]"
                    }`}
                    onClick={() => void handleItemClick(n)}
                  >
                    <div className="flex items-start gap-x-3.5 px-5 py-4">
                      {/* Icon */}
                      <div className={`p-2 rounded-xl border shrink-0 ${color}`}>
                        <Icon className="w-4 h-4" />
                      </div>

                      {/* Content */}
                      <div className="flex grow flex-col min-w-0">
                        <div className="flex items-center gap-x-2">
                          <span className="text-[0.8125rem] font-bold text-white leading-normal truncate">
                            {n.broadcast
                              ? t("notification.announcement_title")
                              : t(n.titleKey + "_title")}
                          </span>
                          {isUnread && (
                            <span className="w-1.5 h-1.5 rounded-full bg-[#fe1616] shrink-0" />
                          )}
                        </div>

                        {/* Message summary */}
                        <p className={`text-[0.75rem] text-[#8b8b8b] mt-1 leading-normal break-words ${isExpanded ? "" : "line-clamp-2"}`}>
                          {messageText}
                        </p>

                        <span className="text-[0.625rem] text-[#5b5b5b] mt-2 font-medium">
                          {new Date(n.createdAt).toLocaleString("vi-VN")}
                        </span>
                      </div>

                      {/* Expand indicator (only for announcements or long body) */}
                      {n.broadcast && (
                        <div className="text-[#5b5b5b] hover:text-white shrink-0 self-center">
                          {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                        </div>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>

            {/* Load more */}
            {page + 1 < totalPages && (
              <div className="px-5 mt-4">
                <button
                  disabled={loadingMore}
                  onClick={() => void loadNotifications(page + 1, true)}
                  className="w-full h-11 bg-[#1f1f1f] active:bg-[#2a2a2a] text-xs font-bold text-white transition-all flex items-center justify-center gap-2 border border-white/5"
                  type="button"
                >
                  {loadingMore ? (
                    <Loader2 className="w-4 h-4 animate-spin text-primary" />
                  ) : null}
                  {t("history.load_more")}
                </button>
              </div>
            )}
          </div>
        )}
      </main>
    </MobileShell>
  );
}
