"use client";

import { useCallback, useEffect, useState, useSyncExternalStore } from "react";

/**
 * Ép bàn Xóc Đĩa hiển thị ở thế NGANG trên điện thoại.
 *
 * VÌ SAO KHÔNG CHỈ GỌI `screen.orientation.lock("landscape")` RỒI XONG: API đó có hai
 * ràng buộc không thể vượt qua bằng mã nguồn.
 *
 *   - Trình duyệt CHỈ cho khoá hướng khi trang đang ở chế độ toàn màn hình. Gọi khi
 *     chưa fullscreen thì `lock()` ném lỗi ngay.
 *   - Safari iOS KHÔNG hỗ trợ API này trong bất kỳ trường hợp nào, kể cả khi đã
 *     fullscreen. Trên iPhone chỉ có nước nhắc người chơi tự xoay máy.
 *
 * Nên hook chạy hai lớp:
 *
 *   1. XOAY THẬT (Android Chrome): người chơi chạm vào lớp nhắc -> xin fullscreen rồi
 *      khoá "landscape". Việc này xoay màn hình thật. KHÔNG thể chạy ngay lúc vào game:
 *      trình duyệt bắt buộc phải có một thao tác của người dùng mới cho fullscreen.
 *   2. DỰ PHÒNG (iOS, hoặc máy đang bật khoá xoay của hệ điều hành): nếu máy vẫn ở thế
 *      dọc mà khoá không thành công, trả `needsRotate = true` để giao diện hiện lớp nhắc
 *      người chơi tự xoay ngang.
 *
 * LUÔN CÓ ĐƯỜNG THOÁT (`dismiss`). Máy nào bật khoá xoay trong cài đặt hệ điều hành thì
 * việc xoay máy KHÔNG làm đổi hướng màn hình. Nếu chỉ hiện lớp nhắc mà không cho thoát,
 * những người đó bị kẹt vĩnh viễn và không chơi được gì.
 */

/**
 * `ScreenOrientation.lock/unlock` là API KHÔNG CHUẨN (chỉ Chrome/Android cài đặt, Safari
 * không có) nên TypeScript không khai báo chúng trong lib.dom. Tự khai báo kiểu ở đây;
 * ở tầng chạy luôn kiểm tra `typeof === "function"` trước khi gọi.
 */
interface LockableOrientation {
  lock?: (orientation: "landscape" | "portrait" | "any") => Promise<void>;
  unlock?: () => void;
}

function getScreenOrientation(): LockableOrientation | undefined {
  if (typeof screen === "undefined") return undefined;
  return screen.orientation as unknown as LockableOrientation | undefined;
}

/**
 * Đối tượng MediaQueryList dùng chung cho cả `subscribe` lẫn `getSnapshot`.
 *
 * PHẢI tạo một lần rồi dùng lại: `getSnapshot` được React gọi rất thường xuyên, và
 * `window.matchMedia(...)` tạo đối tượng mới mỗi lần gọi nên giá trị trả về sẽ luôn
 * "đổi" theo phép so sánh tham chiếu, khiến React render lại vô hạn.
 */
function makeQueryMatcher(mediaQuery: string) {
  let query: MediaQueryList | null = null;

  const get = (): MediaQueryList | null => {
    if (typeof window === "undefined") return null;
    if (!query) query = window.matchMedia(mediaQuery);
    return query;
  };

  return {
    subscribe(callback: () => void): () => void {
      const mql = get();
      if (!mql) return () => {};
      mql.addEventListener("change", callback);
      return () => mql.removeEventListener("change", callback);
    },
    getSnapshot: (): boolean => get()?.matches ?? false,
    /** Trên server không có `window`: coi như màn hình ngang, không máy cảm ứng. */
    getServerSnapshot: (): boolean => false,
  };
}

const portraitQuery = makeQueryMatcher("(orientation: portrait)");
const coarsePointerQuery = makeQueryMatcher("(pointer: coarse)");

export interface ForceLandscapeState {
  /** Đang ở thế dọc trên máy cảm ứng, và chưa khoá được hướng -> cần nhắc xoay. */
  needsRotate: boolean;
  /** Xin toàn màn hình + khoá hướng ngang. Phải gọi trong một thao tác của người dùng. */
  requestLandscape: () => void;
  /** Người chơi chọn chơi tiếp ở thế dọc; ẩn lớp nhắc. */
  dismiss: () => void;
}

export function useForceLandscape(): ForceLandscapeState {
  // Dùng useSyncExternalStore thay vì useState + useEffect: matchMedia là một nguồn
  // trạng thái BÊN NGOÀI React, và đây là cách đúng để đăng ký theo dõi nó (tự xử lý cả
  // giá trị ban đầu lẫn khi giá trị đổi, không gọi setState trong thân effect).
  const isPortrait = useSyncExternalStore(
    portraitQuery.subscribe,
    portraitQuery.getSnapshot,
    portraitQuery.getServerSnapshot,
  );
  const isCoarsePointer = useSyncExternalStore(
    coarsePointerQuery.subscribe,
    coarsePointerQuery.getSnapshot,
    coarsePointerQuery.getServerSnapshot,
  );

  const [locked, setLocked] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  const requestLandscape = useCallback(() => {
    void (async () => {
      // Fullscreen PHẢI xin trước: chưa fullscreen thì bước khoá hướng bên dưới chắc
      // chắn thất bại. iOS không hỗ trợ fullscreen cho phần tử thường nên sẽ ném lỗi —
      // bắt lại và đi tiếp, cùng lắm thì rơi về lớp nhắc xoay.
      try {
        if (!document.fullscreenElement) {
          await document.documentElement.requestFullscreen();
        }
      } catch {
        /* Không hỗ trợ / bị từ chối — vẫn thử khoá hướng. */
      }

      try {
        const orientation = getScreenOrientation();
        if (orientation && typeof orientation.lock === "function") {
          await orientation.lock("landscape");
          setLocked(true);
        }
      } catch {
        /* Safari iOS không có lock() — giữ nguyên lớp nhắc xoay. */
      }
    })();
  }, []);

  // Rời khỏi bàn chơi: trả màn hình về bình thường. Không làm bước này thì người chơi
  // đi sang trang khác sẽ bị kẹt ở fullscreen + hướng ngang.
  useEffect(() => {
    return () => {
      try {
        const orientation = getScreenOrientation();
        if (orientation && typeof orientation.unlock === "function") {
          orientation.unlock();
        }
      } catch {
        /* Không hỗ trợ (iOS) — bỏ qua. */
      }
      if (document.fullscreenElement) {
        void document.exitFullscreen().catch(() => {});
      }
    };
  }, []);

  const dismiss = useCallback(() => setDismissed(true), []);

  return {
    needsRotate: isCoarsePointer && isPortrait && !locked && !dismissed,
    requestLandscape,
    dismiss,
  };
}
