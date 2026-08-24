"use client";

import React, { useEffect, useState, useRef } from "react";
import { X } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

interface AdminModalProps {
  isOpen: boolean;
  onClose: () => void;
  children: React.ReactNode;
  maxWidthClass?: string; // Ví dụ: "max-w-lg", "max-w-2xl", v.v.
  title?: React.ReactNode;
}

/**
 * Component Modal dùng chung cho khu vực quản trị, hỗ trợ hiệu ứng đóng/mở mượt mà.
 * 
 * VÌ SAO TỰ QUẢN LÝ THỜI GIAN UNMOUNT: 
 * Trong React, nếu viết `{isOpen && <Modal />}` thì khi `isOpen` chuyển sang `false`,
 * component sẽ bị hủy mount ngay lập tức làm mất cơ hội chạy animation đóng (fade out / scale down).
 * Component này tự quản lý state `shouldRender` và trì hoãn việc unmount thêm 140ms 
 * (khớp với thời gian của class `animate-modal-panel-out`) để đảm bảo hiệu ứng đóng chạy trọn vẹn.
 */
export const AdminModal: React.FC<AdminModalProps> = ({
  isOpen,
  onClose,
  children,
  maxWidthClass = "max-w-lg",
  title,
}) => {
  const { t } = useTranslation();

  /**
   * Ba pha của hộp thoại. Một biến thay cho cặp `shouldRender` + `isClosing` trước đây:
   * hai biến rời nhau có thể rơi vào tổ hợp vô nghĩa (không hiện mà đang đóng), còn ở đây
   * mỗi pha là một trạng thái hợp lệ.
   */
  const [phase, setPhase] = useState<"hidden" | "open" | "closing">(
    isOpen ? "open" : "hidden"
  );

  // Prop `isOpen` của lần render trước, để nhận ra lúc nó vừa đổi.
  const [prevOpen, setPrevOpen] = useState(isOpen);

  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Điều chỉnh state NGAY trong lúc render, không qua effect.
  //
  // Đây là mẫu React khuyến nghị cho việc suy trạng thái từ prop vừa đổi. Làm trong effect
  // thì component phải render một lần với pha CŨ rồi mới render lại — hộp thoại sẽ nháy
  // một khung ở trạng thái sai. React thấy setState ở đây sẽ bỏ kết quả render hiện tại
  // và tính lại ngay, trước khi vẽ, nên người dùng không thấy khung trung gian nào.
  if (prevOpen !== isOpen) {
    setPrevOpen(isOpen);
    setPhase(isOpen ? "open" : "closing");
  }

  const shouldRender = phase !== "hidden";
  const isClosing = phase === "closing";

  useEffect(() => {
    if (phase !== "closing") {
      return;
    }

    // Tháo khỏi cây sau khi animation đóng chạy xong. setState nằm trong callback của
    // timer nên không phải `setState` đồng bộ trong thân effect.
    timerRef.current = setTimeout(() => {
      setPhase("hidden");
    }, 140); // Khớp với 140ms của .animate-modal-panel-out

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [phase]);

  // Đóng khi nhấn ESC
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && isOpen) {
        onClose();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  if (!shouldRender) return null;

  return (
    <div
      className={`fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs select-none ${
        isClosing ? "animate-modal-backdrop-out" : "animate-modal-backdrop-in"
      }`}
      onClick={onClose}
    >
      <div
        className={`bg-white border border-slate-200 rounded-2xl w-full max-h-[90vh] overflow-y-auto p-6 shadow-2xl relative flex flex-col gap-5 select-text ${maxWidthClass} ${
          isClosing ? "animate-modal-panel-out" : "animate-modal-panel-in"
        }`}
        onClick={(e) => e.stopPropagation()} // Chặn click lan ra lớp phủ gây đóng modal
      >
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-slate-400 hover:text-slate-700 transition-colors p-1 rounded-lg hover:bg-slate-100"
          aria-label={t("admin.states.close")}
        >
          <X className="w-5 h-5" />
        </button>

        {title && (
          <div className="flex items-center gap-3 pr-8 border-b border-slate-100 pb-3">
            {title}
          </div>
        )}

        <div className="flex-1 min-h-0">{children}</div>
      </div>
    </div>
  );
};
