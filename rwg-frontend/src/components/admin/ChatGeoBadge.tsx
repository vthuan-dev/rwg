"use client";

import React from "react";
import { MapPin } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";
import { countryFlagEmoji } from "@/lib/countryFlag";

export interface ChatGeoBadgeProps {
  /** Mã ISO 3166-1 alpha-2; null khi không xác định được. */
  countryCode: string | null;
  countryName: string | null;
  /** Tỉnh / thành phố trực thuộc / vùng. */
  region: string | null;
  city: string | null;
  /** Nhà mạng — hiện trong tooltip, không hiện trên dòng chính. */
  isp: string | null;
  /** IP thô — hiện trong tooltip để đối chiếu với báo cáo rủi ro. */
  ip: string | null;
}

/**
 * Vị trí địa lý của khách, hiện trên header đoạn chat ở khu quản trị.
 *
 * <h2>SUY TỪ IP — KHÔNG XIN QUYỀN VỊ TRÍ CỦA KHÁCH</h2>
 * Khách không cần bấm đồng ý gì; thông tin này lấy từ IP trong request của họ. Đổi lại,
 * độ chính xác chỉ ở mức tỉnh/thành theo nhà mạng, và khách dùng VPN sẽ hiện sai hoàn
 * toàn. Đây là chỉ dấu ĐỊNH HƯỚNG cho người trả lời hỗ trợ, không phải căn cứ xác minh.
 *
 * <h2>HIỆN GÌ Ở ĐÂU</h2>
 * Dòng chính chỉ có cờ + "Thành phố, Nước" — đó là thứ người trả lời cần đọc trong một
 * giây. IP và nhà mạng đẩy vào tooltip: chúng cần khi điều tra, nhưng để trên dòng chính
 * thì đẩy phần thông tin hữu ích nhất ra khỏi tầm mắt trên màn hình hẹp.
 *
 * Trả về null khi KHÔNG CÓ GÌ để hiện, thay vì vẽ một chỗ trống hay chữ "Không rõ":
 * header chat vốn đã chật, và một dòng "Không rõ" chiếm chỗ mà không nói được gì.
 */
export const ChatGeoBadge: React.FC<ChatGeoBadgeProps> = ({
  countryCode,
  countryName,
  region,
  city,
  isp,
  ip,
}) => {
  const { t } = useTranslation();

  const flag = countryFlagEmoji(countryCode);

  // Ghép từ CHI TIẾT NHẤT tới rộng nhất (thành phố -> tỉnh -> nước), và bỏ phần trùng:
  // dịch vụ tra IP rất hay trả city và region giống nhau với các thành phố trực thuộc
  // ("Ho Chi Minh, Ho Chi Minh, Vietnam"), nên phải lọc thay vì nối thẳng.
  const parts: string[] = [];
  if (city) parts.push(city);
  if (region && region !== city) parts.push(region);
  if (countryName && countryName !== region && countryName !== city) {
    parts.push(countryName);
  }

  const label = parts.join(", ");

  if (!label && !flag) {
    return null;
  }

  // Tooltip gom mọi thứ, gồm cả IP thô. Dùng `title` thay vì tooltip tự vẽ: nội dung
  // này chỉ để tra cứu thỉnh thoảng, không đáng thêm một lớp popover và cách xử lý
  // đóng/mở của nó.
  const tooltip = [label, isp, ip].filter(Boolean).join(" · ");

  return (
    <span
      className="flex min-w-0 items-center gap-1 text-[11px] font-medium text-slate-500"
      title={tooltip}
    >
      {flag ? (
        // aria-hidden: cờ là hình trang trí, phần chữ ngay bên cạnh đã mang đủ thông tin.
        // Không ẩn thì trình đọc màn hình đọc thành "cờ Việt Nam Việt Nam".
        <span aria-hidden="true" className="text-[13px] leading-none">
          {flag}
        </span>
      ) : (
        <MapPin className="h-3 w-3 shrink-0 text-slate-400" />
      )}
      <span className="truncate">{label || t("admin.chat.geo_unknown")}</span>
    </span>
  );
};
