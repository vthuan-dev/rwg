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
 * BỐN slide cố định của trang chủ, đúng thứ tự này: hai video rồi hai ảnh.
 *
 * Đây KHÔNG phải banner dự phòng. Trước đây hai ảnh chỉ hiện khi backend chưa có
 * dữ liệu và bị thay sạch ngay khi có — nay cả bốn luôn có mặt, banner từ khu quản
 * trị được nối thêm vào SAU.
 *
 * Hai video: 1248×704, H.264, 5.04 giây, `moov` nằm TRƯỚC `mdat` nên trình duyệt
 * phát được ngay khi tải xong phần đầu, không phải chờ hết file.
 * Hai ảnh: 1280×714 WebP, khoảng 165 KB mỗi cái.
 */
const HOME_BANNERS: BannerItem[] = [
  {
    id: "intro-video-1",
    title: "Resorts World Genting",
    mediaType: "VIDEO",
    mediaUrl: "/element/home-banner-video-wording.mp4",
  },
  {
    id: "intro-video-2",
    title: "Resorts World Genting",
    mediaType: "VIDEO",
    mediaUrl: "/element/home-banner-video-wording2.mp4",
  },
  {
    id: "banner-promo",
    title: "Tích Luỹ Phần Thưởng 2026",
    mediaType: "IMAGE",
    mediaUrl: "/images/banner_promo.webp",
  },
  {
    id: "banner-gateway",
    title: "Your Gateway To Fortune",
    mediaType: "IMAGE",
    mediaUrl: "/images/banner_gateway.webp",
  },
];

/** Tập `mediaUrl` của bốn slide trên, dùng để lọc trùng với dữ liệu backend. */
const HOME_BANNER_URLS = new Set(HOME_BANNERS.map((b) => b.mediaUrl));

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
  const [banners, setBanners] = useState<BannerItem[]>(HOME_BANNERS);
  const [currentIndex, setCurrentIndex] = useState(0);
  const touchStartX = useRef<number | null>(null);
  const touchStartY = useRef<number | null>(null);

  useEffect(() => {
    const fetchBanners = async () => {
      try {
        const res = await fetch(`${USER_API_BASE_URL}/banners/active`);
        if (res.ok) {
          const data: BannerItem[] = await res.json();
          if (data && data.length > 0) {
            const remote = data.map((b) => ({
              ...b,
              mediaUrl: b.mediaUrl.startsWith("/uploads")
                ? `${USER_BASE_URL}${b.mediaUrl}`
                : b.mediaUrl,
            }));
            // Bốn slide cố định luôn đứng trước banner từ backend. Lọc theo `mediaUrl`
            // để nếu ai đó tải đúng một trong bốn file này lên khu quản trị thì nó
            // không hiện hai lần.
            setBanners([
              ...HOME_BANNERS,
              ...remote.filter((b) => !HOME_BANNER_URLS.has(b.mediaUrl)),
            ]);
          }
        }
      } catch {
        // Giữ bốn slide cố định khi backend chưa chạy.
      }
    };
    fetchBanners();
  }, []);

  const activeBanner = banners[currentIndex] || HOME_BANNERS[0];
  const activeIsVideo = activeBanner.mediaType === "VIDEO";

  useEffect(() => {
    if (banners.length <= 1) return;
    // Banner video KHÔNG dùng đồng hồ: nó tự chuyển khi phát xong (`onEnded`). Video
    // mở đầu dài 5.04 giây, sát ngay trên mốc 5 giây của đồng hồ — để đồng hồ chạy
    // thì nó cắt đúng khung cuối, mà khung cuối mới là chỗ chữ hiện đầy đủ.
    if (activeIsVideo) return;
    const timer = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % banners.length);
    }, AUTOPLAY_MS);
    return () => clearInterval(timer);
    // currentIndex nằm trong deps để đồng hồ được đặt lại sau khi người dùng vuốt
    // tay — không thì banner vừa vuốt tới có thể bị đổi ngay sau đó vài trăm ms.
  }, [banners.length, currentIndex, activeIsVideo]);

  const goTo = useCallback(
    (index: number) => {
      const count = banners.length;
      if (count === 0) return;
      setCurrentIndex(((index % count) + count) % count);
    },
    [banners.length]
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
            loop={banners.length <= 1}
            onEnded={() => {
              if (banners.length > 1) goTo(currentIndex + 1);
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

        {banners.length > 1 && (
          <div className="absolute bottom-0 left-1/2 -translate-x-1/2 flex items-center z-10">
            {banners.map((banner, idx) => (
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
