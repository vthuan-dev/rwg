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
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { USER_BASE_URL } from "@/lib/constants";
import { AdminErrorState, AdminEmptyState } from "@/components/admin/AdminStates";
import { AdminModal } from "@/components/admin/AdminModal";
import { useTranslation } from "@/context/LanguageContext";

interface BannerItem {
  id: string;
  title: string;
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

export default function AdminBannersPage() {
  const { t } = useTranslation();
  const [banners, setBanners] = useState<BannerItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  // Upload Modal
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [linkUrl, setLinkUrl] = useState("");
  const [sortOrder, setSortOrder] = useState("0");
  const [uploadLoading, setUploadLoading] = useState(false);
  const [uploadError, setUploadError] = useState("");

  /**
   * Lấy một trang ảnh quảng cáo.
   *
   * Trả dữ liệu về thay vì tự đặt state: `setState` gọi đồng bộ trong thân effect gây
   * chuỗi render liên tiếp và luật lint của dự án chặn.
   */
  const fetchBanners = useCallback(async (): Promise<BannerPage | null> => {
    try {
      // Endpoint tra ve PageResponse {content, totalPages, ...}, KHONG phai mang.
      // Truoc day code kiem tra Array.isArray nen moi response that deu bi bo qua.
      const data = await adminFetch<BannerPage>(`/admin/banners?page=${page}&size=10`);
      setLoadError("");
      return data;
    } catch (err) {
      setLoadError((err as Error).message);
      return null;
    }
  }, [page]);

  /** Đưa kết quả vào state. Dùng chung cho lần tải đầu và các lần tải lại. */
  const applyResult = useCallback((data: BannerPage | null) => {
    setBanners(data?.content ?? []);
    setTotalPages(data?.totalPages || 1);
  }, []);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể đổi trang trước khi request xong, và ghi state vào
    // component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      // Đặt state bên trong hàm async, không ở thân effect — xem lý do ở trên.
      setLoading(true);
      const data = await fetchBanners();
      if (cancelled) return;
      applyResult(data);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchBanners, applyResult]);

  /** Tải lại sau khi ghi. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    applyResult(await fetchBanners());
    setLoading(false);
  }, [fetchBanners, applyResult]);

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
    if (!title.trim()) {
      setUploadError(t("banners.err_title_required"));
      return;
    }

    setUploadLoading(true);
    setUploadError("");

    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("title", title.trim());
      if (linkUrl.trim()) formData.append("linkUrl", linkUrl.trim());
      formData.append("sortOrder", sortOrder);

      await adminFetch("/admin/banners/upload", {
        method: "POST",
        body: formData,
      });

      setShowUploadModal(false);
      setFile(null);
      setTitle("");
      setLinkUrl("");
      void reload();
    } catch (err) {
      setUploadError((err as Error).message || t("banners.err_upload_failed"));
    } finally {
      setUploadLoading(false);
    }
  };

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title={t("banners.title")}
        subtitle={t("banners.subtitle")}
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Top Action Bar */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ImageIcon className="w-5 h-5 text-red-600" />
            <span className="text-sm font-extrabold text-slate-900">
              {t("banners.list_title", { count: banners.length })}
            </span>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowUploadModal(true)}
              className="bg-red-600 hover:bg-red-700 text-white font-bold px-3.5 py-2 rounded-xl text-xs flex items-center gap-1.5 transition-colors shadow-sm"
            >
              <Plus className="w-4 h-4" />
              <span>{t("banners.btn_upload")}</span>
            </button>
            <button
              onClick={reload}
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
          {banners.map((banner) => (
            <div
              key={banner.id}
              className="bg-white border border-slate-200 hover:border-red-300 rounded-2xl overflow-hidden shadow-xs flex flex-col justify-between transition-all group"
            >
              {/* Media Preview Container */}
              <div className="relative w-full aspect-[16/9] bg-slate-900 overflow-hidden flex items-center justify-center">
                {banner.mediaType === "VIDEO" ? (
                  <video
                    src={banner.mediaUrl.startsWith("/uploads") ? `${USER_BASE_URL}${banner.mediaUrl}` : banner.mediaUrl}
                    controls
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <img
                    src={banner.mediaUrl.startsWith("/uploads") ? `${USER_BASE_URL}${banner.mediaUrl}` : banner.mediaUrl}
                    alt={banner.title}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
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
              </div>

              {/* Information */}
              <div className="p-4 flex flex-col gap-2">
                <div className="flex items-start justify-between gap-2">
                  <h3 className="text-sm font-extrabold text-slate-900 line-clamp-1">{banner.title}</h3>
                  <button
                    onClick={() => handleToggleStatus(banner)}
                    className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold flex items-center gap-1 shrink-0 ${
                      banner.isActive
                        ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                        : "bg-slate-100 text-slate-600 border border-slate-200"
                    }`}
                  >
                    {banner.isActive ? <CheckCircle2 className="w-3 h-3" /> : <XCircle className="w-3 h-3" />}
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
          ))}
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
              <span className="text-xs text-slate-500 font-medium truncate">
                {t("banners.modal_upload_subtitle")}
              </span>
            </div>
          </div>
        }
      >
        <div className="flex flex-col gap-5">
          {uploadError && (
            <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700 font-semibold animate-modal-panel-in">
              {uploadError}
            </div>
          )}

          <form onSubmit={handleUploadSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-bold text-slate-700">{t("banners.field_file")}</label>
              <input
                type="file"
                required
                accept="video/mp4,video/webm,image/png,image/jpeg,image/webp"
                onChange={(e) => setFile(e.target.files?.[0] || null)}
                className="bg-slate-50 border border-slate-200 rounded-xl p-2 text-xs text-slate-900 file:mr-3 file:py-1 file:px-3 file:rounded-lg file:border-0 file:text-xs file:font-bold file:bg-red-600 file:text-white hover:file:bg-red-700 outline-none cursor-pointer"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-bold text-slate-700">{t("banners.field_title")}</label>
              <input
                type="text"
                required
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Vd: Hero Video Casino 2026"
                className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-semibold"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-bold text-slate-700">{t("banners.field_link")}</label>
              <input
                type="url"
                value={linkUrl}
                onChange={(e) => setLinkUrl(e.target.value)}
                placeholder="https://rwg.com/promo-hero"
                className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-semibold"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-bold text-slate-700">{t("banners.field_sort")}</label>
              <input
                type="number"
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
                className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-semibold"
              />
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
