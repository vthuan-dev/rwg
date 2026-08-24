"use client";

import React, { useEffect, useRef, useState } from "react";

export interface MatchTimerProps {
  /** Thời điểm kết thúc (ISO). Null thì hiện `--:--`. */
  endsAt: string | null;
  /** Giờ server lúc nhận phản hồi (ISO), để bù lệch đồng hồ máy. */
  serverTime: string;
  /** Gọi một lần khi đồng hồ về 0. */
  onExpired?: () => void;
  className?: string;
}

/**
 * Đồng hồ `mm:ss` của trang đặt cược.
 *
 * Đồng bộ theo GIỜ SERVER: lấy hiệu giữa `serverTime` và giờ máy lúc dựng, rồi bù vào
 * mọi lần đọc sau. Đồng hồ máy người dùng lệch vài phút là chuyện thường, và ở đây
 * lệch đồng nghĩa với người chơi tưởng còn thời gian đặt cược trong khi đã hết.
 *
 * Cộng thêm 500ms như bản gốc: server đóng cửa cược ở đúng mốc, nhưng gói tin đi mất
 * thời gian. Nửa giây đệm khiến đồng hồ về 0 hơi sớm thay vì hơi muộn — thà người chơi
 * thấy hết giờ trước một nhịp còn hơn bấm đặt cược rồi bị server từ chối.
 *
 * Dừng khi tab bị ẩn để không đốt pin cho thứ không ai nhìn.
 */
export const MatchTimer: React.FC<MatchTimerProps> = ({
  endsAt,
  serverTime,
  onExpired,
  className = "",
}) => {
  const [text, setText] = useState<string | null>(null);

  // Ref cập nhật trong effect riêng: gán thẳng lúc render là tác dụng phụ, sẽ sai khi
  // React render thử rồi bỏ.
  const onExpiredRef = useRef(onExpired);
  useEffect(() => {
    onExpiredRef.current = onExpired;
  }, [onExpired]);

  /** Đã gọi `onExpired` cho mốc này chưa — chặn gọi lặp mỗi giây. */
  const firedRef = useRef<string | null>(null);

  useEffect(() => {
    if (!endsAt) return;

    const endMs = new Date(endsAt).getTime();
    const clockSkewMs = new Date(serverTime).getTime() - Date.now();

    const tick = () => {
      const leftMs = endMs + 500 - (Date.now() + clockSkewMs);

      if (leftMs <= 0) {
        setText("00:00");
        if (firedRef.current !== endsAt) {
          firedRef.current = endsAt;
          onExpiredRef.current?.();
        }
        return;
      }

      const totalSeconds = Math.floor(leftMs / 1000);
      // Phút KHÔNG chia lấy dư 60 như bản gốc: một vòng 63 giây thì không bao giờ quá
      // 60 phút, nhưng nếu cấu hình đổi thành vòng dài hơn thì `% 60` sẽ hiện sai.
      const minutes = Math.floor(totalSeconds / 60);
      const seconds = totalSeconds % 60;
      setText(`${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`);
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
      tick();
      if (!timer) timer = setInterval(tick, 1000);
    };

    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      stop();
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [endsAt, serverTime]);

  return (
    <span className={className} role="timer">
      {endsAt ? (text ?? "--:--") : "--:--"}
    </span>
  );
};
