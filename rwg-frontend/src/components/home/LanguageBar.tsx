"use client";

import React from "react";
import { useTranslation } from "@/context/LanguageContext";

const LANGUAGES = [
  { code: "vi", name: "Tiếng Việt" },
  { code: "en", name: "English" },
  { code: "zh", name: "中文" },
  { code: "ms", name: "Malay" },
  { code: "ja", name: "日本語" },
  { code: "ko", name: "한국인" },
];

export const LanguageBar: React.FC = () => {
  const { locale, setLocale } = useTranslation();

  return (
    <div className="w-full my-4 px-4">
      <div className="flex flex-wrap items-center justify-center gap-2 text-xs py-2.5 text-gray-400 font-medium">
        {LANGUAGES.map((lang, idx) => (
          <React.Fragment key={lang.code}>
            <button
              onClick={() => setLocale(lang.code)}
              className={`transition-colors hover:text-white ${
                locale === lang.code
                  ? "text-red-500 font-bold"
                  : "text-gray-400"
              }`}
            >
              {lang.name}
            </button>
            {idx < LANGUAGES.length - 1 && (
              <span className="text-gray-600">|</span>
            )}
          </React.Fragment>
        ))}
      </div>
    </div>
  );
};
