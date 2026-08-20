"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { ShieldCheck, RotateCcw } from "lucide-react";
import { setAdminToken, adminFetch } from "@/lib/adminApi";
import { useTranslation } from "@/context/LanguageContext";
import { ADMIN_URL_PREFIX } from "@/lib/constants";

export default function AdminLoginPage() {
  const router = useRouter();
  const { setLocale } = useTranslation();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [captchaInput, setCaptchaInput] = useState("");
  const [captchaCode, setCaptchaCode] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setLocale("en");
  }, [setLocale]);

  const generateCaptcha = useCallback(() => {
    const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    let code = "";
    for (let i = 0; i < 5; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    setCaptchaCode(code);
    setCaptchaInput("");
  }, []);

  useEffect(() => {
    generateCaptcha();
  }, [generateCaptcha]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (
      !captchaInput.trim() ||
      captchaInput.trim().toUpperCase() !== captchaCode.toUpperCase()
    ) {
      setError("Verification code does not match.");
      generateCaptcha();
      return;
    }

    setLoading(true);

    try {
      const data = await adminFetch("/auth/login", {
        method: "POST",
        body: JSON.stringify({
          username: username.trim(),
          password: password.trim(),
        }),
      });

      if (data && data.token) {
        setAdminToken(data.token);
        router.push(ADMIN_URL_PREFIX);
      } else {
        setError("Login failed. Invalid response from server.");
        generateCaptcha();
      }
    } catch (err: any) {
      setError(err.message || "Invalid username or password.");
      generateCaptcha();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative w-full min-h-[100dvh] bg-[#0b5ed7] flex flex-col items-center justify-center px-4 py-12 overflow-hidden font-sans antialiased">
      {/* Oversized watermark mark */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 select-none"
      >
        <ShieldCheck
          className="h-[640px] w-[640px] text-white/[0.07]"
          strokeWidth={1}
        />
      </div>

      <main className="relative z-10 w-full max-w-[380px]">
        {/* Login card */}
        <section className="bg-white px-10 py-12">
          {/* Brand */}
          <div className="flex flex-col items-center gap-4">
            <ShieldCheck
              className="h-11 w-11 text-[#0b5ed7]"
              strokeWidth={2}
            />
            <h1 className="text-center text-[15px] font-semibold uppercase tracking-[0.14em] text-[#0b5ed7]">
              RWG Backoffice
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
              className="w-full border-b border-slate-300 bg-transparent pb-2 text-[13px] text-slate-800 outline-none transition-colors placeholder:text-slate-400 focus:border-[#0b5ed7]"
            />

            {/* Password */}
            <input
              id="admin-password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Password"
              className="w-full border-b border-slate-300 bg-transparent pb-2 text-[13px] text-slate-800 outline-none transition-colors placeholder:text-slate-400 focus:border-[#0b5ed7]"
            />

            {/* Verification code */}
            <div className="flex items-end gap-3">
              <input
                id="admin-captcha"
                type="text"
                required
                maxLength={5}
                autoComplete="off"
                value={captchaInput}
                onChange={(e) => setCaptchaInput(e.target.value.toUpperCase())}
                placeholder="Verification code"
                className="min-w-0 flex-1 border-b border-slate-300 bg-transparent pb-2 text-[13px] uppercase tracking-[0.14em] text-slate-800 outline-none transition-colors placeholder:tracking-normal placeholder:text-slate-400 focus:border-[#0b5ed7]"
              />

              <span className="select-none font-mono text-[15px] font-semibold tracking-[0.2em] text-slate-800">
                {captchaCode}
              </span>

              <button
                id="admin-refresh-captcha"
                type="button"
                onClick={generateCaptcha}
                aria-label="Get a new verification code"
                className="mb-0.5 text-slate-400 transition-colors hover:text-[#0b5ed7] cursor-pointer"
              >
                <RotateCcw className="h-3.5 w-3.5" />
              </button>
            </div>

            {/* Submit */}
            <button
              id="admin-login-submit"
              type="submit"
              disabled={loading}
              className="mt-3 w-full bg-[#0b5ed7] py-3 text-[12px] font-semibold uppercase tracking-[0.14em] text-white transition-colors hover:bg-[#0949a8] disabled:opacity-60 cursor-pointer"
            >
              {loading ? "Signing in" : "Login"}
            </button>
          </form>
        </section>

        {/* Footer */}
        <footer className="mt-8 text-center text-[11px] leading-relaxed text-white/70">
          <p>© Resorts World Genting 2026</p>
          <p className="mt-1">
            Authorized personnel only. All access attempts are logged.
          </p>
        </footer>
      </main>
    </div>
  );
}
