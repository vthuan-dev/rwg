"use client";

import React, { createContext, useContext, useState, useEffect } from "react";

import vi from "@/locales/vi.json";
import en from "@/locales/en.json";
import zh from "@/locales/zh.json";
import ms from "@/locales/ms.json";
import ja from "@/locales/ja.json";
import ko from "@/locales/ko.json";

type Dictionary = typeof vi;

const dictionaries: Record<string, Dictionary> = {
  vi,
  en,
  zh,
  ms,
  ja,
  ko,
};

interface LanguageContextType {
  locale: string;
  setLocale: (lang: string) => void;
  t: (keyPath: string) => string;
}

const LanguageContext = createContext<LanguageContextType>({
  locale: "vi",
  setLocale: () => {},
  t: (key) => key,
});

export const LanguageProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [locale, setLocaleState] = useState<string>("vi");

  // Fetch locale from API & sync with DB on mount
  useEffect(() => {
    // 1. Initial fallback from localStorage
    const saved = localStorage.getItem("rwg_locale");
    if (saved && dictionaries[saved]) {
      setLocaleState(saved);
    }

    // 2. Fetch from DB if user is logged in
    const token =
      localStorage.getItem("rwg_token") || localStorage.getItem("rwg_admin_token");
    if (token) {
      const baseUrl = localStorage.getItem("rwg_admin_token")
        ? "http://localhost:8081/api/v1"
        : "http://localhost:8080/api/v1";

      fetch(`${baseUrl}/users/me`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
          if (data && data.locale && dictionaries[data.locale]) {
            setLocaleState(data.locale);
            localStorage.setItem("rwg_locale", data.locale);
          }
        })
        .catch(() => {
          // ignore network error
        });
    }
  }, []);

  // Update locale in state, localStorage, and sync to DB
  const setLocale = (lang: string) => {
    if (!dictionaries[lang]) return;

    setLocaleState(lang);
    localStorage.setItem("rwg_locale", lang);

    // Sync to DB via PATCH /api/v1/users/me/locale if logged in
    const token =
      localStorage.getItem("rwg_token") || localStorage.getItem("rwg_admin_token");
    if (token) {
      const baseUrl = localStorage.getItem("rwg_admin_token")
        ? "http://localhost:8081/api/v1"
        : "http://localhost:8080/api/v1";

      fetch(`${baseUrl}/users/me/locale`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ locale: lang }),
      }).catch(() => {
        // ignore sync error
      });
    }
  };

  const t = (keyPath: string): string => {
    const dict = dictionaries[locale] || vi;
    const keys = keyPath.split(".");
    let val: any = dict;
    for (const k of keys) {
      if (val && typeof val === "object" && k in val) {
        val = val[k];
      } else {
        return keyPath; // fallback
      }
    }
    return typeof val === "string" ? val : keyPath;
  };

  return (
    <LanguageContext.Provider value={{ locale, setLocale, t }}>
      {children}
    </LanguageContext.Provider>
  );
};

export const useTranslation = () => useContext(LanguageContext);
