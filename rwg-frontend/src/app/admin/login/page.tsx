"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import {
  ShieldCheck,
  Lock,
  User,
  AlertCircle,
  Eye,
  EyeOff,
  Globe,
  Radio,
  CheckCircle2,
  LockKeyhole,
  ArrowRight,
} from "lucide-react";
import { setAdminToken, adminFetch } from "@/lib/adminApi";
import { useTranslation } from "@/context/LanguageContext";

export default function AdminLoginPage() {
  const router = useRouter();
  const { locale, setLocale, t } = useTranslation();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const languages = [
    { code: "vi", name: "Tiếng Việt", flag: "🇻🇳" },
    { code: "en", name: "English", flag: "🇬🇧" },
    { code: "zh", name: "中文", flag: "🇨🇳" },
  ];

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
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
        router.push("/admin");
      } else {
        setError("Đăng nhập thất bại: Không nhận được token từ hệ thống");
      }
    } catch (err: any) {
      setError(
        err.message || "Tên đăng nhập hoặc mật khẩu không chính xác. Vui lòng kiểm tra lại."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full min-h-screen bg-slate-100 flex items-center justify-center p-4 sm:p-6 lg:p-8 font-sans antialiased">
      {/* Main Split-Screen Container Card */}
      <div className="w-full max-w-5xl bg-white border border-slate-200 rounded-3xl overflow-hidden shadow-2xl grid grid-cols-1 lg:grid-cols-12 min-h-[620px]">
        {/* Left Side: Hero Branding Banner (Dark Red Luxury Theme) */}
        <div className="lg:col-span-5 bg-gradient-to-br from-red-950 via-slate-950 to-black p-8 lg:p-10 flex flex-col justify-between relative overflow-hidden text-white select-none">
          {/* Subtle Background Glow Accent */}
          <div className="absolute top-0 right-0 w-80 h-80 bg-red-600/10 rounded-full blur-3xl -mr-20 -mt-20 pointer-events-none" />
          <div className="absolute bottom-0 left-0 w-80 h-80 bg-red-900/10 rounded-full blur-3xl -ml-20 -mb-20 pointer-events-none" />

          {/* Top Logo */}
          <div className="flex items-center gap-3.5 z-10">
            <div className="w-11 h-11 rounded-2xl bg-gradient-to-br from-red-600 to-red-700 border border-red-400/40 flex items-center justify-center text-white font-black text-xl shadow-lg shadow-red-950/60">
              <ShieldCheck className="w-6 h-6 text-white" />
            </div>
            <div className="flex flex-col">
              <span className="text-white font-black text-lg tracking-tight leading-none">
                Resorts World
              </span>
              <span className="text-red-500 font-extrabold text-xs uppercase tracking-widest leading-normal mt-0.5">
                GENTING BACKOFFICE
              </span>
            </div>
          </div>

          {/* Center Showcase Info */}
          <div className="flex flex-col gap-5 my-8 z-10">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-red-950/80 border border-red-800/60 text-red-400 text-xs font-extrabold w-fit backdrop-blur-md">
              <Radio className="w-3.5 h-3.5 animate-pulse text-red-500" />
              <span>Admin API 8081 Secured Connection</span>
            </div>

            <h2 className="text-2xl lg:text-3xl font-black text-white leading-tight tracking-tight">
              Hệ thống Quản trị & Vận hành Casino Trực tuyến
            </h2>

            <p className="text-xs text-slate-300 font-medium leading-relaxed">
              Truy cập an toàn 256-bit mã hoá JWT, kiểm soát giao dịch 4 mắt, dò chùm gian lận IP/Device đa tài khoản.
            </p>

            {/* Feature Checkmarks */}
            <div className="flex flex-col gap-2.5 pt-2">
              <div className="flex items-center gap-2 text-xs text-slate-200 font-semibold">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Quy trình 4 mắt phê duyệt nạp rút</span>
              </div>
              <div className="flex items-center gap-2 text-xs text-slate-200 font-semibold">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Giám sát thời gian thực sảnh Lucky 28</span>
              </div>
              <div className="flex items-center gap-2 text-xs text-slate-200 font-semibold">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Bảo mật phiên đăng nhập mã hoá OAuth2/JWT</span>
              </div>
            </div>
          </div>

          {/* Bottom Copyright Notice */}
          <div className="z-10 pt-4 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-400 font-medium">
            <span>© 2026 RWG Enterprise</span>
            <span className="text-slate-500 font-mono">v2.4.0</span>
          </div>
        </div>

        {/* Right Side: Clean Light Mode Login Panel */}
        <div className="lg:col-span-7 p-8 lg:p-12 flex flex-col justify-between bg-white relative">
          {/* Top Bar Language Switcher */}
          <div className="flex items-center justify-end gap-2 mb-4">
            <div className="bg-slate-50 border border-slate-200 rounded-xl px-3 py-1.5 flex items-center gap-2 shadow-xs">
              <Globe className="w-3.5 h-3.5 text-slate-500" />
              <select
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
                    {l.flag} {l.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Center Form Container */}
          <div className="flex flex-col gap-6 max-w-md mx-auto w-full">
            {/* Header Form Title */}
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center gap-2">
                <LockKeyhole className="w-5 h-5 text-red-600" />
                <h1 className="text-xl lg:text-2xl font-black text-slate-900 tracking-tight">
                  {t("admin.login.title")}
                </h1>
              </div>
              <p className="text-xs text-slate-500 font-medium">
                {t("admin.login.subtitle")}
              </p>
            </div>

            {/* Error Notification Alert */}
            {error && (
              <div className="bg-red-50 border border-red-200 rounded-2xl p-4 text-xs text-red-700 font-semibold flex items-start gap-3 shadow-xs animate-shake">
                <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                <span className="leading-normal">{error}</span>
              </div>
            )}

            {/* Login Form */}
            <form onSubmit={handleLogin} className="flex flex-col gap-4">
              {/* Username Input */}
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-extrabold text-slate-800">
                  {t("admin.login.username")}
                </label>
                <div className="relative flex items-center">
                  <User className="w-4 h-4 text-slate-400 absolute left-3.5 pointer-events-none" />
                  <input
                    type="text"
                    required
                    autoFocus
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="Vd: admin, finance_admin..."
                    className="w-full bg-slate-50 border border-slate-200 focus:border-red-600 focus:bg-white rounded-xl py-3 pl-10 pr-4 text-xs font-bold text-slate-900 placeholder-slate-400 outline-none transition-all shadow-xs"
                  />
                </div>
              </div>

              {/* Password Input */}
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-extrabold text-slate-800">
                    {t("admin.login.password")}
                  </label>
                </div>
                <div className="relative flex items-center">
                  <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 pointer-events-none" />
                  <input
                    type={showPassword ? "text" : "password"}
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••••••"
                    className="w-full bg-slate-50 border border-slate-200 focus:border-red-600 focus:bg-white rounded-xl py-3 pl-10 pr-10 text-xs font-bold text-slate-900 placeholder-slate-400 outline-none transition-all shadow-xs"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3.5 text-slate-400 hover:text-slate-700 transition-colors"
                    aria-label="Toggle password visibility"
                  >
                    {showPassword ? (
                      <EyeOff className="w-4 h-4" />
                    ) : (
                      <Eye className="w-4 h-4" />
                    )}
                  </button>
                </div>
              </div>

              {/* Submit Button */}
              <button
                type="submit"
                disabled={loading}
                className="w-full py-3.5 rounded-xl bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 text-white font-black text-xs uppercase tracking-wider transition-all shadow-md shadow-red-200 active:scale-98 disabled:opacity-50 flex items-center justify-center gap-2 cursor-pointer mt-2"
              >
                {loading ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    <span>Đang xác thực bảo mật...</span>
                  </>
                ) : (
                  <>
                    <span>{t("admin.login.submit")}</span>
                    <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </button>
            </form>
          </div>

          {/* Bottom Security Footer Note */}
          <div className="mt-8 text-center text-[11px] text-slate-400 font-medium">
            Phiên đăng nhập được giám sát bởi hệ thống kiểm toán an ninh RWG Security.
          </div>
        </div>
      </div>
    </div>
  );
}
