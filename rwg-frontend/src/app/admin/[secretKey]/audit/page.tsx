"use client";

import React, { useState, useEffect, useCallback } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  Activity,
  RefreshCw,
  Search,
  ChevronLeft,
  ChevronRight,
  X,
  ShieldAlert,
} from "lucide-react";
import { adminFetch } from "@/lib/adminApi";
import { AdminErrorState, AdminEmptyState } from "@/components/admin/AdminStates";
import { useTranslation } from "@/context/LanguageContext";

/**
 * Một dòng nhật ký — khớp AuditLogResponse.
 *
 * `details` là chuỗi JSON THÔ đã lưu, backend cố tình không parse lại vì bảng
 * audit là append-only: giữ nguyên chuỗi gốc thì vết không bao giờ bị biến dạng
 * bởi một lần đổi schema DTO về sau.
 */
interface AuditLog {
  id: number;
  actorId: string | null;
  actorUsername: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}

/**
 * Một trang bản ghi kiểm toán, khớp `PageResponse` của backend.
 *
 * Trang rỗng có `totalPages = 0`, nên nơi hiển thị phải kẹp về tối thiểu 1.
 */
interface AuditPage {
  content: AuditLog[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/**
 * Thao tác cần nổi bật: hoặc chạm tiền, hoặc là dấu hiệu bị tấn công.
 *
 * Trong hàng trăm dòng lẫn đầy ADMIN_LOGIN_SUCCESS, những dòng này phải nhảy ra
 * khỏi màn hình.
 */
const CRITICAL_ACTIONS = new Set([
  "ADMIN_WALLET_ADJUSTED",
  "ADMIN_SELF_DEALING_BLOCKED",
  "ADMIN_LIMIT_EXCEEDED",
  "ADMIN_LOGIN_FORBIDDEN",
  "ADMIN_USER_ROLE_CHANGED",
  "ADMIN_COMMISSION_RATE_CHANGED",
  "ADMIN_WITHDRAWAL_PASSWORD_RESET",
]);

/** Nhóm mã thao tác cho ô lọc. Nhãn nhóm tra trong file dịch. */
const FILTER_GROUPS: Array<{ key: string; actions: string[] }> = [
  {
    key: "money",
    actions: [
      "ADMIN_WALLET_ADJUSTED",
      "ADMIN_SELF_DEALING_BLOCKED",
      "ADMIN_LIMIT_EXCEEDED",
      "WITHDRAWAL_APPROVED",
      "WITHDRAWAL_REJECTED",
      // Xem so tai khoan / dia chi vi day du: xep vao nhom tien du chi la thao tac
      // doc, vi day la du lieu dung de chuyen tien ra khoi san.
      "ADMIN_PAYOUT_METHOD_REVEALED",
    ],
  },

  {
    key: "approval",
    actions: ["ADMIN_APPROVAL_APPROVED", "ADMIN_APPROVAL_REJECTED"],
  },
  {
    key: "account",
    actions: [
      "ADMIN_USER_STATUS_CHANGED",
      "ADMIN_USER_ROLE_CHANGED",
      "ADMIN_KYC_UPDATED",
      "ADMIN_WITHDRAWAL_PASSWORD_RESET",
    ],
  },
  {
    key: "login",
    actions: ["ADMIN_LOGIN_SUCCESS", "ADMIN_LOGIN_FORBIDDEN", "LOGIN_FAILED"],
  },
  {
    key: "config",
    actions: [
      "ADMIN_COMMISSION_RATE_CHANGED",
      "ADMIN_TABLE_STATUS_CHANGED",
      "ADMIN_TABLE_LIMITS_CHANGED",
    ],
  },
];

/** Đổi chuỗi JSON thô thành dạng dễ đọc; không parse được thì trả nguyên chuỗi. */
const prettyDetails = (raw: string | null): string | null => {
  if (!raw) return null;
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    // Chuoi khong phai JSON hop le van phai hien duoc: audit khong bao gio duoc
    // im lang chi vi mot dong co dinh dang la.
    return raw;
  }
};

export default function AdminAuditPage() {
  const { t } = useTranslation();

  /**
   * Nhãn tiếng người của một mã thao tác.
   *
   * Mã lạ hiện NGUYÊN BẢN thay vì để trống: backend có thể thêm action mới bất cứ
   * lúc nào, và một nhật ký bỏ trống dòng vì chưa có bản dịch còn tệ hơn nhật ký
   * hiện mã kỹ thuật.
   */
  const labelFor = (action: string): string => {
    const key = `admin.audit.actions.${action}`;
    const label = t(key);
    return label === key ? action : label;
  };

  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const [actionFilter, setActionFilter] = useState("");
  const [targetInput, setTargetInput] = useState("");
  const [targetId, setTargetId] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  const [selected, setSelected] = useState<AuditLog | null>(null);

  /**
   * Lấy một trang bản ghi kiểm toán.
   *
   * Trả dữ liệu về cho nơi gọi thay vì tự đặt state. `setState` gọi đồng bộ trong thân
   * effect gây chuỗi render liên tiếp và luật lint của dự án chặn; tách ra như vậy thì cùng
   * một hàm dùng được cho cả lần tải đầu và nút Tải lại.
   */
  const fetchLogs = useCallback(async (): Promise<AuditPage | null> => {
    try {
      let query = `/admin/audit/logs?page=${page}&size=20`;
      if (actionFilter) query += `&action=${encodeURIComponent(actionFilter)}`;
      if (targetId) query += `&targetId=${encodeURIComponent(targetId)}`;
      if (fromDate) query += `&fromDate=${fromDate}`;
      if (toDate) query += `&toDate=${toDate}`;

      const data = await adminFetch<AuditPage>(query);
      setLoadError("");
      return data;
    } catch (err) {
      setLoadError((err as Error).message);
      return null;
    }
  }, [page, actionFilter, targetId, fromDate, toDate]);

  /** Đưa kết quả vào state. Dùng chung cho lần tải đầu và nút Tải lại. */
  const applyResult = useCallback((data: AuditPage | null) => {
    setLogs(data?.content ?? []);
    setTotalPages(data?.totalPages || 1);
    setTotalElements(data?.totalElements ?? 0);
  }, []);

  useEffect(() => {
    // Cờ huỷ: người vận hành có thể đổi trang hoặc rời đi trước khi request xong, và ghi
    // state vào component đã tháo là một cảnh báo React kèm rò bộ nhớ.
    let cancelled = false;

    (async () => {
      // Đặt state bên trong hàm async, không ở thân effect: gọi thẳng ngoài này là
      // `setState` đồng bộ trong effect, gây chuỗi render liên tiếp và bị lint chặn.
      setLoading(true);
      const data = await fetchLogs();
      if (cancelled) return;
      applyResult(data);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchLogs, applyResult]);

  /** Tải lại từ nút bấm. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    applyResult(await fetchLogs());
    setLoading(false);
  }, [fetchLogs, applyResult]);

  const applyTarget = () => {
    setPage(0);
    setTargetId(targetInput.trim());
  };

  const clearFilters = () => {
    setActionFilter("");
    setTargetInput("");
    setTargetId("");
    setFromDate("");
    setToDate("");
    setPage(0);
  };

  const hasFilter = !!(actionFilter || targetId || fromDate || toDate);

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title={t("admin.audit.title")}
        subtitle={t("admin.audit.subtitle")}
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Bo loc */}
        <div className="bg-white border border-slate-200 rounded-2xl p-4 flex flex-col gap-4 shadow-xs">
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1.5 min-w-[220px]">
              <label
                htmlFor="audit-action"
                className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
              >
                {t("admin.audit.filter_action")}
              </label>
              <select
                id="audit-action"
                value={actionFilter}
                onChange={(e) => {
                  setPage(0);
                  setActionFilter(e.target.value);
                }}
                className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-xs font-bold text-slate-900 outline-none cursor-pointer"
              >
                <option value="">{t("admin.audit.filter_all")}</option>
                {FILTER_GROUPS.map((group) => (
                  <optgroup
                    key={group.key}
                    label={t(`admin.audit.groups.${group.key}`)}
                  >
                    {group.actions.map((a) => (
                      <option key={a} value={a}>
                        {labelFor(a)}
                      </option>
                    ))}
                  </optgroup>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="audit-from"
                className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
              >
                {t("admin.audit.filter_from")}
              </label>
              <input
                id="audit-from"
                type="date"
                value={fromDate}
                onChange={(e) => {
                  setPage(0);
                  setFromDate(e.target.value);
                }}
                className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-xs font-bold text-slate-900 outline-none"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="audit-to"
                className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
              >
                {t("admin.audit.filter_to")}
              </label>
              <input
                id="audit-to"
                type="date"
                value={toDate}
                onChange={(e) => {
                  setPage(0);
                  setToDate(e.target.value);
                }}
                className="bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-xs font-bold text-slate-900 outline-none"
              />
            </div>

            <div className="flex flex-col gap-1.5 flex-1 min-w-[260px]">
              <label
                htmlFor="audit-target"
                className="text-[11px] font-bold text-slate-700 uppercase tracking-wide"
              >
                {t("admin.audit.filter_target")}
              </label>
              <div className="flex items-center gap-2">
                <input
                  id="audit-target"
                  type="text"
                  value={targetInput}
                  onChange={(e) => setTargetInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && applyTarget()}
                  placeholder={t("admin.audit.target_placeholder")}
                  className="flex-1 bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3.5 py-2.5 text-xs font-mono text-slate-900 placeholder-slate-400 outline-none"
                />
                <button
                  onClick={applyTarget}
                  className="p-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white transition-colors"
                  aria-label={t("admin.states.search")}
                >
                  <Search className="w-4 h-4" />
                </button>
              </div>
            </div>

            <button
              onClick={reload}
              className="p-2.5 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-600 shadow-xs"
              aria-label={t("admin.states.refresh")}
            >
              <RefreshCw
                className={`w-4 h-4 ${loading ? "animate-spin text-red-600" : ""}`}
              />
            </button>
          </div>

          <div className="flex items-center justify-between border-t border-slate-100 pt-3">
            <div className="flex items-center gap-2">
              <Activity className="w-4 h-4 text-slate-500" />
              <span className="text-xs font-bold text-slate-700">
                {totalElements.toLocaleString()} {t("admin.audit.rows")}
                {hasFilter && ` ${t("admin.audit.matching_filter")}`}
              </span>
            </div>
            {hasFilter && (
              <button
                onClick={clearFilters}
                className="text-[11px] font-bold text-slate-500 hover:text-slate-900 flex items-center gap-1"
              >
                <X className="w-3 h-3" />
                {t("admin.audit.filter_clear")}
              </button>
            )}
          </div>
        </div>

        {loadError ? (
          <AdminErrorState message={loadError} onRetry={reload} />
        ) : (
          <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-100 border-b border-slate-200 text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                    <th className="py-3.5 px-4">{t("admin.audit.col_time")}</th>
                    <th className="py-3.5 px-4">{t("admin.audit.col_actor")}</th>
                    <th className="py-3.5 px-4">{t("admin.audit.col_action")}</th>
                    <th className="py-3.5 px-4">{t("admin.audit.col_target")}</th>
                    <th className="py-3.5 px-4">{t("admin.audit.col_ip")}</th>
                    <th className="py-3.5 px-4 text-right">
                      {t("admin.audit.col_details")}
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs">
                  {logs.length === 0 ? (
                    <tr>
                      <td colSpan={6}>
                        <AdminEmptyState
                          message={
                            hasFilter
                              ? t("admin.audit.empty_filtered")
                              : t("admin.audit.empty")
                          }
                        />
                      </td>
                    </tr>
                  ) : (
                    logs.map((log) => {
                      const critical = CRITICAL_ACTIONS.has(log.action);
                      return (
                        <tr
                          key={log.id}
                          className="hover:bg-slate-50 transition-colors"
                        >
                          <td className="py-3.5 px-4 text-slate-500 whitespace-nowrap">
                            {new Date(log.createdAt).toLocaleString()}
                          </td>
                          <td className="py-3.5 px-4">
                            <span className="font-bold text-slate-900">
                              {log.actorUsername || "—"}
                            </span>
                          </td>
                          <td className="py-3.5 px-4">
                            <span
                              className={`px-2 py-0.5 rounded-md font-bold text-[10px] border inline-flex items-center gap-1 ${
                                critical
                                  ? "bg-red-50 text-red-700 border-red-200"
                                  : "bg-slate-100 text-slate-700 border-slate-200"
                              }`}
                            >
                              {critical && <ShieldAlert className="w-3 h-3" />}
                              {labelFor(log.action)}
                            </span>
                          </td>
                          <td className="py-3.5 px-4">
                            {log.targetId ? (
                              <div className="flex flex-col leading-tight">
                                <span className="text-[10px] font-bold text-slate-400 uppercase">
                                  {log.targetType}
                                </span>
                                <span className="font-mono text-[10px] text-slate-600">
                                  {log.targetId.slice(0, 13)}
                                </span>
                              </div>
                            ) : (
                              <span className="text-slate-400">—</span>
                            )}
                          </td>
                          <td className="py-3.5 px-4 font-mono text-[10px] text-slate-500">
                            {log.ipAddress || "—"}
                          </td>
                          <td className="py-3.5 px-4 text-right">
                            {log.details ? (
                              <button
                                onClick={() => setSelected(log)}
                                className="px-3 py-1.5 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-700 font-bold text-[11px] transition-colors"
                              >
                                {t("admin.audit.view")}
                              </button>
                            ) : (
                              <span className="text-slate-400">—</span>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="p-4 border-t border-slate-200 flex items-center justify-between text-xs text-slate-500">
                <span className="font-medium">
                  {t("admin.states.page_of", {
                    page: page + 1,
                    total: totalPages,
                  })}
                </span>
                <div className="flex items-center gap-2">
                  <button
                    disabled={page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    className="p-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200"
                    aria-label={t("admin.states.prev_page")}
                  >
                    <ChevronLeft className="w-4 h-4 text-slate-700" />
                  </button>
                  <button
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage((p) => p + 1)}
                    className="p-1.5 rounded-lg bg-slate-100 border border-slate-200 disabled:opacity-40 hover:bg-slate-200"
                    aria-label={t("admin.states.next_page")}
                  >
                    <ChevronRight className="w-4 h-4 text-slate-700" />
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Hop thoai chi tiet */}
      {selected && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white border border-slate-200 rounded-2xl max-w-lg w-full p-6 flex flex-col gap-5 shadow-2xl relative">
            <button
              onClick={() => setSelected(null)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-700"
              aria-label={t("admin.states.close")}
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex flex-col gap-1">
              <h3 className="text-base font-extrabold text-slate-900">
                {labelFor(selected.action)}
              </h3>
              <span className="text-xs text-slate-500 font-medium">
                {new Date(selected.createdAt).toLocaleString()} ·{" "}
                {selected.actorUsername || t("admin.audit.unknown_actor")}
              </span>
            </div>

            <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 flex flex-col gap-1.5 text-xs">
              <Row
                label={t("admin.audit.action_code")}
                value={selected.action}
                mono
              />
              <Row
                label={t("admin.audit.target_type")}
                value={selected.targetType}
              />
              <Row
                label={t("admin.audit.target_id")}
                value={selected.targetId}
                mono
              />
              <Row
                label={t("admin.audit.actor_id")}
                value={selected.actorId}
                mono
              />
              <Row
                label={t("admin.audit.col_ip")}
                value={selected.ipAddress}
                mono
              />
            </div>

            <div className="flex flex-col gap-2">
              <span className="text-[11px] font-bold text-slate-700 uppercase tracking-wide">
                {t("admin.audit.detail_data")}
              </span>
              <pre className="bg-slate-900 text-slate-100 rounded-xl p-3.5 text-[11px] font-mono overflow-x-auto max-h-64 whitespace-pre-wrap break-all">
                {prettyDetails(selected.details)}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const Row: React.FC<{
  label: string;
  value: string | null;
  mono?: boolean;
}> = ({ label, value, mono }) => (
  <div className="flex justify-between gap-4">
    <span className="text-slate-500 font-medium shrink-0">{label}:</span>
    <span
      className={`text-right text-slate-900 font-bold break-all ${
        mono ? "font-mono text-[10px]" : ""
      }`}
    >
      {value || "—"}
    </span>
  </div>
);
