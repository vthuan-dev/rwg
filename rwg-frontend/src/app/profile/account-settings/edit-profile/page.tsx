"use client";

import React, { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import {
  SelectField,
  SubmitButton,
  TextField,
} from "@/components/profile/FormField";
import { useTranslation } from "@/context/LanguageContext";
import { COUNTRIES } from "@/lib/countries";
import { ApiError, getPlayerToken, me, updateProfile } from "@/lib/playerApi";

/**
 * Trang chỉnh sửa hồ sơ.
 *
 * Bốn ô theo đúng bản gốc: tên đăng nhập (chỉ đọc), họ và tên, quốc gia, số điện thoại.
 *
 * TÊN ĐĂNG NHẬP KHÔNG SỬA ĐƯỢC, giống bản gốc. Đây không phải hạn chế tạm: tên đăng nhập
 * là thứ người dùng dùng để đăng nhập và là thứ nhân viên hỗ trợ tra cứu, đổi nó sẽ phá
 * mọi dấu vết audit đã ghi. Ô vẫn hiện ra để người dùng thấy mình đang sửa hồ sơ nào.
 *
 * KHÔNG có ô email: cột `users.email` có ràng buộc unique, cho sửa tự do sẽ mở đường
 * chiếm email của tài khoản khác. Việc đó cần luồng xác thực riêng.
 */
export default function EditProfilePage() {
  const router = useRouter();
  const { t } = useTranslation();

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [username, setUsername] = useState("");
  const [fullName, setFullName] = useState("");
  const [countryCode, setCountryCode] = useState("");
  const [phone, setPhone] = useState("");

  const [phoneError, setPhoneError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }

    let cancelled = false;

    // Mọi thao tác đổi state nằm TRONG closure async, không ở thân effect: đặt thẳng
    // `setLoading(true)` ở thân effect bị luật lint `set-state-in-effect` chặn, và đúng
    // là nó gây một lượt render lồng vô ích.
    const load = async () => {
      try {
        const user = await me();
        if (cancelled) return;

        setUsername(user.username);
        // `?? ""` vì backend trả null khi chưa khai; gán null vào ô nhập sẽ làm React
        // chuyển ô đó thành ô không kiểm soát và cảnh báo.
        setFullName(user.fullName ?? "");
        setCountryCode(user.countryCode ?? "");
        setPhone(user.phone ?? "");
        setLoadError(null);
      } catch (error) {
        if (cancelled) return;
        if (error instanceof ApiError && error.status === 401) {
          router.replace("/login");
          return;
        }
        setLoadError(t("profile.load_failed"));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, [router, t]);

  const handleSave = useCallback(() => {
    // Kiểm số điện thoại ngay tại trình duyệt cho phản hồi tức thì, nhưng backend vẫn kiểm
    // lại bằng regex của nó — bản ở đây chỉ để đỡ một vòng gọi mạng, không phải hàng rào.
    const trimmedPhone = phone.trim();
    if (trimmedPhone && !/^[0-9+\-\s]{6,20}$/.test(trimmedPhone)) {
      setPhoneError(t("profile.phone_invalid"));
      return;
    }
    setPhoneError(null);

    const save = async () => {
      setSaving(true);
      setSaveError(null);
      setSaved(false);
      try {
        // Gửi cả ba trường, kể cả chuỗi rỗng: chuỗi rỗng nghĩa là XOÁ giá trị cũ, nên
        // người dùng bỏ trắng một ô đã khai trước đó thì thông tin đó phải mất đi thật.
        const updated = await updateProfile({
          fullName: fullName.trim(),
          countryCode: countryCode.trim().toUpperCase(),
          phone: trimmedPhone,
        });

        // Điền lại từ phản hồi của server chứ không giữ giá trị vừa gõ: server đã cắt
        // khoảng trắng và chuẩn hoá, hiện đúng thứ đã lưu mới là sự thật.
        setFullName(updated.fullName ?? "");
        setCountryCode(updated.countryCode ?? "");
        setPhone(updated.phone ?? "");
        setSaved(true);
      } catch (error) {
        if (error instanceof ApiError) {
          if (error.status === 401) {
            router.replace("/login");
            return;
          }
          setSaveError(error.message || t("profile.save_failed"));
        } else {
          setSaveError(t("profile.save_failed"));
        }
      } finally {
        setSaving(false);
      }
    };

    void save();
  }, [countryCode, fullName, phone, router, t]);

  return (
    <MobileShell
      background="plain"
      header={
        <TopNavigationBar
          backHref="/profile/account-settings"
          title={t("profile.edit_profile")}
        />
      }
    >
      <main className="flex grow flex-col px-5 py-4">
        {loading ? (
          <p className="mt-[50px] text-center text-[#8b8b8b]">{t("asset.loading")}</p>
        ) : loadError ? (
          <p className="mt-[50px] text-center text-[#fe1616]">{loadError}</p>
        ) : (
          <>
            <div className="flex flex-col gap-y-5">
              <TextField
                disabled
                label={t("profile.username")}
                onChange={() => undefined}
                value={username}
              />
              <TextField
                autoComplete="name"
                label={t("profile.full_name")}
                maxLength={100}
                onChange={setFullName}
                placeholder={t("profile.enter_x", { x: t("profile.full_name") })}
                value={fullName}
              />
              <SelectField
                label={t("profile.country")}
                onChange={setCountryCode}
                options={COUNTRIES.map((country) => ({
                  value: country.code,
                  label: country.name,
                }))}
                placeholder={t("profile.select_x", { x: t("profile.country") })}
                value={countryCode}
              />
              <TextField
                autoComplete="tel"
                error={phoneError ?? undefined}
                inputMode="tel"
                label={t("profile.phone_number")}
                maxLength={20}
                onChange={setPhone}
                placeholder={t("profile.enter_x", { x: t("profile.phone_number") })}
                value={phone}
              />
            </div>

            {saveError ? (
              <p className="mt-4 text-[0.8125rem] text-[#fe1616]" role="alert">
                {saveError}
              </p>
            ) : null}
            {saved ? (
              <p className="mt-4 text-[0.8125rem] text-[#01bd8b]" role="status">
                {t("profile.saved")}
              </p>
            ) : null}

            <div className="mt-8">
              <SubmitButton disabled={saving} onClick={handleSave}>
                {saving ? t("profile.saving") : t("profile.save")}
              </SubmitButton>
            </div>
          </>
        )}
      </main>
    </MobileShell>
  );
}
