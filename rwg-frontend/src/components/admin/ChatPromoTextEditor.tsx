"use client";

import React, { useCallback, useEffect, useState } from "react";
import { MessageSquareText, RefreshCw, Save } from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { useTranslation } from "@/context/LanguageContext";

/** Khớp `AppSettingResponse` của backend. */
interface AppSetting {
  key: string;
  value: string;
  updatedAt: string;
  updatedByUsername: string | null;
}

/** Trần ký tự, khớp `@Size(max = 4000)` ở `UpdateAppSettingRequest`. */
const MAX_LENGTH = 4000;

/**
 * Ô soạn lời chào khuyến mãi của khung chat hỗ trợ.
 *
 * <h2>CHỈ TIẾNG VIỆT — CÓ CHỦ Ý</h2>
 * Đoạn chữ này KHÔNG qua hệ thống dịch. Người vận hành soạn tiếng Việt và mọi khách đều
 * đọc đúng bản đó. Trước đây nó nằm trong sáu file locale, nghĩa là mỗi lần đổi nội dung
 * phải sửa cả sáu file mà năm file kia không ai đọc — và việc đó lại cần một lần build,
 * triển khai lại toàn bộ frontend cho một thay đổi thuộc quyền người vận hành.
 *
 * <h2>ĐẶT CẠNH ẢNH KHUYẾN MÃI, KHÔNG TÁCH THÀNH TRANG RIÊNG</h2>
 * Hai bong bóng chào (chữ rồi ảnh) hiện liền nhau trước mặt khách. Sửa ở hai trang khác
 * nhau thì rất dễ đổi chữ mà quên đổi ảnh, để lại một lời chào nói về đợt khuyến mãi này
 * kèm bảng số liệu của đợt trước.
 */
export const ChatPromoTextEditor: React.FC = () => {
  const { t } = useTranslation();

  const [setting, setSetting] = useState<AppSetting | null>(null);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await adminFetch<AppSetting>("/admin/settings/chat-promo-text");
      setSetting(data);
      setDraft(data.value);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("admin.states.load_failed"));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSave = async () => {
    const trimmed = draft.trim();
    // Chặn ở giao diện trước khi gọi API: một ô rỗng là lỗi rõ ràng và người vận hành
    // nên biết ngay, không phải chờ một vòng mạng để nhận thông báo từ server.
    if (!trimmed) {
      setError(t("chat_promo_text.error_empty"));
      return;
    }

    setSaving(true);
    setError("");
    setSaved(false);
    try {
      const data = await adminFetch<AppSetting>("/admin/settings/chat-promo-text", {
        method: "PUT",
        body: JSON.stringify({ value: trimmed }),
      });
      setSetting(data);
      setDraft(data.value);
      setSaved(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("chat_promo_text.error_save"));
    } finally {
      setSaving(false);
    }
  };

  // So với bản đã lưu, KHÔNG dùng một cờ `dirty` tự bật khi gõ: cờ đó vẫn bật sau khi
  // người dùng gõ rồi hoàn tác về đúng nội dung cũ, và nút Lưu sáng lên cho một thay
  // đổi không tồn tại.
  const isDirty = setting !== null && draft.trim() !== setting.value;

  return (
    <section className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2">
          <MessageSquareText className="h-5 w-5 text-red-600" />
          <div className="flex flex-col">
            <h2 className="text-sm font-extrabold text-slate-900">
              {t("chat_promo_text.title")}
            </h2>
            <p className="text-[11px] font-medium leading-relaxed text-slate-500">
              {t("chat_promo_text.hint")}
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => void load()}
          disabled={loading || saving}
          className="flex items-center gap-1.5 rounded-xl bg-slate-100 px-3 py-2 text-xs font-bold text-slate-700 transition-colors hover:bg-slate-200 disabled:opacity-50"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
          {t("admin.states.refresh")}
        </button>
      </div>

      <label htmlFor="chat-promo-text" className="sr-only">
        {t("chat_promo_text.title")}
      </label>
      <textarea
        id="chat-promo-text"
        value={draft}
        onChange={(e) => {
          setDraft(e.target.value);
          // Xoá dấu "đã lưu" ngay khi gõ tiếp: giữ lại thì người vận hành sẽ tin là
          // thay đổi mới nhất cũng đã được lưu.
          setSaved(false);
        }}
        disabled={loading || saving}
        rows={10}
        maxLength={MAX_LENGTH}
        // `font-mono` + `whitespace-pre-wrap`: nội dung này có nhiều dòng và khách sẽ
        // thấy đúng từng lần xuống dòng, nên ô soạn phải hiện y như vậy.
        className="w-full resize-y rounded-xl border border-slate-200 bg-slate-50 p-3 font-mono text-xs leading-relaxed text-slate-900 outline-none focus:border-red-500 disabled:opacity-60"
        placeholder={t("chat_promo_text.placeholder")}
      />

      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex flex-col gap-0.5">
          {/* Đếm ký tự: trần 4000 không phải con số ai cũng nhớ, và một nội dung dài
              bị cắt lúc lưu mà không báo trước là thứ khó hiểu nhất. */}
          <span
            className={`text-[11px] font-bold tabular-nums ${
              draft.length >= MAX_LENGTH ? "text-red-600" : "text-slate-500"
            }`}
          >
            {draft.length} / {MAX_LENGTH}
          </span>
          {setting?.updatedByUsername && (
            <span className="text-[11px] font-medium text-slate-500">
              {t("chat_promo_text.last_updated", {
                name: setting.updatedByUsername,
                time: new Date(setting.updatedAt).toLocaleString(),
              })}
            </span>
          )}
        </div>

        <div className="flex items-center gap-3">
          {saved && !isDirty && (
            <span className="text-[11px] font-bold text-emerald-600">
              {t("chat_promo_text.saved")}
            </span>
          )}
          <button
            type="button"
            onClick={() => void handleSave()}
            // Tắt khi KHÔNG có gì đổi: bấm Lưu mà nội dung y cũ sẽ tạo một dòng audit
            // rỗng nghĩa, làm loãng sổ audit khi cần tra thay đổi thật.
            disabled={saving || loading || !isDirty}
            className="flex items-center gap-1.5 rounded-xl bg-red-600 px-4 py-2 text-xs font-bold text-white transition-colors hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            <Save className="h-3.5 w-3.5" />
            {saving ? t("chat_promo_text.saving") : t("chat_promo_text.save")}
          </button>
        </div>
      </div>

      {error && (
        <p className="rounded-xl bg-red-50 px-3 py-2 text-[11px] font-semibold text-red-700">
          {error}
        </p>
      )}
    </section>
  );
};
