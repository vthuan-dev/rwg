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

  useEffect(() => {
    const saved = localStorage.getItem("rwg_locale");
    if (saved && dictionaries[saved]) {
      setLocaleState(saved);
    }
  }, []);

  const setLocale = (lang: string) => {
    if (dictionaries[lang]) {
      setLocaleState(lang);
      localStorage.setItem("rwg_locale", lang);
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
