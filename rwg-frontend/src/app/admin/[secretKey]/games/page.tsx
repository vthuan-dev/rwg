"use client";

import React, { useState, useEffect, useCallback } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  Gamepad2,
  CheckCircle2,
  XCircle,
  RefreshCw,
  Sliders,
  AlertCircle,
  Power,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { formatMoney } from "@/lib/money";
import { isSuperAdmin, hasAnyRole } from "@/lib/adminIdentity";
import { AdminErrorState, AdminEmptyState } from "@/components/admin/AdminStates";
import { AdminModal } from "@/components/admin/AdminModal";
import { useTranslation } from "@/context/LanguageContext";
import { BaccaratSimulator } from "@/components/admin/BaccaratSimulator";
import { RouletteSimulator } from "@/components/admin/RouletteSimulator";
import { LotterySimulator } from "@/components/admin/LotterySimulator";

/**
 * Bàn chơi — khớp GameTableResponse của backend.
 *
 * Tên bàn là MỘT MAP đa ngôn ngữ (nameI18n), không phải một chuỗi. Hạn mức là
 * chuỗi vì backend dùng BigDecimal. Trạng thái là "ACTIVE"/"DISABLED", không phải
 * cờ boolean.
 */
interface GameTable {
  id: string;
  gameType: string;
  nameI18n: Record<string, string>;
  status: "ACTIVE" | "DISABLED";
  minBet: string;
  maxBet: string;
  currency: string;
}

export default function AdminGamesPage() {
  const { locale, t } = useTranslation();
  // Doi han muc cuoc la ADMIN-only; bat/tat ban mo cho ca RISK.
  const canEditLimits = isSuperAdmin();
  const canToggle = hasAnyRole(["ADMIN", "RISK"]);

  const [tables, setTables] = useState<GameTable[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [editTable, setEditTable] = useState<GameTable | null>(null);
  const [minBet, setMinBet] = useState("");
  const [maxBet, setMaxBet] = useState("");
  const [limitReason, setLimitReason] = useState("");

  const [toggleTable, setToggleTable] = useState<GameTable | null>(null);
  const [toggleReason, setToggleReason] = useState("");

  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");

  /** Tên bàn theo ngôn ngữ đang chọn, lùi về tiếng Anh rồi tới khoá đầu tiên. */
  const tableName = (t: GameTable): string =>
    t.nameI18n?.[locale] || t.nameI18n?.en || Object.values(t.nameI18n || {})[0] || t.gameType;

  /**
   * Lấy danh sách bàn.
   *
   * Trả dữ liệu về thay vì tự đặt state: `setState` gọi đồng bộ trong thân effect gây
   * chuỗi render liên tiếp và luật lint của dự án chặn.
   *
   * Trả `null` khi lỗi — KHÔNG trả mảng rỗng: bàn bịa đặt có thể khiến người vận hành
   * tưởng đã tắt một bàn trong khi bàn thật vẫn nhận cược.
   */
  const fetchTables = useCallback(async (): Promise<GameTable[] | null> => {
    try {
      const data = await adminFetch<GameTable[]>("/admin/games/tables");
      setLoadError("");
      return Array.isArray(data) ? data : [];
    } catch (err) {
      setLoadError((err as Error).message);
      return null;
    }
  }, []);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể rời trang trước khi request xong, và ghi state vào
    // component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      // Đặt state bên trong hàm async, không ở thân effect — xem lý do ở trên.
      setLoading(true);
      const data = await fetchTables();
      if (cancelled) return;
      setTables(data ?? []);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchTables]);

  /** Tải lại từ nút bấm hoặc sau khi ghi. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    setTables((await fetchTables()) ?? []);
    setLoading(false);
  }, [fetchTables]);

  const openToggle = (t: GameTable) => {
    setToggleTable(t);
    setToggleReason("");
    setActionError("");
  };

  const openLimits = (t: GameTable) => {
    setEditTable(t);
    setMinBet(t.minBet);
    setMaxBet(t.maxBet);
    setLimitReason("");
    setActionError("");
  };

  const submitToggle = async () => {
    if (!toggleTable) return;
    if (!toggleReason.trim()) {
      setActionError(t("admin.games.err_toggle_reason"));
      return;
    }
    setActionLoading(true);
    setActionError("");
    try {
      // Backend nhan {status, reason}, KHONG phai {active}.
      await adminFetch(`/admin/games/tables/${toggleTable.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({
          status: toggleTable.status === "ACTIVE" ? "DISABLED" : "ACTIVE",
          reason: toggleReason.trim(),
        }),
      });
      setToggleTable(null);
      void reload();
    } catch (err) {
      setActionError((err as Error).message);
    } finally {
      setActionLoading(false);
    }
  };

  const submitLimits = async () => {
    if (!editTable) return;
    if (!limitReason.trim()) {
      setActionError(t("admin.games.err_limits_reason"));
      return;
    }
    setActionLoading(true);
    setActionError("");
    try {
      // Tien gui dang CHUOI; backend con doi them truong `reason`.
      await adminFetch(`/admin/games/tables/${editTable.id}/limits`, {
        method: "PATCH",
        body: JSON.stringify({
          minBet: minBet.trim(),
          maxBet: maxBet.trim(),
          reason: limitReason.trim(),
        }),
      });
      setEditTable(null);
      void reload();
    } catch (err) {
      setActionError((err as Error).message);
    } finally {
      setActionLoading(false);
    }
  };

  const activeCount = tables.filter((t) => t.status === "ACTIVE").length;

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title={t("admin.games.title")}
        subtitle={t("admin.games.subtitle")}
      />

      <div className="p-6 flex flex-col gap-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Gamepad2 className="w-5 h-5 text-slate-700" />
            <span className="text-sm font-extrabold text-slate-900">
              {t("admin.games.active_tables_count", { active: activeCount, total: tables.length })}
            </span>
          </div>
          <button
            onClick={reload}
            className="p-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 shadow-xs"
            aria-label={t("admin.states.refresh")}
          >
            <RefreshCw
              className={`w-4 h-4 ${loading ? "animate-spin text-red-600" : ""}`}
            />
          </button>
        </div>

        {loadError ? (
          <AdminErrorState message={loadError} onRetry={reload} />
        ) : tables.length === 0 && !loading ? (
          <div className="bg-white border border-slate-200 rounded-2xl">
            <AdminEmptyState message={t("admin.games.empty")} />
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {tables.map((tbl, index) => {
              let previewImage = "/images/tables/lottery.webp";
              if (tbl.gameType === "ROULETTE") {
                previewImage = "/images/tables/roulette.webp";
              } else if (tbl.gameType === "BACCARAT") {
                previewImage = "/images/tables/baccarat.webp";
              }

              return (
                <div
                  key={tbl.id}
                  style={{ animationDelay: `${index * 60}ms` }}
                  className="bg-white border border-slate-200 rounded-2xl flex flex-col overflow-hidden shadow-xs animate-fade-in-up hover:shadow-lg hover:-translate-y-1 hover:border-slate-300 transition-all duration-300 ease-out"
                >
                  {/* Banner ảnh minh họa bàn chơi thật */}
                  <div className="h-32 w-full relative overflow-hidden bg-slate-900 border-b border-slate-100 shrink-0">
                    <img
                      src={previewImage}
                      alt={tableName(tbl)}
                      className="w-full h-full object-cover opacity-90 transition-transform duration-500 hover:scale-105"
                    />
                    {/* Badge trạng thái nằm đè lên góc ảnh */}
                    <div className="absolute top-3 right-3 select-none">
                      {tbl.status === "ACTIVE" ? (
                        <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-emerald-500 text-white shadow-sm flex items-center gap-1 shrink-0 backdrop-blur-xs">
                          <CheckCircle2 className="w-3 h-3" /> ACTIVE
                        </span>
                      ) : (
                        <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-slate-600/90 text-white shadow-sm flex items-center gap-1 shrink-0 backdrop-blur-xs">
                          <XCircle className="w-3 h-3" /> DISABLED
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="p-5 flex flex-col gap-4 flex-1">
                    <div className="flex flex-col gap-1">
                      <span className="text-sm font-extrabold text-slate-900 leading-tight">
                        {tableName(tbl)}
                      </span>
                      <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                        {tbl.gameType}
                      </span>
                    </div>

                    {tbl.gameType === "ROULETTE" && (
                      <div className="w-full">
                        <RouletteSimulator />
                      </div>
                    )}
                    {tbl.gameType === "BACCARAT" && (
                      <div className="w-full">
                        <BaccaratSimulator />
                      </div>
                    )}
                    {tbl.gameType !== "ROULETTE" && tbl.gameType !== "BACCARAT" && (
                      <div className="w-full">
                        <LotterySimulator />
                      </div>
                    )}

                    <div className="grid grid-cols-2 gap-2">
                      <div className="flex flex-col gap-0.5 px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl">
                        <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">
                          {t("admin.games.min_bet")}
                        </span>
                        <span className="text-sm font-extrabold text-slate-900 tabular-nums">
                          {formatMoney(tbl.minBet)}
                        </span>
                      </div>
                      <div className="flex flex-col gap-0.5 px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl">
                        <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">
                          {t("admin.games.max_bet")}
                        </span>
                        <span className="text-sm font-extrabold text-slate-900 tabular-nums">
                          {formatMoney(tbl.maxBet)}
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 pt-1 mt-auto">
                      {canToggle && (
                        <button
                          onClick={() => openToggle(tbl)}
                          className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl text-xs font-bold border transition-colors ${
                            tbl.status === "ACTIVE"
                              ? "bg-white border-slate-200 text-slate-700 hover:bg-slate-50"
                              : "bg-emerald-50 border-emerald-200 text-emerald-700 hover:bg-emerald-100"
                          }`}
                        >
                          <Power className="w-3.5 h-3.5" />
                          {tbl.status === "ACTIVE" ? t("admin.games.action_disable") : t("admin.games.action_enable")}
                        </button>
                      )}
                      {canEditLimits && (
                        <button
                          onClick={() => openLimits(tbl)}
                          className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition-colors"
                        >
                          <Sliders className="w-3.5 h-3.5" />
                          {t("admin.games.action_limits")}
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Modal bat/tat ban */}
      <AdminModal
        isOpen={!!toggleTable}
        onClose={() => setToggleTable(null)}
        maxWidthClass="max-w-md"
        title={
          toggleTable && (
            <div className="flex items-center gap-3 w-full">
              <div className="p-2.5 rounded-xl bg-slate-100 border border-slate-200 text-slate-700 shrink-0">
                <Power className="w-5 h-5" />
              </div>
              <div className="flex flex-col min-w-0">
                <h3 className="text-base font-extrabold text-slate-900 truncate">
                  {toggleTable.status === "ACTIVE" ? t("admin.games.action_disable") : t("admin.games.action_enable")}
                </h3>
                <span className="text-xs text-slate-500 font-medium truncate">
                  {tableName(toggleTable)}
                </span>
              </div>
            </div>
          )
        }
      >
        {toggleTable && (
          <div className="flex flex-col gap-5">
            {/* Backend dong ban o CUOI vong dang chay; noi ro de nguoi van hanh
                khong tuong la huy vong giua dong. */}
            {toggleTable.status === "ACTIVE" && (
              <div className="flex items-start gap-3 p-3.5 bg-amber-50 border border-amber-300 rounded-xl">
                <AlertCircle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
                <span className="text-[11px] text-amber-800 font-semibold leading-relaxed">
                  {t("admin.games.modal_toggle_warn")}
                </span>
              </div>
            )}

            {actionError && (
              <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
                <AlertCircle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
                <span className="text-xs text-red-700 font-semibold">{actionError}</span>
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="toggle-reason"
                className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
              >
                {t("admin.games.reason_label")} <span className="text-red-600 normal-case">{t("admin.games.reason_required_badge")}</span>
              </label>
              <textarea
                id="toggle-reason"
                rows={3}
                value={toggleReason}
                onChange={(e) => setToggleReason(e.target.value)}
                placeholder={t("admin.games.toggle_placeholder")}
                className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl p-3 text-xs text-slate-900 placeholder-slate-400 outline-none resize-none font-medium"
              />
            </div>

            <div className="flex items-center justify-end gap-3">
              <button
                onClick={() => setToggleTable(null)}
                className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
              >
                {t("admin.states.cancel")}
              </button>
              <button
                onClick={submitToggle}
                disabled={actionLoading}
                className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-bold text-xs transition-colors disabled:opacity-50"
              >
                {actionLoading ? t("admin.states.saving") : t("admin.states.confirm")}
              </button>
            </div>
          </div>
        )}
      </AdminModal>

      {/* Modal han muc cuoc */}
      <AdminModal
        isOpen={!!editTable}
        onClose={() => setEditTable(null)}
        maxWidthClass="max-w-md"
        title={
          editTable && (
            <div className="flex items-center gap-3 w-full">
              <div className="p-2.5 rounded-xl bg-slate-100 border border-slate-200 text-slate-700 shrink-0">
                <Sliders className="w-5 h-5" />
              </div>
              <div className="flex flex-col min-w-0">
                <h3 className="text-base font-extrabold text-slate-900 truncate">
                  {t("admin.games.modal_limits_title")}
                </h3>
                <span className="text-xs text-slate-500 font-medium truncate">
                  {tableName(editTable)}
                </span>
              </div>
            </div>
          )
        }
      >
        {editTable && (
          <div className="flex flex-col gap-5">
            <div className="flex items-start gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-xl">
              <AlertCircle className="w-5 h-5 text-slate-400 shrink-0 mt-0.5" />
              <span className="text-[11px] text-slate-600 font-medium leading-relaxed">
                {t("admin.games.modal_limits_subtitle")}
              </span>
            </div>

            {actionError && (
              <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
                <AlertCircle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
                <span className="text-xs text-red-700 font-semibold">{actionError}</span>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor="min-bet"
                  className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
                >
                  {t("admin.games.min_label")}
                </label>
                <input
                  id="min-bet"
                  type="text"
                  inputMode="decimal"
                  value={minBet}
                  onChange={(e) => setMinBet(e.target.value)}
                  className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-sm font-bold text-slate-900 outline-none tabular-nums"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label
                  htmlFor="max-bet"
                  className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
                >
                  {t("admin.games.max_label")}
                </label>
                <input
                  id="max-bet"
                  type="text"
                  inputMode="decimal"
                  value={maxBet}
                  onChange={(e) => setMaxBet(e.target.value)}
                  className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-sm font-bold text-slate-900 outline-none tabular-nums"
                />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="limit-reason"
                className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
              >
                {t("admin.games.reason_label")} <span className="text-red-600 normal-case">{t("admin.games.reason_required_badge")}</span>
              </label>
              <textarea
                id="limit-reason"
                rows={2}
                value={limitReason}
                onChange={(e) => setLimitReason(e.target.value)}
                placeholder={t("admin.games.limits_placeholder")}
                className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl p-3 text-xs text-slate-900 placeholder-slate-400 outline-none resize-none font-medium"
              />
            </div>

            <div className="flex items-center justify-end gap-3">
              <button
                onClick={() => setEditTable(null)}
                className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
              >
                {t("admin.states.cancel")}
              </button>
              <button
                onClick={submitLimits}
                disabled={actionLoading}
                className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-bold text-xs transition-colors disabled:opacity-50"
              >
                {actionLoading ? t("admin.states.saving") : t("admin.states.confirm")}
              </button>
            </div>
          </div>
        )}
      </AdminModal>
    </div>
  );
}
