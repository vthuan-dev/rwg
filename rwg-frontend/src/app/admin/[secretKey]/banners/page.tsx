"use client";

import React, { useState, useEffect, useCallback } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  Image as ImageIcon,
  Video,
  Upload,
  CheckCircle2,
  XCircle,
  Trash2,
  RefreshCw,
  Plus,
  ExternalLink,
  MessageSquare,
  Star,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { USER_BASE_URL } from "@/lib/constants";
import { AdminErrorState, AdminEmptyState } from "@/components/admin/AdminStates";
import { AdminModal } from "@/components/admin/AdminModal";
import { useTranslation } from "@/context/LanguageContext";
import { ChatPromoTextEditor } from "@/components/admin/ChatPromoTextEditor";

/**
 * Khu hiển thị của một banner, khớp enum {@code BannerPlacement} ở backend.
 *
 * Hai khu dùng chung bảng `banners` nhưng hiện ở hai nơi hoàn toàn khác nhau, nên trang
 * này chia thành hai tab tách biệt — xem chú thích `PLACEMENTS` bên dưới.
 */
type Placement = "HOME_CAROUSEL" | "CHAT_PROMO";

interface BannerItem {
  id: string;
  title: string;
  placement: Placement;
  mediaType: "VIDEO" | "IMAGE";
  mediaUrl: string;
  linkUrl?: string;
  isActive: boolean;
  sortOrder: number;
  createdAt: string;
}

/** Một trang ảnh quảng cáo, khớp `PageResponse` của backend. */
interface BannerPage {
  content: BannerItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/**
 * Giới hạn đọc từ server, khớp {@code BannerLimitsResponse}.
 *
 * KHÔNG gán cứng số 4 và các mức dung lượng ở đây: đổi cấu hình ở backend mà
 * frontend vẫn tắt nút theo con số cũ là hai nguồn sự thật cho cùng một giới hạn.
 */
interface BannerLimits {
  maxCount: number;
  currentCount: number;
  maxImageBytes: number;
  maxVideoBytes: number;
}

/**
 * Cấu hình hai khu.
 *
 * VÌ SAO CHIA TAB thay vì một danh sách phẳng có nhãn phân loại: nhãn thì phải đọc mới
 * thấy, còn tab thì buộc người vận hành chọn khu TRƯỚC khi bấm tải lên. Trước đây mọi
 * ảnh nằm chung một danh sách, nên tải ảnh bảng thưởng lên là nó xuất hiện luôn trên
 * trang chủ — không có gì ngăn.
 *
 * Mỗi tab có mô tả nói rõ ảnh sẽ hiện Ở ĐÂU, vì đó là thứ ngăn nhầm lẫn hiệu quả nhất.
 */
const PLACEMENTS = [
  {
    key: "HOME_CAROUSEL" as const,
    icon: ImageIcon,
    /** Khu trang chủ nhận CẢ video. */
    accept: "video/mp4,video/webm,image/png,image/jpeg,image/webp",
  },
  {
    key: "CHAT_PROMO" as const,
    icon: MessageSquare,
    /**
     * CHỈ ẢNH: bong bóng chat vẽ bằng thẻ `<img>` nên không phát được video. Backend
     * cũng từ chối — đây chỉ là lớp tiện lợi, `accept` là gợi ý chứ không phải rào chắn.
     */
    accept: "image/png,image/jpeg,image/webp",
  },
];

/** Dung lượng dạng "4.2MB" để hiện trong thông báo lỗi. */
const formatBytes = (bytes: number): string => {
  const mb = bytes / (1024 * 1024);
  // Một chữ số thập phân cho tệp nhỏ hơn 10MB (4.2MB dễ đọc hơn 4MB), làm tròn
  // với tệp lớn hơn (50MB đủ rõ, 50.0MB chỉ thêm nhiễu).
  return mb < 10 ? `${mb.toFixed(1)}MB` : `${Math.round(mb)}MB`;
};

export default function AdminBannersPage() {
  const { t } = useTranslation();

  /** Tab đang mở. Quyết định mọi request trên trang này. */
  const [placement, setPlacement] = useState<Placement>("HOME_CAROUSEL");

  const [banners, setBanners] = useState<BannerItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  /**
   * Giới hạn từ server. `null` khi chưa tải xong.
   *
   * Chưa biết giới hạn thì KHÔNG tắt nút: tắt sẵn rồi mới biết là còn chỗ sẽ
   * làm người vận hành tưởng hệ thống hỏng. Server vẫn chặn đúng nếu thật đã đủ.
   */
  const [limits, setLimits] = useState<BannerLimits | null>(null);

  // Upload Modal
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [sortOrder, setSortOrder] = useState("0");
  const [uploadLoading, setUploadLoading] = useState(false);
  const [uploadError, setUploadError] = useState("");

  const activeConfig = PLACEMENTS.find((p) => p.key === placement) ?? PLACEMENTS[0];

  /**
   * Lấy một trang ảnh của khu đang mở.
   *
   * Trả dữ liệu về thay vì tự đặt state: `setState` gọi đồng bộ trong thân effect gây
   * chuỗi render liên tiếp và luật lint của dự án chặn.
   */
  const fetchBanners = useCallback(async (): Promise<BannerPage | null> => {
    try {
      // Endpoint tra ve PageResponse {content, totalPages, ...}, KHONG phai mang.
      const data = await adminFetch<BannerPage>(
        `/admin/banners?placement=${placement}&page=${page}&size=10`
      );
      setLoadError("");
      return data;
    } catch (err) {
      setLoadError((err as Error).message);
      return null;
    }
  }, [page, placement]);

  /** Đọc giới hạn từ server. Lỗi thì bỏ qua: giới hạn thật vẫn được server áp. */
  const fetchLimits = useCallback(async (): Promise<BannerLimits | null> => {
    try {
      return await adminFetch<BannerLimits>(`/admin/banners/limits?placement=${placement}`);
    } catch {
      return null;
    }
  }, [placement]);

  /** Đưa kết quả vào state. Dùng chung cho lần tải đầu và các lần tải lại. */
  const applyResult = useCallback((data: BannerPage | null) => {
    setBanners(data?.content ?? []);
    setTotalPages(data?.totalPages || 1);
  }, []);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể đổi tab hoặc đổi trang trước khi request xong, và
    // ghi state vào component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      // Đặt state bên trong hàm async, không ở thân effect — xem lý do ở trên.
      setLoading(true);
      // Song song: hai request độc lập, chạy nối tiếp chỉ làm chậm gấp đôi.
      const [data, lim] = await Promise.all([fetchBanners(), fetchLimits()]);
      if (cancelled) return;
      applyResult(data);
      // ĐẶT null KHI THẤT BẠI, không giữ giá trị cũ: giữ lại thì sau khi đổi tab, nhãn
      // số lượng sẽ hiện trần của khu TRƯỚC đó — con số sai mà trông hoàn toàn hợp lý.
      setLimits(lim);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchBanners, fetchLimits, applyResult]);

  /** Tải lại sau khi ghi. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    const [data, lim] = await Promise.all([fetchBanners(), fetchLimits()]);
    applyResult(data);
    // TẢI LẠI CẢ GIỚI HẠN, không chỉ danh sách: sau khi xoá hoặc thêm một banner thì
    // `currentCount` đã khác, mà nó quyết định nút tải lên bật hay tắt.
    setLimits(lim);
    setLoading(false);
  }, [fetchBanners, fetchLimits, applyResult]);

  /** Đổi tab. Đưa trang về 0 vì số trang của hai khu khác nhau. */
  const switchPlacement = (next: Placement) => {
    if (next === placement) return;
    setPlacement(next);
    setPage(0);
    // Xoá danh sách cũ ngay: giữ lại thì trong lúc chờ request, người vận hành thấy ảnh
    // của khu cũ dưới tiêu đề khu mới — đúng thứ nhầm lẫn mà việc chia tab muốn tránh.
    setBanners([]);
    setLimits(null);
    setLoadError("");
  };

  /** Đã đủ trần chưa. Chưa biết giới hạn thì coi như còn chỗ — xem chú thích `limits`. */
  const atMaxCount = limits !== null && limits.currentCount >= limits.maxCount;

  /**
   * Ảnh khuyến mãi chat mà khách ĐANG THẤY.
   *
   * Khung chat chỉ gửi một ảnh — bản ACTIVE đầu tiên theo thứ tự. Đánh dấu tường minh
   * thay vì để người vận hành tự suy từ cột thứ tự: suy sai thì họ đổi ảnh mà khách vẫn
   * thấy ảnh cũ, và không hiểu vì sao.
   *
   * Logic chọn ở đây PHẢI khớp `BannerService.chatPromo()`. Danh sách đã được server sắp
   * theo `sortOrder ASC, createdAt DESC` nên chỉ cần lấy phần tử ACTIVE đầu tiên.
   */
  const liveChatPromoId =
    placement === "CHAT_PROMO" && page === 0
      ? banners.find((b) => b.isActive)?.id
      : undefined;

  const handleToggleStatus = async (banner: BannerItem) => {
    try {
      await adminFetch(`/admin/banners/${banner.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ active: !banner.isActive }),
      });
      void reload();
    } catch (err) {
      alert((err as Error).message || t("banners.err_state_failed"));
    }
  };

  const handleDeleteBanner = async (id: string) => {
    if (!confirm(t("banners.confirm_delete"))) return;
    try {
      await adminFetch(`/admin/banners/${id}`, {
        method: "DELETE",
      });
      void reload();
    } catch (err) {
      alert((err as Error).message || t("banners.err_delete_failed"));
    }
  };

  const handleUploadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setUploadError(t("banners.err_media_required"));
      return;
    }

    // KIỂM DUNG LƯỢNG NGAY TẠI TRÌNH DUYỆT trước khi gửi. Server vẫn kiểm lại (đây
    // chỉ là tiện lợi, không phải bảo vệ), nhưng tải lên 60MB rồi mới nhận lỗi là
    // chờ vô ích trên mạng chậm.
    if (limits) {
      const isVideo = file.type.startsWith("video/");
      const maxBytes = isVideo ? limits.maxVideoBytes : limits.maxImageBytes;
      if (file.size > maxBytes) {
        setUploadError(
          t("banners.err_too_large", {
            size: formatBytes(file.size),
            max: formatBytes(maxBytes),
          })
        );
        return;
      }

      // Chặn video ở khu chat NGAY TẠI ĐÂY, không để server báo: người vận hành đã chờ
      // tải xong cả tệp mới biết mình chọn sai loại.
      if (placement === "CHAT_PROMO" && isVideo) {
        setUploadError(t("banners.err_chat_promo_image_only"));
        return;
      }
    }

    setUploadLoading(true);
    setUploadError("");

    try {
      const formData = new FormData();
      formData.append("file", file);
      // KHÔNG gửi `title`: backend suy tiêu đề từ tên tệp. Biểu mẫu chỉ còn tệp +
      // thứ tự hiển thị theo yêu cầu vận hành.
      formData.append("sortOrder", sortOrder);

      // `placement` đi trong QUERY, không phải form-data: backend khai nó là
      // @RequestParam kiểu enum, và Spring không ràng buộc được enum từ phần multipart.
      await adminFetch(`/admin/banners/upload?placement=${placement}`, {
        method: "POST",
        body: formData,
      });

      setShowUploadModal(false);
      setFile(null);
      setSortOrder("0");
      void reload();
    } catch (err) {
      setUploadError((err as Error).message || t("banners.err_upload_failed"));
    } finally {
      setUploadLoading(false);
    }
  };

  const openUploadModal = () => {
    // Xoá lỗi của lần trước: mở lại hộp thoại mà vẫn thấy lỗi cũ khiến người dùng tưởng
    // lần này cũng vừa thất bại.
    setUploadError("");
    setFile(null);
    setShowUploadModal(true);
  };

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader title={t("banners.title")} subtitle={t("banners.subtitle")} />

      <div className="p-6 flex flex-col gap-6">
        {/* ===== Chọn khu ===== */}
        <div
          className="flex items-stretch gap-3 flex-wrap"
          role="tablist"
          aria-label={t("banners.placement_tabs_label")}
        >
          {PLACEMENTS.map((cfg) => {
            const Icon = cfg.icon;
            const selected = cfg.key === placement;
            return (
              <button
                key={cfg.key}
                id={`banner-tab-${cfg.key.toLowerCase()}`}
                type="button"
                role="tab"
                aria-selected={selected}
                onClick={() => switchPlacement(cfg.key)}
                className={`flex-1 min-w-[15rem] text-left px-4 py-3 rounded-2xl border transition-all ${
                  selected
                    ? "bg-white border-red-300 ring-2 ring-red-100 shadow-sm"
                    : "bg-white/60 border-slate-200 hover:border-slate-300"
                }`}
              >
                <span className="flex items-center gap-2">
                  <Icon className={`w-4 h-4 ${selected ? "text-red-600" : "text-slate-400"}`} />
                  <span
                    className={`text-sm font-extrabold ${
                      selected ? "text-slate-900" : "text-slate-600"
                    }`}
                  >
                    {t(`banners.placement.${cfg.key}.name`)}
                  </span>
                </span>
                {/* MÔ TẢ NÓI RÕ ẢNH HIỆN Ở ĐÂU — đây là thứ ngăn nhầm lẫn, không phải cái tên. */}
                <span className="block mt-1 text-[11px] leading-relaxed text-slate-500 font-medium">
                  {t(`banners.placement.${cfg.key}.hint`)}
                </span>
              </button>
            );
          })}
        </div>

        {/* Ô SOẠN LỜI CHÀO — chỉ hiện ở khu chat.

            ĐẶT NGAY DƯỚI TAB, TRÊN danh sách ảnh: hai bong bóng chào hiện liền nhau
            trước mặt khách (chữ rồi ảnh), nên người vận hành cần thấy cả hai trên cùng
            một màn hình. Tách sang trang riêng thì rất dễ đổi chữ mà quên đổi ảnh, để
            lại lời chào của đợt khuyến mãi này kèm bảng số liệu của đợt trước. */}
        {placement === "CHAT_PROMO" && <ChatPromoTextEditor />}

        {/* ===== Thanh hành động ===== */}
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-2">
            <activeConfig.icon className="w-5 h-5 text-red-600" />
            <span className="text-sm font-extrabold text-slate-900">
              {t("banners.list_title", { count: banners.length })}
            </span>
          </div>

          <div className="flex items-center gap-3">
            {/* Nhãn số lượng ĐỨNG NGOÀI nút, không phải tooltip: người vận hành cần
                thấy còn bao nhiêu chỗ TRƯỚC khi bấm, và khi nút bị tắt thì tooltip trên
                phần tử disabled không hiện được trên nhiều trình duyệt. */}
            {limits && (
              <span
                className={`text-[11px] font-bold tabular-nums ${
                  atMaxCount ? "text-red-600" : "text-slate-500"
                }`}
              >
                {t("banners.count_badge", {
                  current: limits.currentCount,
                  max: limits.maxCount,
                })}
              </span>
            )}
            <button
              id="banner-upload-open"
              onClick={openUploadModal}
              disabled={atMaxCount}
              title={atMaxCount ? t("banners.max_reached", { max: limits?.maxCount ?? 0 }) : undefined}
              className="bg-red-600 hover:bg-red-700 disabled:bg-slate-300 disabled:cursor-not-allowed text-white font-bold px-3.5 py-2 rounded-xl text-xs flex items-center gap-1.5 transition-colors shadow-sm"
            >
              <Plus className="w-4 h-4" />
              <span>{t("banners.btn_upload")}</span>
            </button>
            <button
              onClick={reload}
              aria-label={t("admin.states.retry")}
              className="p-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 shadow-xs"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-600" : ""}`} />
            </button>
          </div>
        </div>

        {loadError ? (
          <AdminErrorState message={loadError} onRetry={reload} />
        ) : banners.length === 0 && !loading ? (
          <div className="bg-white border border-slate-200 rounded-2xl">
            <AdminEmptyState message={t("banners.empty")} />
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {banners.map((banner) => {
              const src = banner.mediaUrl.startsWith("/uploads")
                ? `${USER_BASE_URL}${banner.mediaUrl}`
                : banner.mediaUrl;
              const isLive = banner.id === liveChatPromoId;

              return (
                <div
                  key={banner.id}
                  className={`bg-white border rounded-2xl overflow-hidden shadow-xs flex flex-col justify-between transition-all group ${
                    isLive ? "border-emerald-400 ring-2 ring-emerald-100" : "border-slate-200 hover:border-red-300"
                  }`}
                >
                  {/* Media Preview Container */}
                  <div className="relative w-full aspect-[16/9] bg-slate-900 overflow-hidden flex items-center justify-center">
                    {banner.mediaType === "VIDEO" ? (
                      <video src={src} controls className="w-full h-full object-cover" />
                    ) : (
                      /* eslint-disable-next-line @next/next/no-img-element */
                      <img
                        src={src}
                        alt={banner.title}
                        // `object-contain` cho ảnh chat, `object-cover` cho banner trang chủ:
                        // ảnh chat thường là bảng số liệu dọc, cắt về 16/9 thì phần bị cắt
                        // lại đúng là phần cần xem để biết mình chọn đúng ảnh chưa.
                        className={`w-full h-full ${
                          banner.placement === "CHAT_PROMO"
                            ? "object-contain"
                            : "object-cover group-hover:scale-105 transition-transform duration-300"
                        }`}
                      />
                    )}

                    {/* Badge Type */}
                    <div className="absolute top-3 left-3 px-2.5 py-1 rounded-lg bg-slate-900/80 backdrop-blur-md border border-white/20 text-[10px] font-bold text-white flex items-center gap-1">
                      {banner.mediaType === "VIDEO" ? (
                        <>
                          <Video className="w-3 h-3 text-red-500" /> VIDEO MP4
                        </>
                      ) : (
                        <>
                          <ImageIcon className="w-3 h-3 text-amber-400" /> ANH PROMO
                        </>
                      )}
                    </div>

                    {/* NHÃN "ĐANG DÙNG" — chỉ ở khu chat, và chỉ trên đúng ảnh khách thấy. */}
                    {isLive && (
                      <div className="absolute top-3 right-3 px-2.5 py-1 rounded-lg bg-emerald-500 text-[10px] font-bold text-white flex items-center gap-1 shadow-sm">
                        <Star className="w-3 h-3" />
                        {t("banners.live_badge")}
                      </div>
                    )}
                  </div>

                  {/* Information */}
                  <div className="p-4 flex flex-col gap-2">
                    <div className="flex items-start justify-between gap-2">
                      <h3 className="text-sm font-extrabold text-slate-900 line-clamp-1">
                        {banner.title}
                      </h3>
                      <button
                        onClick={() => handleToggleStatus(banner)}
                        className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold flex items-center gap-1 shrink-0 ${
                          banner.isActive
                            ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                            : "bg-slate-100 text-slate-600 border border-slate-200"
                        }`}
                      >
                        {banner.isActive ? (
                          <CheckCircle2 className="w-3 h-3" />
                        ) : (
                          <XCircle className="w-3 h-3" />
                        )}
                        {banner.isActive ? "ACTIVE" : "OFF"}
                      </button>
                    </div>

                    {banner.linkUrl && (
                      <a
                        href={banner.linkUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="text-[11px] font-bold text-blue-600 hover:text-blue-700 flex items-center gap-1 line-clamp-1"
                      >
                        <span>{banner.linkUrl}</span>
                        <ExternalLink className="w-3 h-3" />
                      </a>
                    )}

                    <div className="flex items-center justify-between text-[11px] text-slate-500 pt-2 border-t border-slate-100">
                      <span>{t("banners.priority", { order: banner.sortOrder })}</span>
                      <span>{new Date(banner.createdAt).toLocaleDateString()}</span>
                    </div>
                  </div>

                  {/* Action Buttons */}
                  <div className="p-3 bg-slate-50 border-t border-slate-100 flex items-center justify-end">
                    <button
                      onClick={() => handleDeleteBanner(banner.id)}
                      className="px-3 py-1.5 rounded-xl bg-red-50 hover:bg-red-100 border border-red-200 text-xs font-bold text-red-700 flex items-center gap-1 transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>{t("banners.delete_btn")}</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {totalPages > 1 && !loadError && (
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span className="font-medium">
              {t("admin.states.page_of", { page: page + 1, total: totalPages })}
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1.5 rounded-lg bg-white border border-slate-200 disabled:opacity-40 hover:bg-slate-100 font-bold"
              >
                {t("admin.states.prev_page")}
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1.5 rounded-lg bg-white border border-slate-200 disabled:opacity-40 hover:bg-slate-100 font-bold"
              >
                {t("admin.states.next_page")}
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Upload Modal */}
      <AdminModal
        isOpen={showUploadModal}
        onClose={() => setShowUploadModal(false)}
        maxWidthClass="max-w-md"
        title={
          <div className="flex items-center gap-3 w-full">
            <div className="p-2.5 rounded-xl bg-red-50 border border-red-200 text-red-600 shrink-0">
              <Upload className="w-5 h-5" />
            </div>
            <div className="flex flex-col min-w-0">
              <h3 className="text-base font-extrabold text-slate-900 truncate">
                {t("banners.modal_upload_title")}
              </h3>
              {/* NHẮC LẠI KHU TRONG HỘP THOẠI: người vận hành có thể đã mở tab từ lâu rồi
                  mới bấm tải lên, và hộp thoại che mất phần chọn tab phía sau. */}
              <span className="text-xs text-slate-500 font-medium truncate">
                {t(`banners.placement.${placement}.name`)}
              </span>
            </div>
          </div>
        }
      >
        <div className="flex flex-col gap-5">
          {/* Nhắc lại ảnh sẽ hiện ở đâu, ngay trên ô chọn tệp. */}
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-[11px] leading-relaxed text-slate-600 font-medium">
            {t(`banners.placement.${placement}.hint`)}
          </div>

          {uploadError && (
            <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700 font-semibold animate-modal-panel-in">
              {uploadError}
            </div>
          )}

          <form onSubmit={handleUploadSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="banner-file" className="text-xs font-bold text-slate-700">
                {t("banners.field_file")}
              </label>
              <input
                id="banner-file"
                type="file"
                required
                accept={activeConfig.accept}
                onChange={(e) => setFile(e.target.files?.[0] || null)}
                className="bg-slate-50 border border-slate-200 rounded-xl p-2 text-xs text-slate-900 file:mr-3 file:py-1 file:px-3 file:rounded-lg file:border-0 file:text-xs file:font-bold file:bg-red-600 file:text-white hover:file:bg-red-700 outline-none cursor-pointer"
              />
              {/* NÓI TRƯỚC mức tối đa thay vì để người dùng chọn tệp rồi mới báo lỗi. */}
              {limits && (
                <span className="text-[11px] text-slate-500 font-medium">
                  {placement === "CHAT_PROMO"
                    ? t("banners.hint_limits_image_only", {
                        image: formatBytes(limits.maxImageBytes),
                      })
                    : t("banners.hint_limits", {
                        image: formatBytes(limits.maxImageBytes),
                        video: formatBytes(limits.maxVideoBytes),
                      })}
                  {" · "}
                  {t("banners.title_from_filename")}
                </span>
              )}
            </div>

            {/* KHÔNG CÒN trường "Tiêu đề" và "Đường dẫn khi bấm vào": biểu mẫu chỉ
                cần tệp + thứ tự. Tiêu đề do backend suy từ tên tệp (cột title là
                NOT NULL) và chỉ hiện trong danh sách quản trị, người chơi không đọc thấy. */}

            <div className="flex flex-col gap-1.5">
              <label htmlFor="banner-sort" className="text-xs font-bold text-slate-700">
                {t("banners.field_sort")}
              </label>
              <input
                id="banner-sort"
                type="number"
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
                className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-semibold"
              />
              {/* Ở khu chat, thứ tự KHÔNG chỉ là thứ tự — nó quyết định ảnh nào được dùng. */}
              {placement === "CHAT_PROMO" && (
                <span className="text-[11px] text-slate-500 font-medium">
                  {t("banners.hint_sort_chat_promo")}
                </span>
              )}
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowUploadModal(false)}
                className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
              >
                {t("admin.states.cancel")}
              </button>
              <button
                type="submit"
                disabled={uploadLoading}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white font-bold text-xs transition-colors disabled:opacity-50"
              >
                {uploadLoading ? t("banners.uploading") : t("banners.btn_confirm_upload")}
              </button>
            </div>
          </form>
        </div>
      </AdminModal>
    </div>
  );
}
