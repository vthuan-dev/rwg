package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình lưu trữ file media (prefix rwg.media).
 *
 * VÌ SAO PHẢI ĐƯA THƯ MỤC RA CẤU HÌNH: trước đây đường dẫn là hằng số tương đối
 * {@code Paths.get("./uploads/media")}, và Maven chạy mỗi module ở thư mục làm việc
 * riêng. Nên cùng một dòng mã giải ra ba đường dẫn khác nhau:
 * {@code rwg-core/uploads/media}, {@code rwg-user-app/uploads/media},
 * {@code rwg-admin-app/uploads/media}.
 *
 * Với banner thì không lộ ra vì chỉ app quản trị ghi và cũng chỉ nó đọc. Với chat thì
 * lộ ngay: nhân sự gửi ảnh ở app 8081 lưu vào thư mục của app đó, còn trình duyệt
 * người chơi tải ảnh từ app 8080 sẽ tìm trong thư mục khác và KHÔNG THẤY — ảnh hiện
 * thành ô hỏng. Cả hai app phải trỏ về CÙNG một thư mục.
 *
 * Mọi giá trị đều có mặc định trong constructor: thiếu khối {@code rwg.media} trong
 * application.yml thì app vẫn khởi động, thay vì ném NullPointerException lúc ai đó
 * gửi tệp đầu tiên.
 */
@ConfigurationProperties(prefix = "rwg.media")
public record MediaProperties(

        /**
         * Thư mục lưu file tải lên.
         *
         * Mặc định trỏ RA NGOÀI thư mục module (`../uploads/media`) để hai app cùng
         * giải về một chỗ khi chạy bằng `mvn -pl <module>`: thư mục làm việc lúc đó là
         * `rwg-user-app/` hoặc `rwg-admin-app/`, nên lùi một cấp là gốc `rwg-backend`.
         *
         * Môi trường thật nên đặt đường dẫn TUYỆT ĐỐI qua RWG_MEDIA_UPLOAD_DIR.
         */
        String uploadDir,

        /**
         * Dung lượng tối đa một tệp đính kèm trong chat, tính theo byte.
         *
         * 10MB: ảnh chụp từ điện thoại hiện nay thường 3–8MB, nên mức thấp hơn sẽ từ
         * chối đúng loại ảnh mà người chơi cần gửi nhất (ảnh biên lai, ảnh chụp màn
         * hình lỗi). Cao hơn nữa không thêm giá trị gì cho việc hỗ trợ mà chỉ tăng chi
         * phí đĩa và thời gian chờ tải lên.
         *
         * PHẢI NHỎ HƠN {@code spring.servlet.multipart.max-file-size}: giới hạn của
         * Spring ném MaxUploadSizeExceededException ở tầng servlet với thông báo khó
         * hiểu, còn giới hạn ở đây trả lỗi có khoá dịch nói rõ mức tối đa.
         */
        long chatMaxFileSizeBytes,

        /**
         * Số tệp một người chơi tải lên được trong {@link #chatUploadWindow}.
         *
         * THẤP HƠN NHIỀU so với hạn mức gửi tin (20 tin/phút): 20 tệp 10MB mỗi phút là
         * 200MB/phút cho MỖI người chơi, đủ làm đầy đĩa trong vài giờ. Gõ chữ nhanh là
         * hành vi bình thường, tải 10 tệp một phút thì không.
         */
        int chatUploadLimitPerWindow,

        /** Cửa sổ tính hạn mức tải tệp. */
        Duration chatUploadWindow
) {

    public MediaProperties {
        if (uploadDir == null || uploadDir.isBlank()) uploadDir = "../uploads/media";
        if (chatMaxFileSizeBytes <= 0) chatMaxFileSizeBytes = 10L * 1024 * 1024;
        if (chatUploadLimitPerWindow <= 0) chatUploadLimitPerWindow = 10;
        if (chatUploadWindow == null) chatUploadWindow = Duration.ofMinutes(1);
    }

    /** Dung lượng tối đa hiển thị cho người dùng, dạng "10MB". */
    public String chatMaxFileSizeLabel() {
        return (chatMaxFileSizeBytes / (1024 * 1024)) + "MB";
    }
}
