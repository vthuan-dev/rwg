import { getAdminToken } from "./adminApi";

/**
 * Thông tin lấy từ access token của quản trị viên.
 *
 * CẢNH BÁO: đây CHỈ dùng để quyết định hiển thị. Token do client giữ nên người dùng
 * hoàn toàn có thể sửa nội dung này. Quyền thật vẫn do backend quyết định trong
 * SecurityConfig — nếu client vượt qua kiểm tra ở đây thì API vẫn trả 403.
 */
export interface AdminIdentity {
  /** id của chính quản trị viên đang đăng nhập (claim sub). */
  userId: string | null;
  username: string | null;
  /** Danh sách vai trò đã bỏ tiền tố ROLE_, ví dụ ["ADMIN"]. */
  roles: string[];
}

const EMPTY: AdminIdentity = { userId: null, username: null, roles: [] };

/**
 * Giải mã phần payload của JWT.
 *
 * KHÔNG xác thực chữ ký — việc đó là của backend. Ở đây chỉ đọc claim để biết nên
 * hiện hay ẩn nút.
 */
function decodePayload(token: string): Record<string, unknown> | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    // base64url -> base64, rồi bù padding cho đủ bội số của 4.
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    const json = decodeURIComponent(
      atob(padded)
        .split("")
        .map((c) => `%${c.charCodeAt(0).toString(16).padStart(2, "0")}`)
        .join("")
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/** Đọc danh tính quản trị viên đang đăng nhập từ token trong localStorage. */
export function getAdminIdentity(): AdminIdentity {
  const token = getAdminToken();
  if (!token) return EMPTY;

  const payload = decodePayload(token);
  if (!payload) return EMPTY;

  const rawRoles = payload.roles;
  const roles = Array.isArray(rawRoles)
    ? rawRoles
        .filter((r): r is string => typeof r === "string")
        .map((r) => r.replace(/^ROLE_/, ""))
    : [];

  return {
    userId: typeof payload.sub === "string" ? payload.sub : null,
    username: typeof payload.username === "string" ? payload.username : null,
    roles,
  };
}

/**
 * Vai trò được phép chạm tiền, khớp với ma trận phân quyền trong SecurityConfig:
 * điều chỉnh ví và phê duyệt lệnh rút chỉ mở cho ADMIN và FINANCE. SUPPORT và RISK
 * xem được nhưng không được thao tác.
 */
const MONEY_ROLES = ["ADMIN", "FINANCE"];

export function canAdjustWallet(identity: AdminIdentity = getAdminIdentity()): boolean {
  return identity.roles.some((r) => MONEY_ROLES.includes(r));
}

/** Kiểm tra có một trong các vai trò truyền vào. */
export function hasAnyRole(
  roles: string[],
  identity: AdminIdentity = getAdminIdentity()
): boolean {
  return identity.roles.some((r) => roles.includes(r));
}

/**
 * Chỉ ADMIN. Dùng cho các thao tác đổi cấu hình toàn hệ thống — ví dụ % hoa hồng
 * và hạn mức bàn cược — nơi SecurityConfig KHÔNG mở cho FINANCE.
 */
export function isSuperAdmin(identity: AdminIdentity = getAdminIdentity()): boolean {
  return identity.roles.includes("ADMIN");
}

/** Được sửa hồ sơ người dùng: trạng thái, KYC, mật khẩu rút. */
export function canManageUsers(identity: AdminIdentity = getAdminIdentity()): boolean {
  return hasAnyRole(["ADMIN", "FINANCE", "SUPPORT"], identity);
}

/** Được xem và kết luận về liên kết rủi ro. */
export function canViewRisk(identity: AdminIdentity = getAdminIdentity()): boolean {
  return hasAnyRole(["ADMIN", "RISK"], identity);
}

/**
 * Được TRẢ LỜI người chơi trong hộp thư hỗ trợ.
 *
 * Khớp chính xác matcher `POST /api/v1/admin/chat/**` trong SecurityConfig. RISK đọc
 * được toàn bộ lịch sử chat nhưng không được gửi gì — nên với vai trò đó phải ẩn hẳn
 * ô nhập, chứ không phải để họ gõ xong rồi nhận 403.
 */
export function canChatReply(identity: AdminIdentity = getAdminIdentity()): boolean {
  return hasAnyRole(["ADMIN", "FINANCE", "SUPPORT"], identity);
}

/**
 * Thứ tự quyền hạn từ cao xuống thấp.
 *
 * Một tài khoản có thể mang nhiều vai trò, nên phải chọn theo mức quyền chứ không
 * lấy phần tử đầu mảng — claim `roles` không đảm bảo thứ tự nào.
 */
const ROLE_PRIORITY = ["ADMIN", "FINANCE", "RISK", "SUPPORT", "PLAYER"];

/** Vai trò cao nhất của tài khoản đang đăng nhập, dùng để hiển thị. */
export function primaryRole(identity: AdminIdentity = getAdminIdentity()): string | null {
  for (const role of ROLE_PRIORITY) {
    if (identity.roles.includes(role)) return role;
  }
  return identity.roles[0] ?? null;
}

/**
 * Nhãn của một vai trò, tra trong file dịch.
 *
 * NHẬN hàm `t` từ bên gọi thay vì giữ bảng nhãn tại đây: một bảng chữ cứng trong
 * file lib thì bộ chọn ngôn ngữ không thấy được, và đó chính là lý do giao diện
 * đang đặt English vẫn hiện "Quản trị tối cao".
 *
 * Vai trò lạ trả về nguyên mã — thà hiện mã kỹ thuật còn hơn để trống.
 */
export function roleLabel(
  role: string | null,
  t: (key: string) => string
): string {
  if (!role) return t("admin.roles.unknown");
  const label = t(`admin.roles.${role}`);
  // t() tra ve nguyen duong dan khoa khi khong tim thay -> dung ma goc cho gon.
  return label === `admin.roles.${role}` ? role : label;
}


