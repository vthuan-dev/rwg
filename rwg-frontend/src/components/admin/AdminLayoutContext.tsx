"use client";

import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import { usePathname } from "next/navigation";
import { getAdminToken, adminFetch } from "@/lib/adminApi";

interface AdminLayoutContextValue {
  isMobileSidebarOpen: boolean;
  setIsMobileSidebarOpen: (open: boolean) => void;
  toggleMobileSidebar: () => void;
  closeMobileSidebar: () => void;
  pendingCount: number;
  chatUnread: number;
  refreshCounts: () => Promise<void>;
}

const AdminLayoutContext = createContext<AdminLayoutContextValue>({
  isMobileSidebarOpen: false,
  setIsMobileSidebarOpen: () => {},
  toggleMobileSidebar: () => {},
  closeMobileSidebar: () => {},
  pendingCount: 0,
  chatUnread: 0,
  refreshCounts: async () => {},
});

export const useAdminLayout = () => useContext(AdminLayoutContext);

export const AdminLayoutProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState(false);
  const [pendingCount, setPendingCount] = useState(0);
  const [chatUnread, setChatUnread] = useState(0);
  const pathname = usePathname();

  const toggleMobileSidebar = useCallback(() => {
    setIsMobileSidebarOpen((prev) => !prev);
  }, []);

  const closeMobileSidebar = useCallback(() => {
    setIsMobileSidebarOpen(false);
  }, []);

  // Tự động đóng mobile drawer khi chuyển trang
  useEffect(() => {
    setIsMobileSidebarOpen(false);
  }, [pathname]);

  // Khóa scroll trên body khi mobile sidebar đang mở để tránh cuộn nền
  useEffect(() => {
    if (isMobileSidebarOpen) {
      const originalOverflow = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      return () => {
        document.body.style.overflow = originalOverflow;
      };
    }
  }, [isMobileSidebarOpen]);

  // Đóng khi nhấn phím Escape
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setIsMobileSidebarOpen(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  // Polling hợp nhất cho cả thông báo rút tiền chờ duyệt lẫn tin nhắn chat hỗ trợ
  const refreshCounts = useCallback(async () => {
    if (!getAdminToken()) return;
    try {
      const [resPending, resChat] = await Promise.allSettled([
        adminFetch<{ pendingWithdrawals: number }>("/admin/withdrawals/pending-count"),
        adminFetch<{ messages: number; conversations: number }>("/admin/chat/unread-count"),
      ]);

      if (resPending.status === "fulfilled") {
        setPendingCount(resPending.value.pendingWithdrawals || 0);
      }
      if (resChat.status === "fulfilled") {
        setChatUnread(resChat.value.conversations || 0);
      }
    } catch {
      // Bỏ qua lỗi kết nối nền
    }
  }, []);

  useEffect(() => {
    void refreshCounts();
    const interval = setInterval(() => {
      void refreshCounts();
    }, 20000); // 20 giây một nhịp

    return () => clearInterval(interval);
  }, [refreshCounts]);

  return (
    <AdminLayoutContext.Provider
      value={{
        isMobileSidebarOpen,
        setIsMobileSidebarOpen,
        toggleMobileSidebar,
        closeMobileSidebar,
        pendingCount,
        chatUnread,
        refreshCounts,
      }}
    >
      {children}
    </AdminLayoutContext.Provider>
  );
};
