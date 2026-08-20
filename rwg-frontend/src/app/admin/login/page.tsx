"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import {
  ShieldCheck,
  Lock,
  User,
  Eye,
  EyeOff,
  LockKeyhole,
  ArrowRight,
  ShieldAlert,
  RotateCcw,
  ShieldQuestion,
} from "lucide-react";
import { setAdminToken, adminFetch } from "@/lib/adminApi";
import { useTranslation } from "@/context/LanguageContext";

export default function AdminLoginPage() {
  const router = useRouter();
  const { setLocale, t } = useTranslation();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [captchaInput, setCaptchaInput] = useState("");
  const [captchaCode, setCaptchaCode] = useState("");
  
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  // Default login page language to English ("en")
  useEffect(() => {
    setLocale("en");
  }, [setLocale]);

  // Generate random 4-character Captcha code
  const generateCaptcha = useCallback(() => {
    const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    let code = "";
    for (let i = 0; i < 4; i++) {
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

    // Validate Captcha Code
    if (!captchaInput.trim() || captchaInput.trim().toUpperCase() !== captchaCode.toUpperCase()) {
      setError("Captcha code is invalid. Please try again.");
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
        router.push("/admin");
      } else {
        setError("Login failed: Invalid token received from server");
        generateCaptcha();
      }
    } catch (err: any) {
      setError(
        err.message || "Invalid username or password. Please try again."
      );
      generateCaptcha();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full min-h-screen bg-slate-100 flex items-center justify-center p-4 sm:p-6 lg:p-8 font-sans antialiased select-none">
      {/* Outer Shell - Doppelrand Hardware Card */}
      <div className="p-2.5 sm:p-3 rounded-[2.5rem] bg-white border border-slate-200 shadow-2xl max-w-md w-full relative z-10 transition-all duration-700 ease-[cubic-bezier(0.32,0.72,0,1)]">
        
        {/* Inner Core Enclosure */}
        <div className="bg-white rounded-[calc(2.5rem-0.75rem)] border border-slate-200/60 p-6 sm:p-8 flex flex-col gap-6 shadow-xs">
          
          {/* Top Bar: Branding Logo */}
          <div className="flex items-center justify-between border-b border-slate-100 pb-4">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-red-600 to-red-700 border border-red-500/40 flex items-center justify-center text-white font-black text-sm shadow-md shadow-red-200">
                <ShieldCheck className="w-5 h-5 text-white" />
              </div>
              <div className="flex flex-col">
                <span className="text-slate-900 font-extrabold text-sm tracking-tight leading-tight">
                  RWG Admin
                </span>
                <span className="text-red-600 font-bold text-[9px] uppercase tracking-widest leading-none">
                  BACKOFFICE
                </span>
              </div>
            </div>
          </div>

          {/* Form Header */}
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center gap-2">
              <LockKeyhole className="w-5 h-5 text-red-600" />
              <h1 className="text-xl font-black text-slate-900 tracking-tight">
                {t("admin.login.title")}
              </h1>
            </div>
            <p className="text-xs text-slate-500 font-medium leading-relaxed">
              {t("admin.login.subtitle")}
            </p>
          </div>

          {/* Error Alert Box */}
          {error && (
            <div className="p-1 rounded-2xl bg-red-100/80 border border-red-200 shadow-xs animate-shake">
              <div className="bg-red-50 rounded-[calc(1rem-0.25rem)] p-3 text-xs text-red-700 font-bold flex items-start gap-2.5">
                <ShieldAlert className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                <span className="leading-normal">{error}</span>
              </div>
            </div>
          )}

          {/* Login Form */}
          <form onSubmit={handleLogin} className="flex flex-col gap-4">
            
            {/* Username Input */}
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-black text-slate-800 uppercase tracking-wider">
                {t("admin.login.username")}
              </label>
              <div className="p-1 rounded-2xl bg-slate-50 border border-slate-200 focus-within:border-red-500 transition-all shadow-xs">
                <div className="relative flex items-center bg-white rounded-[calc(1rem-0.25rem)]">
                  <User className="w-4 h-4 text-slate-400 absolute left-3.5 pointer-events-none" />
                  <input
                    type="text"
                    required
                    autoFocus
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="E.g. admin, finance_admin..."
                    className="w-full bg-transparent py-2.5 pl-10 pr-4 text-xs font-bold text-slate-900 placeholder-slate-400 outline-none"
                  />
                </div>
              </div>
            </div>

            {/* Password Input */}
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-black text-slate-800 uppercase tracking-wider">
                {t("admin.login.password")}
              </label>
              <div className="p-1 rounded-2xl bg-slate-50 border border-slate-200 focus-within:border-red-500 transition-all shadow-xs">
                <div className="relative flex items-center bg-white rounded-[calc(1rem-0.25rem)]">
                  <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 pointer-events-none" />
                  <input
                    type={showPassword ? "text" : "password"}
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••••••"
                    className="w-full bg-transparent py-2.5 pl-10 pr-10 text-xs font-bold text-slate-900 placeholder-slate-400 outline-none"
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

            {/* Captcha Security Code Verification Block */}
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-black text-slate-800 uppercase tracking-wider flex items-center justify-between">
                <span>Verification Code (Captcha)</span>
                <span className="text-[10px] text-slate-400 font-medium">Bot Protection</span>
              </label>
              
              <div className="grid grid-cols-12 gap-2.5 items-center">
                {/* Captcha Input */}
                <div className="col-span-7 p-1 rounded-2xl bg-slate-50 border border-slate-200 focus-within:border-red-500 transition-all shadow-xs">
                  <div className="relative flex items-center bg-white rounded-[calc(1rem-0.25rem)]">
                    <ShieldQuestion className="w-4 h-4 text-slate-400 absolute left-3.5 pointer-events-none" />
                    <input
                      type="text"
                      required
                      maxLength={4}
                      value={captchaInput}
                      onChange={(e) => setCaptchaInput(e.target.value.toUpperCase())}
                      placeholder="4-character code"
                      className="w-full bg-transparent py-2.5 pl-10 pr-3 text-xs font-black tracking-widest uppercase text-slate-900 placeholder-slate-400 outline-none"
                    />
                  </div>
                </div>

                {/* Captcha Display Preview Box */}
                <div className="col-span-5 flex items-center gap-1.5 bg-gradient-to-r from-red-950 to-slate-950 p-1.5 rounded-2xl border border-slate-800 shadow-xs relative overflow-hidden">
                  <div className="flex-1 bg-black/60 rounded-xl py-2 px-3 text-center tracking-[0.25em] font-mono font-black text-sm text-red-400 select-none border border-red-900/40 relative">
                    <span className="line-through decoration-red-600/50">{captchaCode}</span>
                  </div>
                  <button
                    type="button"
                    onClick={generateCaptcha}
                    title="Refresh code"
                    className="p-2 rounded-xl bg-white/10 hover:bg-white/20 text-slate-300 hover:text-white transition-all active:scale-95"
                  >
                    <RotateCcw className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>

            {/* Submit Button - Button-in-Button Architecture */}
            <button
              type="submit"
              disabled={loading}
              className="w-full group p-1.5 rounded-full bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 text-white font-black text-xs uppercase tracking-wider transition-all duration-500 ease-[cubic-bezier(0.32,0.72,0,1)] shadow-md shadow-red-200 active:scale-[0.98] disabled:opacity-50 flex items-center justify-between cursor-pointer mt-2"
            >
              <span className="pl-6 py-2">
                {loading ? "Authenticating..." : t("admin.login.submit")}
              </span>
              
              {/* Button-in-Button Trailing Icon Badge */}
              <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center text-white transition-transform duration-500 group-hover:translate-x-0.5 group-hover:scale-105">
                {loading ? (
                  <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                ) : (
                  <ArrowRight className="w-4 h-4" />
                )}
              </div>
            </button>
          </form>

          {/* Security Footnote */}
          <div className="pt-2 border-t border-slate-100 text-center text-[11px] text-slate-400 font-medium">
            Captcha Bot Protection & 256-bit JWT • RWG Backoffice 2026
          </div>
        </div>

      </div>
    </div>
  );
}
