"use client";

import React, { useState, useEffect } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  Image as ImageIcon,
  Video,
  Upload,
  CheckCircle2,
  XCircle,
  Trash2,
  RefreshCw,
  X,
  Plus,
  ExternalLink,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { USER_BASE_URL } from "@/lib/constants";

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

export default function AdminBannersPage() {
  const [banners, setBanners] = useState<BannerItem[]>([]);
  const [loading, setLoading] = useState(true);

  // Upload Modal
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [linkUrl, setLinkUrl] = useState("");
  const [sortOrder, setSortOrder] = useState("0");
  const [uploadLoading, setUploadLoading] = useState(false);
  const [uploadError, setUploadError] = useState("");

  const loadBanners = async () => {
    setLoading(true);
    try {
      const data = await adminFetch("/admin/banners");
      if (Array.isArray(data)) {
        setBanners(data);
      }
    } catch {
      setBanners([
        {
          id: "b-1",
          title: "Tích Luỹ Phần Thưởng 2026",
          mediaType: "IMAGE",
          mediaUrl: "/images/banner_promo.jpg",
          isActive: true,
          sortOrder: 1,
          createdAt: "2026-08-20T10:00:00Z",
        },
        {
          id: "b-2",
          title: "Your Gateway To Fortune",
          mediaType: "IMAGE",
          mediaUrl: "/images/banner_gateway.jpg",
          isActive: true,
          sortOrder: 2,
          createdAt: "2026-08-20T11:00:00Z",
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBanners();
  }, []);

  const handleToggleStatus = async (banner: BannerItem) => {
    try {
      await adminFetch(`/admin/banners/${banner.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ active: !banner.isActive }),
      });
      loadBanners();
    } catch (err: any) {
      alert(err.message || "Đổi trạng thái thất bại");
    }
  };

  const handleDeleteBanner = async (id: string) => {
    if (!confirm("Anh có chắc muốn xoá banner này khỏi hệ thống?")) return;
    try {
      await adminFetch(`/admin/banners/${id}`, {
        method: "DELETE",
      });
      loadBanners();
    } catch (err: any) {
      alert(err.message || "Xoá banner thất bại");
    }
  };

  const handleUploadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setUploadError("Vui lòng chọn file Video hoặc Ảnh!");
      return;
    }
    if (!title.trim()) {
      setUploadError("Vui lòng nhập tiêu đề banner!");
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
      loadBanners();
    } catch (err: any) {
      setUploadError(err.message || "Upload banner thất bại");
    } finally {
      setUploadLoading(false);
    }
  };

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title="Quản lý Banner Video & Media Quảng cáo"
        subtitle="Upload video MP4/ảnh khuyến mãi trang chủ, quản lý thứ tự hiển thị"
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Top Action Bar */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ImageIcon className="w-5 h-5 text-red-600" />
            <span className="text-sm font-extrabold text-slate-900">Danh sách Banners Trang chủ ({banners.length})</span>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowUploadModal(true)}
              className="bg-red-600 hover:bg-red-700 text-white font-bold px-3.5 py-2 rounded-xl text-xs flex items-center gap-1.5 transition-colors shadow-sm"
            >
              <Plus className="w-4 h-4" />
              <span>Upload Banner Video / Ảnh Mới</span>
            </button>
            <button
              onClick={loadBanners}
              className="p-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 shadow-xs"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-600" : ""}`} />
            </button>
          </div>
        </div>

        {/* Banners Grid */}
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
                  <span>Ưu tiên: Sort #{banner.sortOrder}</span>
                  <span>{new Date(banner.createdAt).toLocaleDateString("vi-VN")}</span>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="p-3 bg-slate-50 border-t border-slate-100 flex items-center justify-end">
                <button
                  onClick={() => handleDeleteBanner(banner.id)}
                  className="px-3 py-1.5 rounded-xl bg-red-50 hover:bg-red-100 border border-red-200 text-xs font-bold text-red-700 flex items-center gap-1 transition-colors"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>Xoá Banner</span>
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Upload Modal */}
      {showUploadModal && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white border border-slate-200 rounded-2xl max-w-md w-full p-6 flex flex-col gap-5 shadow-2xl relative">
            <button
              onClick={() => setShowUploadModal(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-700"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-red-50 border border-red-200 text-red-600">
                <Upload className="w-5 h-5" />
              </div>
              <div className="flex flex-col">
                <h3 className="text-base font-extrabold text-slate-900">Upload Banner Video / Ảnh Mới</h3>
                <span className="text-xs text-slate-500 font-medium">Hỗ trợ file MP4, WebM, PNG, JPG, WebP</span>
              </div>
            </div>

            {uploadError && (
              <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700 font-semibold">
                {uploadError}
              </div>
            )}

            <form onSubmit={handleUploadSubmit} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-slate-700">Chọn File Media (Video/Ảnh) *</label>
                <input
                  type="file"
                  required
                  accept="video/mp4,video/webm,image/png,image/jpeg,image/webp"
                  onChange={(e) => setFile(e.target.files?.[0] || null)}
                  className="bg-slate-50 border border-slate-200 rounded-xl p-2 text-xs text-slate-900 file:mr-3 file:py-1 file:px-3 file:rounded-lg file:border-0 file:text-xs file:font-bold file:bg-red-600 file:text-white hover:file:bg-red-700 outline-none cursor-pointer"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-slate-700">Tiêu đề Banner *</label>
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
                <label className="text-xs font-bold text-slate-700">Link Liên kết khi bấm (Tùy chọn)</label>
                <input
                  type="url"
                  value={linkUrl}
                  onChange={(e) => setLinkUrl(e.target.value)}
                  placeholder="https://rwg.com/promo-hero"
                  className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-semibold"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-slate-700">Thứ tự Ưu tiên (Sort Order)</label>
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
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={uploadLoading}
                  className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white font-bold text-xs transition-colors disabled:opacity-50"
                >
                  {uploadLoading ? "Đang Upload..." : "Xác nhận Upload Banner"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
