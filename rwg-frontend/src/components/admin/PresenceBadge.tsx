"use client";

import React from "react";
import { formatRelativeTime, formatAbsoluteDateTime } from "@/lib/datetime";
import { useTranslation } from "@/context/LanguageContext";

interface Props {
  /** Kết luận đã tính ở server. KHÔNG tự suy ra từ `lastSeenAt` — xem javadoc bên dưới. */
  online: boolean;
  /** Mốc hoạt động cuối; null nghĩa là CHƯA RÕ, không phải "đã rời đi từ lâu". */
  lastSeenAt: string | null;
  /**
   * Lần đăng nhập gần nhất, dùng làm phương án cuối khi không có mốc hoạt động nào.
   *
   * CẦN vì trạng thái có mặt chỉ tồn tại từ lúc tính năng được bật, và mốc hết hạn sau
   * một thời gian. Không có nó thì mọi tài khoản lâu ngày không vào đều hiện "Chưa rõ" —
   * đúng về mặt kỹ thuật nhưng vô ích với người đang đánh giá tài khoản.
   */
  lastLoginAt: string | null;
  locale: string;
}

/**
 * Chỉ báo người chơi đang online hay đã rời đi.
 *
 * <h2>VÌ SAO KHÔNG TỰ TÍNH TRẠNG THÁI Ở ĐÂY</h2>
 * Cờ `online` do server tính. Tự so `lastSeenAt` với `Date.now()` sẽ sai khi đồng hồ máy
 * người vận hành lệch, và sẽ tạo ra một định nghĩa thứ hai của "bao lâu thì coi là offline"
 * bên cạnh cấu hình `rwg.presence.online-window` — hai nơi đó sẽ trôi khỏi nhau.
 *
 * <h2>BA TRẠNG THÁI, KHÔNG PHẢI HAI</h2>
 * "Chưa rõ" là trạng thái thật, không phải lỗi: Redis có thể chưa có mốc nào cho tài khoản
 * lâu ngày không đăng nhập. Vẽ nó thành "Ngoại tuyến" là khẳng định một điều chưa biết.
 */
export const PresenceBadge: React.FC<Props> = ({
  online,
  lastSeenAt,
  lastLoginAt,
  locale,
}) => {
  const { t } = useTranslation();

  if (online) {
    return (
      <span
        className="flex w-fit items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-[10px] font-bold text-emerald-700"
        title={t("admin.users.presence.online_hint")}
      >
        {/* Hai vòng lồng nhau: vòng ngoài lan ra rồi mờ dần, vòng trong đứng yên.
            Chỉ dùng hiệu ứng động cho trạng thái ĐANG online — một chấm tĩnh không phân
            biệt được với chấm "còn mới" ở các cột khác, còn chấm nhấp nháy thì đọc được
            ngay từ khoé mắt khi đang quét bảng. */}
        <span aria-hidden="true" className="relative flex h-1.5 w-1.5 shrink-0">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-500 opacity-75" />
          <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-emerald-500" />
        </span>
        {t("admin.users.presence.online")}
      </span>
    );
  }

  // Mốc hoạt động đáng tin hơn lần đăng nhập, nên ưu tiên nó khi có.
  const reference = lastSeenAt ?? lastLoginAt;

  if (!reference) {
    return (
      <span className="text-[11px] font-medium text-slate-300">
        {t("admin.users.presence.unknown")}
      </span>
    );
  }

  return (
    <span
      className="flex w-fit items-center gap-1.5 text-[11px] font-semibold text-slate-500"
      title={`${t("admin.users.presence.last_seen", {
        time: formatAbsoluteDateTime(reference, locale),
      })}`}
    >
      <span aria-hidden="true" className="h-1.5 w-1.5 shrink-0 rounded-full bg-slate-300" />
      {formatRelativeTime(reference, locale)}
    </span>
  );
};
