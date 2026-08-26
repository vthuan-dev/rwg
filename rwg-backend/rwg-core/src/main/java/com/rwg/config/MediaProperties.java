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
        Duration chatUploadWindow,

        /**
         * Số banner tối đa được phép tồn tại.
         *
         * ĐẾM CẢ BANNER ĐANG TẮT, không chỉ banner ACTIVE: trần này sinh ra để giới hạn
         * số tệp trên đĩa, mà tệp của banner đang tắt vẫn chiếm chỗ y như tệp đang bật.
         * Nếu chỉ đếm ACTIVE thì tắt hết đi là tải lên được vô hạn.
         */
        int bannerMaxCount,

        /**
         * Dung lượng tối đa một ảnh banner, tính theo byte.
         *
         * Hai ảnh banner hiện có là WebP ~168 KB, nên 10MB rất thoải mái kể cả khi người
         * vận hành tải thẳng ảnh PNG chưa nén từ thiết kế.
         */
        long bannerMaxImageBytes,

        /**
         * Dung lượng tối đa một video banner, tính theo byte.
         *
         * 50MB: hai video hiện có là 4.2MB và 2.2MB, nên mức này còn dư nhiều cho video
         * dài hơn hoặc nét hơn về sau, đồng thời vẫn chặn được việc làm đầy đĩa.
         *
         * PHẢI NHỎ HƠN {@code spring.servlet.multipart.max-file-size}: giới hạn của
         * Spring ném MaxUploadSizeExceededException ở tầng servlet với thông báo khó
         * hiểu, còn giới hạn ở đây trả lỗi nói rõ mức tối đa.
         */
        long bannerMaxVideoBytes,

        /**
         * Số ảnh khuyến mãi chat tối đa được phép tồn tại.
         *
         * TRẦN RIÊNG, không dùng chung {@link #bannerMaxCount}: hai khu không liên quan
         * gì đến nhau, nên dùng chung một trần thì tải đủ 4 banner trang chủ là hết chỗ
         * cho ảnh chat.
         *
         * 3 thay vì 1: khung chat chỉ dùng MỘT ảnh, nhưng cho lưu thêm vài bản để nhân
         * sự chuẩn bị trước cho đợt sau rồi chỉ cần bật lên, thay vì phải xoá ảnh đang
         * chạy mới tải được ảnh mới.
         */
        int chatPromoMaxCount
) {

    public MediaProperties {
        if (uploadDir == null || uploadDir.isBlank()) uploadDir = "../uploads/media";
        if (chatMaxFileSizeBytes <= 0) chatMaxFileSizeBytes = 10L * 1024 * 1024;
        if (chatUploadLimitPerWindow <= 0) chatUploadLimitPerWindow = 10;
        if (chatUploadWindow == null) chatUploadWindow = Duration.ofMinutes(1);
        if (bannerMaxCount <= 0) bannerMaxCount = 4;
        if (bannerMaxImageBytes <= 0) bannerMaxImageBytes = 10L * 1024 * 1024;
        if (bannerMaxVideoBytes <= 0) bannerMaxVideoBytes = 50L * 1024 * 1024;
        if (chatPromoMaxCount <= 0) chatPromoMaxCount = 3;
    }

    /** Dung lượng tối đa hiển thị cho người dùng, dạng "10MB". */
    public String chatMaxFileSizeLabel() {
        return (chatMaxFileSizeBytes / (1024 * 1024)) + "MB";
    }

    /** Trần ảnh banner dạng "10MB". */
    public String bannerMaxImageLabel() {
        return (bannerMaxImageBytes / (1024 * 1024)) + "MB";
    }

    /** Trần video banner dạng "50MB". */
    public String bannerMaxVideoLabel() {
        return (bannerMaxVideoBytes / (1024 * 1024)) + "MB";
    }
}
