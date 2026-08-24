"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { ProfileMenuRow } from "@/components/profile/ProfileMenuRow";
import { useTranslation } from "@/context/LanguageContext";
import { getPlayerToken } from "@/lib/playerApi";

/**
 * Trang cài đặt tài khoản — chỉ là danh sách hai lối vào.
 *
 * SAO LẠI ĐÚNG BẢN GỐC (`pages/profile/account-settings-0041dce005267f14.js`): thân trang
 * là `grow flex flex-col` KHÔNG padding, hai dòng dùng đúng component dòng menu của trang
 * hồ sơ. Bản gốc KHÔNG gọi API nào ở trang này.
 *
 * KHÔNG dùng `PlayerLayout`: bản gốc đặt `topNavigationBarProps.type = "default"` cho các
 * trang con, tức là có thanh tiêu đề kèm nút quay lại và KHÔNG có thanh điều hướng dưới.
 * Bốn mục ở thanh dưới đều là trang gốc của một nhánh, nên ở trang con sâu hai cấp thì
 * không mục nào đúng để làm sáng.
 *
 * `backHref` cứng là `/profile` theo đúng bản gốc, KHÔNG dùng `router.back()`: người dùng
 * vào đây từ liên kết ngoài hoặc mở tab mới sẽ không có lịch sử để lùi, bấm nút là không
 * có gì xảy ra.
 */
export default function AccountSettingsPage() {
  const router = useRouter();
  const { t } = useTranslation();

  // `checked` để không vẽ nội dung trong lúc chưa biết đã đăng nhập hay chưa: hiện danh
  // sách rồi mới nhảy sang trang đăng nhập tạo ra một nháy khó chịu.
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
    <MobileShell header={<TopNavigationBar backHref="/profile" title={t("profile.account_settings")} />}>
      <main className="flex grow flex-col">
        {checked ? (
          <>
            <ProfileMenuRow
              href="/profile/account-settings/edit-profile"
              icon="icon-icon66"
              subtitle={t("profile.edit_profile_description")}
              title={t("profile.edit_profile")}
            />
            <ProfileMenuRow
              href="/profile/account-settings/withdrawal-details"
              icon="icon-icon65"
              subtitle={t("profile.withdrawal_details_description")}
              title={t("profile.withdrawal_details")}
            />
          </>
        ) : null}
      </main>
    </MobileShell>
  );
}
