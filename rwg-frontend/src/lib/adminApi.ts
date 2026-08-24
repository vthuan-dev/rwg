import { ADMIN_API_BASE_URL, ADMIN_URL_PREFIX } from "./constants";

const ACCESS_TOKEN_KEY = "rwg_admin_token";
const REFRESH_TOKEN_KEY = "rwg_admin_refresh_token";

export const getAdminToken = (): string | null => {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ACCESS_TOKEN_KEY);
};

export const setAdminToken = (token: string) => {
  if (typeof window !== "undefined") {
    localStorage.setItem(ACCESS_TOKEN_KEY, token);
  }
};

export const getAdminRefreshToken = (): string | null => {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(REFRESH_TOKEN_KEY);
};

export const setAdminRefreshToken = (token: string) => {
  if (typeof window !== "undefined") {
    localStorage.setItem(REFRESH_TOKEN_KEY, token);
  }
};

/**
 * Xoá CẢ HAI token. Phải xoá cùng lúc: giữ lại refresh token sau khi access token
 * đã bị coi là hết hiệu lực sẽ khiến lần tải trang sau tưởng là còn phiên.
 */
export const removeAdminToken = () => {
  if (typeof window !== "undefined") {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }
};

/** Tuỳ chọn riêng của adminFetch, không thuộc RequestInit. */
export interface AdminFetchOptions extends RequestInit {
  /**
   * Bỏ qua xử lý phiên hết hạn khi nhận 401.
   *
   * Trang đăng nhập BẮT BUỘC bật cờ này: ở đó 401 nghĩa là "sai mật khẩu", không
   * phải "phiên hết hạn". Nếu không bỏ qua, người dùng nhập sai mật khẩu sẽ nhận
   * thông báo hoàn toàn sai lệch và không hiểu vì sao mình không vào được.
   */
  skipAuthRedirect?: boolean;
}

/**
 * Lỗi từ API quản trị, giữ lại code và details của backend.
 *
 * Response lỗi có dạng {code, message, details, traceId, timestamp}. Trước đây chỉ
 * message được giữ lại nên UI không thể phân biệt "vượt hạn mức ngày" với "số dư
 * không đủ" để hiện hướng xử lý phù hợp.
 */
export class AdminApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly details?: Record<string, unknown>;

  constructor(
    message: string,
    status: number,
    code?: string,
    details?: Record<string, unknown>
  ) {
    super(message);
    this.name = "AdminApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

/** Kết quả gọi API kèm mã trạng thái, dùng khi 202 mang nghĩa khác 200. */
export interface AdminResult<T> {
  status: number;
  data: T;
  /**
   * true khi backend trả 202: nghiệp vụ CHƯA được thực thi, đang chờ phê duyệt.
   *
   * Với điều chỉnh ví, 202 nghĩa là TIỀN CHƯA CHUYỂN. Gộp nó vào nhánh thành công
   * của 200 sẽ khiến người vận hành tưởng đã cộng tiền xong rồi cộng lại lần nữa.
   */
  pending: boolean;
}

/**
 * Lời gọi gia hạn đang diễn ra, dùng chung cho mọi request.
 *
 * VÌ SAO PHẢI DÙNG CHUNG: refresh token là DÙNG MỘT LẦN — backend xoay vòng và thu
 * hồi token cũ ngay khi cấp token mới. Nếu ba request song song cùng nhận 401 rồi
 * mỗi cái tự gọi refresh, chỉ cái đầu thành công; hai cái sau gửi token đã bị thu
 * hồi và nhận 401, kết quả là người dùng vẫn bị đăng xuất dù phiên còn hợp lệ.
 *
 * Giữ một Promise duy nhất để mọi request cùng chờ đúng một lần gia hạn.
 */
let refreshInFlight: Promise<boolean> | null = null;

/**
 * Đổi refresh token lấy cặp token mới. Trả về false nếu phiên đã hết thật.
 *
 * KHÔNG dùng adminFetch ở đây: sẽ tạo vòng lặp vô tận khi chính lời gọi refresh
 * nhận 401.
 */
async function renewSession(): Promise<boolean> {
  const refreshToken = getAdminRefreshToken();
  if (!refreshToken) return false;

  try {
    const res = await fetch(`${ADMIN_API_BASE_URL}/admin/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;

    const data = await res.json();
    if (!data.accessToken || !data.refreshToken) return false;

    // Phải lưu CẢ HAI: token cũ đã bị thu hồi, giữ lại sẽ hỏng lần gia hạn sau.
    setAdminToken(data.accessToken);
    setAdminRefreshToken(data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

/** Gọi renewSession nhưng gộp các lời gọi trùng vào một Promise. */
function renewSessionOnce(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = renewSession().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

/** Đăng xuất và chuyển về trang đăng nhập. */
function endSession(): never {
  removeAdminToken();
  const loginPath = `${ADMIN_URL_PREFIX}/login`;
  if (
    typeof window !== "undefined" &&
    !window.location.pathname.startsWith(loginPath)
  ) {
    // Dựng URL TUYỆT ĐỐI từ origin hiện tại.
    //
    // Gán trực tiếp một đường dẫn tương đối để trình duyệt tự giải nghĩa theo trang đang
    // mở; dựng sẵn qua `new URL` thì đích đến luôn rõ ràng bất kể đang ở route nào.
    //
    // Vẫn dùng `window.location` chứ không dùng router của Next: phiên đã hết hiệu lực nên
    // ta CẦN tải lại cả trang để xoá sạch mọi state cũ còn trong bộ nhớ.
    window.location.href = new URL(loginPath, window.location.origin).toString();
  }
  throw new AdminApiError("Admin session expired. Please log in again.", 401);
}

/**
 * Gọi API quản trị và trả về CẢ mã trạng thái.
 *
 * Dùng cho các thao tác mà 2xx không đủ để biết chuyện gì đã xảy ra. Với các lời
 * gọi thông thường hãy dùng {@link adminFetch} cho gọn.
 */
export async function adminFetchWithStatus<T = unknown>(
  endpoint: string,
  options: AdminFetchOptions = {}
): Promise<AdminResult<T>> {
  const { skipAuthRedirect, ...fetchOptions } = options;

  // Tách phần gửi request ra để có thể chạy lại y nguyên sau khi gia hạn.
  // Đọc token BÊN TRONG hàm, không phải bên ngoài: lần thử lại phải lấy token mới.
  const send = async (): Promise<Response> => {
    const token = getAdminToken();
    const headers: Record<string, string> = {
      ...(fetchOptions.headers as Record<string, string>),
    };
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
    // If not FormData, set Content-Type to application/json
    if (!(fetchOptions.body instanceof FormData) && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }
    return fetch(`${ADMIN_API_BASE_URL}${endpoint}`, {
      ...fetchOptions,
      headers,
    });
  };

  let res = await send();

  if (res.status === 401 && !skipAuthRedirect) {
    // Thử gia hạn ĐÚNG MỘT LẦN. Nếu lần thử lại vẫn 401 thì phiên hết hạn thật,
    // không thử tiếp để tránh vòng lặp.
    const renewed = await renewSessionOnce();
    if (!renewed) {
      endSession();
    }
    res = await send();
    if (res.status === 401) {
      endSession();
    }
  }

  if (!res.ok) {
    let errorMsg = `System Error (${res.status})`;
    let code: string | undefined;
    let details: Record<string, unknown> | undefined;
    try {
      const errorData = await res.json();
      errorMsg = errorData.message || errorData.detail || errorMsg;
      code = errorData.code;
      details = errorData.details;
    } catch {
      // ignore JSON parse error
    }
    throw new AdminApiError(errorMsg, res.status, code, details);
  }

  if (res.status === 204) {
    return { status: res.status, data: {} as T, pending: false };
  }

  return {
    status: res.status,
    data: (await res.json()) as T,
    pending: res.status === 202,
  };
}

/**
 * Gọi API quản trị, trả về thẳng phần body.
 *
 * KHÔNG dùng cho endpoint có thể trả 202 — hãy dùng {@link adminFetchWithStatus}
 * để không mất thông tin "đang chờ duyệt".
 */
export async function adminFetch<T = unknown>(
  endpoint: string,
  options: AdminFetchOptions = {}
): Promise<T> {
  const result = await adminFetchWithStatus<T>(endpoint, options);
  return result.data;
}

/**
 * Tải một tệp nhị phân cần đăng nhập và trả về blob URL dùng được cho `<img src>`.
 *
 * VÌ SAO KHÔNG GÁN THẲNG ĐƯỜNG DẪN VÀO `<img src>`: ảnh đính kèm của chat nằm sau
 * endpoint yêu cầu `Authorization: Bearer ...`, và thẻ `<img>` không có cách nào gửi
 * header — trình duyệt chỉ gửi cookie. Gán thẳng thì mọi ảnh trả về 401 và nhân sự
 * thấy toàn ô vỡ trong hộp thư.
 *
 * KHÔNG dùng {@link adminFetchWithStatus}: hàm đó luôn gọi `res.json()` trên phần
 * thân, và một tệp ảnh sẽ làm bước đó ném lỗi phân tích JSON.
 *
 * NGƯỜI GỌI PHẢI thu hồi URL trả về bằng `URL.revokeObjectURL` khi không dùng nữa.
 */
export async function adminFetchBlobUrl(endpoint: string): Promise<string> {
  const send = async (): Promise<Response> => {
    const token = getAdminToken();
    return fetch(`${ADMIN_API_BASE_URL}${endpoint}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
  };

  let res = await send();

  // Cùng lớp gia hạn MỘT LẦN như adminFetchWithStatus: ảnh trong lịch sử chat được tải
  // rải rác theo lúc nhân sự cuộn, nên rất dễ rơi đúng vào lúc token vừa hết hạn.
  if (res.status === 401) {
    const renewed = await renewSessionOnce();
    if (!renewed) {
      endSession();
    }
    res = await send();
  }

  if (!res.ok) {
    throw new AdminApiError(`System Error (${res.status})`, res.status);
  }

  return URL.createObjectURL(await res.blob());
}

/** Đường dẫn API (không gồm base) của một ảnh đính kèm đã lưu trong tin nhắn. */
export function chatAttachmentEndpoint(attachmentUrl: string): string {
  // Backend lưu đường dẫn đầy đủ "/api/v1/chat/attachments/<tên>", còn base URL đã
  // chứa "/api/v1" — nối thẳng sẽ ra "/api/v1/api/v1/...".
  return attachmentUrl.replace(/^\/api\/v1/, "");
}
