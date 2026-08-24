"use client";

import React, { useCallback, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { TextField } from "@/components/ui/TextField";
import { Button } from "@/components/ui/Button";
import { PasswordRule } from "@/components/ui/PasswordRule";
import { AuthModal } from "@/components/ui/AuthModal";
import { useTranslation } from "@/context/LanguageContext";
import { ApiError, register } from "@/lib/playerApi";

/** Điều kiện mật khẩu đăng nhập, giống bản gốc. */
const PASSWORD_PATTERN = /^.{8,}$/;
/** Mật khẩu rút tiền: đúng 6 chữ số. */
const WITHDRAWAL_PATTERN = /^\d{6}$/;

/**
 * Ràng buộc tên đăng nhập, sao lại ĐÚNG `RegisterRequest` của backend.
 *
 * Kiểm trước ở trình duyệt để người dùng biết ngay, không phải chờ một lượt mạng.
 * Backend VẪN kiểm lại — đây chỉ là tiện ích, không phải lớp bảo vệ.
 */
const USERNAME_PATTERN = /^[a-zA-Z0-9_.]+$/;
const USERNAME_MIN = 3;
const USERNAME_MAX = 32;

/**
 * Backend chặn mật khẩu theo ĐỘ DÀI BYTE UTF-8, không theo số ký tự, vì BCrypt chỉ
 * băm 72 byte đầu tiên. Một chữ tiếng Việt có dấu chiếm từ 2 đến 3 byte, nên mật
 * khẩu "nhìn thấy 40 ký tự" vẫn có thể vượt ngưỡng. Đếm đúng bằng byte để thông
 * báo khớp với thuộc tính backend sẽ trả về.
 */
const PASSWORD_MIN_BYTES = 8;
const PASSWORD_MAX_BYTES = 72;

/** Số byte UTF-8 của một chuỗi. */
function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).length;
}

export default function NewAccountPage() {
  const { t } = useTranslation();
  const router = useRouter();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [withdrawalPassword, setWithdrawalPassword] = useState("");

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [created, setCreated] = useState(false);

  /**
   * Rời hộp thoại thành công sang trang đăng nhập.
   *
   * `replace` chứ không `push`: bấm nút quay lại sau đó sẽ đưa về trang tạo tài khoản
   * với form đã trống, người dùng dễ tưởng việc tạo tài khoản thất bại.
   *
   * Cờ `navigating` chặn gọi lần hai. Ba đường thoát cùng gọi hàm này, và điều hướng
   * của Next là không đồng bộ nên hộp thoại vẫn hiện thêm một lúc — bấm Xác nhận rồi
   * bấm tiếp ra ngoài sẽ phát hai lệnh điều hướng chồng nhau.
   */
  const navigating = useRef(false);
  const goToLogin = useCallback(() => {
    if (navigating.current) return;
    navigating.current = true;
    router.replace("/login");
  }, [router]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    // Gộp TẤT CẢ lỗi rồi mới hiện, không dừng ở lỗi đầu tiên: báo từng lỗi một sẽ
    // bắt người dùng bấm gửi nhiều lần mới thấy hết.
    const errors: Record<string, string> = {};
    const trimmedUsername = username.trim();

    if (
      trimmedUsername.length < USERNAME_MIN ||
      trimmedUsername.length > USERNAME_MAX
    ) {
      errors.username = t("auth.username_length");
    } else if (!USERNAME_PATTERN.test(trimmedUsername)) {
      errors.username = t("auth.username_invalid");
    }

    const passwordBytes = utf8ByteLength(password);
    if (passwordBytes < PASSWORD_MIN_BYTES || passwordBytes > PASSWORD_MAX_BYTES) {
      errors.password = t("auth.password_length");
    }

    // Khớp hai lần nhập mật khẩu phải kiểm ở đây: backend chỉ nhận MỘT trường
    // password nên nó KHÔNG THỂ phát hiện được lỗi này.
    if (password !== confirmPassword) {
      errors.confirmPassword = t("auth.password_mismatch");
    }

    // Mật khẩu rút tiền là TÙY CHỌN: bỏ trống thì đặt sau trong phần cài đặt. Chỉ
    // kiểm khi người dùng có gõ.
    if (withdrawalPassword && !WITHDRAWAL_PATTERN.test(withdrawalPassword)) {
      errors.withdrawalPassword = t("auth.security_pin_reminder_0");
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setSubmitting(true);

    try {
      await register({
        username: trimmedUsername,
        password,
        withdrawalPassword: withdrawalPassword.trim() || undefined,
      });
      setCreated(true);
    } catch (error) {
      if (error instanceof ApiError) {
        // details là bản đồ trường -> thông báo; gắn vào từng ô để người dùng
        // thấy lỗi ngay tại chỗ cần sửa, thay vì phải tự suy từ hộp thoại.
        if (error.details) {
          setFieldErrors(error.details);
        }
        if (error.code === "NETWORK_ERROR") {
          setErrorMessage(t("auth.network_error"));
        } else {
          setErrorMessage(error.message || t("auth.unknown_error"));
        }
      } else {
        setErrorMessage(t("auth.unknown_error"));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      header={<TopNavigationBar backHref="/login" title={t("auth.new_account")} />}
    >

      <div className="px-5 py-4 pb-6 grow flex flex-col">
        <form onSubmit={handleSubmit} noValidate>
          <div className="flex flex-col gap-y-6">
            {/* KHÔNG truyền `required` ở các ô: bản gốc không hiện dấu * nào. Ràng
                buộc vẫn do backend kiểm và các dòng PasswordRule bên dưới đã nói rõ
                điều kiện cho người dùng. */}
            <TextField
              autoComplete="username"
              error={fieldErrors.username}
              label={t("auth.username")}
              name="username"
              onChange={(event) => setUsername(event.target.value)}
              placeholder={t("auth.enter_x", { title: t("auth.username") })}
              value={username}
            />

            {/* Đường kẻ và tiêu đề "Chi tiết bảo mật" đứng NGAY SAU ô tên đăng nhập:
                bản gốc chia trang thành hai phần — thông tin tài khoản ở trên, toàn
                bộ phần mật khẩu ở dưới. `-mx-5` cho đường kẻ tràn hết chiều ngang,
                `sm:mx-0` thu lại khi khung đã có lề riêng. */}
            <hr className="-mx-5 border-[#1e2327] sm:mx-0" />
            <div className="text-[1rem] text-primary font-bold leading-[1.875rem]">
              {t("auth.security_detail")}
            </div>

            <div className="flex flex-col gap-y-2">
              <TextField
                autoComplete="new-password"
                error={fieldErrors.password}
                label={t("auth.password")}
                name="password"
                onChange={(event) => setPassword(event.target.value)}
                placeholder={t("auth.enter_x", { title: t("auth.password") })}
                type="password"
                value={password}
              />
              <PasswordRule
                label={t("auth.password_reminder_0")}
                pattern={PASSWORD_PATTERN}
                value={password}
              />
            </div>

            <TextField
              autoComplete="new-password"
              error={fieldErrors.confirmPassword}
              label={t("auth.re_enter_password")}
              name="confirmPassword"
              onChange={(event) => setConfirmPassword(event.target.value)}
              // Bản gốc dùng thẳng nhãn làm placeholder ở ô này, không phải "Nhập ...".
              placeholder={t("auth.re_enter_password")}
              type="password"
              value={confirmPassword}
            />

            <div className="flex flex-col gap-y-2">
              <TextField
                autoComplete="new-password"
                error={fieldErrors.withdrawalPassword}
                // inputMode numeric để điện thoại mở bàn phím số, maxLength 6 để
                // không gõ quá được — ràng buộc "đúng 6 chữ số" thấy ngay khi gõ
                // thay vì chỉ báo sau khi bấm gửi.
                inputMode="numeric"
                label={t("auth.security_pin")}
                maxLength={6}
                name="withdrawalPassword"
                onChange={(event) =>
                  // Lọc bỏ ký tự không phải số: dán từ clipboard hoặc bàn phím
                  // máy tính vẫn gõ được chữ dù inputMode là numeric.
                  setWithdrawalPassword(event.target.value.replace(/\D/g, ""))
                }
                placeholder={t("auth.enter_x", { title: t("auth.security_pin") })}
                type="password"
                value={withdrawalPassword}
              />
              <PasswordRule
                label={t("auth.security_pin_reminder_0")}
                pattern={WITHDRAWAL_PATTERN}
                value={withdrawalPassword}
              />
            </div>

            {/* Nút nằm TRONG cùng khối `gap-y-6` với `mt-2` như bản gốc, không tách
                thành khối riêng cách `mt-9`. */}
            <div className="mt-2">
              <Button loading={submitting} type="submit">
                {submitting ? t("auth.submitting") : t("auth.create_account")}
              </Button>
            </div>
          </div>
        </form>

        <div className="mt-9 flex flex-col items-center gap-y-1 text-[0.875rem] leading-[1.1875rem]">
          <div className="flex items-center gap-x-2">
            <span className="text-[#4a5056]">{t("auth.already_have_account")}</span>
            {/* min-h-11 = 44px cho vùng bấm bằng ngón tay. */}
            <Link
              className="inline-flex items-center min-h-11 px-1 -mx-1 text-primary font-bold"
              href="/login"
            >
              {t("auth.login")}
            </Link>
          </div>
        </div>
      </div>

      {/* Cả HAI đường thoát (Xác nhận, bấm ra ngoài) đều dẫn sang trang đăng nhập.
          Tài khoản đã tạo xong nên form phía sau không còn việc gì để làm — đóng
          hộp thoại mà giữ người dùng ở lại đó chỉ tạo cảm giác bế tắc. */}
      <AuthModal
        buttonLabel={t("auth.confirm")}
        message={t("auth.account_created_proceed_to_login")}
        onConfirm={goToLogin}
        onOverlayClick={goToLogin}
        open={created}
        title={t("auth.x_successful", { title: t("auth.account_creation") })}
        variant="success"
      />

      {/* Hộp thoại lỗi CỐ Ý không truyền `onOverlayClick`: một cú bấm lỡ tay sẽ làm
          mất thông báo lỗi trước khi người dùng đọc xong, và họ không có cách nào
          xem lại. */}
      <AuthModal
        buttonLabel={t("auth.try_again")}
        message={errorMessage ?? ""}
        onConfirm={() => setErrorMessage(null)}
        open={errorMessage !== null}
        title={t("auth.x_failed", { title: t("auth.account_creation") })}
        variant="error"
      />
    </AuthLayout>
  );
}
