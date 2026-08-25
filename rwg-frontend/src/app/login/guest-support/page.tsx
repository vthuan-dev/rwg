"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Headphones, ArrowRight, ArrowLeft } from "lucide-react";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { TextField } from "@/components/ui/TextField";
import { Button } from "@/components/ui/Button";
import { AuthModal } from "@/components/ui/AuthModal";
import { useTranslation } from "@/context/LanguageContext";
import { ApiError, guestSupport } from "@/lib/playerApi";

/**
 * Trang mở phiên hỗ trợ cho người chơi quên mật khẩu.
 *
 * BỐ CỤC ĐÁP ỨNG — các quyết định và lý do:
 *
 * 1. Thang khoảng cách theo BƯỚC MÀN HÌNH, không một con số cho mọi máy. Mốc `sm:`
 *    của Tailwind là 640px, trùng đúng `--shell-max-width` của khung (xem
 *    MobileShell), nên "dưới sm" = đang xem trên điện thoại và "từ sm" = khung đã
 *    đạt bề rộng tối đa. Bản trước dùng `py-10` cố định: trên iPhone SE (667px cao)
 *    khối nội dung bị đẩy quá thấp, còn trên máy màn hình lớn thì lại quá sát đỉnh.
 *
 * 2. Lề ngang `px-5` (20px) khớp trang đăng nhập và trang tạo tài khoản. Ba trang
 *    của khu xác thực phải cùng một lề, nếu không thì chuyển giữa chúng sẽ thấy nội
 *    dung nhảy ngang.
 *
 * 3. KHÔNG dùng `justify-between` cho khối ngoài như bản trước. Với thân trang ngắn,
 *    nó ném phần chân trang xuống đáy màn hình và để lại một khoảng trống lớn giữa
 *    form và chân trang. Thay bằng `mt-auto` trên chân trang: chân trang xuống đáy
 *    KHI CÒN CHỖ, còn khi nội dung dài (bàn phím ảo bật lên làm màn hình co lại) thì
 *    nó đi theo luồng bình thường.
 *
 * 4. Bề rộng cột nội dung giới hạn `max-w-[420px] mx-auto`. Không có nó, trên khung
 *    640px form kéo dài hết bề rộng và một ô nhập duy nhất trông lạc giữa khoảng
 *    trống.
 *
 * 5. Cỡ chữ và cỡ khối icon đều tăng một bậc từ `sm:`. Trên điện thoại ưu tiên gọn
 *    để không phải cuộn khi bàn phím bật; từ 640px thì nới ra cho cân với khung.
 */
export default function GuestSupportPage() {
  const { t } = useTranslation();
  const router = useRouter();

  const [username, setUsername] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!username.trim() || submitting) return;

    setSubmitting(true);
    setErrorMessage(null);

    try {
      await guestSupport({ username: username.trim() });
      // Đã lưu phiên làm việc của user -> Chuyển thẳng sang trang trò chuyện CSKH
      router.replace("/profile/contact-us");
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(error.message || "Không tìm thấy tên đăng nhập trên hệ thống");
      } else {
        setErrorMessage("Có lỗi xảy ra. Vui lòng kiểm tra lại tên đăng nhập.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout variant="login">
      <div className="grow flex flex-col px-5 pt-6 pb-8 sm:pt-10 sm:pb-10">
        {/* Nút quay lại: `min-h-11` (44px) là vùng bấm tối thiểu cho ngón tay.
            `-ml-2` bù phần đệm ngang để chữ vẫn thẳng lề với nội dung bên dưới —
            không có nó, nút trông như bị thụt vào so với cả cột. */}
        <Link
          href="/login"
          className="inline-flex items-center gap-1.5 -ml-2 px-2 min-h-11 self-start text-[0.8125rem] text-[#8b8b8b] transition-colors hover:text-white active:opacity-70"
        >
          <ArrowLeft aria-hidden="true" className="size-4 shrink-0" />
          <span>Quay lại Đăng nhập</span>
        </Link>

        <div className="w-full max-w-[420px] mx-auto">
          {/* Khối tiêu đề */}
          <div className="flex flex-col items-center text-center pt-4 pb-7 sm:pt-8 sm:pb-9">
            <div className="flex items-center justify-center size-14 sm:size-16 mb-4 rounded-2xl bg-amber-500/10 border border-amber-500/20 shadow-lg shadow-amber-500/5">
              <Headphones
                aria-hidden="true"
                className="size-7 sm:size-8 text-amber-400"
              />
            </div>

            {/* `<h1>` chứ không `<div>`: mỗi trang cần đúng một tiêu đề cấp một để
                trình đọc màn hình có mốc điều hướng. */}
            <h1 className="text-[1.25rem] sm:text-[1.5rem] font-bold leading-tight text-white">
              Dịch Vụ Khách Hàng 24/7
            </h1>

            {/* `text-balance` chia đều số chữ giữa các dòng, tránh dòng cuối trơ lại
                một hai từ. `max-w-[34ch]` giới hạn theo SỐ KÝ TỰ chứ không theo px:
                độ dài dòng dễ đọc phụ thuộc số ký tự, và con số này tự đúng khi cỡ
                chữ đổi giữa hai bước màn hình. */}
            <p className="mt-2.5 max-w-[34ch] text-[0.8125rem] sm:text-[0.875rem] leading-relaxed text-[#a1a1aa] text-balance">
              Nhập <strong className="font-medium text-white">Tên đăng nhập</strong>{" "}
              của bạn để mở phiên trò chuyện trực tiếp với Chuyên viên CSKH xin đặt lại
              mật khẩu.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="flex flex-col gap-y-6" noValidate>
            <TextField
              autoComplete="username"
              label="Tên đăng nhập tài khoản"
              name="username"
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Nhập tên đăng nhập của bạn..."
              value={username}
            />

            {/* `disabled` khi ô còn trống: `handleSubmit` đã chặn sẵn, nhưng nút vẫn
                sáng như bấm được thì người dùng bấm mà không có gì xảy ra và không
                hiểu vì sao. */}
            <Button
              disabled={!username.trim()}
              loading={submitting}
              type="submit"
            >
              {submitting ? (
                <span>Đang kết nối CSKH...</span>
              ) : (
                <>
                  <span>Kết nối CSKH ngay</span>
                  <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
                </>
              )}
            </Button>
          </form>
        </div>

        {/* `mt-auto` đẩy chân trang xuống đáy khi còn chỗ trống; `pt-8` giữ khoảng
            cách tối thiểu với form khi màn hình thấp (bàn phím ảo đang bật). */}
        <footer className="mt-auto pt-8 text-center text-[0.75rem] leading-normal text-[#8b8b8b]">
          © 2025 . RWG . {t("auth.all_rights_reserved")}
        </footer>
      </div>

      <AuthModal
        buttonLabel="Thử lại"
        message={errorMessage ?? ""}
        onConfirm={() => setErrorMessage(null)}
        open={errorMessage !== null}
        title="Không thể kết nối CSKH"
        variant="error"
      />
    </AuthLayout>
  );
}
