export const ADMIN_API_BASE = "http://localhost:8081/api/v1";

export const getAdminToken = (): string | null => {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("rwg_admin_token");
};

export const setAdminToken = (token: string) => {
  if (typeof window !== "undefined") {
    localStorage.setItem("rwg_admin_token", token);
  }
};

export const removeAdminToken = () => {
  if (typeof window !== "undefined") {
    localStorage.removeItem("rwg_admin_token");
  }
};

export async function adminFetch<T = any>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getAdminToken();
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  // If not FormData, set Content-Type to application/json
  if (!(options.body instanceof FormData) && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }

  const res = await fetch(`${ADMIN_API_BASE}${endpoint}`, {
    ...options,
    headers,
  });

  if (res.status === 401) {
    removeAdminToken();
    if (typeof window !== "undefined" && !window.location.pathname.startsWith("/admin/login")) {
      window.location.href = "/admin/login";
    }
    throw new Error("Phiên làm việc admin đã hết hạn, vui lòng đăng nhập lại.");
  }

  if (!res.ok) {
    let errorMsg = `Lỗi hệ thống (${res.status})`;
    try {
      const errorData = await res.json();
      errorMsg = errorData.message || errorData.detail || errorMsg;
    } catch {
      // ignore JSON parse error
    }
    throw new Error(errorMsg);
  }

  if (res.status === 24 || res.status === 204) {
    return {} as T;
  }

  return res.json();
}
