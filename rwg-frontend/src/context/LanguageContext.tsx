"use client";

import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useMemo,
} from "react";
import { me, updateLocale, isBackendLocale } from "@/lib/playerApi";

/**
 * Chỉ tiếng Việt được nhúng SẴN vào bundle. Năm ngôn ngữ còn lại nạp theo yêu cầu.
 *
 * VÌ SAO: sáu file dịch cộng lại 148 KB, và trước đây cả sáu đều bị `import` tĩnh
 * nên mọi người dùng phải tải hết 148 KB dù chỉ đọc được một ngôn ngữ. Tiếng Việt
 * là mặc định nên phải có ngay từ lần vẽ đầu, không thể chờ tải; các ngôn ngữ khác
 * chỉ cần khi người dùng thực sự đổi.
 */
import vi from "@/locales/vi.json";

type Dictionary = { [key: string]: string | Dictionary };

/** Các mã ngôn ngữ được hỗ trợ. Giữ đúng thứ tự hiển thị của trang gốc. */
export const SUPPORTED_LOCALES = ["en", "zh", "vi", "ms", "ja", "ko"] as const;

/**
 * Hàm nạp cho từng ngôn ngữ.
 *
 * PHẢI viết `import()` với đường dẫn là hằng chuỗi ở từng nhánh, KHÔNG dùng
 * `import(\`@/locales/${code}.json\`)`: với đường dẫn động, bundler không biết
 * trước cần file nào nên gộp CẢ SÁU file vào một chunk — mất trắng lợi ích của
 * việc tách.
 */
const LOADERS: Record<string, () => Promise<{ default: Dictionary }>> = {
  en: () => import("@/locales/en.json"),
  zh: () => import("@/locales/zh.json"),
  ms: () => import("@/locales/ms.json"),
  ja: () => import("@/locales/ja.json"),
  ko: () => import("@/locales/ko.json"),
};

/**
 * Bộ nhớ đệm cấp module, KHÔNG phải state.
 *
 * Đặt ngoài component để từ điển đã nạp còn nguyên khi điều hướng giữa các trang;
 * nếu để trong state thì mỗi lần Provider bị dựng lại là phải tải lại.
 */
const cache: Record<string, Dictionary> = { vi: vi as Dictionary };

interface LanguageContextType {
  locale: string;
  setLocale: (lang: string) => void;
  t: (keyPath: string, params?: Record<string, string | number>) => string;
}

const LanguageContext = createContext<LanguageContextType>({
  locale: "vi",
  setLocale: () => {},
  t: (key) => key,
});

/** Nạp từ điển và ghi vào bộ đệm; trả về null nếu mã không hợp lệ hoặc tải lỗi. */
async function loadDictionary(code: string): Promise<Dictionary | null> {
  if (cache[code]) return cache[code];
  const loader = LOADERS[code];
  if (!loader) return null;
  try {
    const mod = await loader();
    cache[code] = mod.default;
    return mod.default;
  } catch {
    // Tải chunk lỗi (mất mạng giữa lúc đổi ngôn ngữ): giữ nguyên ngôn ngữ hiện tại
    // thay vì hiện một trang toàn khoá thô.
    return null;
  }
}

export const LanguageProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [locale, setLocaleState] = useState<string>("vi");
  // Tăng mỗi khi có từ điển mới vào bộ đệm, để React vẽ lại sau khi nạp xong.
  // Bộ đệm nằm ngoài React nên tự nó không kích hoạt render.
  const [, setCacheVersion] = useState(0);

  /** Đổi ngôn ngữ hiện tại, nạp từ điển trước khi áp dụng. */
  const applyLocale = useCallback(async (lang: string) => {
    const dict = await loadDictionary(lang);
    if (!dict) return;
    setCacheVersion((v) => v + 1);
    setLocaleState(lang);
  }, []);

  useEffect(() => {
    // Cờ chống đổi state sau khi Provider đã bị tháo: hai lời gọi bất đồng bộ dưới
    // đây có thể về sau thời điểm đó.
    let cancelled = false;

    /**
     * Áp dụng ngôn ngữ khi từ điển đã nằm trong bộ đệm.
     *
     * PHẢI gọi từ trong callback của promise, KHÔNG gọi thẳng ở thân effect: đổi
     * state ngay trong thân effect tạo thêm một vòng render ngay sau lần render đầu.
     */
    const commit = (lang: string, dict: Dictionary | null) => {
      if (cancelled || !dict) return;
      setCacheVersion((v) => v + 1);
      setLocaleState(lang);
    };

    // Khôi phục lựa chọn đã lưu. Đọc localStorage trong effect vì nó không tồn tại
    // trên server; đọc lúc render sẽ làm lệch kết quả hydrate.
    const saved = localStorage.getItem("rwg_locale");
    if (saved && saved !== "vi") {
      void loadDictionary(saved).then((dict) => commit(saved, dict));
    }

    // Lấy ngôn ngữ từ CSDL nếu người chơi đã đăng nhập.
    //
    // Dùng `me()` của playerApi chứ KHÔNG tự gọi fetch: hàm đó dồn trùng các lời gọi
    // đang bay, nên request này và request của Header (lấy tên đăng nhập) hợp thành
    // MỘT. Trước đây mỗi lần tải trang chủ gửi hai request y hệt nhau.
    //
    // `me()` tự từ chối với 401 khi không có token nên không cần kiểm tra trước.
    // Riêng phiên admin thì phải chặn: backend admin (cổng 8081) KHÔNG có route
    // /users/me — UserController bị loại khỏi rwg-admin-app — nên gọi chỉ nhận 404.
    if (!localStorage.getItem("rwg_admin_token")) {
      void me()
        .then((user) => {
          const lang = user.locale;
          // Bỏ qua mã lạ: CSDL có thể còn giá trị của một ngôn ngữ đã ngừng hỗ trợ.
          if (!lang || (lang !== "vi" && !LOADERS[lang])) return;
          localStorage.setItem("rwg_locale", lang);
          return loadDictionary(lang).then((dict) => commit(lang, dict));
        })
        .catch(() => {
          // Bỏ qua: chưa đăng nhập hoặc lỗi mạng, ngôn ngữ hiện tại vẫn dùng được.
        });
    }

    return () => {
      cancelled = true;
    };
  }, []);

  const setLocale = useCallback(
    (lang: string) => {
      if (lang !== "vi" && !LOADERS[lang]) return;

      localStorage.setItem("rwg_locale", lang);
      void applyLocale(lang);

      // Đồng bộ sang CSDL — CHỈ với bốn mã backend lưu được (en/vi/zh/ja).
      //
      // Giao diện có SÁU ngôn ngữ nhưng backend chỉ có bốn bundle thông báo, và
      // `UpdateLocaleRequest` chặn bằng regex `^(en|vi|zh|ja)$` nên gửi `ms` hay `ko`
      // sẽ nhận 400. Trước đây lỗi đó bị khối catch rỗng nuốt mất, khiến người chọn
      // tiếng Mã Lai hay tiếng Hàn thấy giao diện đổi nhưng lựa chọn KHÔNG được lưu —
      // đăng nhập ở máy khác là mất. Nay hai ngôn ngữ này được ghi nhận rõ là chỉ lưu
      // trên máy, không phải lỗi ngầm.
      //
      // Phiên admin thì bỏ qua hẳn: cổng admin không có route này.
      const isAdminSession = !!localStorage.getItem("rwg_admin_token");
      if (isBackendLocale(lang) && !isAdminSession) {
        updateLocale(lang).catch(() => {
          // Chưa đăng nhập hoặc mất mạng: lựa chọn đã nằm ở localStorage nên lần tải
          // sau vẫn đúng.
        });
      }
    },
    [applyLocale]
  );

  /**
   * Tra chuỗi theo đường dẫn khoá, thay thế tham số dạng `{tên}`.
   *
   * Có tham số là bắt buộc chứ không phải tiện lợi: câu như "39 tài khoản đang bị
   * khoá" nếu ghép tay từ một đoạn dịch với một con số thì sẽ sai ở những ngôn ngữ
   * đặt số ở vị trí khác. Cả câu phải nằm trong file dịch.
   */
  const t = useCallback(
    (keyPath: string, params?: Record<string, string | number>): string => {
      // Rơi về tiếng Việt khi từ điển đang tải: hiện chữ đọc được vẫn tốt hơn hiện
      // khoá thô kiểu "auth.login" trong khoảnh khắc chờ.
      const dict = cache[locale] ?? (vi as Dictionary);
      let val: string | Dictionary | undefined = dict;
      for (const k of keyPath.split(".")) {
        if (val && typeof val === "object" && k in val) {
          val = val[k];
        } else {
          return keyPath;
        }
      }
      if (typeof val !== "string") return keyPath;
      if (!params) return val;

      return val.replace(/\{(\w+)\}/g, (whole, name) =>
        // Giữ nguyên placeholder khi thiếu tham số: hiện "{count}" cho thấy có lỗi,
        // còn xoá thành chuỗi rỗng sẽ tạo ra câu thiếu nghĩa mà không ai phát hiện.
        name in params ? String(params[name]) : whole
      );
    },
    [locale]
  );

  // useMemo để các component con dùng useTranslation không bị vẽ lại mỗi lần
  // Provider render vì một nguyên nhân không liên quan.
  const value = useMemo(() => ({ locale, setLocale, t }), [locale, setLocale, t]);

  return (
    <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>
  );
};

export const useTranslation = () => useContext(LanguageContext);
