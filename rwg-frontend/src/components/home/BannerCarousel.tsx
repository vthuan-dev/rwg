"use client";

import React, { useState, useEffect, useRef, useCallback } from "react";
import Image from "next/image";
import { USER_API_BASE_URL, USER_BASE_URL } from "@/lib/constants";

interface BannerItem {
  id: string;
  title: string;
  mediaType: "VIDEO" | "IMAGE";
  mediaUrl: string;
  linkUrl?: string;
}

/**
 * BẢNG DUY NHẤT còn gán cứng: banner dự phòng khi API lỗi hoặc chưa có banner nào.
 *
 * TRƯỚC ĐÂY Ở ĐÂY CÓ BỐN SLIDE gán cứng luôn hiện trước banner từ backend. Hệ
 * quả: khu quản trị không thấy và không quản lý được bốn slide đó — danh sách banner
 * luôn hiện "0" trong khi trang chủ vẫn chạy 4 slide. Bốn banner đó giờ nằm trong DB
 * (migration V20260824_04), nên mọi banner đều đến từ MỘT nguồn: API.
 *
 * VẪN GIỮ MỘT ẢNH dự phòng: bỏ hẳn thì khi backend chết, chỗ này thành khung đen
 * trên trang chủ — trông như web hỏng hơn là thiếu quảng cáo. Dùng ảnh chứ không
 * dùng video: ảnh 165 KB tải xong gần như tức thì, còn video 4.2MB trong tình huống
 * hệ thống đang có vấn đề thì chỉ làm chậm thêm.
 */
const FALLBACK_BANNER: BannerItem = {
  id: "fallback-gateway",
  title: "Resorts World Genting",
  mediaType: "IMAGE",
  mediaUrl: "/images/banner_gateway.webp",
};

/** Banner rộng bằng khung nội dung, tối đa 640px. */
const BANNER_SIZES = "(max-width: 640px) 100vw, 640px";

/**
 * Khoảng vuốt ngang tối thiểu (px) để tính là đổi banner.
 *
 * Đặt 40px chứ không nhỏ hơn: cuộn dọc bằng ngón tay luôn kèm một chút lệch ngang
 * ngoài ý muốn, ngưỡng quá thấp sẽ làm banner tự nhảy khi người dùng chỉ đang cuộn
 * trang.
 */
const SWIPE_THRESHOLD_PX = 40;

/** Tự chuyển banner ảnh sau mỗi 5 giây. Banner video chờ hết video, xem `useEffect`. */
const AUTOPLAY_MS = 5000;

export const BannerCarousel: React.FC = () => {
  /**
   * `null` = CHƯA tải xong, `[]` = tải xong nhưng không có banner nào.
   *
   * Phân biệt hai trạng thái này để không hiện ảnh dự phòng rồi thay ngay bằng
   * banner thật sau vài trăm ms — một cú nhảy ảnh nhìn rất rẻ. Trong lúc chờ,
   * khung vẫn chiếm đúng chỗ (aspect-[16/9]) nên không có hiện tượng nhảy layout.
   */
  const [banners, setBanners] = useState<BannerItem[] | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const touchStartX = useRef<number | null>(null);
  const touchStartY = useRef<number | null>(null);

  useEffect(() => {
    const fetchBanners = async () => {
      try {
        const res = await fetch(`${USER_API_BASE_URL}/banners/active`);
        if (!res.ok) {
          setBanners([]);
          return;
        }
        const data: BannerItem[] = await res.json();
        setBanners(
          (data ?? []).map((b) => ({
            ...b,
            // Chỉ tệp do khu quản trị TẢI LÊN mới cần tiền tố domain của backend
            // (chúng nằm dưới /uploads và do backend phục vụ). Banner seed từ
            // migration trỏ vào thư mục public của frontend nên giữ nguyên đường dẫn
            // — Next.js phục vụ trực tiếp, không qua backend.
            mediaUrl: b.mediaUrl.startsWith("/uploads")
              ? `${USER_BASE_URL}${b.mediaUrl}`
              : b.mediaUrl,
          }))
        );
      } catch {
        // Backend chưa chạy hoặc mạng lỗi: đánh dấu đã tải xong với danh sách rỗng
        // để ảnh dự phòng được hiện, thay vì đứng mãi ở trạng thái đang tải.
        setBanners([]);
      }
    };
    fetchBanners();
  }, []);

  /**
   * Danh sách thực sự được hiển thị.
   *
   * Tải xong mà không có banner nào thì dùng ảnh dự phòng. Đang tải thì rỗng — phần
   * render bên dưới chỉ vẽ khung đen giữ chỗ.
   */
  const visibleBanners =
    banners === null ? [] : banners.length > 0 ? banners : [FALLBACK_BANNER];

  const activeBanner = visibleBanners[currentIndex] ?? visibleBanners[0];
  const activeIsVideo = activeBanner?.mediaType === "VIDEO";

  useEffect(() => {
    if (visibleBanners.length <= 1) return;
    // Banner video KHÔNG dùng đồng hồ: nó tự chuyển khi phát xong (`onEnded`). Video
    // mở đầu dài 5.04 giây, sát ngay trên mốc 5 giây của đồng hồ — để đồng hồ chạy
    // thì nó cắt đúng khung cuối, mà khung cuối mới là chỗ chữ hiện đầy đủ.
    if (activeIsVideo) return;
    const timer = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % visibleBanners.length);
    }, AUTOPLAY_MS);
    return () => clearInterval(timer);
    // currentIndex nằm trong deps để đồng hồ được đặt lại sau khi người dùng vuốt
    // tay — không thì banner vừa vuốt tới có thể bị đổi ngay sau đó vài trăm ms.
  }, [visibleBanners.length, currentIndex, activeIsVideo]);

  const goTo = useCallback(
    (index: number) => {
      const count = visibleBanners.length;
      if (count === 0) return;
      setCurrentIndex(((index % count) + count) % count);
    },
    [visibleBanners.length]
  );

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartX.current = e.touches[0].clientX;
    touchStartY.current = e.touches[0].clientY;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartX.current == null || touchStartY.current == null) return;
    const dx = e.changedTouches[0].clientX - touchStartX.current;
    const dy = e.changedTouches[0].clientY - touchStartY.current;
    touchStartX.current = null;
    touchStartY.current = null;

    // Bỏ qua khi thành phần dọc lớn hơn ngang: đó là người dùng đang cuộn trang,
    // không phải vuốt banner. Thiếu điều kiện này thì mỗi lần cuộn qua banner là
    // banner lại nhảy sang ảnh khác.
    if (Math.abs(dx) < SWIPE_THRESHOLD_PX || Math.abs(dx) <= Math.abs(dy)) return;
    goTo(currentIndex + (dx < 0 ? 1 : -1));
  };

  // ĐANG TẢI: vẽ khung đen ĐÚNG KÍCH THƯỚC thay vì không vẽ gì. Không vẽ thì khi
  // dữ liệu về, phần nội dung bên dưới bị đẩy xuống một đoạn — đúng lúc người
  // dùng có thể đang chạm vào một nút nào đó.
  if (banners === null) {
    return (
      <section className="w-full my-3" aria-busy="true">
        <div className="relative w-full aspect-[16/9] overflow-hidden bg-black" />
      </section>
    );
  }

  return (
    // KHÔNG có `px-4`: banner trải hết chiều rộng khung, sát hai viền như trang gốc.
    // Các mục khác của trang chủ vẫn giữ lề riêng của chúng — chỉ banner tràn viền.
    <section className="w-full my-3" aria-label={activeBanner.title}>
      <div
        className="relative w-full aspect-[16/9] overflow-hidden bg-black"
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
      >
        {activeIsVideo ? (
          <video
            // `autoPlay` chỉ được phép khi có ĐỦ `muted` và `playsInline`. Thiếu `muted`
            // thì mọi trình duyệt hiện đại chặn phát tự động; thiếu `playsInline` thì
            // Safari iOS mở video toàn màn hình đè lên cả trang.
            autoPlay
            className="w-full h-full object-cover"
            // Đặt `key` theo id: không có nó thì React dùng lại đúng thẻ <video> khi đổi
            // slide và video không phát lại từ đầu lúc quay về.
            key={activeBanner.id}
            muted
            // Chạy một lần rồi chuyển slide. Chỉ lặp lại khi đây là banner duy nhất —
            // không lặp thì khung cuối đứng im mãi.
            loop={visibleBanners.length <= 1}
            onEnded={() => {
              if (visibleBanners.length > 1) goTo(currentIndex + 1);
            }}
            playsInline
            // `auto` để video sẵn sàng ngay: đây là slide ĐẦU TIÊN, người dùng thấy nó
            // trước mọi thứ khác nên để `metadata` sẽ thành khung đen chờ tải.
            preload="auto"
            src={activeBanner.mediaUrl}
          />
        ) : (
          <Image
            src={activeBanner.mediaUrl}
            alt={activeBanner.title}
            fill
            priority
            sizes={BANNER_SIZES}
            className="object-cover"
          />
        )}

        {visibleBanners.length > 1 && (
          <div className="absolute bottom-0 left-1/2 -translate-x-1/2 flex items-center z-10">
            {visibleBanners.map((banner, idx) => (
              <button
                key={banner.id}
                type="button"
                onClick={() => goTo(idx)}
                // Vùng bấm 44px bọc ngoài, dấu chấm bên trong vẫn nhỏ như thiết kế:
                // chấm 6px thì không thể bấm chính xác bằng ngón tay.
                className="flex items-center justify-center size-11"
                aria-label={banner.title}
                aria-current={idx === currentIndex ? "true" : undefined}
              >
                <span
                  className={`h-1.5 transition-all ${
                    idx === currentIndex ? "w-5 bg-primary" : "w-1.5 bg-white/50"
                  }`}
                />
              </button>
            ))}
          </div>
        )}
      </div>
    </section>
  );
};
