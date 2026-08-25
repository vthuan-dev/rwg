"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { ProfileMenuRow } from "@/components/profile/ProfileMenuRow";
import { useTranslation } from "@/context/LanguageContext";
import { getPlayerToken } from "@/lib/playerApi";

/**
 * Trang trung tâm bảo mật — danh sách hai lối vào.
 *
 * SAO LẠI ĐÚNG BẢN GỐC (`pages/profile/security-center-a495f1990e8a8207.js`): cùng cấu
 * trúc với trang cài đặt tài khoản, chỉ khác hai dòng bên trong. Bản gốc KHÔNG gọi API.
 *
 * Xem ghi chú ở `account-settings/page.tsx` để biết vì sao không dùng `PlayerLayout` và
 * vì sao `backHref` là đường dẫn cứng.
 */
export default function SecurityCenterPage() {
  const router = useRouter();
  const { t } = useTranslation();

  const [checked, setChecked] = useState(false);

  // Hoãn `setChecked` sang microtask thay vì gọi thẳng trong thân effect: gọi thẳng làm
  // React vẽ lại ngay trong lượt hiện tại, sinh chuỗi render lồng nhau.
  //
  // KHÔNG dùng `useState(() => Boolean(getPlayerToken()))`: lần render đầu chạy trên
  // server, nơi không có `localStorage`, nên giá trị khởi tạo sẽ khác giữa server và
  // trình duyệt và React báo lệch hydration.
  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }

    let cancelled = false;
    void Promise.resolve().then(() => {
      if (!cancelled) setChecked(true);
    });

    return () => {
      cancelled = true;
    };
  }, [router]);

  return (
    <MobileShell header={<TopNavigationBar backHref="/profile" title={t("profile.security_center")} />}>
      <main className="flex grow flex-col">
        {checked ? (
          <>
            {/* CẢ HAI DÒNG DẪN SANG TRANG HỖ TRỢ, không sang trang tự đổi.

                Trước đây chúng trỏ tới `/profile/security-center/login-password` và
                `.../security-pin` — HAI TRANG KHÔNG TỒN TẠI. Thư mục `security-center/`
                chỉ có `page.tsx`, nên bấm vào là 404 và người dùng tưởng chức năng bị
                lỗi.

                Hướng vận hành đã chọn: người chơi nhắn cho hỗ trợ, nhân sự đổi hộ. Nên
                đích đúng là trang trò chuyện.

                DÙNG CHUỖI cho `href` chứ không dùng hàm gọi `router.push`:
                `ProfileMenuRow` render hàm thành `<button>` (xem javadoc của nó), còn
                đây là điều hướng thật — cần `<a>` để trình đọc màn hình đọc đúng là
                liên kết và người dùng mở được trong tab mới.

                Phụ đề đổi sang khoá riêng nói rõ phải liên hệ hỗ trợ, thay vì để người
                dùng bấm vào rồi mới biết mình không tự đổi được. Hai khoá cũ giữ nguyên
                vì trang `account-settings` cũng dùng chúng. */}
            <ProfileMenuRow
              href="/profile/security-center/login-password"
              icon="icon-icon37"
              subtitle={t("profile.login_password_contact")}
              title={t("profile.login_password")}
            />
            <ProfileMenuRow
              href="/profile/security-center/security-pin"
              icon="icon-icon42"
              subtitle={t("profile.security_pin_contact")}
              title={t("profile.security_pin")}
            />
          </>
        ) : null}
      </main>
    </MobileShell>
  );
}
