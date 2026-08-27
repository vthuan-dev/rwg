"use client";

import { useCallback, useEffect, useLayoutEffect, useRef } from "react";

/**
 * Khoảng cách tính từ đáy (px) vẫn được coi là "đang xem tin mới nhất".
 *
 * KHÔNG dùng 0: cuộn bằng bánh xe hoặc ngón tay gần như không bao giờ dừng đúng ở
 * scrollHeight, và trên màn hình có tỉ lệ phóng không nguyên (Windows 125%) phép tính
 * scrollHeight - scrollTop - clientHeight còn lệch vài phần lẻ pixel. Đòi đúng 0 sẽ
 * khiến việc ghim đáy tự nhả ra dù người dùng đang ở cuối hội thoại.
 */
const PIN_THRESHOLD_PX = 80;

/**
 * Thời gian (ms) sau khi mở hội thoại mà khung tin nhắn còn tự dán lại xuống đáy mỗi khung hình.
 *
 * VÌ SAO CẦN MỘT CỬA SỔ THỜI GIAN thay vì chỉ nghe từng sự kiện: chiều cao vùng tin
 * nhắn còn đổi nhiều lần sau lần vẽ đầu, và có những lần đổi KHÔNG phát ra sự kiện nào
 * mà hook bắt được — rõ nhất là ảnh đã nằm trong bộ đệm trình duyệt: nó phát sự kiện
 * tải xong NGAY trong lần vẽ đầu, trước khi hook kịp gắn listener, nên phần chiều cao
 * tăng thêm bị bỏ qua hoàn toàn và người dùng mở trang ra thấy dở dang ở giữa.
 *
 * 1200ms đủ cho ảnh khuyến mãi (vài trăm KB) và phông chữ web vẽ xong trên mạng chậm,
 * mà vẫn ngắn hơn khoảng thời gian một người kịp đọc rồi cuộn lên tìm tin cũ.
 */
const SETTLE_WINDOW_MS = 1200;

/**
 * Giữ khung tin nhắn ở đáy.
 *
 * VÌ SAO KHÔNG CHỈ CUỘN MỘT LẦN SAU KHI TẢI: chiều cao vùng tin nhắn còn đổi NHIỀU LẦN
 * sau lần vẽ đầu tiên, và mỗi lần đổi lại đẩy tin mới nhất ra khỏi tầm nhìn:
 *   - Ô nhập tự giãn theo nội dung (xem `autoGrow` bên ChatComposer) chạy trong effect
 *     SAU lần cuộn, làm vùng tin nhắn thấp đi trong khi scrollTop giữ nguyên.
 *   - Ảnh đính kèm và phông chữ web vẽ xong muộn hơn.
 *   - Bàn phím ảo trên điện thoại mở ra cắt mất nửa màn hình.
 * Kết quả là khung "đã cuộn xuống đáy" nhưng người dùng vẫn thấy dở dang ở giữa.
 *
 * Hook theo dõi ba nguồn thay đổi đó rồi tự cuộn lại, NHƯNG chỉ khi người dùng đang ở
 * sát đáy. Họ cuộn lên đọc lại tin cũ thì tuyệt đối không được giật về đáy — đó là lỗi
 * khó chịu nhất của các khung chat tự cuộn.
 *
 * @param ready   Đã tải xong lịch sử chưa. Cuộn khi còn spinner là cuộn một khung rỗng.
 * @param resetKey Đổi giá trị = đổi hội thoại: ghim lại đáy từ đầu. Phía quản trị chuyển
 *                 qua lại giữa nhiều luồng trong cùng một lần tải trang.
 */
export function useChatAutoScroll(ready: boolean, resetKey?: string | null) {
  const scrollRef = useRef<HTMLDivElement>(null);

  /** Người dùng đang ở sát đáy — điều kiện DUY NHẤT để hook được tự cuộn. */
  const pinned = useRef(true);

  /**
   * Tạm ngưng tự cuộn (dùng khi chèn tin CŨ vào đầu danh sách).
   *
   * Chèn vào đầu làm scrollHeight tăng vọt; nếu lúc đó vẫn đang ghim đáy thì hook sẽ
   * kéo thẳng xuống cuối và người dùng mất luôn đoạn vừa bấm xem.
   */
  const suspended = useRef(false);

  /**
   * Người dùng đã tự cuộn trong cửa sổ "ổn vị trí" chưa.
   *
   * NHẬN BIẾT QUA SỰ KIỆN NHẬP LIỆU (bánh xe, ngón tay, bàn phím) chứ KHÔNG qua sự kiện
   * cuộn: chính hook cũng đặt scrollTop nên nó phát ra sự kiện cuộn liên tục trong cửa
   * sổ này, và đọc chúng như thao tác của người dùng sẽ tự tắt việc dán đáy ngay lập tức.
   */
  const userTookOver = useRef(false);

  /**
   * Mốc thời gian bỏ qua sự kiện `scroll`.
   *
   * Cuộn mượt phát ra một chuỗi sự kiện scroll trong lúc chạy, và những sự kiện đầu
   * tiên còn cách đáy rất xa. Đọc chúng như "người dùng vừa cuộn lên" sẽ nhả ghim ngay
   * giữa animation do chính hook tạo ra.
   */
  const lockUntil = useRef(0);

  const scrollToBottom = useCallback((smooth = false) => {
    const el = scrollRef.current;
    if (!el) return;
    pinned.current = true;
    lockUntil.current = Date.now() + (smooth ? 700 : 120);
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? "smooth" : "auto" });
  }, []);

  /**
   * Ngưng tự cuộn cho tới khi gọi hàm trả về.
   *
   * Trả về hàm dọn thay vì một cặp bật/tắt: nơi gọi luôn nằm trong try/finally của một
   * lời gọi mạng, và quên gọi tắt sẽ làm khung chat mất hẳn khả năng tự cuộn cho tới
   * khi tải lại trang.
   */
  const suspendAutoScroll = useCallback(() => {
    suspended.current = true;
    return () => {
      suspended.current = false;
    };
  }, []);

  /**
   * Mở hội thoại: dán xuống đáy và GIỮ ở đó suốt SETTLE_WINDOW_MS.
   *
   * Cuộn một lần là không đủ. Ngay sau lần vẽ đầu còn ít nhất bốn thứ làm chiều cao
   * thay đổi: ô nhập tự giãn, ảnh đính kèm vẽ xong, phông chữ web thay phông dự phòng,
   * và với trang này là cả lời chào khuyến mãi tải về từ máy chủ rồi chèn thêm hai bong
   * bóng ở CUỐI danh sách. Mỗi lần như vậy đẩy tin mới nhất ra khỏi tầm nhìn.
   *
   * Vòng lặp theo khung hình bắt được MỌI thay đổi đó mà không phải kể tên từng nguyên
   * nhân — kể cả những nguyên nhân không phát ra sự kiện nào.
   *
   * NGƯỜI DÙNG LUÔN THẮNG: chạm vào bánh xe, màn hình hay bàn phím là vòng lặp dừng ngay,
   * không chờ hết cửa sổ. Bị khung chat kéo về đáy trong lúc đang đọc tin cũ là lỗi khó
   * chịu nhất của kiểu tự cuộn này.
   */
  useLayoutEffect(() => {
    if (!ready) return;

    const el = scrollRef.current;
    if (!el) return;

    pinned.current = true;
    userTookOver.current = false;
    scrollToBottom(false);

    const release = () => {
      userTookOver.current = true;
    };

    // Bánh xe và kéo ngón tay là ý muốn cuộn của người dùng; keydown bắt PageUp/Home
    // và mũi lên. passive vì không chặn hành vi mặc định nào.
    el.addEventListener("wheel", release, { passive: true });
    el.addEventListener("touchmove", release, { passive: true });
    el.addEventListener("keydown", release);

    const deadline = Date.now() + SETTLE_WINDOW_MS;
    let frame = 0;

    const settle = () => {
      const target = scrollRef.current;
      if (!target || userTookOver.current || suspended.current) return;

      // KHÔNG dùng scrollToBottom ở đây: hàm đó đẩy lockUntil thêm 120ms mỗi khung hình,
      // nên sau khi vòng lặp kết thúc thì các sự kiện cuộn thật của người dùng vẫn còn
      // bị bỏ qua một lúc.
      target.scrollTop = target.scrollHeight;

      if (Date.now() < deadline) {
        frame = requestAnimationFrame(settle);
      }
    };

    frame = requestAnimationFrame(settle);

    return () => {
      cancelAnimationFrame(frame);
      el.removeEventListener("wheel", release);
      el.removeEventListener("touchmove", release);
      el.removeEventListener("keydown", release);
    };
  }, [ready, resetKey, scrollToBottom]);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    const isNearBottom = () =>
      el.scrollHeight - el.scrollTop - el.clientHeight <= PIN_THRESHOLD_PX;

    const onScroll = () => {
      if (Date.now() < lockUntil.current) return;
      pinned.current = isNearBottom();
    };

    /** Dán lại xuống đáy — chỉ khi đang ghim và không bị tạm ngưng. */
    const stick = () => {
      if (suspended.current || !pinned.current) return;
      const target = scrollRef.current;
      if (!target) return;
      lockUntil.current = Date.now() + 120;
      target.scrollTop = target.scrollHeight;
    };

    el.addEventListener("scroll", onScroll, { passive: true });

    // Chiều cao KHUNG đổi: ô nhập giãn ra, bàn phím ảo mở, quay ngang máy.
    const resizeObserver = new ResizeObserver(stick);
    resizeObserver.observe(el);

    // NỘI DUNG đổi: thêm bong bóng mới, đổi src ảnh, chữ dài ra thêm dòng.
    // ResizeObserver trên khung KHÔNG bắt được những việc này vì hộp của khung không đổi.
    const mutationObserver = new MutationObserver(stick);
    mutationObserver.observe(el, {
      childList: true,
      subtree: true,
      characterData: true,
    });

    // Ảnh tải xong. `load` KHÔNG nổi bọt nên phải bắt ở pha capture; thiếu dòng này thì
    // mỗi ảnh vẽ ra sau khi cuộn lại đẩy phần dưới ra khỏi màn hình.
    el.addEventListener("load", stick, true);
    el.addEventListener("error", stick, true);

    // Phông chữ web thay phông dự phòng làm mọi dòng chữ đổi chiều cao một lượt.
    let fontsCancelled = false;
    void document.fonts?.ready.then(() => {
      if (!fontsCancelled) stick();
    });

    return () => {
      fontsCancelled = true;
      el.removeEventListener("scroll", onScroll);
      el.removeEventListener("load", stick, true);
      el.removeEventListener("error", stick, true);
      resizeObserver.disconnect();
      mutationObserver.disconnect();
    };
    // `ready`/`resetKey` nằm trong phụ thuộc vì khung chat của khu quản trị chỉ được vẽ
    // khi đã chọn một luồng — trước đó scrollRef.current là null và không gắn được gì.
  }, [ready, resetKey]);

  return { scrollRef, scrollToBottom, suspendAutoScroll };
}
