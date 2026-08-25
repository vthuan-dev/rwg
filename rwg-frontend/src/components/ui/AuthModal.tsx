"use client";

import React, { useEffect, useRef } from "react";
import Image from "next/image";

/**
 * Hộp thoại kết quả cho khu xác thực, sao lại đúng hộp thoại của trang gốc.
 *
 * Trang gốc báo cả thành công và thất bại bằng hộp thoại CHẶN (phải bấm xác nhận
 * mới đi tiếp) chứ không dùng thông báo tự tắt. Giữ đúng cách này vì luồng đăng ký
 * cần người dùng đọc được thông báo trước khi bị chuyển sang trang đăng nhập —
 * thông báo tự tắt sau 3 giây rất dễ bị bỏ lỡ ngay lúc trang đang đổi.
 *
 * Bố cục lấy từ DOM thật của trang gốc:
 *   khung   px-7 pt-18 pb-5, bg-[#1F1F1F], KHÔNG bo góc
 *   ảnh     absolute -top-[70px] w-[140px]  (nhô hẳn LÊN TRÊN khỏi khung)
 *   tiêu đề text-[1.5rem] font-semibold text-[#D0D5DA]
 *   mô tả   mt-2 text-[1rem] leading-[1.35rem] text-white/50
 *   nút     mt-6, biến thể `md` (w-full h-11 rounded-none)
 *
 * `pt-18` (4.5rem) chính là chỗ chừa cho phần ảnh nhô lên: ảnh nằm ngoài luồng nên
 * không tự đẩy nội dung xuống, thiếu padding này là ảnh đè lên tiêu đề.
 *
 * CĂN GIỮA
 * --------
 * Căn bằng FLEX, KHÔNG dùng `left-1/2` + `translate(-50%)`. Lý do: `left: 50%` tính
 * theo khối chứa gần nhất, mà hộp thoại nằm sâu trong cây DOM của trang nên khối đó
 * không phải khung 640px — kết quả là hộp thoại lệch hẳn sang một bên so với cột nội
 * dung. Cách này còn tránh luôn việc `transform` bị hiệu ứng ghi đè.
 *
 * Lớp phủ được giới hạn `sm:max-w-[640px] mx-auto` GIỐNG HỆT khung của MobileShell,
 * nên tâm hộp thoại trùng tâm cột ở mọi bề rộng màn hình. Nếu sửa `--shell-max-width`
 * hoặc con số 640px ở MobileShell thì phải sửa cả ở đây.
 */
export const AuthModal: React.FC<{
  open: boolean;
  title: string;
  message: string;
  buttonLabel: string;
  onConfirm: () => void;
  /**
   * `success` dùng ảnh dấu tích, `error` dùng ảnh dấu X — đúng hai ảnh trang gốc
   * dùng (`/element/icon-success.png` và `/element/icon-fail.png`).
   */
  variant?: "success" | "error";
  /**
   * Bấm ra vùng tối ngoài hộp thoại. Bỏ trống thì bấm ra ngoài KHÔNG làm gì.
   *
   * Cứ để trống ở hộp thoại lỗi: người dùng chỉ cần trượt tay là mất thông báo
   * lỗi trước khi đọc xong.
   */
  onOverlayClick?: () => void;
}> = ({
  open,
  title,
  message,
  buttonLabel,
  onConfirm,
  variant = "success",
  onOverlayClick,
}) => {
  const confirmRef = useRef<HTMLButtonElement>(null);

  // Đưa focus vào nút xác nhận khi hộp thoại mở: người dùng bàn phím đang ở giữa
  // form, nếu không chuyển focus thì Tab tiếp theo vẫn đi trong form bị che phía
  // sau và họ không chạm được vào hộp thoại.
  useEffect(() => {
    if (open) confirmRef.current?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      // Escape ưu tiên hành động "đóng" nếu có, ngược lại coi như xác nhận — hộp
      // thoại không còn đường thoát nào khác thì phải làm gì đó.
      if (event.key === "Escape") (onOverlayClick ?? onConfirm)();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, onConfirm, onOverlayClick]);

  if (!open) return null;

  const icon =
    variant === "success"
      ? { src: "/element/icon-success.png", width: 800, height: 812 }
      : { src: "/element/icon-fail.png", width: 800, height: 800 };

  // Chỗ gọi có thể cố tình bỏ trống phần mô tả khi tiêu đề đã nói đủ (ví dụ hộp thoại
  // đăng nhập thất bại). Khi đó KHÔNG render thẻ <p>: nó mang `mt-2` nên dù rỗng vẫn
  // chừa ra một khoảng trống giữa tiêu đề và nút, trông như thiếu chữ chứ không như
  // một hộp thoại gọn.
  const hasMessage = message.trim().length > 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Lớp phủ trải HẾT màn hình (kể cả hai bên ngoài cột 640px): nếu chỉ tối phần
          trong cột thì trên màn hình rộng, hai dải bên cạnh vẫn sáng như thường và
          hộp thoại trông như đang nổi trên một trang chưa bị chặn.

          Lớp phủ là phần tử EM của khung ngoài, KHÔNG phải cha của hộp thoại, nên
          cú bấm bên trong hộp thoại không thể nổi bọt xuống đây — không cần
          `stopPropagation`, và cũng không có nguy cơ bấm vào nút bên trong lại đóng
          luôn hộp thoại. */}
      <div
        aria-hidden="true"
        className={`absolute inset-0 bg-black/50 animate-auth-backdrop-in ${
          onOverlayClick ? "cursor-pointer" : ""
        }`}
        onClick={onOverlayClick}
      />

      {/* Dải giới hạn đúng bề rộng khung của MobileShell. `px-4` để trên điện thoại
          hẹp hộp thoại không dính sát mép — khớp `max-w-[calc(100%-2rem)]` của bản gốc.
          `pointer-events-none` để hai khoảng trống hai bên không nuốt cú bấm dành cho
          lớp phủ; riêng hộp thoại bật lại. */}
      <div className="relative w-full sm:max-w-[640px] mx-auto px-4 flex justify-center pointer-events-none">
        <div
          aria-describedby={hasMessage ? "auth-modal-description" : undefined}
          aria-labelledby="auth-modal-title"
          aria-modal="true"
          className="relative w-full sm:max-w-lg bg-[#1F1F1F] rounded-none shadow-lg outline-none pointer-events-auto animate-auth-panel-in"
          // `alertdialog` chứ không `dialog`: đây là thông báo kết quả cần người dùng
          // xác nhận, trình đọc màn hình sẽ đọc ngay thay vì chờ được điều hướng tới.
          role="alertdialog"
        >
          <div className="px-7 pt-18 pb-5 flex flex-col items-center">
            <Image
              alt=""
              // Ảnh thuần trang trí: nội dung đã nằm ở tiêu đề, đọc thêm "biểu tượng
              // thành công" chỉ làm trình đọc màn hình nhắc hai lần cùng một ý.
              aria-hidden="true"
              className="absolute -top-[70px] left-1/2 -translate-x-1/2 w-[140px] h-auto animate-auth-icon-in"
              height={icon.height}
              // Ảnh nằm trong hộp thoại vừa mở nên phải có ngay, không lười tải.
              priority
              src={icon.src}
              width={icon.width}
            />

            {/* Tiêu đề, mô tả và nút trôi lên lần lượt sau khi khung đã hiện. */}
            <h2
              className="text-center text-[1.5rem] font-semibold leading-normal text-[#D0D5DA] animate-auth-content-in"
              id="auth-modal-title"
            >
              {title}
            </h2>

            {/* Backend có thể trả nhiều lỗi validation ghép bằng ký tự xuống dòng.
                whitespace-pre-line giữ được các dòng đó mà KHÔNG cần dựng HTML từ
                chuỗi của server — dựng HTML ở đây là mở đường cho chèn mã. */}
            {hasMessage ? (
              <p
                className="mt-2 text-center text-[1rem] font-normal leading-[1.35rem] text-white/50 whitespace-pre-line animate-auth-content-in [animation-delay:60ms]"
                id="auth-modal-description"
              >
                {message}
              </p>
            ) : null}

            {/* Không dùng component Button: nút này giống biến thể `md` nhưng KHÔNG
                có hiệu ứng sáng lên khi trỏ vào — trang gốc chỉ đổi độ mờ. */}
            <button
              className="mt-6 w-full h-11 inline-flex justify-center items-center rounded-none bg-primary text-[1rem] text-white font-bold leading-normal shadow-lg transition-colors hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white animate-auth-content-in [animation-delay:120ms]"
              onClick={onConfirm}
              ref={confirmRef}
              type="button"
            >
              {buttonLabel}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
