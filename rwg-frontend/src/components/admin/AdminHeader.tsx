"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import Link from "next/link";
import { Shield, Radio, Globe, WifiOff, Loader2, Bell } from "lucide-react";
import { useTranslation } from "@/context/LanguageContext";
import { adminFetch } from "@/lib/adminApi";
import {
  getAdminIdentity,
  primaryRole,
  roleLabel,
  type AdminIdentity,
} from "@/lib/adminIdentity";
import { ADMIN_URL_PREFIX } from "@/lib/constants";
import {
  playNotificationChime,
  primeNotificationSound,
} from "@/lib/notificationSound";

/**
 * Mot lenh rut dang cho duyet - khop AdminWithdrawalRowResponse cua backend.
 *
 * TRUOC DAY interface nay khop de nghi phe duyet 4 mat (/admin/approvals), trong khi
 * con so tren badge lai dem tu /admin/withdrawals/pending-count. Hai nguon khac nhau
 * nen badge hien so ma danh sach rong. Quy trinh 4 mat da bo, nen ca hai gio doc cung
 * mot nguon: lenh rut cho duyet.
 */
interface PendingWithdrawal {
  id: string;
  userId: string;
  username: string;
  amount: string;
  currency: string;
  status: string;
  bankCode: string | null;
  maskedLast4: string | null;
  holderName: string | null;
  createdAt: string;
}

interface AdminHeaderProps {
  title: string;
  subtitle?: string;
}

type HealthState = "checking" | "online" | "offline";

/** Nhịp kiểm tra backend. 30 giây đủ để phát hiện sự cố mà không làm ồn mạng. */
const HEALTH_INTERVAL_MS = 30_000;

export const AdminHeader: React.FC<AdminHeaderProps> = ({ title, subtitle }) => {
  const { locale, setLocale, t } = useTranslation();
  const [health, setHealth] = useState<HealthState>("checking");
  const [pendingCount, setPendingCount] = useState(0);
  const prevCountRef = useRef<number | null>(null);

  const [isOpen, setIsOpen] = useState(false);
  const [pendingList, setPendingList] = useState<PendingWithdrawal[]>([]);
  const [listLoading, setListLoading] = useState(false);

  const fetchPendingList = useCallback(async () => {
    try {
      setListLoading(true);
      const res = await adminFetch<{ content: PendingWithdrawal[] }>(
        "/admin/withdrawals?status=PENDING&page=0&size=5"
      );
      setPendingList(res.content || []);
    } catch {
      // Bo qua loi ket noi nen
    } finally {
      setListLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isOpen) {
      void fetchPendingList();
      const listTimer = setInterval(fetchPendingList, 10000); // 10 giây
      return () => clearInterval(listTimer);
    }
  }, [isOpen, fetchPendingList]);

  const fetchPendingCount = useCallback(async () => {
    const token = typeof window !== "undefined" ? localStorage.getItem("rwg_admin_token") : null;
    if (!token) return;

    try {
      const res = await adminFetch<{ pendingWithdrawals: number }>(
        "/admin/withdrawals/pending-count"
      );
      const newCount = res.pendingWithdrawals || 0;

      if (prevCountRef.current !== null && newCount > prevCountRef.current) {
        playNotificationChime();
      }

      prevCountRef.current = newCount;
      setPendingCount(newCount);
    } catch {
      // Bỏ qua lỗi polling nền
    }
  }, []);

  useEffect(() => {
    void fetchPendingCount();
    const timer = setInterval(fetchPendingCount, 20000); // 20 giây
    return () => clearInterval(timer);
  }, [fetchPendingCount]);

  // Mở khoá âm thanh sau tương tác đầu tiên của nhân sự.
  //
  // Đặt ở AdminHeader vì component này có mặt trên MỌI trang quản trị, nên chỉ cần một
  // chỗ là bao trọn cả tiếng chuông lệnh rút chờ duyệt lẫn tiếng chuông tin nhắn hỗ trợ.
  useEffect(() => primeNotificationSound(), []);

  // Danh tinh PHAI doc sau khi mount, khong doc trong useState khoi tao.
  //
  // getAdminIdentity() doc localStorage - thu khong ton tai luc server render.
  // Doc ngay o lan render dau tien khien HTML tu server in "Chua xac dinh" con
  // client in "Quan tri toi cao", va React bao hydration mismatch.
  //
  // Dat trong effect voi co huy chu khong goi setState thang trong than effect:
  // setState dong bo trong effect gay chuoi render lien tiep va bi lint chan.
  const [identity, setIdentity] = useState<AdminIdentity | null>(null);

  useEffect(() => {
    let cancelled = false;

    // Hoan sang microtask: doc localStorage la dong bo nen neu goi thang o than
    // effect thi React coi la setState dong bo trong effect.
    void Promise.resolve().then(() => {
      if (!cancelled) setIdentity(getAdminIdentity());
    });

    return () => {
      cancelled = true;
    };
  }, []);

  const role = identity ? primaryRole(identity) : null;

  useEffect(() => {
    let alive = true;

    const check = async () => {
      try {
        // GET /admin/health tra {area, status}. Chi can khong nem loi la con song.
        await adminFetch("/admin/health");
        if (alive) setHealth("online");
      } catch {
        // KHONG giu nguyen trang thai cu: badge xanh khi backend da sap la thong
        // tin sai nguy hiem nhat, vi day dung la luc nguoi van hanh can biet.
        if (alive) setHealth("offline");
      }
    };

    check();
    const timer = setInterval(check, HEALTH_INTERVAL_MS);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, []);

  const languages = [
    { code: "vi", name: "Tiếng Việt" },
    { code: "en", name: "English" },
    { code: "zh", name: "中文" },
  ];

  return (
    <header className="w-full bg-white border-b border-slate-200 px-6 py-4 flex items-center justify-between sticky top-0 z-40 shadow-xs">
      <div className="flex flex-col">
        <h1 className="text-lg font-extrabold text-slate-900 tracking-tight leading-tight">
          {title}
        </h1>
        {subtitle && (
          <p className="text-xs text-slate-500 font-medium">{subtitle}</p>
        )}
      </div>

      <div className="flex items-center gap-3">
        {/* Chon ngon ngu */}
        <div className="bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 flex items-center gap-1.5 shadow-xs">
          <Globe className="w-3.5 h-3.5 text-slate-500" />
          <label htmlFor="admin-locale" className="sr-only">
            {t("admin.header.language")}
          </label>
          <select
            id="admin-locale"
            value={locale}
            onChange={(e) => setLocale(e.target.value)}
            className="bg-transparent text-xs font-bold text-slate-800 outline-none cursor-pointer"
          >
            {languages.map((l) => (
              <option
                key={l.code}
                value={l.code}
                className="bg-white text-slate-900 font-semibold"
              >
                {l.name}
              </option>
            ))}
          </select>
        </div>

        {/* Trang thai backend THAT, khong phai chu co dinh */}
        <HealthBadge state={health} />

        {/* Vai tro doc tu claim roles trong token.
            Chi render khi da doc duoc danh tinh: neu render truoc, badge se nhay
            tu "Chua xac dinh" sang vai tro that ngay truoc mat nguoi dung. */}
        {/* Chuông thông báo yêu cầu phê duyệt rút tiền */}
        {isOpen && (
          <div
            className="fixed inset-0 z-40 cursor-default"
            onClick={() => setIsOpen(false)}
          />
        )}
        <div className="relative z-50">
          <button
            type="button"
            onClick={() => setIsOpen(!isOpen)}
            className="relative p-2.5 rounded-lg hover:bg-slate-100 text-slate-500 hover:text-red-600 transition-all flex items-center justify-center shrink-0 cursor-pointer border border-slate-200 bg-slate-50 shadow-xs"
            title="Yêu cầu phê duyệt rút tiền"
          >
            <Bell className="w-4 h-4" />
            {pendingCount > 0 ? (
              <span className="absolute -top-1 -right-1 flex h-4 min-w-[16px] px-1 items-center justify-center rounded-full bg-red-600 text-[9px] font-black text-white ring-2 ring-white animate-pulse">
                {pendingCount}
              </span>
            ) : null}
          </button>

          {isOpen && (
            <div className="absolute right-0 mt-2 w-80 bg-white rounded-xl shadow-xl border border-slate-200 py-2 text-slate-800 text-left">
              <div className="px-4 py-2 border-b border-slate-100 flex items-center justify-between">
                <span className="font-bold text-xs text-slate-700">Yêu cầu chờ duyệt ({pendingCount})</span>
                {pendingCount > 0 && (
                  <span className="h-2 w-2 rounded-full bg-red-500 animate-ping" />
                )}
              </div>

              <div className="max-h-64 overflow-y-auto">
                {listLoading && pendingList.length === 0 ? (
                  <div className="flex items-center justify-center py-6 text-slate-400 text-xs gap-2">
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    Đang tải...
                  </div>
                ) : pendingList.length === 0 ? (
                  <div className="py-8 text-center text-slate-400 text-[11px]">
                    Không có yêu cầu nào chờ duyệt
                  </div>
                ) : (
                  pendingList.map((item) => (
                    <Link
                      key={item.id}
                      href={`${ADMIN_URL_PREFIX}/payments`}
                      onClick={() => setIsOpen(false)}
                      className="block px-4 py-3 hover:bg-slate-50 border-b border-slate-50 last:border-0 transition-colors text-left"
                    >
                      <div className="flex items-start gap-2.5">
                        <div className="p-1.5 rounded-lg bg-red-50 text-red-600 shrink-0 mt-0.5 border border-red-100">
                          <Bell className="w-3.5 h-3.5" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="flex justify-between items-baseline gap-2">
                            <p className="font-bold text-[11px] text-slate-800 truncate">
                              Rút tiền — {item.username}
                            </p>
                            <span className="text-[9px] text-slate-400 shrink-0">
                              {new Date(item.createdAt).toLocaleTimeString("vi-VN", {
                                hour: "2-digit",
                                minute: "2-digit",
                              })}
                            </span>
                          </div>
                          <p className="text-[12px] font-black text-red-600 mt-0.5">
                            ${parseFloat(item.amount).toLocaleString("en-US", {
                              minimumFractionDigits: 2,
                              maximumFractionDigits: 2,
                            })}
                          </p>
                          {item.maskedLast4 && (
                            <p className="text-[10px] text-slate-500 truncate mt-0.5">
                              {item.bankCode} ••••{item.maskedLast4}
                            </p>
                          )}
                        </div>
                      </div>
                    </Link>
                  ))
                )}
              </div>

              <div className="px-3 pt-2 pb-1 border-t border-slate-100">
                <Link
                  href={`${ADMIN_URL_PREFIX}/payments`}
                  onClick={() => setIsOpen(false)}
                  className="block w-full py-1.5 text-center text-[10px] font-bold text-[#1e5fc4] hover:bg-[#1e5fc4]/5 rounded-lg transition-colors"
                >
                  Xem tất cả hàng chờ
                </Link>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};

const HealthBadge: React.FC<{ state: HealthState }> = ({ state }) => {
  const { t } = useTranslation();

  if (state === "checking") {
    return (
      <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-1.5 flex items-center gap-2">
        <Loader2 className="w-3.5 h-3.5 text-slate-400 animate-spin" />
        <span className="text-[11px] font-bold text-slate-500">
          {t("admin.header.health_checking")}
        </span>
      </div>
    );
  }

  if (state === "offline") {
    return (
      <div
        className="bg-red-50 border border-red-300 rounded-lg px-3 py-1.5 flex items-center gap-2"
        role="alert"
      >
        <WifiOff className="w-3.5 h-3.5 text-red-600" />
        <span className="text-[11px] font-bold text-red-700">
          {t("admin.header.health_offline")}
        </span>
      </div>
    );
  }

  return (
    <div className="bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-1.5 flex items-center gap-2">
      <Radio className="w-3.5 h-3.5 text-emerald-600 animate-pulse" />
      <span className="text-[11px] font-bold text-emerald-700">
        {t("admin.header.health_online")}
      </span>
    </div>
  );
};
