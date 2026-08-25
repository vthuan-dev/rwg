package com.rwg.identity.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.config.AdminPinProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kiểm mã xác nhận cho các thao tác quản trị KHÔNG HOÀN TÁC ĐƯỢC.
 *
 * <h2>GIỚI HẠN SỐ LẦN THỬ MỚI LÀ LỚP BẢO VỆ THẬT</h2>
 * Mã sáu chữ số chỉ có một triệu khả năng. Không giới hạn số lần thử thì một đoạn script dò
 * hết trong khoảng một giây, và khi đó việc có mã hay không cũng như nhau. Với 5 lần thử mỗi
 * 15 phút, dò hết một triệu khả năng cần khoảng năm năm.
 *
 * <h2>ĐẾM THEO TỪNG NGƯỜI QUẢN TRỊ, KHÔNG PHẢI TOÀN CỤC</h2>
 * Đếm toàn cục sẽ tạo ra một cách phá hoại rẻ tiền: một người quản trị (hoặc một token bị
 * đánh cắp) gõ sai liên tục là khóa thao tác của TẤT CẢ nhân sự còn lại. Đếm theo người thì
 * hậu quả của việc gõ sai chỉ giới hạn trong tài khoản đã gõ sai.
 */
@Service
public class AdminDestructivePinService {

    /** Số lần gõ sai được phép trước khi khóa. */
    private static final int MAX_ATTEMPTS = 5;

    /** Thời gian khóa sau khi hết số lần thử. */
    private static final Duration LOCK_WINDOW = Duration.ofMinutes(15);

    /**
     * Số lần gõ sai của từng người quản trị.
     *
     * TRONG BỘ NHỚ, không dùng Redis: đây là hạn chế đã biết và chấp nhận được. Chỉ có một
     * tiến trình phục vụ khu quản trị, và số người quản trị là hữu hạn nên bản đồ này không
     * phình. Nếu về sau chạy nhiều bản app quản trị thì phải chuyển sang Redis — lúc đó bộ
     * đếm chia theo từng tiến trình sẽ nhân số lần thử lên theo số bản đang chạy.
     */
    private final Map<UUID, Attempts> attempts = new ConcurrentHashMap<>();

    private final AdminPinProperties pinProperties;

    public AdminDestructivePinService(AdminPinProperties pinProperties) {
        this.pinProperties = pinProperties;
    }

    /** Số lần sai và thời điểm lần sai gần nhất. */
    private record Attempts(int count, Instant lastFailedAt) {
    }

    /**
     * Xác nhận mã. Ném lỗi nếu sai, chưa cấu hình, hoặc đang bị khóa vì gõ sai quá nhiều.
     *
     * @param adminId người đang thao tác — bộ đếm tính theo người này.
     * @param pin     mã do người vận hành gõ vào.
     */
    public void verify(UUID adminId, String pin) {
        if (!pinProperties.configured()) {
            // CHẶN chứ không cho qua. Cấu hình thiếu không được biến việc xóa tài khoản thành
            // thao tác một cú bấm — đó là chế độ "mở toang mặc định", kiểu sai sót cấu hình
            // tệ nhất vì nó không báo gì cho tới khi có thiệt hại.
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Chưa cấu hình mã xác nhận cho thao tác này",
                    null, "error.admin.pin.not_configured");
        }

        requireNotLocked(adminId);

        if (pin == null || !constantTimeEquals(pin, pinProperties.destructivePin())) {
            recordFailure(adminId);
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Mã xác nhận không đúng",
                    Map.of("field", "confirmPin"), "error.admin.pin.mismatch");
        }

        // Gõ đúng thì xoá bộ đếm: người vận hành gõ sai vài lần rồi gõ đúng là chuyện bình
        // thường, và giữ lại số lần sai cũ sẽ khiến họ bị khóa ở một lần gõ sai rất lâu sau đó.
        attempts.remove(adminId);
    }

    private void requireNotLocked(UUID adminId) {
        Attempts current = attempts.get(adminId);
        if (current == null || current.count() < MAX_ATTEMPTS) {
            return;
        }
        if (Instant.now().isBefore(current.lastFailedAt().plus(LOCK_WINDOW))) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Đã gõ sai mã xác nhận quá nhiều lần. Vui lòng thử lại sau 15 phút.",
                    null, "error.admin.pin.locked");
        }
        // Hết thời gian khóa: xoá hẳn bộ đếm thay vì cho thêm một lần thử. Không xoá thì mỗi
        // 15 phút họ lại có đúng một lần thử, và cửa sổ đó vẫn đủ để dò dần theo thời gian.
        attempts.remove(adminId);
    }

    private void recordFailure(UUID adminId) {
        attempts.compute(adminId, (id, current) -> {
            int next = current == null ? 1 : current.count() + 1;
            return new Attempts(next, Instant.now());
        });
    }

    /**
     * So sánh hai chuỗi trong thời gian KHÔNG phụ thuộc nội dung.
     *
     * {@code String.equals} dừng ngay ở ký tự đầu tiên khác nhau, nên thời gian trả lời tiết
     * lộ số ký tự đầu đã khớp. Với mã sáu chữ số, dò kiểu đó chỉ cần khoảng 60 lần thử thay
     * vì một triệu — mà đó lại là con số nằm dưới ngưỡng khóa nếu chia ra nhiều cửa sổ.
     *
     * {@code MessageDigest.isEqual} là hàm so sánh thời gian hằng số có sẵn trong JDK.
     */
    private static boolean constantTimeEquals(String provided, String expected) {
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
