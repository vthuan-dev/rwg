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
  ShieldAlert,
  KeyRound,
  Cpu,
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
    <div className="w-full min-h-screen bg-slate-100 flex items-center justify-center p-4 sm:p-6 lg:p-10 font-sans antialiased">
      {/* Outer Shell - Doppelrand / Double-Bezel Hardware Architecture */}
      <div className="p-2 sm:p-3 rounded-[2.5rem] bg-slate-200/80 ring-1 ring-slate-300/80 shadow-2xl shadow-slate-300/60 max-w-5xl w-full transition-all duration-700 ease-[cubic-bezier(0.32,0.72,0,1)]">
        
        {/* Inner Core Enclosure */}
        <div className="bg-white rounded-[calc(2.5rem-0.75rem)] overflow-hidden grid grid-cols-1 lg:grid-cols-12 min-h-[640px] shadow-[inset_0_1px_2px_rgba(255,255,255,1)]">
          
          {/* LEFT COLUMN: Ethereal Obsidian Hero Showcase */}
          <div className="lg:col-span-5 bg-gradient-to-br from-red-950 via-slate-950 to-black p-8 lg:p-10 flex flex-col justify-between relative overflow-hidden text-white select-none">
            
            {/* Mesh Glow Background Orbs */}
            <div className="absolute top-0 right-0 w-96 h-96 bg-red-600/15 rounded-full blur-3xl -mr-32 -mt-32 pointer-events-none" />
            <div className="absolute bottom-0 left-0 w-96 h-96 bg-red-900/15 rounded-full blur-3xl -ml-32 -mb-32 pointer-events-none" />

            {/* Top Branding Section */}
            <div className="flex flex-col gap-6 z-10">
              <div className="flex items-center gap-3.5">
                {/* Nested Icon Bezel */}
                <div className="p-1 rounded-2xl bg-white/10 ring-1 ring-white/20">
                  <div className="w-11 h-11 rounded-[calc(1rem-0.25rem)] bg-gradient-to-br from-red-600 to-red-700 flex items-center justify-center text-white font-black text-xl shadow-lg shadow-red-950/80">
                    <ShieldCheck className="w-6 h-6 text-white" />
                  </div>
                </div>
                <div className="flex flex-col">
                  <span className="text-white font-black text-lg tracking-tight leading-none">
                    Resorts World
                  </span>
                  <span className="text-red-500 font-extrabold text-[11px] uppercase tracking-widest leading-normal mt-1">
                    GENTING BACKOFFICE
                  </span>
                </div>
              </div>
            </div>

            {/* Center Interactive Security Showcase */}
            <div className="flex flex-col gap-6 my-8 z-10">
              {/* Eyebrow Status Pill */}
              <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-red-950/80 border border-red-800/60 text-red-400 text-[10px] uppercase tracking-[0.2em] font-black w-fit backdrop-blur-md shadow-sm">
                <Radio className="w-3.5 h-3.5 animate-pulse text-red-500" />
                <span>API 8081 SECURED GATEWAY</span>
              </div>

              <h2 className="text-2xl lg:text-3xl font-black text-white leading-tight tracking-tight">
                Hệ thống Vận hành & An toàn Quản trị Casino
              </h2>

              <p className="text-xs text-slate-300 font-medium leading-relaxed">
                Môi trường quản trị bảo mật cao cấp 256-bit mã hoá JWT, kiểm soát giao dịch 4 mắt và phát hiện gian lận đa tài khoản real-time.
              </p>

              {/* Double-Bezel Compliance Chips Grid */}
              <div className="flex flex-col gap-3 pt-2">
                <div className="p-1 rounded-2xl bg-white/5 border border-white/10">
                  <div className="bg-slate-900/60 backdrop-blur-md rounded-[calc(1rem-0.25rem)] p-3 flex items-center gap-3">
                    <div className="w-8 h-8 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 shrink-0">
                      <CheckCircle2 className="w-4 h-4" />
                    </div>
                    <div className="flex flex-col">
                      <span className="text-xs font-extrabold text-white">Quy trình 4 Mắt Duyệt Rút</span>
                      <span className="text-[10px] text-slate-400">Chặn tuyệt đối tự duyệt lệnh tài chính</span>
                    </div>
                  </div>
                </div>

                <div className="p-1 rounded-2xl bg-white/5 border border-white/10">
                  <div className="bg-slate-900/60 backdrop-blur-md rounded-[calc(1rem-0.25rem)] p-3 flex items-center gap-3">
                    <div className="w-8 h-8 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center text-red-400 shrink-0">
                      <Cpu className="w-4 h-4" />
                    </div>
                    <div className="flex flex-col">
                      <span className="text-xs font-extrabold text-white">Giám sát Sảnh Lucky 28</span>
                      <span className="text-[10px] text-slate-400">Kết nối trực tiếp WebSocket 60fps</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Bottom Footer Meta */}
            <div className="z-10 pt-4 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-400 font-medium">
              <span className="flex items-center gap-1.5">
                <KeyRound className="w-3.5 h-3.5 text-red-500" />
                <span>OAuth2 / JWT 256-bit</span>
              </span>
              <span className="text-slate-500 font-mono">v2.4.0 High-End</span>
            </div>
          </div>

          {/* RIGHT COLUMN: Soft Structuralism Light Login Form */}
          <div className="lg:col-span-7 p-8 lg:p-12 flex flex-col justify-between bg-white relative">
            
            {/* Header Toolbar: Language Switcher Pill */}
            <div className="flex items-center justify-end gap-2 mb-2">
              <div className="p-1 rounded-2xl bg-slate-100 border border-slate-200 shadow-xs">
                <div className="bg-white rounded-[calc(1rem-0.25rem)] px-3 py-1.5 flex items-center gap-2 border border-slate-200/80">
                  <Globe className="w-3.5 h-3.5 text-slate-500" />
                  <select
                    value={locale}
                    onChange={(e) => setLocale(e.target.value)}
                    className="bg-transparent text-xs font-extrabold text-slate-800 outline-none cursor-pointer"
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
            </div>

            {/* Main Form Center Enclosure */}
            <div className="flex flex-col gap-6 max-w-md mx-auto w-full">
              
              {/* Form Title & Eyebrow */}
              <div className="flex flex-col gap-2">
                <div className="px-3 py-1 rounded-full bg-red-50 border border-red-200 text-red-700 text-[10px] uppercase tracking-[0.2em] font-black w-fit">
                  BACKOFFICE GATEWAY
                </div>
                <h1 className="text-2xl lg:text-3xl font-black text-slate-900 tracking-tight">
                  {t("admin.login.title")}
                </h1>
                <p className="text-xs text-slate-500 font-medium">
                  {t("admin.login.subtitle")}
                </p>
              </div>

              {/* Error Alert Box */}
              {error && (
                <div className="p-1 rounded-2xl bg-red-100/60 border border-red-200 shadow-xs">
                  <div className="bg-red-50 rounded-[calc(1rem-0.25rem)] p-3.5 text-xs text-red-800 font-bold flex items-start gap-3">
                    <ShieldAlert className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                    <span className="leading-normal">{error}</span>
                  </div>
                </div>
              )}

              {/* Login Form Inputs */}
              <form onSubmit={handleLogin} className="flex flex-col gap-5">
                
                {/* Username Input - Double-Bezel Beveled Input */}
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-black text-slate-800 uppercase tracking-wider">
                    {t("admin.login.username")}
                  </label>
                  <div className="p-1 rounded-2xl bg-slate-100 border border-slate-200 focus-within:border-red-500 transition-all shadow-xs">
                    <div className="relative flex items-center bg-white rounded-[calc(1rem-0.25rem)]">
                      <User className="w-4 h-4 text-slate-400 absolute left-3.5 pointer-events-none" />
                      <input
                        type="text"
                        required
                        autoFocus
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        placeholder="Vd: admin, finance_admin..."
                        className="w-full bg-transparent py-3 pl-10 pr-4 text-xs font-bold text-slate-900 placeholder-slate-400 outline-none"
                      />
                    </div>
                  </div>
                </div>

                {/* Password Input - Double-Bezel Beveled Input */}
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-black text-slate-800 uppercase tracking-wider">
                    {t("admin.login.password")}
                  </label>
                  <div className="p-1 rounded-2xl bg-slate-100 border border-slate-200 focus-within:border-red-500 transition-all shadow-xs">
                    <div className="relative flex items-center bg-white rounded-[calc(1rem-0.25rem)]">
                      <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 pointer-events-none" />
                      <input
                        type={showPassword ? "text" : "password"}
                        required
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        placeholder="••••••••••••"
                        className="w-full bg-transparent py-3 pl-10 pr-10 text-xs font-bold text-slate-900 placeholder-slate-400 outline-none"
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
                </div>

                {/* Submit Button - Nested Button-in-Button Trailing Icon Architecture */}
                <button
                  type="submit"
                  disabled={loading}
                  className="w-full group p-1.5 rounded-full bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 text-white font-black text-xs uppercase tracking-wider transition-all duration-500 ease-[cubic-bezier(0.32,0.72,0,1)] shadow-lg shadow-red-200 active:scale-[0.98] disabled:opacity-50 flex items-center justify-between cursor-pointer mt-2"
                >
                  <span className="pl-6 py-2.5">
                    {loading ? "Đang xác thực bảo mật..." : t("admin.login.submit")}
                  </span>
                  
                  {/* Button-in-Button Trailing Icon Badge */}
                  <div className="w-9 h-9 rounded-full bg-white/20 flex items-center justify-center text-white transition-transform duration-500 group-hover:translate-x-0.5 group-hover:scale-105">
                    {loading ? (
                      <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    ) : (
                      <ArrowRight className="w-4 h-4" />
                    )}
                  </div>
                </button>
              </form>
            </div>

            {/* Bottom Compliance Footer */}
            <div className="mt-8 text-center text-[11px] text-slate-400 font-medium">
              Hệ thống được bảo mật bởi RWG Enterprise Security Gateway.
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
