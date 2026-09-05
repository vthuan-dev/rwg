"use client";

import React, { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { ShieldCheck, Eye, EyeOff } from "lucide-react";
import { setAdminToken, setAdminRefreshToken, adminFetch } from "@/lib/adminApi";
import { useTranslation } from "@/context/LanguageContext";
import { ADMIN_URL_PREFIX } from "@/lib/constants";

/**
 * Phản hồi đăng nhập, khớp `TokenResponse` của backend.
 *
 * Trường là `accessToken`/`refreshToken`, KHÔNG phải `token`.
 */
interface TokenResponse {
  accessToken?: string;
  refreshToken?: string;
}

export default function AdminLoginPage() {
  const router = useRouter();
  const { setLocale } = useTranslation();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setLocale("en");
  }, [setLocale]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    setLoading(true);

    try {
      // Endpoint RIÊNG của khu quản trị. App admin cố tình không đăng ký
      // /api/v1/auth/login (đó là cửa của người chơi) nên gọi vào đó sẽ nhận 404.
      const data = await adminFetch<TokenResponse>("/admin/auth/login", {
        method: "POST",
        // Backend nhận "identifier" (username HOẶC email), không phải "username".
        body: JSON.stringify({
          identifier: username.trim(),
          password: password.trim(),
        }),
        // Ở trang này, 401 nghĩa là sai mật khẩu — không phải phiên hết hạn.
        skipAuthRedirect: true,
      });

      // TokenResponse trả về accessToken/refreshToken, KHÔNG có field "token".
      if (data && data.accessToken) {
        setAdminToken(data.accessToken);
        if (data.refreshToken) {
          setAdminRefreshToken(data.refreshToken);
        }
        router.push(ADMIN_URL_PREFIX);
      } else {
        setError("Login failed. Invalid response from server.");
      }
    } catch (err: unknown) {
      const anyErr = err as { status?: number; code?: string; message?: string };
      const msg = anyErr?.message || "";
      if (
        anyErr?.status === 401 ||
        msg.includes("401") ||
        msg.toLowerCase().includes("unauthorized") ||
        anyErr?.code === "INVALID_CREDENTIALS"
      ) {
        setError("Tên đăng nhập hoặc mật khẩu không chính xác. Vui lòng kiểm tra lại.");
      } else {
        setError(msg || "Invalid username or password.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative w-full min-h-[100dvh] bg-[#6aa8f5] flex flex-col items-center justify-center px-4 py-12 overflow-hidden font-sans antialiased">
      {/* Oversized watermark mark */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 select-none"
      >
        <ShieldCheck
          className="h-[640px] w-[640px] text-white/[0.13]"
          strokeWidth={1}
        />
      </div>

      <main className="relative z-10 w-full max-w-[380px]">
        {/* Login card */}
        <section className="bg-white px-10 py-12">
          {/* Brand */}
          <div className="flex flex-col items-center gap-4">
            <ShieldCheck
              className="h-11 w-11 text-[#1e5fc4]"
              strokeWidth={2}
            />
            <h1 className="text-center text-[15px] font-semibold uppercase tracking-[0.14em] text-[#1e5fc4]">
              Admin
            </h1>
          </div>

          {error && (
            <p
              role="alert"
              className="mt-8 text-center text-[12px] font-medium text-[#c8102e]"
              data-error-tone="critical"
            >
              {error}
            </p>
          )}

          <form onSubmit={handleLogin} className="mt-10 flex flex-col gap-7">
            {/* Username */}
            <input
              id="admin-username"
              type="text"
              required
              autoFocus
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Username"
              className="w-full border-b border-slate-300 bg-transparent pb-2 text-[13px] text-slate-800 outline-none transition-colors placeholder:text-slate-400 focus:border-[#1e5fc4]"
            />

            {/* Password */}
            <div className="relative w-full">
              <input
                id="admin-password"
                type={showPassword ? "text" : "password"}
                required
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Password"
                className="w-full border-b border-slate-300 bg-transparent pb-2 pr-8 text-[13px] text-slate-800 outline-none transition-colors placeholder:text-slate-400 focus:border-[#1e5fc4]"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? "Hide password" : "Show password"}
                className="absolute right-0 top-1/2 -translate-y-1/2 p-1 text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
              >
                {showPassword ? (
                  <EyeOff className="w-4 h-4" />
                ) : (
                  <Eye className="w-4 h-4" />
                )}
              </button>
            </div>

            {/* Submit */}
            <button
              id="admin-login-submit"
              type="submit"
              disabled={loading}
              className="mt-3 w-full bg-[#1e5fc4] py-3 text-[12px] font-semibold uppercase tracking-[0.14em] text-white transition-colors hover:bg-[#174a9c] disabled:opacity-60 cursor-pointer"
            >
              {loading ? "Signing in" : "Login"}
            </button>
          </form>
        </section>

        {/* Footer */}
        <footer className="mt-8 text-center text-[11px] leading-relaxed text-white/85">
          <p>© Resorts World Genting 2026</p>
          <p className="mt-1">
            Authorized personnel only. All access attempts are logged.
          </p>
        </footer>
      </main>
    </div>
  );
}
