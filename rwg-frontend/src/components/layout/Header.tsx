"use client";

import React, { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useTranslation } from "@/context/LanguageContext";
import { me, clearPlayerTokens } from "@/lib/playerApi";

/**
 * Thanh đầu trang của khu người chơi.
 *
 * `sticky top-0` để logo và lời chào không mất khi cuộn — trang gốc làm vậy, và
 * trên màn hình điện thoại thì đây cũng là chỗ duy nhất người dùng bấm để về đầu.
 *
 * Tên đăng nhập lấy từ `/users/me` chứ KHÔNG nhận qua prop với giá trị mặc định
 * cứng: trước đây trang chủ truyền sẵn "jinbao01" nên người chưa đăng nhập vẫn
 * thấy lời chào của một tài khoản không tồn tại.
 */
export const Header: React.FC = () => {
  const { t } = useTranslation();
  const [username, setUsername] = useState<string | null>(null);
  // `checked` phân biệt "chưa biết" với "biết là chưa đăng nhập". Thiếu nó thì
  // trong lúc chờ gọi API, giao diện sẽ nháy hiện nút đăng nhập rồi mới đổi sang
  // lời chào ở mọi lần tải trang.
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    let cancelled = false;
    // KHÔNG kiểm tra token rồi setState ngay trong thân effect: gọi setState đồng
    // bộ ở đây tạo một vòng render phụ ngay sau lần render đầu. `me()` tự từ chối
    // với 401 khi không có token và KHÔNG gửi request nào, nên trường hợp "chưa
    // đăng nhập" đi qua đúng nhánh catch bên dưới.
    me()
      .then((user) => {
        if (!cancelled) setUsername(user.username);
      })
      .catch(() => {
        // Không có token, token hết hạn, hoặc token bị thu hồi — đều là chuyện
        // bình thường: dọn token và coi như khách, KHÔNG hiện thông báo lỗi.
        if (!cancelled) {
          clearPlayerTokens();
          setUsername(null);
        }
      })
      .finally(() => {
        if (!cancelled) setChecked(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <header className="sticky top-0 z-10 w-full bg-[#0d0d0f] border-b border-[#1a1a1e]">
      {/* h-16 (64px) thay vì h-15: logo to hơn cần thêm chỗ thở trên dưới, không thì
          nó dính sát viền dưới của thanh. */}
      <div className="flex items-center justify-between gap-3 px-4 h-16">
        <Link href="/" className="flex items-center shrink-0" aria-label="Resorts World Genting">
          <Image
            src="/logo/logo1.png"
            alt="Resorts World Genting"
            width={800}
            height={185}
            priority
            // 160px: ảnh gốc 800×185 nên cao thành 37px, vừa trong thanh 64px.
            className="w-[160px] h-auto"
          />
        </Link>

        {checked && !username ? (
          <div className="flex items-center gap-2 shrink-0">
            <Link
              href="/login"
              // min-h-11 = 44px, mức tối thiểu cho vùng bấm bằng ngón tay.
              className="flex items-center justify-center min-h-11 px-4 text-[0.8125rem] font-bold text-[#d0d5da] active:opacity-70"
            >
              {t("auth.login")}
            </Link>
            <Link
              href="/new-account"
              className="flex items-center justify-center min-h-11 px-4 bg-primary text-[0.8125rem] font-bold text-white active:opacity-90"
            >
              {t("auth.new_account")}
            </Link>
          </div>
        ) : null}
      </div>

      {username ? (
        <p className="px-4 pb-2.5 text-primary font-bold text-[1.0625rem] leading-normal truncate">
          {t("header.welcome_back")}, {username}
        </p>
      ) : null}
    </header>
  );
};
