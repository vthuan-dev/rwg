"use client";

import React, { useState, useEffect, useCallback } from "react";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  Search,
  Filter,
  RefreshCw,
  Shield,
  Lock,
  Ban,
  CheckCircle2,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Wallet,
  Loader2,
  ScrollText,
  BadgeCheck,
  Landmark,
  ChevronDown,
  Percent,
} from "lucide-react";
import {
  adminFetch,
  adminOverrideUserPassword,
  adminOverrideUserWithdrawalPassword,
} from "@/lib/adminApi";
import { formatMoney } from "@/lib/money";
import { useTranslation } from "@/context/LanguageContext";
import { WalletAdjustPanel } from "@/components/admin/WalletAdjustPanel";
import { UserGameOddsPanel } from "@/components/admin/UserGameOddsPanel";
import { WalletLedgerPanel } from "@/components/admin/WalletLedgerPanel";
import { PayoutMethodsPanel } from "@/components/admin/PayoutMethodsPanel";
import { AdminErrorState } from "@/components/admin/AdminStates";
import { AdminModal } from "@/components/admin/AdminModal";
import {
  canAdjustWallet,
  canManageUsers,
} from "@/lib/adminIdentity";

/**
 * Một dòng trong bảng người dùng — khớp AdminUserListItemResponse của backend.
 *
 * `balance` là String vì backend trả BigDecimal.toPlainString. KHÔNG đổi sang number:
 * số thực dấu phẩy động làm tròn sai ở các số lẻ.
 */
interface UserItem {
  id: string;
  username: string;
  /** null với tài khoản người chơi — API đăng ký không nhận email. */
  email: string | null;
  role: "PLAYER" | "SUPPORT" | "FINANCE" | "RISK" | "ADMIN";
  status: "ACTIVE" | "LOCKED" | "BANNED";
  createdAt: string;
  /** Luôn có giá trị: người dùng chưa có ví được backend trả "0.00" chứ không null. */
  balance: string;
  currency: string;
}

/**
 * Chi tiết user — khớp AdminUserDetailResponse của backend.
 *
 * Tiền là String vì backend trả BigDecimal.toPlainString. KHÔNG đổi sang number:
 * số thực dấu phẩy động làm tròn sai ở các số lẻ.
 */
interface UserDetail {
  id: string;
  username: string;
  /** null với tài khoản người chơi — API đăng ký không nhận email. */
  email: string | null;
  role: string;
  status: string;
  kycLevel: string;
  hasWithdrawalPassword: boolean;
  locale: string;
  lastLoginAt: string | null;
  createdAt: string;
  walletBalance: string;
  currency: string;
  totalDeposited: string;
  totalWithdrawn: string;
  pendingWithdrawals: number;
}

/** Khung phân trang backend trả về (com.rwg.common.PageResponse). */
interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/**
 * Modal chi tiết chỉ còn HAI tab, chia theo câu hỏi người vận hành đang trả lời:
 *   finance : tiền của người này đi đâu, đến từ đâu, trả về tài khoản nào
 *   account : người này là ai, được phép làm gì
 *
 * Trước đây có 5 tab. Chia đều bằng {@code flex-1} thì nhãn bị bóp đến mức không
 * đọc được, mà cho cuộn ngang thì có tab nấp ngoài khung nên người dùng không biết
 * nó tồn tại. Việc đoán "thứ mình cần nằm ở tab nào" giữa 5 lựa chọn cũng tốn
 * thọi gian hơn là mở một mục gấp trong hai nhóm rõ nghĩa.
 */
type ModalTab = "finance" | "account" | "odds";

/**
 * Mục có thể gấp/mở trong modal.
 *
 * {@code null} = đóng hết. Mỗi lúc chỉ MỘT mục được mở, không phải vì thẩm mỹ mà
 * vì mỗi panel tự gọi API riêng của nó khi mount — mở cả ba cùng lúc là ba request
 * cho một lần mở modal.
 */
type ModalSectionId = "payout" | "adjust" | "ledger" | "permission";

/**
 * Mục gấp trong modal: hàng tiêu đề bấm được, nội dung chỉ mount khi MỞ.
 *
 * VÌ SAO PHẢI MOUNT CÓ ĐIỀU KIỆN chứ không ẩn bằng CSS: {@code WalletLedgerPanel} và
 * {@code PayoutMethodsPanel} đều gọi API trong {@code useEffect} lúc mount. Ẩn bằng
 * {@code hidden} vẫn mount, tức vẫn gọi API — và với endpoint reveal thì còn tệ hơn
 * nữa vì nó ghi nhật ký.
 */
const ModalSection: React.FC<{
  id: ModalSectionId;
  label: string;
  icon: typeof Wallet;
  open: boolean;
  onToggle: (id: ModalSectionId) => void;
  children: React.ReactNode;
}> = ({ id, label, icon: Icon, open, onToggle, children }) => (
  <div
    className={`rounded-xl border transition-colors ${
      open ? "bg-white border-slate-300" : "bg-slate-50 border-slate-200"
    }`}
  >
    <button
      id={`user-section-${id}`}
      onClick={() => onToggle(id)}
      aria-expanded={open}
      className="w-full flex items-center justify-between gap-2 px-3.5 py-3 text-left"
    >
      <span className="flex items-center gap-2 min-w-0">
        <Icon className={`w-4 h-4 shrink-0 ${open ? "text-slate-900" : "text-slate-500"}`} />
        <span
          className={`text-[11px] font-extrabold uppercase tracking-wide truncate ${
            open ? "text-slate-900" : "text-slate-600"
          }`}
        >
          {label}
        </span>
      </span>
      <ChevronDown
        className={`w-4 h-4 shrink-0 text-slate-400 transition-transform ${
          open ? "rotate-180" : ""
        }`}
      />
    </button>

    {open && <div className="px-3.5 pb-3.5 pt-0.5 border-t border-slate-200">{children}</div>}
  </div>
);

/** Các mức KYC backend nhận (UpdateKycLevelRequest). Nhãn tra trong file dịch. */
const KYC_LEVELS = ["NONE", "LEVEL_1", "LEVEL_2", "LEVEL_3"];

export default function AdminUsersPage() {
  const { t } = useTranslation();

  // Quyen quyet dinh tab nao hien ra. An han tab khong co quyen thay vi de bam
  // roi nhan 403 khong hieu vi sao.
  const canAdjust = canAdjustWallet();
  const canManage = canManageUsers();

  /** Nhãn mức KYC tra trong file dịch; mức lạ hiện nguyên mã. */
  const kycLabel = (level: string): string => {
    const key = `admin.kyc_levels.${level}`;
    const label = t(key);
    return label === key ? level : label;
  };

  const [users, setUsers] = useState<UserItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [roleFilter, setRoleFilter] = useState("");

  const [selectedUser, setSelectedUser] = useState<UserItem | null>(null);
  const [detail, setDetail] = useState<UserDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [modalTab, setModalTab] = useState<ModalTab>("finance");

  /**
   * Mục đang mở trong tab hiện tại. Mặc định mở sẵn "payout".
   *
   * Đây là thứ người vận hành mở modal để xem nhiều nhất khi xử lý lệnh rút, nên
   * bắt họ bấm thêm một lần cho việc thường gặp nhất là vô ích.
   */
  const [openSection, setOpenSection] = useState<ModalSectionId | null>("payout");

  /**
   * Bấm vào mục đang mở thì ĐÓNG nó lại.
   *
   * Không cho đóng sẽ biến hàng tiêu đề thành nút chỉ có tác dụng một lần, và người
   * dùng mất cách thu gọn một sổ giao dịch dài để xem phần bên dưới.
   */
  const toggleSection = (id: ModalSectionId) =>
    setOpenSection((current) => (current === id ? null : id));

  // Duyet KYC nam trong tab tong quan: day la thuoc tinh cua tai khoan, khac
  // nhom thao tac can thiep vao quyen truy cap.
  const [kycValue, setKycValue] = useState("");
  const [kycSaving, setKycSaving] = useState(false);
  const [kycError, setKycError] = useState("");
  const [kycOk, setKycOk] = useState(false);

  // Admin đổi mật khẩu cấp 1 và cấp 2
  const [overridePassword, setOverridePassword] = useState("");
  const [overridePasswordSaving, setOverridePasswordSaving] = useState(false);
  const [overridePasswordError, setOverridePasswordError] = useState("");
  const [overridePasswordOk, setOverridePasswordOk] = useState(false);

  const [overridePin, setOverridePin] = useState("");
  const [overridePinSaving, setOverridePinSaving] = useState(false);
  const [overridePinError, setOverridePinError] = useState("");
  const [overridePinOk, setOverridePinOk] = useState(false);

  const [statusModalUser, setStatusModalUser] = useState<UserItem | null>(null);
  const [newStatus, setNewStatus] = useState<"ACTIVE" | "LOCKED" | "BANNED">("LOCKED");
  const [reason, setReason] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState("");

  // Xóa tài khoản
  const [deletePin, setDeletePin] = useState("");
  const [deleteSaving, setDeleteSaving] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [deleteOk, setDeleteOk] = useState(false);

  /**
   * Tải danh sách user theo trang + bộ lọc hiện tại.
   *
   * Bọc {@code useCallback} để hàm không đổi danh tính mỗi lần render — nhờ vậy
   * {@code useEffect} bên dưới khai được đủ phụ thuộc mà không chạy lại vô tận.
   * Không bọc thì phải bỏ hàm khỏi mảng phụ thuộc, và khi đó hàm bị đóng băng ở
   * giá trị {@code search} của lần render đầu.
   *
   * Trả dữ liệu về thay vì tự đặt state: `setState` gọi đồng bộ trong thân effect gây
   * chuỗi render liên tiếp và luật lint của dự án chặn.
   */
  const fetchUsers = useCallback(async (): Promise<PageResponse<UserItem> | null> => {
    try {
      let query = `/admin/users?page=${page}&size=10`;
      // Backend nhan tham so `keyword` (AdminUserController.search), khong phai `search`.
      if (search) query += `&keyword=${encodeURIComponent(search)}`;
      if (statusFilter) query += `&status=${statusFilter}`;
      if (roleFilter) query += `&role=${roleFilter}`;

      const data = await adminFetch<PageResponse<UserItem>>(query);
      setLoadError("");
      return data;
    } catch (err) {
      // Bang trong de bi hieu la "khong co nguoi dung nao"; phai noi ro la loi tai.
      setLoadError((err as Error).message);
      return null;
    }
  }, [page, search, statusFilter, roleFilter]);

  /** Đưa kết quả vào state. Dùng chung cho lần tải đầu và các lần tải lại. */
  const applyResult = useCallback((data: PageResponse<UserItem> | null) => {
    setUsers(data?.content ?? []);
    setTotalPages(data?.totalPages || 1);
  }, []);

  /** Tải lại từ nút bấm hoặc sau khi ghi. Gọi ngoài effect nên đặt state trực tiếp là được. */
  const reload = useCallback(async () => {
    setLoading(true);
    applyResult(await fetchUsers());
    setLoading(false);
  }, [fetchUsers, applyResult]);

  /**
   * Tải chi tiết user.
   *
   * Số dư BẮT BUỘC lấy từ endpoint này: GET /admin/users trả UserResponse, DTO đó
   * KHÔNG có trường số dư nên trước đây modal luôn hiện 0 với mọi user.
   */
  const openDetail = async (u: UserItem) => {
    setSelectedUser(u);
    setModalTab("finance");
    setOpenSection("payout");
    setDetail(null);
    setDetailError("");
    setKycError("");
    setKycOk(false);
    setDeletePin("");
    setDeleteError("");
    setDeleteOk(false);
    setDetailLoading(true);
    try {
      const data = await adminFetch<UserDetail>(`/admin/users/${u.id}`);
      setDetail(data);
      setKycValue(data.kycLevel);
    } catch (err) {
      setDetailError((err as Error).message);
    } finally {
      setDetailLoading(false);
    }
  };

  /** Duyệt KYC. Backend nhận {kycLevel, reason}, reason tuỳ chọn. */
  const saveKyc = async () => {
    if (!detail || kycValue === detail.kycLevel) return;
    setKycSaving(true);
    setKycError("");
    setKycOk(false);
    try {
      await adminFetch(`/admin/users/${detail.id}/kyc`, {
        method: "PATCH",
        body: JSON.stringify({ kycLevel: kycValue, reason: null }),
      });
      setKycOk(true);
      refreshDetail();
    } catch (err) {
      setKycError((err as Error).message);
    } finally {
      setKycSaving(false);
    }
  };

  const saveOverridePassword = async () => {
    if (!detail || !overridePassword.trim()) return;
    setOverridePasswordSaving(true);
    setOverridePasswordError("");
    setOverridePasswordOk(false);
    try {
      await adminOverrideUserPassword(detail.id, overridePassword.trim());
      setOverridePasswordOk(true);
      setOverridePassword("");
    } catch (err: unknown) {
      setOverridePasswordError((err as Error).message || "Không thể đổi mật khẩu đăng nhập");
    } finally {
      setOverridePasswordSaving(false);
    }
  };

  const saveOverridePin = async () => {
    if (!detail || !overridePin.trim()) return;
    setOverridePinSaving(true);
    setOverridePinError("");
    setOverridePinOk(false);
    try {
      await adminOverrideUserWithdrawalPassword(detail.id, overridePin.trim());
      setOverridePinOk(true);
      setOverridePin("");
      setDetail((prev) => (prev ? { ...prev, hasWithdrawalPassword: true } : null));
    } catch (err: unknown) {
      setOverridePinError((err as Error).message || "Không thể đổi mật khẩu rút tiền");
    } finally {
      setOverridePinSaving(false);
    }
  };

  const handleDeleteUser = async () => {
    if (!detail || !deletePin.trim()) return;
    if (!window.confirm("Bạn có chắc chắn muốn xóa tài khoản này không? Hành động này không thể hoàn tác!")) return;
    setDeleteSaving(true);
    setDeleteError("");
    setDeleteOk(false);
    try {
      await adminFetch(`/admin/users/${detail.id}`, {
        method: "DELETE",
        body: JSON.stringify({ confirmPin: deletePin.trim() }),
      });
      setDeleteOk(true);
      setDeletePin("");
      setTimeout(() => {
        setSelectedUser(null);
        void reload();
      }, 1500);
    } catch (err: unknown) {
      setDeleteError((err as Error).message || "Không thể xóa tài khoản");
    } finally {
      setDeleteSaving(false);
    }
  };

  /** Tải lại số dư sau khi điều chỉnh, đồng thời làm mới danh sách. */
  const refreshDetail = async () => {
    if (!selectedUser) return;
    try {
      const data = await adminFetch<UserDetail>(`/admin/users/${selectedUser.id}`);
      setDetail(data);
    } catch {
      // giữ số cũ, người dùng có thể mở lại modal
    }
    void reload();
  };

  // Tai lai khi doi trang hoac doi bo loc. `search` KHONG nam trong day: nguoi dung
  // dang go tung ky tu, moi ky tu mot request la vo ich - viec tim chay khi submit.
  //
  // Van giu eslint-disable: `fetchUsers` phu thuoc `search`, ma dua no vao mang phu thuoc
  // se khien moi ky tu vua go chay mot request.
  useEffect(() => {
    // Co huy: nguoi van hanh co the doi trang truoc khi request xong, va ghi state vao
    // component da thao la mot canh bao React kem ro bo nho.
    let cancelled = false;

    (async () => {
      // Dat state ben trong ham async, khong o than effect - xem ly do o tren.
      setLoading(true);
      const data = await fetchUsers();
      if (cancelled) return;
      applyResult(data);
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, statusFilter, roleFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    void reload();
  };

  const handleUpdateStatus = async () => {
    if (!statusModalUser) return;
    if (!reason.trim()) {
      setActionError(t("admin.users.status.reason_required"));
      return;
    }

    setActionLoading(true);
    setActionError("");

    try {
      await adminFetch(`/admin/users/${statusModalUser.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({
          status: newStatus,
          reason: reason.trim(),
        }),
      });

      setStatusModalUser(null);
      setReason("");
      void reload();
    } catch (err) {
      setActionError((err as Error).message || t("admin.users.status.failed"));
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="flex flex-col w-full min-h-screen bg-slate-50">
      <AdminHeader
        title={t("admin.users.title")}
        subtitle={t("admin.users.subtitle")}
      />

      <div className="p-6 flex flex-col gap-6">
        {/* Filters & Search Bar Light */}
        <div className="bg-white border border-slate-200 rounded-2xl p-4 flex flex-wrap items-center justify-between gap-4 shadow-sm">
          <form onSubmit={handleSearchSubmit} className="flex items-center gap-2 flex-1 max-w-md">
            <div className="relative flex items-center w-full">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5" />
              <input
                type="text"
                placeholder={t("admin.users.search_placeholder")}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl py-2 pl-10 pr-4 text-xs text-slate-900 placeholder-slate-400 outline-none font-medium"
              />
            </div>
            <button
              type="submit"
              className="bg-red-600 hover:bg-red-700 text-white font-bold px-4 py-2 rounded-xl text-xs transition-colors shadow-xs"
            >
              {t("admin.users.search")}
            </button>
          </form>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5 bg-slate-50 border border-slate-200 rounded-xl px-3 py-1.5">
              <Filter className="w-3.5 h-3.5 text-slate-500" />
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="bg-transparent text-xs font-semibold text-slate-700 outline-none cursor-pointer"
              >
                <option value="" className="bg-white text-slate-900">{t("admin.users.all_status")}</option>
                <option value="ACTIVE" className="bg-white text-slate-900">ACTIVE</option>
                <option value="LOCKED" className="bg-white text-slate-900">LOCKED</option>
                <option value="BANNED" className="bg-white text-slate-900">BANNED</option>
              </select>
            </div>

            <div className="flex items-center gap-1.5 bg-slate-50 border border-slate-200 rounded-xl px-3 py-1.5">
              <Shield className="w-3.5 h-3.5 text-slate-500" />
              <select
                value={roleFilter}
                onChange={(e) => setRoleFilter(e.target.value)}
                className="bg-transparent text-xs font-semibold text-slate-700 outline-none cursor-pointer"
              >
                <option value="" className="bg-white text-slate-900">{t("admin.users.all_roles")}</option>
                <option value="PLAYER" className="bg-white text-slate-900">PLAYER</option>
                <option value="SUPPORT" className="bg-white text-slate-900">SUPPORT</option>
                <option value="FINANCE" className="bg-white text-slate-900">FINANCE</option>
                <option value="RISK" className="bg-white text-slate-900">RISK</option>
                <option value="ADMIN" className="bg-white text-slate-900">ADMIN</option>
              </select>
            </div>

            <button
              onClick={reload}
              className="p-2 rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-600 transition-colors shadow-xs"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin text-red-600" : ""}`} />
            </button>
          </div>
        </div>

        {loadError ? (
          <AdminErrorState message={loadError} onRetry={reload} />
        ) : (
        <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-100 border-b border-slate-200 text-[11px] font-extrabold text-slate-600 uppercase tracking-wider">
                  <th className="py-3.5 px-4">{t("admin.users.table_username")}</th>
                  <th className="py-3.5 px-4">{t("admin.users.table_email")}</th>
                  <th className="py-3.5 px-4">{t("admin.users.table_role")}</th>
                  <th className="py-3.5 px-4">{t("admin.users.table_status")}</th>
                  <th className="py-3.5 px-4 text-right">{t("admin.users.table_balance")}</th>
                  <th className="py-3.5 px-4">{t("admin.users.table_created")}</th>
                  <th className="py-3.5 px-4 text-right">{t("admin.users.table_action")}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {users.length === 0 && !loading && (
                  <tr>
                    <td
                      colSpan={7}
                      className="py-12 text-center text-xs text-slate-500 font-medium"
                    >
                      {t("admin.users.empty_filtered")}
                    </td>
                  </tr>
                )}
                {users.map((u) => (
                  <tr key={u.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3.5 px-4 font-bold text-slate-900 flex items-center gap-2">
                      <div className="w-7 h-7 rounded-full bg-red-100 border border-red-200 flex items-center justify-center text-red-700 font-extrabold text-xs">
                        {u.username.substring(0, 1).toUpperCase()}
                      </div>
                      <span className="font-extrabold text-slate-900">{u.username}</span>
                    </td>
                    <td className="py-3.5 px-4 font-medium text-slate-700">
                      {u.email ?? <span className="text-slate-300">&mdash;</span>}
                    </td>
                    <td className="py-3.5 px-4">
                      <span className="px-2.5 py-0.5 rounded-md font-bold text-[10px] bg-slate-100 text-slate-700 border border-slate-200">
                        {u.role}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      {u.status === "ACTIVE" && (
                        <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1 w-fit">
                          <CheckCircle2 className="w-3 h-3 text-emerald-600" /> ACTIVE
                        </span>
                      )}
                      {u.status === "LOCKED" && (
                        <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-amber-50 text-amber-700 border border-amber-200 flex items-center gap-1 w-fit">
                          <Lock className="w-3 h-3 text-amber-600" /> LOCKED
                        </span>
                      )}
                      {u.status === "BANNED" && (
                        <span className="px-2.5 py-1 rounded-full font-bold text-[10px] bg-red-50 text-red-700 border border-red-200 flex items-center gap-1 w-fit">
                          <Ban className="w-3 h-3 text-red-600" /> BANNED
                        </span>
                      )}
                    </td>
                    {/* Số dư đặt sau trạng thái: đây là hai thứ người vận hành đọc cùng
                        nhau khi quyết định mở tài khoản nào.
                        `tabular-nums` để các chữ số thẳng cột theo chiều dọc, đọc nhanh hơn
                        khi so nhiều dòng. */}
                    <td className="py-3.5 px-4 text-right">
                      <span
                        className={`font-bold tabular-nums ${
                          Number(u.balance) > 0 ? "text-slate-900" : "text-slate-400"
                        }`}
                      >
                        {formatMoney(u.balance)}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-slate-500 font-medium">
                      {new Date(u.createdAt).toLocaleDateString("vi-VN")}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => openDetail(u)}
                          className="px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 border border-slate-200 text-xs font-bold text-slate-700 transition-colors"
                        >
                          {t("admin.users.btn_detail")}
                        </button>
                        <button
                          onClick={() => {
                            setStatusModalUser(u);
                            setNewStatus(u.status === "ACTIVE" ? "LOCKED" : "ACTIVE");
                          }}
                          className="px-2.5 py-1 rounded-lg bg-red-50 hover:bg-red-100 border border-red-200 text-xs font-bold text-red-700 transition-colors"
                        >
                          {t("admin.users.btn_change_status")}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="p-4 border-t border-slate-200 flex items-center justify-between text-xs text-slate-600 font-medium bg-white">
            <span>
              {t("admin.states.page_of", { page: page + 1, total: totalPages })}
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
        </div>
        )}
      </div>

      {/* User Detail Modal */}
      <AdminModal
        isOpen={!!selectedUser}
        onClose={() => setSelectedUser(null)}
        maxWidthClass="max-w-lg"
        title={
          selectedUser && (
            <div className="flex items-start justify-between gap-3 w-full">
              {/* `items-center` chứ không `items-start`: khối chữ giờ chỉ còn một dòng nên
                  căn theo mép trên sẽ để avatar 40px thò hẳn xuống dưới phần chữ. */}
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-10 h-10 rounded-xl bg-red-100 border border-red-200 flex items-center justify-center text-red-700 font-black text-base shrink-0">
                  {selectedUser.username.substring(0, 1).toUpperCase()}
                </div>
                {/* Chỉ còn tên tài khoản. Email đã gỡ: API đăng ký không thu email nên với
                    mọi tài khoản người chơi ô này luôn là một dấu gạch, chiếm chỗ mà không
                    mang thông tin. Cột email ở bảng danh sách vẫn giữ. */}
                <div className="flex flex-col min-w-0">
                  <span className="text-[9px] uppercase font-black tracking-widest text-slate-400 leading-none mb-1">
                    Tên tài khoản
                  </span>
                  <h3 className="text-sm font-extrabold text-slate-900 truncate leading-none">
                    {selectedUser.username}
                  </h3>
                </div>
              </div>

              {detail && (
                <div className="flex flex-col items-end shrink-0">
                  <span className="text-[9px] uppercase font-bold tracking-wider text-slate-400">
                    {t("admin.users.wallet.balance")}
                  </span>
                  <span className="text-lg font-extrabold text-slate-900 tabular-nums leading-tight">
                    {formatMoney(detail.walletBalance)}
                    <span className="text-[10px] font-bold text-slate-400 ml-1">
                      {detail.currency}
                    </span>
                  </span>
                </div>
              )}
            </div>
          )
        }
      >
        {selectedUser && (
          <div className="flex flex-col gap-5">
            {/* CHI 2 TAB, chia theo cau hoi nguoi van hanh dang tra loi:
                  - Tai chinh: tien cua nguoi nay di dau, den tu dau.
                  - Tai khoan: nguoi nay la ai, duoc lam gi.
                Truoc day 5 tab chia deu bang flex-1 bop nhan den muc khong doc duoc,
                va viec doan tab nao chua thu can tim ton thoi gian hon la cuon. */}
            <div className="flex items-center gap-1 p-1 bg-slate-100 rounded-xl">
              {(
                [
                  ["finance", t("admin.users.tab_finance"), Wallet],
                  ["account", t("admin.users.tab_account"), Shield],
                  ["odds", t("admin.users.tab_odds"), Percent],
                ] as Array<[ModalTab, string, typeof Wallet]>
              ).map(([key, label, Icon]) => (
                <button
                  key={key}
                  onClick={() => setModalTab(key)}
                  disabled={!detail}
                  className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-bold transition-all disabled:opacity-40 disabled:cursor-not-allowed ${
                    modalTab === key
                      ? "bg-white text-slate-900 shadow-xs"
                      : "text-slate-500 hover:text-slate-700"
                  }`}
                >
                  <Icon className="w-3.5 h-3.5 shrink-0" />
                  {label}
                </button>
              ))}
            </div>

            {detailLoading && (
              <div className="flex items-center justify-center gap-2 py-8 text-xs text-slate-500 font-semibold">
                <Loader2 className="w-4 h-4 animate-spin" />
                {t("admin.states.loading")}
              </div>
            )}

            {detailError && (
              <div className="flex items-start gap-3 p-3.5 bg-red-50 border border-red-200 rounded-xl">
                <AlertTriangle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
                <span className="text-xs text-red-700 font-semibold">{detailError}</span>
              </div>
            )}

            {/* ===== TAB 1: TAI CHINH ===== */}
            {!detailLoading && detail && modalTab === "finance" && (
              <div className="flex flex-col gap-3">
                <div className="grid grid-cols-2 gap-3">
                  <div className="flex flex-col gap-0.5 p-3 bg-slate-50 border border-slate-200 rounded-xl">
                    <span className="text-slate-500 text-[10px] uppercase font-bold">
                      {t("admin.users.wallet.total_deposited")}
                    </span>
                    <span className="text-slate-900 font-extrabold text-sm tabular-nums">
                      {formatMoney(detail.totalDeposited)}
                    </span>
                  </div>
                  <div className="flex flex-col gap-0.5 p-3 bg-slate-50 border border-slate-200 rounded-xl">
                    <span className="text-slate-500 text-[10px] uppercase font-bold">
                      {t("admin.users.wallet.total_withdrawn")}
                    </span>
                    <span className="text-slate-900 font-extrabold text-sm tabular-nums">
                      {formatMoney(detail.totalWithdrawn)}
                    </span>
                  </div>
                </div>

                {/* Lenh rut cho duyet anh huong den viec co nen tru tien hay khong,
                    nen lam noi bat khi khac 0. */}
                {detail.pendingWithdrawals > 0 && (
                  <div className="flex items-center gap-2 px-3.5 py-3 bg-amber-50 border border-amber-300 rounded-xl">
                    <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
                    <span className="text-xs font-bold text-amber-900">
                      {t("admin.users.wallet.pending_withdrawals")}: {detail.pendingWithdrawals}
                    </span>
                  </div>
                )}

                {/* Phuong thuc nhan tien mo san: day la thu nguoi van hanh mo modal
                    de xem nhieu nhat khi xu ly lenh rut. */}
                <ModalSection
                  id="payout"
                  label={t("admin.users.payout.section")}
                  icon={Landmark}
                  open={openSection === "payout"}
                  onToggle={toggleSection}
                >
                  <PayoutMethodsPanel userId={detail.id} />
                </ModalSection>

                {canAdjust && (
                  <ModalSection
                    id="adjust"
                    label={t("admin.users.wallet.tab_adjust")}
                    icon={Wallet}
                    open={openSection === "adjust"}
                    onToggle={toggleSection}
                  >
                    <WalletAdjustPanel
                      userId={detail.id}
                      username={detail.username}
                      currentBalance={detail.walletBalance}
                      currency={detail.currency}
                      onAdjusted={refreshDetail}
                    />
                  </ModalSection>
                )}

                <ModalSection
                  id="ledger"
                  label={t("admin.users.ledger.title")}
                  icon={ScrollText}
                  open={openSection === "ledger"}
                  onToggle={toggleSection}
                >
                  <WalletLedgerPanel userId={detail.id} />
                </ModalSection>
              </div>
            )}

            {/* ===== TAB 2: TAI KHOAN ===== */}
            {!detailLoading && detail && modalTab === "account" && (
              <div className="flex flex-col gap-3">
                <div className="grid grid-cols-2 gap-3">
                  <div className="flex flex-col gap-0.5 p-3 bg-slate-50 border border-slate-200 rounded-xl">
                    <span className="text-slate-500 text-[10px] uppercase font-bold">
                      {t("admin.users.table_role")}
                    </span>
                    <span className="text-slate-900 font-bold text-sm">{detail.role}</span>
                  </div>
                  <div className="flex flex-col gap-0.5 p-3 bg-slate-50 border border-slate-200 rounded-xl">
                    <span className="text-slate-500 text-[10px] uppercase font-bold">
                      {t("admin.users.wpwd.title")}
                    </span>
                    <span
                      className={`font-bold text-sm ${
                        detail.hasWithdrawalPassword
                          ? "text-emerald-700"
                          : "text-slate-400"
                      }`}
                    >
                      {detail.hasWithdrawalPassword
                        ? t("admin.users.wpwd.has")
                        : t("admin.users.wpwd.none")}
                    </span>
                  </div>
                </div>

                {/* Duyet KYC de INLINE chu khong gap vao section: no chi la mot o chon
                    va mot nut, gap lai thi them mot lan bam ma khong tiet kiem cho. */}
                <div className="flex flex-col gap-2 p-3.5 bg-white border border-slate-200 rounded-xl">
                  <div className="flex items-center gap-2">
                    <BadgeCheck className="w-4 h-4 text-slate-600" />
                    <span className="text-[11px] font-extrabold text-slate-900 uppercase tracking-wide">
                      {t("admin.users.kyc.title")}
                    </span>
                  </div>

                  {canManage ? (
                    <>
                      <div className="flex items-center gap-2">
                        <label htmlFor="user-kyc" className="sr-only">
                          {t("admin.users.kyc.new")}
                        </label>
                        <select
                          id="user-kyc"
                          value={kycValue}
                          onChange={(e) => {
                            setKycValue(e.target.value);
                            setKycOk(false);
                            setKycError("");
                          }}
                          className="flex-1 bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3 py-2 text-xs font-bold text-slate-900 outline-none cursor-pointer"
                        >
                          {KYC_LEVELS.map((level) => (
                            <option key={level} value={level}>
                              {kycLabel(level)}
                            </option>
                          ))}
                        </select>
                        <button
                          onClick={saveKyc}
                          disabled={kycSaving || kycValue === detail.kycLevel}
                          className="px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed text-white text-xs font-bold transition-colors shrink-0"
                        >
                          {kycSaving ? "..." : t("admin.users.kyc.submit")}
                        </button>
                      </div>

                      {kycError && (
                        <span className="text-[11px] text-red-700 font-semibold">
                          {kycError}
                        </span>
                      )}
                      {kycOk && (
                        <span className="text-[11px] text-emerald-700 font-semibold flex items-center gap-1">
                          <CheckCircle2 className="w-3 h-3" />{" "}
                          {t("admin.users.kyc.done")}
                        </span>
                      )}
                    </>
                  ) : (
                    <span className="text-xs font-bold text-slate-900">
                      {kycLabel(detail.kycLevel)}
                    </span>
                  )}
                </div>

                {/* Block 1: Admin Đổi Mật khẩu Đăng nhập (Cấp 1) */}
                <div className="flex flex-col gap-2 p-3.5 bg-white border border-slate-200 rounded-xl">
                  <div className="flex items-center gap-2">
                    <Lock className="w-4 h-4 text-amber-600" />
                    <span className="text-[11px] font-extrabold text-slate-900 uppercase tracking-wide">
                      Đổi Mật khẩu Đăng nhập (Cấp 1)
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      value={overridePassword}
                      onChange={(e) => {
                        setOverridePassword(e.target.value);
                        setOverridePasswordOk(false);
                        setOverridePasswordError("");
                      }}
                      placeholder="Mật khẩu mới (vd: admin1712)..."
                      className="flex-1 bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3 py-2 text-xs font-bold text-slate-900 outline-none"
                    />
                    <button
                      onClick={saveOverridePassword}
                      disabled={overridePasswordSaving || !overridePassword.trim()}
                      className="px-3.5 py-2 rounded-xl bg-amber-600 hover:bg-amber-700 disabled:opacity-40 disabled:cursor-not-allowed text-white text-xs font-bold transition-colors shrink-0"
                    >
                      {overridePasswordSaving ? "..." : "Cập nhật"}
                    </button>
                  </div>
                  {overridePasswordError && (
                    <span className="text-[11px] text-red-700 font-semibold">{overridePasswordError}</span>
                  )}
                  {overridePasswordOk && (
                    <span className="text-[11px] text-emerald-700 font-semibold flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" /> Đã cập nhật mật khẩu đăng nhập mới!
                    </span>
                  )}
                </div>

                {/* Block 2: Admin Đổi Mật khẩu Rút tiền 6 số (Cấp 2) */}
                <div className="flex flex-col gap-2 p-3.5 bg-white border border-slate-200 rounded-xl">
                  <div className="flex items-center gap-2">
                    <Shield className="w-4 h-4 text-emerald-600" />
                    <span className="text-[11px] font-extrabold text-slate-900 uppercase tracking-wide">
                      Đổi Mật khẩu Rút tiền 6 số (Cấp 2)
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      maxLength={6}
                      value={overridePin}
                      onChange={(e) => {
                        const val = e.target.value.replace(/\D/g, "");
                        setOverridePin(val);
                        setOverridePinOk(false);
                        setOverridePinError("");
                      }}
                      placeholder="Mã PIN 6 số (vd: 123456)..."
                      className="flex-1 bg-slate-50 border border-slate-200 focus:border-slate-900 rounded-xl px-3 py-2 text-xs font-bold text-slate-900 outline-none"
                    />
                    <button
                      onClick={saveOverridePin}
                      disabled={overridePinSaving || overridePin.length !== 6}
                      className="px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-40 disabled:cursor-not-allowed text-white text-xs font-bold transition-colors shrink-0"
                    >
                      {overridePinSaving ? "..." : "Cập nhật"}
                    </button>
                  </div>
                  {overridePinError && (
                    <span className="text-[11px] text-red-700 font-semibold">{overridePinError}</span>
                  )}
                  {overridePinOk && (
                    <span className="text-[11px] text-emerald-700 font-semibold flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" /> Đã cập nhật mã PIN 6 số mới!
                    </span>
                  )}
                </div>

                {/* Block 3: Admin Xóa tài khoản người dùng */}
                <div className="flex flex-col gap-2 p-3.5 bg-white border border-red-200 rounded-xl">
                  <div className="flex items-center gap-2">
                    <AlertTriangle className="w-4 h-4 text-red-600" />
                    <span className="text-[11px] font-extrabold text-red-700 uppercase tracking-wide">
                      Xóa Tài Khoản (Nguy Hiểm)
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="password"
                      value={deletePin}
                      onChange={(e) => {
                        setDeletePin(e.target.value);
                        setDeleteOk(false);
                        setDeleteError("");
                      }}
                      placeholder="Mã xác nhận bảo mật..."
                      className="flex-1 bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl px-3 py-2 text-xs font-bold text-slate-900 outline-none"
                    />
                    <button
                      onClick={handleDeleteUser}
                      disabled={deleteSaving || deletePin.trim().length === 0}
                      className="px-3.5 py-2 rounded-xl bg-red-600 hover:bg-red-700 disabled:opacity-40 disabled:cursor-not-allowed text-white text-xs font-bold transition-colors shrink-0"
                    >
                      {deleteSaving ? "..." : "Xóa"}
                    </button>
                  </div>
                  {deleteError && (
                    <span className="text-[11px] text-red-700 font-semibold">{deleteError}</span>
                  )}
                  {deleteOk && (
                    <span className="text-[11px] text-emerald-700 font-semibold flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" /> Đã xóa tài khoản thành công!
                    </span>
                  )}
                </div>



                {detail.lastLoginAt && (
                  <div className="flex items-center justify-between px-1 text-[11px]">
                    <span className="text-slate-500 font-semibold">
                      {t("admin.users.wallet.last_login")}
                    </span>
                    <span className="text-slate-700 font-bold">
                      {new Date(detail.lastLoginAt).toLocaleString("vi-VN")}
                    </span>
                  </div>
                )}
              </div>
            )}

            {/* ===== TAB 3: TY LE CUOC ===== */}
            {/* Mount CÓ ĐIỀU KIỆN: panel gọi API lúc mount, nên ẩn bằng CSS sẽ vẫn tải
                dữ liệu mỗi lần mở modal dù người vận hành không xem tab này. */}
            {!detailLoading && detail && modalTab === "odds" && (
              <UserGameOddsPanel userId={detail.id} username={detail.username} />
            )}

            <div className="flex justify-end pt-1">
              <button
                onClick={() => setSelectedUser(null)}
                className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
              >
                {t("admin.states.close")}
              </button>
            </div>
          </div>
        )}
      </AdminModal>

      {/* Status Modal */}
      <AdminModal
        isOpen={!!statusModalUser}
        onClose={() => setStatusModalUser(null)}
        maxWidthClass="max-w-md"
        title={
          statusModalUser && (
            <div className="flex items-center gap-3 w-full">
              <div className="p-2.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-600 shrink-0">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div className="flex flex-col min-w-0">
                <h3 className="text-base font-extrabold text-slate-900 truncate">
                  {t("admin.users.status.modal_title")}
                </h3>
                <span className="text-xs text-slate-500 font-medium truncate">
                  {statusModalUser.username}
                </span>
              </div>
            </div>
          )
        }
      >
        {statusModalUser && (
          <div className="flex flex-col gap-5">
            {actionError && (
              <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700 font-semibold">
                {actionError}
              </div>
            )}

            <div className="flex flex-col gap-3">
              <label
                htmlFor="user-new-status"
                className="text-xs font-bold text-slate-700"
              >
                {t("admin.users.status.pick_new")}
              </label>
              <select
                id="user-new-status"
                value={newStatus}
                onChange={(e) =>
                  setNewStatus(e.target.value as "ACTIVE" | "LOCKED" | "BANNED")
                }
                className="bg-slate-50 border border-slate-200 rounded-xl p-2.5 text-xs text-slate-900 outline-none font-bold cursor-pointer"
              >
                <option value="ACTIVE" className="bg-white text-slate-900">
                  {t("admin.users.status.opt_active")}
                </option>
                <option value="LOCKED" className="bg-white text-slate-900">
                  {t("admin.users.status.opt_locked")}
                </option>
                <option value="BANNED" className="bg-white text-slate-900">
                  {t("admin.users.status.opt_banned")}
                </option>
              </select>

              <label
                htmlFor="user-status-reason"
                className="text-xs font-bold text-slate-700 mt-1"
              >
                {t("admin.users.status.reason_label")}{" "}
                <span className="text-red-600">
                  {t("admin.users.status.reason_required_note")}
                </span>
              </label>
              <textarea
                id="user-status-reason"
                rows={3}
                required
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={t("admin.users.status.reason_placeholder")}
                className="bg-slate-50 border border-slate-200 focus:border-red-500 rounded-xl p-3 text-xs text-slate-900 placeholder-slate-400 outline-none font-medium"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setStatusModalUser(null)}
                className="px-4 py-2 rounded-xl bg-slate-100 text-xs font-bold text-slate-700 hover:bg-slate-200"
              >
                {t("admin.states.cancel")}
              </button>
              <button
                onClick={handleUpdateStatus}
                disabled={actionLoading}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white font-bold text-xs transition-colors disabled:opacity-50"
              >
                {actionLoading
                  ? t("admin.states.saving")
                  : t("admin.users.status.submit")}
              </button>
            </div>
          </div>
        )}
      </AdminModal>
    </div>
  );
}
