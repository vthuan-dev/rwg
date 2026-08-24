"use client";

import React, { useEffect, useRef, useState } from "react";

export interface DrawCountdownProps {
  /** Thời điểm pha hiện tại kết thúc (ISO), từ `phaseEndsAt` của API. */
  endsAt: string | null;
  /** Giờ server lúc nhận phản hồi (ISO), từ `serverTime`. */
  serverTime: string;
  /** Tổng thời lượng pha (giây), để biết vòng cung đầy là bao nhiêu. */
  totalSeconds: number;
  /** Gọi khi đồng hồ về 0, dùng để tải lại dữ liệu vòng. */
  onExpired?: () => void;
  size?: number;
  strokeWidth?: number;
}

/** Màu vòng cung và chữ số, lấy từ trang gốc. */
const ACCENT = "#72f238";

/**
 * Vòng đếm ngược của thẻ bàn chơi.
 *
 * Vẽ hai `<circle>` lồng nhau: vòng dưới màu đen là rãnh, vòng trên màu xanh chạy
 * theo `strokeDashoffset`. Cả `<svg>` quay `-90deg` để vạch bắt đầu từ đỉnh chứ
 * không phải mép phải.
 *
 * ĐỒNG BỘ THEO GIỜ SERVER, không theo đồng hồ máy. Đồng hồ máy người dùng lệch vài
 * phút là chuyện thường, và lệch chỉ 30 giây thôi là đồng hồ này đã sai hoàn toàn
 * vì cả vòng chỉ 45 giây. Cách làm: lấy hiệu giữa `serverTime` và giờ máy tại lúc
 * nhận phản hồi, rồi cộng hiệu đó vào mọi lần đọc giờ sau này.
 *
 * DỪNG KHI TAB BỊ ẨN: trang này có 6 thẻ, mỗi thẻ một bộ đếm chạy mỗi giây. Để
 * chạy tiếp khi người dùng chuyển tab là tiêu pin cho thứ không ai nhìn.
 */
export const DrawCountdown: React.FC<DrawCountdownProps> = ({
  endsAt,
  serverTime,
  totalSeconds,
  onExpired,
  size = 32,
  strokeWidth = 3,
}) => {
  const [remaining, setRemaining] = useState<number | null>(null);

  /**
   * Ref chứ không state cho `onExpired`: nếu đưa nó vào mảng phụ thuộc của effect
   * đếm thời gian thì mỗi lần component cha render lại (và tạo hàm mới) là đồng hồ
   * bị dựng lại từ đầu, làm mất một nhịp đếm.
   *
   * Cập nhật trong effect RIÊNG chứ không gán thẳng lúc render: ghi vào ref trong
   * thân hàm render là tác dụng phụ, sẽ sai khi React render thử rồi bỏ (Strict
   * Mode, hoặc render bị gián đoạn ở chế độ đồng thời).
   */
  const onExpiredRef = useRef(onExpired);
  useEffect(() => {
    onExpiredRef.current = onExpired;
  }, [onExpired]);

  /** Đã gọi `onExpired` cho mốc kết thúc này chưa — chặn gọi lặp mỗi giây. */
  const firedRef = useRef<string | null>(null);

  useEffect(() => {
    // Không có mốc kết thúc thì không dựng đồng hồ. KHÔNG gọi setState ở đây: phần
    // render tự bỏ qua `remaining` khi `endsAt` là null, nên không cần dọn state —
    // gọi setState đồng bộ trong effect sinh thêm một vòng render vô ích.
    if (!endsAt) return;

    const endMs = new Date(endsAt).getTime();
    // Độ lệch giữa đồng hồ server và đồng hồ máy, đo MỘT lần lúc dựng effect.
    const clockSkewMs = new Date(serverTime).getTime() - Date.now();

    const tick = () => {
      const nowServerMs = Date.now() + clockSkewMs;
      const leftSeconds = Math.max(0, Math.ceil((endMs - nowServerMs) / 1000));
      setRemaining(leftSeconds);

      if (leftSeconds === 0 && firedRef.current !== endsAt) {
        firedRef.current = endsAt;
        onExpiredRef.current?.();
      }
    };

    tick();
    let timer: ReturnType<typeof setInterval> | null = setInterval(tick, 1000);

    const stop = () => {
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    };

    const onVisibilityChange = () => {
      if (document.hidden) {
        stop();
        return;
      }
      // Chạy `tick()` NGAY khi quay lại: sau một lúc ẩn, con số hiện tại đã cũ, chờ
      // thêm một giây mới cập nhật sẽ thấy đồng hồ nhảy giật.
      tick();
      if (!timer) timer = setInterval(tick, 1000);
    };

    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      stop();
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [endsAt, serverTime]);

  // Bỏ qua `remaining` khi không có mốc kết thúc: giá trị còn lại từ vòng trước sẽ
  // là con số của một vòng đã xong.
  const shown = endsAt ? (remaining ?? 0) : 0;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  // Kẹp trong [0,1]: `remaining` có thể lớn hơn `totalSeconds` một nhịp ngay sau khi
  // đổi pha (dữ liệu cũ, mốc mới), không kẹp thì vòng cung vẽ tràn ra ngoài.
  const ratio = totalSeconds > 0 ? Math.min(1, Math.max(0, shown / totalSeconds)) : 0;

  return (
    <div
      aria-hidden="true"
      className="relative flex shrink-0 items-center justify-center rounded-full bg-[#1F1F1F]"
      style={{ width: size, height: size }}
    >
      <svg className="absolute top-0 left-0 -rotate-90" height={size} width={size}>
        <circle
          cx={size / 2}
          cy={size / 2}
          fill="transparent"
          r={radius}
          stroke="#000"
          strokeWidth={strokeWidth}
        />
        {endsAt ? (
          <circle
            cx={size / 2}
            cy={size / 2}
            fill="transparent"
            r={radius}
            stroke={ACCENT}
            strokeDasharray={circumference}
            strokeDashoffset={circumference - ratio * circumference}
            strokeLinecap="round"
            strokeWidth={strokeWidth}
            // `transition` inline chứ không class: giá trị đổi mỗi giây nên phải là
            // thuộc tính động, không phải một lớp tĩnh.
            style={{ transition: "stroke-dashoffset 1s linear" }}
          />
        ) : null}
      </svg>

      <p className="mb-0 text-[0.625rem] font-medium leading-normal" style={{ color: ACCENT }}>
        {shown}
      </p>
    </div>
  );
};
