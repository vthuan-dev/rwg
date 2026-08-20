"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import { ShieldCheck, Lock, User, AlertCircle, ArrowRight } from "lucide-react";
import { setAdminToken } from "@/lib/adminApi";

export default function AdminLoginPage() {
  const router = useRouter();
  const [identifier, setIdentifier] = useState("banneradmin");
  const [password, setPassword] = useState("Pass123!@#");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      // Connect to Player/Auth backend on 8080 or 8081 for login
      const res = await fetch("http://localhost:8080/api/v1/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ identifier, password }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || "Sai tài khoản hoặc mật khẩu admin");
      }

      if (data.accessToken) {
        setAdminToken(data.accessToken);
        router.push("/admin");
      } else {
        throw new Error("Không nhận được token xác thực");
      }
    } catch (err: any) {
      setError(err.message || "Đăng nhập thất bại, vui lòng thử lại");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-md p-6 bg-[#0f0f13] border border-[#22222a] rounded-2xl shadow-2xl flex flex-col gap-6">
      {/* Header Logo */}
      <div className="flex flex-col items-center gap-3 text-center">
        <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-red-600 to-red-950 border border-red-500/50 flex items-center justify-center text-white shadow-xl shadow-red-950/60">
          <ShieldCheck className="w-8 h-8 text-white" />
        </div>
        <div className="flex flex-col">
          <h2 className="text-xl font-extrabold text-white tracking-tight">
            RWG Backoffice Portal
          </h2>
          <p className="text-xs text-gray-400 font-medium">
            Đăng nhập khu vực quản trị sàn Casino
          </p>
        </div>
      </div>

      {/* Error Alert */}
      {error && (
        <div className="bg-red-950/60 border border-red-800/80 rounded-xl p-3 flex items-center gap-3 text-xs text-red-300">
          <AlertCircle className="w-4 h-4 text-red-500 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Form */}
      <form onSubmit={handleLogin} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <label className="text-xs font-semibold text-gray-300">
            Tài khoản Admin
          </label>
          <div className="relative flex items-center">
            <User className="w-4 h-4 text-gray-500 absolute left-3.5" />
            <input
              type="text"
              required
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="Username hoặc Email Admin"
              className="w-full bg-[#16161c] border border-[#272730] focus:border-red-500 rounded-xl py-2.5 pl-10 pr-4 text-xs text-white placeholder-gray-500 outline-none transition-colors"
            />
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-xs font-semibold text-gray-300">
            Mật khẩu
          </label>
          <div className="relative flex items-center">
            <Lock className="w-4 h-4 text-gray-500 absolute left-3.5" />
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Nhập mật khẩu admin"
              className="w-full bg-[#16161c] border border-[#272730] focus:border-red-500 rounded-xl py-2.5 pl-10 pr-4 text-xs text-white placeholder-gray-500 outline-none transition-colors"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={loading}
          className="mt-2 w-full bg-gradient-to-r from-red-600 to-red-800 hover:from-red-500 hover:to-red-700 text-white font-bold py-3 rounded-xl text-xs flex items-center justify-center gap-2 transition-all active:scale-[0.98] shadow-lg shadow-red-950/50 disabled:opacity-50"
        >
          {loading ? (
            <span>Đang xác thực...</span>
          ) : (
            <>
              <span>Đăng nhập Quản trị</span>
              <ArrowRight className="w-4 h-4" />
            </>
          )}
        </button>
      </form>
    </div>
  );
}
