"use client";

import React from "react";
import { Megaphone } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Dải thông báo chạy ngang.
 *
 * `animate-marquee` chỉ chạy khi người dùng KHÔNG bật giảm chuyển động — quy tắc
 * `prefers-reduced-motion` trong globals.css lo phần đó. Chữ chạy liên tục là một
 * trong những hiệu ứng gây khó chịu nhất với người nhạy cảm chuyển động.
 *
 * Dùng lucide cho biểu tượng loa: bộ icon font nhúng từ trang gốc chỉ ánh xạ 4 mã
 * (mắt, mắt gạch, quả cầu, mũi nhọn quay lại) và không có biểu tượng loa nào.
 */
export const MarqueeNotice: React.FC = () => {
  const { t } = useTranslation();

  return (
    // `py-1.5` (6px) thay vì `py-2`: dải này chỉ là thông báo phụ, không cần chiếm
    // nhiều chiều cao ở màn hình đầu tiên.
    <div className="w-full bg-[#161619] border-y border-[#232328] py-1.5 px-4 flex items-center gap-2.5 overflow-hidden">
      <Megaphone className="w-3.5 h-3.5 shrink-0 text-primary" aria-hidden="true" />
      <div className="flex-1 overflow-hidden">
        {/* role=status để trình đọc màn hình đọc nội dung mà không cắt ngang việc
            người dùng đang làm. */}
        <p
          role="status"
          className="whitespace-nowrap inline-block animate-marquee text-[0.75rem] font-medium text-[#d0d5da]"
        >
          {t("marquee.notice")}
        </p>
      </div>
    </div>
  );
};
