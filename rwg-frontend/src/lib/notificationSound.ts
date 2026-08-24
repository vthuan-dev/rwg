"use client";

/**
 * Âm thanh thông báo dùng chung cho toàn bộ giao diện.
 *
 * VÌ SAO TỔNG HỢP BẰNG WEB AUDIO, KHÔNG DÙNG TỆP MP3: một tiếng chuông hai nốt chỉ cần
 * vài dòng dao động ký, còn thêm tệp âm thanh nghĩa là thêm một tài nguyên phải tải,
 * phải đặt trong `public/`, và phải xử lý trường hợp tải lỗi.
 *
 * VÌ SAO MỘT AudioContext DÙNG CHUNG: trình duyệt giới hạn số AudioContext đồng thời
 * (Chrome khoảng 6). Tạo mới mỗi lần phát và không đóng — như bản cũ trong AdminHeader
 * đang làm — thì sau vài tiếng chuông việc khởi tạo bắt đầu ném lỗi và âm thanh tắt hẳn,
 * không có dấu hiệu gì ngoài một dòng warn trong console.
 */

/** Khoá lưu lựa chọn tắt/bật tiếng. */
const MUTE_KEY = "rwg_notification_muted";

/**
 * Khoảng lặng tối thiểu giữa hai tiếng chuông.
 *
 * Nhân sự có thể nhận một loạt tin trong vài trăm milli giây (người chơi gửi liên tiếp,
 * hoặc nhiều luồng cùng lúc). Phát chồng lên nhau thì thành tiếng ồn chứ không còn là
 * thông báo, nên bỏ qua các lần gọi quá gần nhau.
 */
const THROTTLE_MS = 1500;

let audioCtx: AudioContext | null = null;
let lastPlayedAt = 0;

/**
 * Đã có thao tác thật của người dùng chưa.
 *
 * CẦN vì trình duyệt chỉ cho phép tạo `AudioContext` ở trạng thái chạy được sau khi
 * người dùng đã bấm / chạm / gõ. Tạo trước thời điểm đó thì Chrome ghi cảnh báo
 * *"The AudioContext was not allowed to start"* vào console và context nằm treo mãi.
 *
 * Đó là điều xảy ra khi một tin nhắn đến trước cả cú bấm đầu tiên: `playNotificationChime`
 * dựng context, không phát ra tiếng nào, và để lại một cảnh báo trông như lỗi.
 */
let unlocked = false;

/**
 * Lấy AudioContext dùng chung.
 *
 * @param create chỉ đặt `true` khi đang ở TRONG một trình xử lý thao tác của người dùng.
 *        Ngoài ngữ cảnh đó, hàm trả về context đã có hoặc null — không dựng mới.
 */
function getAudioContext(create: boolean): AudioContext | null {
  if (typeof window === "undefined") return null;
  if (audioCtx) return audioCtx;
  if (!create) return null;

  const Ctor =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext?: typeof AudioContext })
      .webkitAudioContext;
  if (!Ctor) return null;

  try {
    audioCtx = new Ctor();
  } catch {
    // Trình duyệt từ chối tạo context (thường vì đã quá giới hạn). Âm thanh là thứ
    // phụ trợ, nên chịu mất tiếng chứ không để nó làm vỡ luồng thông báo.
    return null;
  }
  return audioCtx;
}

/** Người dùng có đang tắt tiếng không. */
export function isNotificationMuted(): boolean {
  if (typeof window === "undefined") return false;
  return localStorage.getItem(MUTE_KEY) === "true";
}

/** Đặt lựa chọn tắt/bật tiếng, trả về trạng thái mới. */
export function setNotificationMuted(muted: boolean): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(MUTE_KEY, muted ? "true" : "false");
}

/**
 * Mở khoá âm thanh sau thao tác đầu tiên của người dùng.
 *
 * Chính sách tự phát của trình duyệt không cho phát âm thanh trước khi người dùng có
 * tương tác thật (bấm, chạm, gõ). Không có bước này thì mọi tiếng chuông im lặng mà
 * KHÔNG báo lỗi — đúng kiểu lỗi khiến người ta đi tìm trong mã phát nhạc trong khi vấn
 * đề nằm ở chỗ khác.
 *
 * Context được dựng NGAY TRONG trình xử lý thao tác, không sớm hơn: đó là điều kiện để
 * trình duyệt cho nó chạy thay vì treo kèm một cảnh báo trong console.
 *
 * Gọi hàm này một lần lúc ứng dụng khởi động; nó tự tháo listener sau lần tương tác đầu.
 */
export function primeNotificationSound(): () => void {
  if (typeof window === "undefined") return () => {};

  const unlock = () => {
    unlocked = true;
    const ctx = getAudioContext(true);
    if (ctx && ctx.state === "suspended") {
      void ctx.resume().catch(() => {
        // Không làm gì được thêm; mất tiếng chuông không đáng để ném lỗi lên trên.
      });
    }
    detach();
  };

  const detach = () => {
    window.removeEventListener("pointerdown", unlock);
    window.removeEventListener("keydown", unlock);
    window.removeEventListener("touchstart", unlock);
  };

  window.addEventListener("pointerdown", unlock, { once: true });
  window.addEventListener("keydown", unlock, { once: true });
  window.addEventListener("touchstart", unlock, { once: true });

  return detach;
}

/**
 * Phát tiếng chuông thông báo hai nốt (E5 -> A5).
 *
 * Tự bỏ qua khi người dùng đã tắt tiếng, khi chưa có thao tác nào để mở khoá âm thanh,
 * hoặc khi vừa phát cách đây chưa lâu.
 */
export function playNotificationChime(): void {
  if (isNotificationMuted()) return;

  // Chưa có thao tác nào: trình duyệt sẽ không phát ra tiếng, và dựng context lúc này
  // chỉ để lại một cảnh báo trong console. Bỏ qua im lặng là đúng — tin nhắn vẫn hiện
  // trên màn hình, chỉ không có tiếng cho tới lần người dùng chạm vào trang.
  if (!unlocked) return;

  const now = Date.now();
  if (now - lastPlayedAt < THROTTLE_MS) return;

  const ctx = getAudioContext(true);
  if (!ctx) return;

  // Tab vừa được đánh thức thì context có thể bị treo lại: thử mở rồi vẫn phát. Không
  // mở được thì các nốt bên dưới đơn giản là không kêu.
  if (ctx.state === "suspended") {
    void ctx.resume().catch(() => {});
  }

  try {
    const playNote = (frequency: number, startTime: number, duration: number) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.type = "sine";
      osc.frequency.setValueAtTime(frequency, startTime);
      // Giảm dần theo hàm mũ, không cắt đột ngột: dừng một dao động ký đang ở biên độ
      // lớn tạo ra tiếng "tách" nghe rõ hơn cả nốt nhạc.
      gain.gain.setValueAtTime(0.15, startTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, startTime + duration);
      osc.start(startTime);
      osc.stop(startTime + duration);
    };

    const t0 = ctx.currentTime;
    playNote(659.25, t0, 0.3); // E5
    playNote(880.0, t0 + 0.12, 0.4); // A5

    lastPlayedAt = now;
  } catch (err) {
    console.warn("Không phát được tiếng chuông thông báo", err);
  }
}
