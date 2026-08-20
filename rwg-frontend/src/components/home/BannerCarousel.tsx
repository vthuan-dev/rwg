"use client";

import React, { useState, useEffect } from "react";
import Image from "next/image";

interface BannerItem {
  id: string;
  title: string;
  mediaType: "VIDEO" | "IMAGE";
  mediaUrl: string;
  linkUrl?: string;
}

const FALLBACK_BANNERS: BannerItem[] = [
  {
    id: "1",
    title: "Tích Luỹ Phần Thưởng 2026",
    mediaType: "IMAGE",
    mediaUrl: "/images/banner_promo.jpg",
    linkUrl: "#",
  },
  {
    id: "2",
    title: "Your Gateway To Fortune",
    mediaType: "IMAGE",
    mediaUrl: "/images/banner_gateway.jpg",
    linkUrl: "#",
  },
];

export const BannerCarousel: React.FC = () => {
  const [banners, setBanners] = useState<BannerItem[]>(FALLBACK_BANNERS);
  const [currentIndex, setCurrentIndex] = useState(0);

  useEffect(() => {
    // Try fetching active banners from backend
    const fetchBanners = async () => {
      try {
        const res = await fetch("http://localhost:8080/api/v1/banners/active");
        if (res.ok) {
          const data: BannerItem[] = await res.json();
          if (data && data.length > 0) {
            setBanners(data);
          }
        }
      } catch {
        // Fallback to local images if backend API is not running
      }
    };
    fetchBanners();
  }, []);

  useEffect(() => {
    if (banners.length <= 1) return;
    const timer = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % banners.length);
    }, 5000);
    return () => clearInterval(timer);
  }, [banners.length]);

  const activeBanner = banners[currentIndex] || FALLBACK_BANNERS[0];

  return (
    <div className="w-full px-4 my-3">
      <div className="relative w-full aspect-[16/9] rounded-xl overflow-hidden shadow-2xl border border-red-900/40 group">
        {activeBanner.mediaType === "VIDEO" ? (
          <video
            src={activeBanner.mediaUrl}
            autoPlay
            loop
            muted
            playsInline
            className="w-full h-full object-cover"
          />
        ) : (
          <Image
            src={activeBanner.mediaUrl}
            alt={activeBanner.title}
            fill
            priority
            className="object-cover"
          />
        )}

        {/* Carousel Dots */}
        {banners.length > 1 && (
          <div className="absolute bottom-2 left-1/2 -translate-x-1/2 flex items-center gap-1.5 z-10">
            {banners.map((_, idx) => (
              <button
                key={idx}
                onClick={() => setCurrentIndex(idx)}
                className={`h-1.5 rounded-full transition-all ${
                  idx === currentIndex
                    ? "w-5 bg-red-500"
                    : "w-1.5 bg-white/50 hover:bg-white"
                }`}
                aria-label={`Slide ${idx + 1}`}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
