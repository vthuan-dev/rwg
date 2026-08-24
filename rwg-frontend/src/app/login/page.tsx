"use client";

import React, { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { TextField } from "@/components/ui/TextField";
import { Button } from "@/components/ui/Button";
import { AuthModal } from "@/components/ui/AuthModal";
import { LanguagePopover } from "@/components/ui/LanguagePopover";
import { useTranslation } from "@/context/LanguageContext";
import { ApiError, login, me } from "@/lib/playerApi";

/**
 * Nạp sớm ảnh nền của trang.
 *
 * Ảnh khai báo bằng `background-image` trong CSS bị trình duyệt phát hiện RẤT MUỘN:
 * nó phải tải xong file CSS, phân tích, dựng cây kiểu, rồi mới biết là cần ảnh này.
 * Trong suốt khoảng đó ảnh chưa hề được yêu cầu. Thẻ preload đặt yêu cầu ngay lúc
 * trình duyệt đọc HTML, nên ảnh tải song song với CSS và JS.
 *
 * `fetchPriority="high"` vì đây là phần hình ảnh lớn nhất của màn hình đầu, tức là
 * thứ quyết định mốc LCP của trang.
 *
 * Render thẻ <link> trực tiếp trong cây chứ KHÔNG dùng `ReactDOM.preload()`: hàm đó
 * không sinh ra thẻ nào trong HTML trả về từ server (đã kiểm chứng bằng cách đọc
 * HTML), nên ảnh vẫn chỉ được yêu cầu sau khi JS chạy — đúng cái mà preload cần
 * tránh. React 19 tự nâng thẻ <link> lên <head>, đặt ở đâu trong cây cũng được.
 */
const BackgroundPreload: React.FC = () => (
  <link
    rel="preload"
    as="image"
    href="/element/ss2.webp"
    fetchPriority="high"
  />
);

export default function LoginPage() {
  const { t } = useTranslation();
  const router = useRouter();

  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [captcha, setCaptcha] = useState("");
  /**
   * Ô mã xác minh mặc định ẨN để khớp trang gốc, chỉ hiện khi server thực sự yêu
   * cầu (mã CAPTCHA_REQUIRED sau nhiều lần sai). Hiện sẵn từ đầu sẽ thêm một ô mà
   * gần như mọi người dùng không cần.
   */
  const [captchaNeeded, setCaptchaNeeded] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  /**
   * Lỗi theo từng ô, lấy từ `details` của phản hồi 400.
   *
   * Backend trả về bản đồ trường → thông báo (ví dụ `identifier`, `password`) nhưng
   * trước đây trang chỉ hiện hộp thoại chung, người dùng không biết sai ở ô nào.
   */
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  /**
   * Đã đăng nhập thì không có lý do ở lại trang này.
   *
   * Dùng `replace` chứ không `push`: nếu đẩy vào lịch sử thì bấm nút quay lại sẽ về
   * đúng trang đăng nhập này rồi lại bị chuyển tiếp — người dùng mắt kẹt.
   */
  useEffect(() => {
    let cancelled = false;
    me()
      .then(() => {
        if (!cancelled) router.replace("/");
      })
      .catch(() => {
        // Chưa đăng nhập hoặc token hết hạn — đúng lúc cần ở trang này, không làm gì.
      });
    return () => {
      cancelled = true;
    };
  }, [router]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    setFieldErrors({});
    setSubmitting(true);
    try {
      await login({
        // Backend nhận trường `identifier` (tên đăng nhập HOẶC email), không phải
        // `username` — gửi sai tên trường sẽ nhận 400 VALIDATION_ERROR.
        identifier: identifier.trim(),
        password,
        captchaToken: captchaNeeded ? captcha.trim() : undefined,
      });
      router.replace("/");
    } catch (error) {
      if (error instanceof ApiError) {
        // Gắn lỗi vào đúng ô cần sửa trước, rồi mới tính đến hộp thoại.
        if (error.details) {
          setFieldErrors(error.details);
        }

        if (error.captchaRequired) {
          setCaptchaNeeded(true);
          setErrorMessage(t("auth.captcha_required"));
        } else if (error.accountLocked) {
          // 423: bị khóa tạm thời sau nhiều lần sai. PHẢI nói rõ là bị khóa — nếu chỉ
          // báo "sai thông tin" thì người dùng sẽ gõ lại mãi mà không hiểu vì sao
          // mật khẩu đúng vẫn không vào được.
          setErrorMessage(error.message || t("auth.account_locked"));
        } else if (error.code === "NETWORK_ERROR") {
          setErrorMessage(t("auth.network_error"));
        } else {
          // Thông báo của server đã được bản địa hoá theo locale của request nên
          // dùng trực tiếp; chỉ khi thiếu mới dùng câu chung.
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
    <AuthLayout variant="login">
      <BackgroundPreload />
      <div className="px-5 grow flex flex-col">
        {/* Khối logo + lời chào + chọn ngôn ngữ: bản gốc gộp cả BA thứ vào một khối
            `mt-20 gap-y-8`, nên bộ chọn ngôn ngữ nằm NGAY DƯỚI lời chào chứ không
            phải ở cuối trang. */}
        <div className="mx-auto mt-20 flex flex-col gap-y-8">
          <Image
            alt="Resorts World Genting"
            className="mx-auto w-[260px] sm:w-[300px] h-auto"
            height={185}
            priority
            src="/logo/logo1.png"
            width={800}
          />
          {/* Bản gốc dùng <div>; ở đây dùng <h1> để trang có đúng một tiêu đề cấp
              một cho SEO và trình đọc màn hình. Kiểu chữ giữ y nguyên. */}
          <h1 className="text-center text-[1.25rem] text-white font-medium">
            {t("auth.hi_welcome_back")}
          </h1>
          <LanguagePopover />
        </div>

        <form className="mt-10" onSubmit={handleSubmit} noValidate>
          <div className="flex flex-col gap-y-6">
            {/* KHÔNG truyền `required`: bản gốc không hiện dấu * ở hai ô này. Việc
                kiểm tra bắt buộc vẫn do backend làm, và form đã có noValidate. */}
            <TextField
              autoComplete="username"
              error={fieldErrors.identifier}
              label={t("auth.username")}
              name="identifier"
              onChange={(event) => setIdentifier(event.target.value)}
              placeholder={t("auth.enter_x", { title: t("auth.username") })}
              value={identifier}
            />
            <TextField
              autoComplete="current-password"
              error={fieldErrors.password}
              label={t("auth.password")}
              name="password"
              onChange={(event) => setPassword(event.target.value)}
              placeholder={t("auth.enter_x", { title: t("auth.password") })}
              type="password"
              value={password}
            />
            {captchaNeeded ? (
              <TextField
                autoComplete="off"
                error={fieldErrors.captchaToken}
                label={t("auth.verification_code")}
                name="captchaToken"
                onChange={(event) => setCaptcha(event.target.value)}
                placeholder={t("auth.enter_x", {
                  title: t("auth.verification_code"),
                })}
                value={captcha}
              />
            ) : null}

            {/* Nút và khối liên kết nằm TRONG form với `mt-2`, không phải hai khối
                riêng cách nhau `mt-9` — đó là lý do bản của tôi trước đây thưa hơn
                bản gốc rõ rệt. */}
            <div className="mt-2">
              <Button loading={submitting} type="submit">
                {submitting ? t("auth.submitting") : t("auth.login")}
              </Button>
            </div>

            {/* Xếp DỌC: bản gốc để câu hỏi một dòng, liên kết dòng dưới. */}
            <div className="mt-2 flex flex-col items-center gap-y-2.5">
              <div className="text-[0.875rem] text-[#8b8b8b]">
                {t("auth.dont_have_account")}
              </div>
              <Link
                // min-h-11 = 44px cho vùng bấm bằng ngón tay; -my-3 triệt tiêu phần
                // chiều cao thêm ra để khoảng cách nhìn thấy vẫn đúng như bản gốc.
                className="inline-flex items-center min-h-11 -my-3 px-2 text-[0.875rem] text-primary font-bold leading-[1.125rem]"
                href="/new-account"
              >
                {t("auth.new_account")}
              </Link>
            </div>
          </div>
        </form>

        <footer className="mt-auto mb-5 text-center text-[0.75rem] text-[#8b8b8b] leading-normal">
          © 2025 . RWG . {t("auth.all_rights_reserved")}
        </footer>
      </div>

      <AuthModal
        buttonLabel={t("auth.try_again")}
        message={errorMessage ?? ""}
        onConfirm={() => setErrorMessage(null)}
        open={errorMessage !== null}
        title={t("auth.x_failed", { title: t("auth.login") })}
        variant="error"
      />
    </AuthLayout>
  );
}
