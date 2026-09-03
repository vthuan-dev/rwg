import { USER_API_BASE_URL } from "@/lib/constants";

/**
 * Gọi API của khu NGƯỜI CHƠI (cổng 8080).
 *
 * Tách riêng khỏi adminApi.ts: hai khu dùng hai backend khác nhau, hai khoá token
 * khác nhau, và quan trọng hơn là hai vòng đời phiên khác nhau — gộp chung sẽ
 * dẫn tới chuyện đăng xuất khu này lại thu hồi phiên khu kia.
 */

/** Khoá lưu access token của người chơi. Khu quản trị dùng "rwg_admin_token". */
const PLAYER_TOKEN_KEY = "rwg_token";
const PLAYER_REFRESH_KEY = "rwg_refresh_token";

/** Khoá lưu ngôn ngữ giao diện. Trùng với khoá LanguageContext đang dùng. */
const LOCALE_KEY = "rwg_locale";

/**
 * Các mã ngôn ngữ backend hiểu được.
 *
 * Giao diện có SÁU ngôn ngữ nhưng backend chỉ có bốn bundle thông báo
 * (messages, _vi, _zh, _ja). Gửi `Accept-Language: ms` sẽ khiến backend không khớp
 * được mã nào và rơi về tiếng Anh — TỆ HƠN là không gửi gì, vì khi không có header
 * thì nó còn dùng locale đã lưu của người dùng.
 */
const BACKEND_LOCALES = new Set(["en", "vi", "zh", "ja"]);

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  accessTokenExpiresIn: number;
}

export interface UserResponse {
  id: string;
  username: string;
  /** Luôn null với tài khoản tạo từ form người chơi — API đăng ký không nhận email. */
  email: string | null;
  role: string;
  status: string;
  kycLevel: string;
  hasWithdrawalPassword: boolean;
  locale: string;
  /** Họ tên tự khai; null khi chưa khai. */
  fullName: string | null;
  /**
   * Mã quốc gia ISO 3166-1 alpha-2, ví dụ "VN"; null khi chưa khai.
   *
   * Là MÃ chứ không phải tên nước — dùng `countryName()` trong `lib/countries` để hiển thị.
   */
  countryCode: string | null;
  /** Số điện thoại tự khai; null khi chưa khai. */
  phone: string | null;
  createdAt: string;
}

/**
 * Lỗi nghiệp vụ từ backend, giữ nguyên `code` và `details`.
 *
 * Bọc thành class riêng thay vì ném Error thường để chỗ gọi phân biệt được
 * "sai mật khẩu" với "mất mạng" — hai trường hợp này cần hai thông báo khác nhau,
 * và trường hợp CAPTCHA_REQUIRED còn phải làm hiện thêm một ô nhập.
 */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly details: Record<string, string> | null;

  constructor(
    code: string,
    message: string,
    status: number,
    details: Record<string, string> | null
  ) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.details = details;
  }

  /** Server yêu cầu captcha (sau nhiều lần đăng nhập sai). */
  get captchaRequired(): boolean {
    return this.code === "CAPTCHA_REQUIRED";
  }

  /**
   * Tài khoản bị khoá tạm thời (mặc định 15 phút sau 10 lần sai).
   *
   * Phải phân biệt với "sai mật khẩu": người dùng bị khoá mà chỉ thấy thông báo sai
   * mật khẩu sẽ gõ lại mãi, mỗi lần lại gia hạn thêm thời gian khoá.
   */
  get accountLocked(): boolean {
    return this.status === 423;
  }
}

interface ErrorBody {
  code?: string;
  message?: string;
  details?: Record<string, unknown> | null;
}

/**
 * Chuẩn hoá `details` về Record<string, string>.
 *
 * Backend trả details với kiểu giá trị tự do (chuỗi, số, mảng), nhưng giao diện
 * chỉ hiển thị được chuỗi. Chuyển đổi ở một chỗ để phía component không phải
 * đoán kiểu.
 */
function normalizeDetails(
  details: Record<string, unknown> | null | undefined
): Record<string, string> | null {
  if (!details) return null;
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(details)) {
    if (value == null) continue;
    out[key] = Array.isArray(value) ? value.join(", ") : String(value);
  }
  return Object.keys(out).length > 0 ? out : null;
}

/**
 * Ngôn ngữ để gửi kèm `Accept-Language`, hoặc null nếu backend không hiểu.
 *
 * Backend ƯU TIÊN header này TRƯỚC locale đã lưu của người dùng, nên đây là cách
 * duy nhất để thông báo lỗi về đúng ngôn ngữ giao diện. Không gửi thì trình duyệt
 * tự gửi theo cài đặt hệ điều hành: người dùng Chrome tiếng Anh đang xem giao diện
 * tiếng Việt sẽ nhận thông báo lỗi tiếng Anh.
 *
 * Đọc thẳng localStorage chứ KHÔNG import LanguageContext: file này là tầng vận
 * chuyển, kéo một React context vào đây sẽ khiến nó không dùng được ngoài component.
 */
function interfaceLocale(): string | null {
  if (typeof window === "undefined") return null;
  const locale = localStorage.getItem(LOCALE_KEY);
  return locale && BACKEND_LOCALES.has(locale) ? locale : null;
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const locale = interfaceLocale();

  // KHÔNG đặt Content-Type khi body là FormData.
  //
  // Với multipart, header này phải kèm chuỗi boundary do trình duyệt sinh ra để ngăn
  // cách các phần của request. Gán cứng "application/json" (hoặc kể cả
  // "multipart/form-data" thủ công) thì boundary bị mất và server không tách được
  // tệp — lỗi hiện ra là 400 không nói gì về nguyên nhân thật.
  const isFormData =
    typeof FormData !== "undefined" && init.body instanceof FormData;

  let response: Response;
  try {
    response = await fetch(`${USER_API_BASE_URL}${path}`, {
      ...init,
      headers: {
        ...(isFormData ? {} : { "Content-Type": "application/json" }),
        ...(locale ? { "Accept-Language": locale } : {}),
        ...(init.headers ?? {}),
      },
    });
  } catch {
    // Không phân biệt được mất mạng với server sập từ phía trình duyệt, nên dùng
    // một mã chung và để người dùng thử lại.
    throw new ApiError("NETWORK_ERROR", "network_error", 0, null);
  }

  if (!response.ok) {
    // Backend luôn trả JSON lỗi chuẩn {code, message, details, traceId}, nhưng
    // proxy/gateway chen ngang có thể trả HTML — nên vẫn phải phòng trường hợp
    // body không phải JSON, không thì lỗi thật bị che bởi lỗi phân tích JSON.
    let body: ErrorBody = {};
    try {
      body = (await response.json()) as ErrorBody;
    } catch {
      body = {};
    }
    throw new ApiError(
      body.code ?? "UNKNOWN",
      body.message ?? `HTTP ${response.status}`,
      response.status,
      normalizeDetails(body.details)
    );
  }

  // 204 No Content không có body — gọi .json() sẽ ném lỗi.
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

// ===== Xoay vòng token =====

/**
 * Lời gọi refresh đang bay, dùng để DỒN TRÙNG.
 *
 * Backend thu hồi theo HỌ token: dùng lại một refresh token đã xoay vòng sẽ thu hồi
 * cả họ và đăng xuất người dùng. Nếu hai request cùng gặp 401 một lúc và mỗi cái tự
 * gọi refresh, lượt thứ hai gửi token đã bị lượt đầu xoay vòng — tự đăng xuất chính
 * mình. Vì vậy mọi lời gọi phải dùng chung MỘT lượt xoay vòng.
 */
let pendingRefresh: Promise<TokenResponse> | null = null;

/**
 * Đổi refresh token lấy cặp token mới. Xoá token khi thất bại.
 *
 * Refresh token hết hạn hoặc đã bị thu hồi là hết đường cứu: giữ lại chỉ khiến mọi
 * request sau đó thử xoay vòng rồi lại thất bại.
 */
function refreshTokens(): Promise<TokenResponse> {
  if (pendingRefresh) return pendingRefresh;

  const guestUser = getGuestUsername();

  // Nếu là phiên Guest Support và hết token -> tự âm thầm xin lại token qua username
  if (isGuestSupportOnly() && guestUser) {
    pendingRefresh = request<TokenResponse>("/auth/guest-support", {
      method: "POST",
      body: JSON.stringify({ username: guestUser }),
    })
      .then((tokens) => {
        storeTokens(tokens);
        return tokens;
      })
      .catch((error) => {
        clearPlayerTokens();
        throw error;
      })
      .finally(() => {
        pendingRefresh = null;
      });
    return pendingRefresh;
  }

  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return Promise.reject(
      new ApiError("UNAUTHORIZED", "no_refresh_token", 401, null)
    );
  }

  pendingRefresh = request<TokenResponse>("/auth/refresh", {
    method: "POST",
    body: JSON.stringify({ refreshToken }),
  })
    .then((tokens) => {
      storeTokens(tokens);
      return tokens;
    })
    .catch((error) => {
      clearPlayerTokens();
      throw error;
    })
    .finally(() => {
      pendingRefresh = null;
    });

  return pendingRefresh;
}

/**
 * Gọi API cần đăng nhập, tự xoay vòng token MỘT lần khi gặp 401.
 *
 * Access token chỉ sống 15 phút (`accessTokenExpiresIn: 900`). Không có lớp này thì
 * người dùng đang dùng dở bị đăng xuất giữa chừng, dù refresh token vẫn còn hiệu lực.
 *
 * CHỈ thử lại MỘT lần, và KHÔNG dùng cho `/auth/*`: nếu chính lời gọi refresh cũng
 * trả 401 mà lại đi xoay vòng tiếp thì thành vòng lặp vô hạn.
 */
async function authedRequest<T>(path: string, init: RequestInit): Promise<T> {
  const token = getPlayerToken();
  if (!token) {
    throw new ApiError("UNAUTHORIZED", "no_token", 401, null);
  }

  const withToken = (accessToken: string): RequestInit => ({
    ...init,
    headers: { ...(init.headers ?? {}), Authorization: `Bearer ${accessToken}` },
  });

  try {
    return await request<T>(path, withToken(token));
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    // refreshTokens() tự ném khi hết đường — để nguyên cho chỗ gọi xử lý như
    // "chưa đăng nhập".
    const tokens = await refreshTokens();
    return await request<T>(path, withToken(tokens.accessToken));
  }
}

/**
 * Tải một tệp nhị phân cần đăng nhập và trả về blob URL dùng được cho `<img src>`.
 *
 * VÌ SAO KHÔNG GÁN THẲNG ĐƯỜNG DẪN VÀO `<img src>`: ảnh đính kèm của chat nằm sau
 * endpoint yêu cầu `Authorization: Bearer ...`, và thẻ `<img>` không có cách nào gửi
 * header — trình duyệt chỉ gửi cookie. Gán thẳng thì mọi ảnh trả về 401 và hiện thành
 * ô vỡ.
 *
 * Cách khác là chuyển sang xác thực bằng cookie hoặc nhét token vào query string. Cả
 * hai đều tệ hơn: cookie mở ra cả một lớp CSRF cho một tính năng đọc ảnh, còn token
 * trong URL thì nằm lại trong log của mọi proxy trên đường và trong lịch sử trình
 * duyệt — đúng thứ mà việc bảo vệ ảnh này đang cố tránh.
 *
 * NGƯỜI GỌI PHẢI thu hồi URL trả về bằng `URL.revokeObjectURL` khi không dùng nữa;
 * blob giữ toàn bộ ảnh trong bộ nhớ tới lúc đó.
 */
export async function fetchAuthedBlobUrl(path: string): Promise<string> {
  const token = getPlayerToken();
  if (!token) {
    throw new ApiError("UNAUTHORIZED", "no_token", 401, null);
  }

  const fetchBlob = async (accessToken: string): Promise<Blob> => {
    const response = await fetch(`${USER_API_BASE_URL}${path}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!response.ok) {
      throw new ApiError("UNKNOWN", `HTTP ${response.status}`, response.status, null);
    }
    return response.blob();
  };

  let blob: Blob;
  try {
    blob = await fetchBlob(token);
  } catch (error) {
    // Cùng lớp xoay vòng token một lần như authedRequest: ảnh trong lịch sử chat được
    // tải rải rác theo lúc người dùng cuộn, nên rất dễ rơi đúng vào lúc token vừa hết
    // hạn — và khi đó cả lịch sử ảnh hiện thành ô vỡ dù người dùng vẫn đang đăng nhập.
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    const tokens = await refreshTokens();
    blob = await fetchBlob(tokens.accessToken);
  }

  return URL.createObjectURL(blob);
}

/** Đường dẫn API (không gồm base) của một ảnh đính kèm đã lưu trong tin nhắn. */
export function chatAttachmentPath(attachmentUrl: string): string {
  // Backend lưu đường dẫn đầy đủ "/api/v1/chat/attachments/<tên>", còn base URL đã
  // chứa "/api/v1" — nối thẳng sẽ ra "/api/v1/api/v1/...".
  return attachmentUrl.replace(/^\/api\/v1/, "");
}

// ===== Xác thực =====

export interface LoginPayload {
  identifier: string;
  password: string;
  captchaToken?: string;
}

export async function login(payload: LoginPayload): Promise<TokenResponse> {
  const tokens = await request<TokenResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
  clearGuestSupportOnly();
  storeTokens(tokens);
  return tokens;
}

export interface RegisterPayload {
  username: string;
  password: string;
  /** Đúng 6 chữ số; bỏ trống thì người dùng đặt sau trong phần cài đặt. */
  withdrawalPassword?: string;
  referralCode?: string;
}

export async function register(payload: RegisterPayload): Promise<UserResponse> {
  return request<UserResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

/**
 * Đăng xuất: thu hồi refresh token ở server rồi dọn token trên máy.
 *
 * Dọn token ở `finally` để dù server có lỗi thì trên máy này vẫn đăng xuất được —
 * người bấm đăng xuất luôn phải được đăng xuất, đây là kỳ vọng về bảo mật.
 */
export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  try {
    if (refreshToken) {
      await request<void>("/auth/logout", {
        method: "POST",
        body: JSON.stringify({ refreshToken }),
      });
    }
  } catch {
    // Bỏ qua: token có thể đã bị thu hồi từ trước, vẫn coi là đăng xuất xong.
  } finally {
    clearPlayerTokens();
  }
}

/**
 * Lời gọi `/users/me` đang bay, dùng để DỒN TRÙNG.
 *
 * Header (lấy tên đăng nhập) và LanguageContext (lấy ngôn ngữ đã lưu) đều cần cùng
 * một hồ sơ và cùng chạy lúc trang khởi động, nên trước đây mỗi lần tải trang chủ
 * gửi HAI request y hệt nhau. Giữ lại promise đang bay để người gọi thứ hai dùng
 * chung kết quả.
 *
 * Xoá tham chiếu khi xong (kể cả khi lỗi) để lần gọi sau vẫn lấy dữ liệu mới —
 * đây là dồn trùng trong khoảnh khắc, KHÔNG phải cache lâu dài. Cache lâu dài sẽ
 * làm giao diện hiện thông tin cũ sau khi người dùng đổi hồ sơ.
 */
let pendingMe: Promise<UserResponse> | null = null;

/**
 * Hồ sơ người chơi đang đăng nhập.
 *
 * Ném ApiError 401 khi không có token, hoặc khi token hết hạn mà cũng không xoay
 * vòng được; chỗ gọi nên coi đó là "chưa đăng nhập" và dọn token, chứ KHÔNG hiện
 * thông báo lỗi — token hết hạn là chuyện bình thường, không phải sự cố.
 */
export function me(): Promise<UserResponse> {
  if (pendingMe) return pendingMe;

  pendingMe = authedRequest<UserResponse>("/users/me", {
    method: "GET",
  }).finally(() => {
    pendingMe = null;
  });

  return pendingMe;
}

/** Đổi ngôn ngữ hiển thị đã lưu của tài khoản. Backend chỉ nhận en/vi/zh/ja. */
export async function updateLocale(locale: string): Promise<UserResponse> {
  return authedRequest<UserResponse>("/users/me/locale", {
    method: "PATCH",
    body: JSON.stringify({ locale }),
  });
}

/** Mã ngôn ngữ mà backend lưu được. Dùng để lọc trước khi gọi updateLocale. */
export function isBackendLocale(locale: string): boolean {
  return BACKEND_LOCALES.has(locale);
}

export interface UpdateProfilePayload {
  /** Tối đa 100 ký tự. Chuỗi rỗng = xoá giá trị đã lưu. */
  fullName?: string;
  /** Mã ISO 3166-1 alpha-2 chữ IN HOA, ví dụ "VN". Chuỗi rỗng = xoá. */
  countryCode?: string;
  /** 6-20 ký tự gồm chữ số, '+', '-', khoảng trắng. Chuỗi rỗng = xoá. */
  phone?: string;
}

/**
 * Cập nhật hồ sơ: họ tên, quốc gia, số điện thoại.
 *
 * Trường KHÔNG gửi thì backend giữ nguyên; gửi chuỗi rỗng thì XOÁ. Nhờ vậy màn hình chỉ
 * cần gửi những ô người dùng thật sự sửa.
 *
 * KHÔNG đổi được tên đăng nhập ở đây, và cũng không có ô email: đổi email cần luồng xác
 * thực riêng vì cột `users.email` có ràng buộc unique.
 */
export async function updateProfile(
  payload: UpdateProfilePayload
): Promise<UserResponse> {
  return authedRequest<UserResponse>("/users/me/profile", {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

// ===== Bàn chơi và vòng quay =====

export interface GameTable {
  id: string;
  gameType: string;
  /** Tên bàn theo ngôn ngữ. Backend chỉ có en/vi/zh/ja, KHÔNG có ms/ko. */
  nameI18n: Record<string, string>;
  status: string;
  minBet: string;
  maxBet: string;
  currency: string;
}

export interface GameRound {
  roundId: string;
  tableId: string;
  roundSeq: number;
  /** BETTING_OPEN | BETTING_CLOSED | SPINNING | RESULT | SETTLE */
  phase: string;
  /** OPEN | SETTLED | VOIDED */
  status: string;
  /** Chỉ Roulette. Một số duy nhất 0–36. */
  winningNumber: number | null;
  baccaratPlayerCards: string | null;
  baccaratBankerCards: string | null;
  baccaratPlayerScore: number | null;
  baccaratBankerScore: number | null;
  baccaratPlayerPair: boolean | null;
  baccaratBankerPair: boolean | null;
  baccaratResult: string | null;
  /** Lucky 28 và biến thể. Ba số cách nhau bằng dấu phẩy, ví dụ "7,12,3". */
  kl28Numbers: string | null;
  kl28Sum: number | null;
  /**
   * Thời điểm ván BẮT ĐẦU.
   *
   * Đây là thứ phải dùng làm "giờ ván" trên màn hình, KHÔNG phải `serverTime`:
   * `serverTime` là lúc API trả lời, nên hai ván đọc trong cùng một giây sẽ hiện ra
   * cùng giờ dù chúng cách nhau cả vòng.
   *
   * Không bao giờ null, kể cả với ván đã đóng — khác `roundEndsAt`.
   */
  startedAt: string;
  /**
   * Thời điểm pha hiện tại kết thúc. Null khi vòng đã đóng.
   *
   * Trong pha `BETTING_OPEN`, đây chính là lúc đóng cửa cược.
   */
  phaseEndsAt: string | null;
  /**
   * Thời điểm cả vòng kết thúc. Null khi vòng đã đóng.
   *
   * Muộn hơn `phaseEndsAt` vì còn các pha quay số, kết quả và thanh toán. Là mốc
   * ƯỚC TÍNH — chỉ dùng để vẽ đồng hồ, không dùng để quyết định nghiệp vụ.
   */
  roundEndsAt: string | null;
  /**
   * Độ dài trọn một ván, tính bằng giây.
   *
   * Lấy từ cấu hình server. Đừng viết cứng ở frontend: cấu hình hiện tại là 63 giây,
   * không phải một phút chẵn, và đổi cấu hình mà quên sửa frontend là hiện sai.
   */
  roundSeconds: number;
  /** Giờ server lúc trả phản hồi, dùng để bù lệch đồng hồ máy người dùng. */
  serverTime: string;
}

/** Danh sách bàn đang mở. Cần đăng nhập. */
export async function gameTables(): Promise<GameTable[]> {
  return authedRequest<GameTable[]>("/games/tables", { method: "GET" });
}

/**
 * Vòng đang chạy của một bàn. Cần đăng nhập.
 *
 * Ném ApiError với `code: "ROUND_NOT_FOUND"` (404) ở khoảnh khắc giữa hai vòng —
 * vòng cũ vừa đóng mà vòng mới chưa tạo. Đây là trạng thái BÌNH THƯỜNG, chỗ gọi
 * nên coi là "chưa có dữ liệu" chứ không phải sự cố.
 */
export async function currentRound(tableId: string): Promise<GameRound> {
  return authedRequest<GameRound>(`/games/tables/${tableId}/rounds/current`, {
    method: "GET",
  });
}

/** Một trang kết quả có phân trang, khớp `PageResponse` của backend. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/**
 * Lịch sử các vòng đã xong của một bàn, mới nhất trước.
 *
 * Chỉ trả vòng SETTLED và VOIDED — vòng đang chạy không nằm ở đây, phải dùng
 * `currentRound`. Trang đặt cược dùng `size: 1` để lấy riêng ván trước.
 */
export async function roundsHistory(
  tableId: string,
  page = 0,
  size = 20
): Promise<Page<GameRound>> {
  return authedRequest<Page<GameRound>>(
    `/games/tables/${tableId}/rounds?page=${page}&size=${size}`,
    { method: "GET" }
  );
}

export interface Wallet {
  walletId: string;
  userId: string;
  /** Chuỗi thập phân, KHÔNG phải số. Xem ghi chú ở `placeBet`. */
  balance: string;
  currency: string;
}

/**
 * Ví của người đang đăng nhập.
 *
 * Đường dẫn là `/wallet/me` — SỐ ÍT, khác với `/games/...` số nhiều. Theo đúng
 * `@RequestMapping` của `WalletController`.
 */
export async function walletMe(): Promise<Wallet> {
  return authedRequest<Wallet>("/wallet/me", { method: "GET" });
}

/**
 * Mã giới thiệu của chính người chơi, khớp `ReferralCodeResponse` của backend.
 *
 * `registerPath` là ĐƯỜNG DẪN TƯƠNG ĐỐI ("/register?ref=ABC123"), không phải URL
 * đầy đủ. Backend không biết tên miền công khai của frontend (nó ở sau reverse
 * proxy) nên không thể tự ghép. Phía client phải nối thêm origin.
 */
export interface ReferralCode {
  code: string;
  registerPath: string;
}

/**
 * Mã giới thiệu của tôi.
 *
 * Backend SINH MÃ Ở LẦN GỌI ĐẦU TIÊN rồi lưu lại (xem `MyAffiliateService.myCode`),
 * nên gọi lại luôn trả đúng một mã. KHÔNG được sinh mã ở phía client: mã phải
 * duy nhất toàn hệ thống và phải tra cứu được ngược về chủ sở hữu khi tính hoa hồng.
 */
export async function myReferralCode(): Promise<ReferralCode> {
  return authedRequest<ReferralCode>("/affiliate/me/code", { method: "GET" });
}

/**
 * Tổng quan hoa hồng của tôi, khớp `MyAffiliateSummaryResponse`.
 *
 * Các trường tiền và tỷ lệ là CHUỖI, không phải số: backend dùng BigDecimal scale 8
 * và `number` của JS làm tròn sai ở các số lẻ.
 */
export interface AffiliateSummary {
  code: string;
  level1Count: number;
  level2Count: number;
  totalEarned: string;
  level1Rate: string;
  level2Rate: string;
}

export async function myAffiliateSummary(): Promise<AffiliateSummary> {
  return authedRequest<AffiliateSummary>("/affiliate/me/summary", { method: "GET" });
}

export interface PlayerBet {
  id: string;
  roundId: string;
  tableId: string;
  betType: string;
  selection: string | null;
  stake: string;
  status: string;
  payout: string;
  createdAt: string;
}

/** Cược của chính mình trong một vòng. */
export async function myBets(roundId: string): Promise<PlayerBet[]> {
  return authedRequest<PlayerBet[]>(`/games/me/bets?roundId=${roundId}`, {
    method: "GET",
  });
}

/**
 * Lịch sử cược của chính mình, mới nhất trước.
 *
 * `tableId` là tuỳ chọn: bỏ trống thì trả cược ở MỌI bàn. Trang lịch sử cược của một bàn
 * truyền vào để chỉ xem bàn đó.
 */
export async function betsHistory(
  tableId?: string,
  page = 0,
  size = 20
): Promise<Page<PlayerBet>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (tableId) params.set("tableId", tableId);

  return authedRequest<Page<PlayerBet>>(`/games/me/bets/history?${params}`, {
    method: "GET",
  });
}

export interface PlaceBetPayload {
  betType: string;
  selection?: string | null;
  /** Chuỗi thập phân. Xem ghi chú bên dưới. */
  stake: string;
  /** Tăng dần trong mỗi vòng. Xem ghi chú bên dưới. */
  seq: number;
}

export interface BetResult {
  id: string;
  roundId: string;
  betType: string;
  selection: string | null;
  stake: string;
  status: string;
  balanceAfter: string;
}

/**
 * Đặt MỘT cược.
 *
 * `stake` và `balanceAfter` là CHUỖI chứ không phải số, theo đúng backend. Tiền
 * không được đi qua `number` của JavaScript: `0.1 + 0.2` cho `0.30000000000000004`,
 * và số nguyên lớn hơn 2^53 bị làm tròn. Chỉ chuyển sang số khi cần hiển thị.
 *
 * `seq` là KHOÁ CHỐNG TRÙNG: backend dựng khoá `BET:{roundId}:{userId}:{seq}`. Gửi
 * hai cược KHÁC NHAU cùng một `seq` trong một vòng thì cược thứ hai bị coi là gửi
 * lại của cược thứ nhất và BỊ Bỏ LẶNG LẺ — người chơi mất cược mà không thấy lỗi.
 * Chỗ gọi PHẢI tăng `seq` cho từng cược và đặt lại về 0 khi sang vòng mới.
 *
 * Server là nơi quyết định: nó tự kiểm số dư, hạn mức cược của bàn, và pha hiện
 * tại có còn nhận cược hay không. Kiểm tra ở trình duyệt chỉ để đỡ một lượt gọi
 * vô ích, không thay được cho kiểm tra của server.
 */
export async function placeBet(
  tableId: string,
  payload: PlaceBetPayload
): Promise<BetResult> {
  return authedRequest<BetResult>(`/games/tables/${tableId}/bets`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

/** Tỷ lệ của một loại cược, đã áp tỷ lệ riêng của người chơi nếu có. */
export interface BetTypeOdds {
  betType: string;
  /** Odds LỢI trước hoa hồng, ví dụ "0.9800". */
  odds: string;
  /**
   * Hệ số THỰC NHẬN gồm tiền gốc — bằng đúng số tiền nhận về trên mỗi đồng cược.
   *
   * Với cửa Nhà băng Baccarat, hoa hồng 5% ĐÃ trừ ở đây: odds lợi 1 cho ra "1.95" chứ
   * không phải "2". Đừng tự cộng 1 vào `odds` để suy ra con số này — làm vậy sẽ hiện 2 và
   * người chơi nhận 1.95.
   */
  multiplier: string;
  /**
   * Tỷ lệ hoa hồng bị trừ, ví dụ "0.05"; null nếu cược này không chịu hoa hồng.
   *
   * Dùng để chú thích vì sao hệ số thấp hơn tỷ lệ niêm yết thông thường.
   */
  commissionRate: string | null;
  /** True nếu người chơi này được đặt tỷ lệ khác mức chung. */
  personalized: boolean;
}

/**
 * Tỷ lệ khi đoán đúng MỘT tổng cụ thể của Lucky 28.
 *
 * Tách khỏi `BetTypeOdds` vì đây là cược có `selection`: cùng một loại cược
 * (`KL28_NUMBER`) nhưng 28 tổng khác nhau có 28 tỷ lệ khác nhau, từ 12 tới 999.
 */
export interface NumberOdds {
  /** Tổng cần đoán, 0 tới 27. */
  sum: number;
  /** Odds LỢI, ví dụ "11.0000". */
  odds: string;
  /** Hệ số trả GỒM tiền gốc, ví dụ "12.0000" — đây là con số hiện cho người chơi. */
  multiplier: string;
}

export interface TableOdds {
  tableId: string;
  gameType: string;
  options: BetTypeOdds[];
  /** Rỗng ở bàn không phải Lucky 28. */
  numberOdds: NumberOdds[];
}

/**
 * Tỷ lệ cược hiệu lực của CHÍNH người đang đăng nhập ở một bàn.
 *
 * Phải gọi endpoint này thay vì dùng bảng tỷ lệ viết cứng ở frontend: mỗi người chơi có
 * thể được quản trị đặt tỷ lệ riêng, nên con số hiện trên màn hình chỉ đúng khi lấy từ
 * server. Hiển thị 1.98 trong lúc server trả 1.50 là thu tiền theo một tỷ lệ rồi trả theo
 * tỷ lệ khác.
 *
 * Không có tham số `userId`: server lấy từ JWT. Nếu nhận qua tham số thì ai cũng đọc được
 * tỷ lệ riêng của người khác bằng cách đổi số trên URL.
 */
export async function tableOdds(tableId: string): Promise<TableOdds> {
  return authedRequest<TableOdds>(`/games/tables/${tableId}/odds`, {
    method: "GET",
  });
}

// ===== Nạp tiền / Rút tiền =====

/** Hạn mức một lệnh nạp, khớp `DepositService.MIN_DEPOSIT` và `MAX_DEPOSIT`. */
export const DEPOSIT_MIN = "10";
export const DEPOSIT_MAX = "50000";

/**
 * Hạn mức rút, khớp `rwg.withdrawal.min-amount` và `daily-max-amount`.
 *
 * `WITHDRAW_DAILY_MAX` là TỔNG trong một ngày (UTC), không phải mỗi lệnh: rút hai lần
 * $3.000 sẽ bị chặn ở lần thứ hai dù mỗi lệnh đều dưới mức trần.
 */
export const WITHDRAW_MIN = "20";
export const WITHDRAW_DAILY_MAX = "5000";

/**
 * Một lệnh nạp hoặc rút.
 *
 * KHÔNG có `providerTxnId`: backend cố tình không trả mã giao dịch của cổng thanh toán
 * ra client vì đoán được mã đó là dựng được callback giả.
 */
export interface PaymentOrder {
  id: string;
  /** "DEPOSIT" | "WITHDRAWAL". */
  type: string;
  /** Chuỗi thập phân, KHÔNG phải số. */
  amount: string;
  currency: string;
  /**
   * Nạp: PENDING -> SUCCESS | FAILED.
   * Rút: PENDING -> SETTLED (admin duyệt) | VOIDED (bị từ chối, tiền đã hoàn).
   */
  status: string;
  /** Chỉ có ở lệnh rút. */
  bankAccountId: string | null;
  createdAt: string;
}

/** Một bút toán trong sổ ví. Dòng ghi có thì `debit` là "0...", và ngược lại. */
export interface WalletTransaction {
  id: string;
  createdAt: string;
  debit: string;
  credit: string;
  balanceAfter: string;
  /** BET | WIN | DEPOSIT | WITHDRAWAL | REFUND | BONUS | ADJUSTMENT | COMMISSION. */
  refType: string;
  refId: string | null;
  status: string;
}

/**
 * Phương thức nhận tiền: tài khoản ngân hàng hoặc ví tiền điện tử.
 *
 * Số tài khoản và địa chỉ ví LUÔN ở dạng che — backend không bao giờ trả bản đầy đủ
 * cho người chơi, kể cả chính chủ.
 */
export interface BankAccount {
  id: string;
  bankCode: string;
  /** "****1234" */
  maskedAccountNumber: string;
  holderName: string;
  isDefault: boolean;
  status: string;
  createdAt: string;
}

/**
 * Tạo lệnh nạp tiền.
 *
 * `amount` là CHUỖI thập phân, không phải số: tiền đi qua `Number` sẽ mất chính xác.
 */
export async function deposit(amount: string): Promise<PaymentOrder> {
  return authedRequest<PaymentOrder>("/wallet/deposits", {
    method: "POST",
    body: JSON.stringify({ amount }),
  });
}

/**
 * Tạo lệnh rút tiền.
 *
 * TIỀN BỊ TRỪ KHỎI VÍ NGAY khi lệnh được tạo, trước khi admin duyệt — người chơi sẽ
 * thấy số dư giảm lập tức trong lúc trạng thái vẫn là PENDING. Bị từ chối thì tiền
 * được hoàn lại bằng một bút toán REFUND.
 *
 * Ba lỗi thường gặp, cần xử lý riêng ở giao diện chứ không hiện chung một thông báo:
 * - `WITHDRAWAL_PASSWORD_NOT_SET`: chưa đặt mật khẩu rút tiền
 * - `BANK_ACCOUNT_REQUIRED`: chưa có phương thức nhận tiền đặt làm mặc định
 * - `WITHDRAWAL_LIMIT_EXCEEDED`: vượt tổng hạn mức trong ngày
 *
 * Gõ sai mật khẩu rút nhiều lần sẽ bị khoá tạm (429/423) — bộ đếm tách riêng khỏi
 * bộ đếm đăng nhập.
 *
 * `bankAccountId` KHÔNG bắt buộc: bỏ trống thì backend dùng tài khoản MẶC ĐỊNH. Truyền vào
 * thì backend kiểm tài khoản đó có đúng của người đang đăng nhập không, nên gửi id lạ sẽ bị
 * từ chối với 404 chứ không chuyển tiền đi đâu.
 */
export async function withdraw(
  amount: string,
  withdrawalPassword: string,
  bankAccountId?: string
): Promise<PaymentOrder> {
  return authedRequest<PaymentOrder>("/wallet/withdrawals", {
    method: "POST",
    body: JSON.stringify({ amount, withdrawalPassword, bankAccountId }),
  });
}

/**
 * Lịch sử LỆNH nạp/rút kèm trạng thái duyệt.
 *
 * Khác `walletTransactions`: hàm đó trả sổ cái ví gồm cả cược và thắng, còn hàm này chỉ
 * trả lệnh nạp/rút nhưng cho biết lệnh đã được duyệt hay còn treo.
 *
 * `type` lọc Ở SERVER, không lọc sau khi lấy về: bỏ bớt dòng ở phía client làm mỗi trang
 * còn số dòng khác nhau và tổng số trang không còn khớp những gì người dùng thấy.
 */
export async function paymentOrders(
  page = 0,
  size = 20,
  type?: "DEPOSIT" | "WITHDRAWAL"
): Promise<Page<PaymentOrder>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (type) params.set("type", type);

  return authedRequest<Page<PaymentOrder>>(`/wallet/me/orders?${params}`, {
    method: "GET",
  });
}

/** Sổ cái ví: mọi bút toán, mới nhất trước. */
export async function walletTransactions(
  page = 0,
  size = 20
): Promise<Page<WalletTransaction>> {
  return authedRequest<Page<WalletTransaction>>(
    `/wallet/me/transactions?page=${page}&size=${size}`,
    { method: "GET" }
  );
}

/** Các phương thức nhận tiền đã liên kết. */
export async function bankAccounts(): Promise<BankAccount[]> {
  return authedRequest<BankAccount[]>("/wallet/me/bank-accounts", {
    method: "GET",
  });
}

export interface AddBankAccountPayload {
  bankCode: string;
  accountNumber: string;
  holderName: string;
  setDefault?: boolean;
  withdrawalPassword: string;
}

/**
 * Thêm phương thức nhận tiền.
 *
 * Luật kiểm địa chỉ nằm ở backend (`PayoutAddressValidator`) vì mỗi mạng có định dạng
 * riêng — đừng chép luật đó sang frontend, hai bản sẽ lệch nhau.
 */
export async function addBankAccount(
  payload: AddBankAccountPayload
): Promise<BankAccount> {
  return authedRequest<BankAccount>("/wallet/me/bank-accounts", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

/** Gỡ phương thức nhận tiền (backend chỉ đánh dấu đã xoá, không xoá hẳn). */
export async function removeBankAccount(id: string): Promise<void> {
  return authedRequest<void>(`/wallet/me/bank-accounts/${id}`, {
    method: "DELETE",
  });
}

/**
 * Đặt hoặc đổi mật khẩu rút tiền.
 *
 * BẮT BUỘC xác nhận lại mật khẩu đăng nhập: nếu không, ai chiếm được phiên đang mở đều
 * đặt được mật khẩu rút rồi rút sạch ví.
 *
 * Mật khẩu mới dài 6 tới 72 ký tự (giới hạn của BCrypt).
 */
export async function setWithdrawalPassword(
  loginPassword: string,
  newWithdrawalPassword: string
): Promise<UserResponse> {
  return authedRequest<UserResponse>("/users/me/withdrawal-password", {
    method: "POST",
    body: JSON.stringify({ loginPassword, newWithdrawalPassword }),
  });
}

/** Kết quả kiểm mật khẩu rút tiền. */
export interface WithdrawalPasswordCheck {
  /** Mật khẩu có khớp hash đã đặt hay không. */
  valid: boolean;
  /** Số lần còn được thử trước khi bị khoá tạm. */
  attemptsRemaining: number;
}

/**
 * Kiểm mật khẩu rút tiền mà KHÔNG tạo lệnh rút.
 *
 * Dùng để chỉ bật nút gửi lệnh khi mật khẩu đã đúng, thay vì để người chơi bấm gửi rồi mới
 * biết sai.
 *
 * MẬT KHẨU SAI KHÔNG PHẢI LỖI: hàm trả về `{ valid: false }` chứ không ném. Chỉ ném khi
 * chưa đặt mật khẩu rút (`WITHDRAWAL_PASSWORD_NOT_SET`) hoặc đã bị khoá tạm (429/423).
 *
 * TIÊU NGÂN SÁCH CHỐNG DÒ: mỗi lần gõ sai đều trừ một lượt trong CÙNG bộ đếm mà
 * `withdraw()` dùng — gõ sai quá `10` lần là bị khoá 15 phút, và lệnh rút cũng bị khoá theo.
 * Vì vậy chỗ gọi PHẢI debounce và chỉ gọi khi mật khẩu đã đủ độ dài tối thiểu, đừng gọi
 * theo từng ký tự người dùng gõ.
 */
export async function verifyWithdrawalPassword(
  withdrawalPassword: string
): Promise<WithdrawalPasswordCheck> {
  return authedRequest<WithdrawalPasswordCheck>(
    "/users/me/withdrawal-password/verify",
    {
      method: "POST",
      body: JSON.stringify({ withdrawalPassword }),
    }
  );
}

// ===== Danh sách ngân hàng =====

export interface BankOption {
  /** Mã ngân hàng dạng chứ, ví dụ "VCB". Đây là giá trị gửi lên trong `bankCode`. */
  code: string;
  /**
   * Mã BIN dạng số, ví dụ "970436".
   *
   * Cần để nhận ra ngân hàng của các tài khoản liên kết từ trước: chúng lưu BIN thay vì
   * mã chứ, nên dò theo `code` một mình sẽ không khớp và hiện ra con số thô.
   */
  bin: string;
  /** Tên ngắn để hiển thị, ví dụ "Vietcombank". */
  shortName: string;
  /** Tên đầy đủ. */
  name: string;
  /** URL logo. Có thể là chuỗi rỗng. */
  logo: string;
}

/**
 * Tìm ngân hàng theo mã đã lưu trong tài khoản nhận tiền.
 *
 * DÒ THEO CẢ `code` VÀ `bin`: tài khoản thêm qua khu quản trị hoặc từ các phiên trước lưu
 * BIN dạng số ("970436"), còn tài khoản thêm từ trang mới lưu mã chứ ("VCB"). Chỉ so một
 * trong hai sẽ làm một nhóm tài khoản hiện mã thô thay vì tên ngân hàng.
 */
export function findBank(
  list: BankOption[],
  storedCode: string | null
): BankOption | null {
  if (!storedCode) return null;
  const needle = storedCode.trim().toUpperCase();
  return (
    list.find((b) => b.code.toUpperCase() === needle || b.bin === needle) ?? null
  );
}

/**
 * Danh sách ngân hàng Việt Nam.
 *
 * Gọi route trung gian `/api/banks` TRONG DỰ ÁN NÀY, không phải backend Java: dữ liệu đến
 * từ VietQR và route đó cache 24 giờ (xem `app/api/banks/route.ts`).
 *
 * KHÔNG dùng `authedRequest`: đây là dữ liệu công khai, và ép qua đường có token sẽ khiến
 * một lỗi 401 tình cờ làm hỏng cả ô chọn ngân hàng.
 *
 * NÉM LỖI khi không lấy được, thay vì trả mảng rỗng: chỗ gọi phải phân biệt được "chưa lấy
 * được danh sách" (thì cho gõ mã bằng tay) với "danh sách rỗng".
 */
export async function banks(): Promise<BankOption[]> {
  const res = await fetch("/api/banks", {
    headers: { Accept: "application/json" },
  });

  const body: unknown = await res.json().catch(() => null);

  if (!res.ok || typeof body !== "object" || body === null || !("banks" in body)) {
    const reason =
      typeof body === "object" && body !== null && "error" in body
        ? String((body as { error: unknown }).error)
        : `HTTP ${res.status}`;
    throw new ApiError("BANK_LIST_UNAVAILABLE", reason, res.status, null);
  }

  const list = (body as { banks: unknown }).banks;
  return Array.isArray(list) ? (list as BankOption[]) : [];
}

// ===== Quản lý token =====

export function storeTokens(tokens: TokenResponse): void {
  localStorage.setItem(PLAYER_TOKEN_KEY, tokens.accessToken);
  localStorage.setItem(PLAYER_REFRESH_KEY, tokens.refreshToken);
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event("auth_changed"));
  }
}

export function getPlayerToken(): string | null {
  // Hàm này bị gọi từ component client trong lúc render, mà lần render đầu chạy
  // trên server (không có localStorage) — thiếu guard này là lỗi runtime.
  if (typeof window === "undefined") return null;
  return localStorage.getItem(PLAYER_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(PLAYER_REFRESH_KEY);
}

export function clearPlayerTokens(): void {
  localStorage.removeItem(PLAYER_TOKEN_KEY);
  localStorage.removeItem(PLAYER_REFRESH_KEY);
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event("auth_changed"));
  }
}

/**
 * Định danh phiên (claim `sid`) trong access token đang giữ, hoặc null.
 *
 * DÙNG ĐỂ LÀM GÌ: server gửi gói "phiên đã bị thay thế" tới MỌI phiên WebSocket của một
 * người, kể cả phiên vừa mới tạo. Không so định danh thì một lần đăng nhập lại ngay trên
 * chính trình duyệt đang mở sẽ khiến tab cũ xoá token trong localStorage — mà token trong
 * đó lúc này là token MỚI vừa đăng nhập xong, tức tự đăng xuất chính mình.
 *
 * CHỈ ĐỌC, KHÔNG XÁC THỰC. Phần thân JWT là base64 nên ai cũng đọc được và sửa được; giá
 * trị này chỉ dùng để so hai chuỗi rồi quyết định có nên dọn phiên trên máy hay không.
 * Mọi quyết định về quyền vẫn do server làm, nơi chữ ký được kiểm.
 */
export function getPlayerSessionId(): string | null {
  const token = getPlayerToken();
  if (!token) return null;

  try {
    const payload = token.split(".")[1];
    if (!payload) return null;

    // JWT dùng base64url: `-` và `_` thay cho `+` và `/`, và bỏ phần đệm `=`. `atob`
    // chỉ hiểu base64 chuẩn nên phải chuyển lại, nếu không token có ký tự đó sẽ ném.
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");

    const claims = JSON.parse(atob(padded)) as { sid?: unknown };
    return typeof claims.sid === "string" ? claims.sid : null;
  } catch {
    // Token méo hoặc không phải JWT: coi như không có định danh phiên. Người gọi sẽ xử lý
    // như trường hợp token cũ chưa mang claim này.
    return null;
  }
}

export interface DbNotification {
  id: string;
  type: string;
  titleKey: string;
  paramsJson: string | null;
  body: string | null;
  broadcast: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export async function getNotifications(
  page: number = 0,
  size: number = 20
): Promise<PageResponse<DbNotification>> {
  return authedRequest<PageResponse<DbNotification>>(
    `/notifications?page=${page}&size=${size}`,
    { method: "GET" }
  );
}

export async function getUnreadNotificationsCount(): Promise<{ count: number }> {
  return authedRequest<{ count: number }>("/notifications/unread-count", {
    method: "GET",
  });
}

export async function markNotificationAsRead(id: string): Promise<void> {
  await authedRequest<void>(`/notifications/${id}/read`, {
    method: "POST",
  });
}

export async function markAllNotificationsAsRead(): Promise<{ updated: number }> {
  return authedRequest<{ updated: number }>("/notifications/read-all", {
    method: "POST",
  });
}

/* ==========================================================================
 * Chat hỗ trợ trực tiếp
 * ========================================================================== */

export interface ChatConversation {
  id: string;
  /** OPEN | CLOSED. */
  status: string;
  /** Số tin nhân sự gửi mà mình chưa xem. */
  unreadCount: number;
  lastMessageAt: string | null;
  createdAt: string;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  /** PLAYER | STAFF | SYSTEM. */
  senderType: "PLAYER" | "STAFF" | "SYSTEM";
  senderId: string | null;
  senderUsername: string | null;
  /**
   * Với PLAYER/STAFF là văn bản thô người gõ.
   * Với SYSTEM là KHOÁ DỊCH (vd "chat.system.assigned") — phải đưa qua `t()`
   * trước khi hiển thị, nếu không người dùng sẽ thấy đúng chuỗi khoá đó trên màn hình.
   *
   * CÓ THỂ RỖNG khi tin chỉ có ảnh — gửi ảnh không kèm chữ là hành vi bình thường.
   */
  body: string;
  /** Đường dẫn ảnh đính kèm; null nếu tin chỉ có chữ. */
  attachmentUrl: string | null;
  attachmentType: "IMAGE" | null;
  attachmentName: string | null;
  attachmentSize: number | null;
  readAt: string | null;
  clientMsgId: string | null;
  createdAt: string;
}

/** Kết quả tải một ảnh đính kèm lên, trước khi gửi tin. */
export interface ChatAttachment {
  url: string;
  name: string;
  size: number;
  type: "IMAGE";
}

/**
 * Sinh id chống gửi trùng cho một tin nhắn.
 *
 * `crypto.randomUUID` chỉ tồn tại trong ngữ cảnh bảo mật (https hoặc localhost).
 * Mở giao diện qua http trên IP LAN để thử trên điện thoại là trường hợp có thật
 * và ở đó hàm này không có — nên cần nhánh dự phòng, vì không có id nghĩa là mất
 * hẳn khả năng chống gửi trùng đúng lúc mạng kém nhất.
 */
export function newClientMsgId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/** Luồng hội thoại hỗ trợ của mình, backend tự tạo nếu chưa có. */
export async function getChatConversation(): Promise<ChatConversation> {
  return authedRequest<ChatConversation>("/chat/conversation", { method: "GET" });
}

/**
 * Một trang lịch sử tin nhắn, MỚI NHẤT TRƯỚC.
 *
 * @param before mốc `createdAt` của tin CŨ NHẤT đang hiển thị; bỏ trống để lấy trang
 *        đầu. Phân trang theo mốc thời gian thay vì số trang vì chat có tin mới chèn
 *        vào đầu liên tục, và số trang sẽ trôi khiến người dùng thấy tin lặp.
 */
export async function getChatMessages(before?: string): Promise<ChatMessage[]> {
  const query = before ? `?before=${encodeURIComponent(before)}` : "";
  return authedRequest<ChatMessage[]>(`/chat/messages${query}`, { method: "GET" });
}

/**
 * Gửi một tin nhắn, có thể kèm ảnh đã tải lên trước đó.
 *
 * @param body có thể rỗng NẾU có `attachment`.
 */
export async function sendChatMessage(
  body: string,
  clientMsgId: string,
  attachment?: ChatAttachment | null
): Promise<ChatMessage> {
  return authedRequest<ChatMessage>("/chat/messages", {
    method: "POST",
    body: JSON.stringify({
      body,
      clientMsgId,
      attachmentUrl: attachment?.url ?? null,
      attachmentName: attachment?.name ?? null,
      attachmentSize: attachment?.size ?? null,
    }),
  });
}

/**
 * Tải một ảnh lên, trả về đường dẫn để gửi kèm tin nhắn.
 *
 * Dùng `authedRequest` để dùng chung logic gắn token và làm mới token khi hết hạn.
 * KHÔNG đặt `Content-Type`: với FormData trình duyệt phải tự sinh header đó kèm
 * chuỗi boundary ngăn cách các phần. Tự đặt "multipart/form-data" thọt thì thiếu
 * boundary và server không tách được tệp — lỗi hiện ra là 400 không rõ nguyên nhân.
 */
export async function uploadChatAttachment(file: File): Promise<ChatAttachment> {
  const form = new FormData();
  form.append("file", file);
  return authedRequest<ChatAttachment>("/chat/attachments", {
    method: "POST",
    body: form,
  });
}

export async function markChatRead(): Promise<{ updated: number }> {
  return authedRequest<{ updated: number }>("/chat/read", { method: "POST" });
}

export async function getChatUnreadCount(): Promise<{
  messages: number;
  conversations: number;
}> {
  return authedRequest<{ messages: number; conversations: number }>(
    "/chat/unread-count",
    { method: "GET" }
  );
}

// ===== Phiên Hỗ trợ Khách & Payment Limits =====

export const GUEST_SUPPORT_ONLY_KEY = "rwg_guest_support_only";
export const GUEST_USERNAME_KEY = "rwg_guest_username";

export function isGuestSupportOnly(): boolean {
  if (typeof window === "undefined") return false;
  return localStorage.getItem(GUEST_SUPPORT_ONLY_KEY) === "true";
}

export function getGuestUsername(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(GUEST_USERNAME_KEY);
}

export function setGuestSupportOnly(username: string): void {
  if (typeof window !== "undefined") {
    localStorage.setItem(GUEST_SUPPORT_ONLY_KEY, "true");
    localStorage.setItem(GUEST_USERNAME_KEY, username);
  }
}

export function clearGuestSupportOnly(): void {
  if (typeof window !== "undefined") {
    localStorage.removeItem(GUEST_SUPPORT_ONLY_KEY);
    localStorage.removeItem(GUEST_USERNAME_KEY);
  }
}

export interface GuestSupportPayload {
  username: string;
}

export async function guestSupport(payload: GuestSupportPayload): Promise<TokenResponse> {
  const tokens = await request<TokenResponse>("/auth/guest-support", {
    method: "POST",
    body: JSON.stringify(payload),
  });
  setGuestSupportOnly(payload.username);
  storeTokens(tokens);
  return tokens;
}

export interface PaymentLimits {
  depositMin: string;
  depositMax: string;
  withdrawMin: string;
  withdrawDailyMax: string | null;
}

export async function paymentLimits(): Promise<PaymentLimits> {
  return request<PaymentLimits>("/payments/limits", { method: "GET" });
}

