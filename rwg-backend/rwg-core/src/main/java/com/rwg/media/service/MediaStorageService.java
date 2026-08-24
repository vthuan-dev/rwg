package com.rwg.media.service;

import com.rwg.banner.domain.BannerMediaType;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.config.MediaProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dịch vụ lưu trữ file media (Video, Ảnh) lên thư mục đĩa cục bộ.
 *
 * Thư mục lấy từ {@link MediaProperties} chứ không phải hằng số tương đối: xem lý do
 * đầy đủ trong Javadoc của lớp đó — tóm lại là hai app giải ra hai thư mục khác nhau
 * và ảnh chat gửi từ app này sẽ không hiện được ở app kia.
 */
@Service
public class MediaStorageService {

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of("video/mp4", "video/webm");
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/webp");

    /**
     * Đuôi tệp cho phép với ảnh đính kèm trong chat.
     *
     * CHỈ ẢNH, không nhận tệp tuỳ ý. Đủ cho mọi tình huống hỗ trợ thật (ảnh chụp màn
     * hình lỗi, ảnh biên lai, ảnh giấy tờ), và tránh việc nhân sự nhận được `.exe`,
     * `.html` hay `.svg` từ người chơi — cả ba đều là đường tấn công vào chính máy của
     * nhân sự, vì họ là người sẽ mở tệp đó.
     */
    private static final Set<String> CHAT_IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");

    /**
     * Chữ ký byte đầu tệp của từng định dạng ảnh.
     *
     * KIỂM TRA NỘI DUNG, không chỉ tin vào Content-Type và đuôi tệp: cả hai đều do
     * client khai và client có thể khai bất cứ gì. Đổi tên `virus.exe` thành `anh.jpg`
     * là việc làm được bằng chuột phải, nên nếu chỉ xét đuôi thì tệp thực thi sẽ nằm
     * trên đĩa server dưới cái tên trông vô hại.
     *
     * WebP không có trong bảng này vì nó cần kiểm tra hai đoạn rời nhau ("RIFF" ở
     * offset 0 và "WEBP" ở offset 8) — xử lý riêng bên dưới.
     */
    private static final Map<String, byte[]> IMAGE_MAGIC_BYTES = Map.of(
            "png", new byte[]{(byte) 0x89, 'P', 'N', 'G'},
            "jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    /** Số byte đầu tệp cần đọc để nhận dạng: đủ cho chữ ký dài nhất (WebP cần 12). */
    private static final int MAGIC_BYTES_LENGTH = 12;

    public record StoredMediaResult(String publicUrl, BannerMediaType mediaType) {}

    /** Kết quả lưu một tệp đính kèm của chat. */
    public record StoredAttachment(String url, String originalName, long sizeBytes) {}

    /**
     * Tên thư mục con dành cho ảnh đính kèm trong chat.
     *
     * TÁCH KHỎI THƯ MỤC BANNER vì hai loại tệp có quyền truy cập ĐỐI LẬP nhau. Banner
     * là ảnh tiếp thị, phục vụ công khai qua {@code /uploads/media/**} — đúng như nó
     * cần. Ảnh chat thì ngược lại: đó là biên lai chuyển tiền, ảnh giấy tờ, ảnh màn
     * hình số dư tài khoản. Để chung một thư mục thì luật permitAll của banner sẽ phục
     * vụ luôn cả ảnh chat, và bất kỳ ai có đường dẫn đều xem được — đường dẫn thì bị
     * chia sẻ lại, nằm trong lịch sử trình duyệt, và không bao giờ hết hiệu lực.
     */
    private static final String CHAT_SUBDIR = "chat";

    /** Tiền tố đường dẫn trả về cho ảnh chat — endpoint CÓ KIỂM TRA QUYỀN, không phải tệp tĩnh. */
    public static final String CHAT_URL_PREFIX = "/api/v1/chat/attachments/";

    private final Path uploadDir;
    private final Path chatDir;
    private final MediaProperties mediaProperties;

    public MediaStorageService(MediaProperties mediaProperties) {
        this.mediaProperties = mediaProperties;
        this.uploadDir = Paths.get(mediaProperties.uploadDir()).toAbsolutePath().normalize();
        this.chatDir = uploadDir.resolve(CHAT_SUBDIR).normalize();
        try {
            Files.createDirectories(uploadDir);
            Files.createDirectories(chatDir);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo thư mục lưu trữ media: " + uploadDir, e);
        }
    }

    /** Thư mục ảnh/video công khai (banner) — {@code MediaController} phục vụ từ đây. */
    public Path uploadDir() {
        return uploadDir;
    }

    /**
     * Đọc một ảnh đính kèm của chat từ đĩa.
     *
     * @param filename tên tệp trần (không đường dẫn).
     * @return đường dẫn tệp, hoặc empty nếu không tồn tại / nằm ngoài thư mục cho phép.
     */
    public Optional<Path> findChatAttachment(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return Optional.empty();
        }
        Path file = chatDir.resolve(filename).normalize();
        if (!file.startsWith(chatDir) || !Files.exists(file) || !Files.isReadable(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    /**
     * Tải file media lên đĩa và trả về đường dẫn công khai.
     */
    public StoredMediaResult store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File media tải lên không được để rỗng");
        }

        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "file.bin").toLowerCase();
        String contentType = file.getContentType();
        if (contentType == null) {
            contentType = "";
        }
        contentType = contentType.toLowerCase();

        BannerMediaType mediaType;
        String extension;

        if (ALLOWED_VIDEO_TYPES.contains(contentType) || originalFilename.endsWith(".mp4") || originalFilename.endsWith(".webm")) {
            mediaType = BannerMediaType.VIDEO;
            extension = originalFilename.endsWith(".webm") ? ".webm" : ".mp4";
        } else if (ALLOWED_IMAGE_TYPES.contains(contentType) || originalFilename.endsWith(".png") || originalFilename.endsWith(".jpg") || originalFilename.endsWith(".jpeg") || originalFilename.endsWith(".webp")) {
            mediaType = BannerMediaType.IMAGE;
            if (originalFilename.endsWith(".png")) extension = ".png";
            else if (originalFilename.endsWith(".webp")) extension = ".webp";
            else extension = ".jpg";
        } else {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Định dạng file không hợp lệ. Chỉ chấp nhận Video (MP4, WebM) hoặc Ảnh (PNG, JPG, WebP)");
        }

        // Trần dung lượng theo TẮNG LOẠI, không một mức chung: video lớn hơn ảnh cả một
        // bậc độ lớn (4.2MB so với 168KB), nên một mức chung hoặc quá chật cho video
        // hoặc quá rộng cho ảnh.
        long maxBytes = mediaType == BannerMediaType.VIDEO
                ? mediaProperties.bannerMaxVideoBytes()
                : mediaProperties.bannerMaxImageBytes();
        String maxLabel = mediaType == BannerMediaType.VIDEO
                ? mediaProperties.bannerMaxVideoLabel()
                : mediaProperties.bannerMaxImageLabel();
        if (file.getSize() > maxBytes) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Tệp vượt quá dung lượng tối đa " + maxLabel,
                    Map.of("maxSizeBytes", maxBytes),
                    "error.banner.media.too_large", maxLabel);
        }

        // KIỂM NỘI DUNG THẬT, không chỉ tin Content-Type và đuôi tệp — cả hai đều do
        // client khai. Trước đây đặt tên tệp thành "payload.mp4" là qua được, bất kể nội
        // dung bên trong là gì, và tệp đó sẽ nằm trên đĩa server dưới một cái tên vô hại.
        //
        // Mức độ nguy hiểm thấp hơn chat (tệp được phục vụ tĩnh chứ không thực thi),
        // nhưng một tệp không phải video nằm trong banner sẽ thành ô đen trên trang chủ
        // của MỌI người chơi, và người vận hành không biết vì sao.
        boolean contentOk = mediaType == BannerMediaType.VIDEO
                ? looksLikeVideo(file)
                : looksLikeImage(file);
        if (!contentOk) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Nội dung tệp không khớp định dạng đã khai", null,
                    "error.banner.media.bad_type");
        }

        String filename = UUID.randomUUID() + extension;
        return new StoredMediaResult(copyToDisk(file, filename), mediaType);
    }

    /**
     * Lưu một ảnh đính kèm của chat.
     *
     * HÀM RIÊNG chứ không dùng lại {@link #store}: quy tắc của chat khác banner ở ba
     * điểm — chỉ nhận ảnh (banner nhận cả video), có trần dung lượng, và kiểm tra chữ
     * ký byte. Nhồi cả hai vào một hàm thì phải thêm tham số kiểu "đang gọi từ đâu", và
     * một lần truyền sai tham số đó là người chơi tải lên được video 100MB.
     *
     * @return đường dẫn công khai + tên gốc + dung lượng, để lưu kèm tin nhắn.
     */
    public StoredAttachment storeChatImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Tệp tải lên không được để rỗng", null, "error.chat.attachment.empty");
        }

        if (file.getSize() > mediaProperties.chatMaxFileSizeBytes()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Tệp vượt quá dung lượng tối đa " + mediaProperties.chatMaxFileSizeLabel(),
                    Map.of("maxSizeBytes", mediaProperties.chatMaxFileSizeBytes()),
                    "error.chat.attachment.too_large", mediaProperties.chatMaxFileSizeLabel());
        }

        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "image");
        String extension = extensionOf(originalName.toLowerCase());
        if (!CHAT_IMAGE_EXTENSIONS.contains(extension)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Chỉ chấp nhận ảnh PNG, JPG hoặc WebP", null,
                    "error.chat.attachment.bad_type");
        }

        // Đuôi tệp đã đúng, giờ mới xét nội dung thật. Thứ tự này có chủ ý: kiểm tra
        // đuôi trước vì nó rẻ, và loại được phần lớn tệp sai ngay trước khi đọc byte.
        if (!looksLikeImage(file)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Nội dung tệp không phải ảnh hợp lệ", null,
                    "error.chat.attachment.bad_type");
        }

        // Tên trên đĩa là UUID, KHÔNG dùng tên gốc: tên do người dùng đặt có thể chứa
        // "../" để ghi ra ngoài thư mục, hoặc trùng với tệp đã có và ghi đè lên nó. Tên
        // gốc vẫn được lưu lại trong DB để hiện cho người nhận.
        String filename = UUID.randomUUID() + normalizeExtension(extension);

        Path targetPath = chatDir.resolve(filename).normalize();
        if (!targetPath.startsWith(chatDir)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Tên tệp không hợp lệ");
        }
        writeFile(file, targetPath);

        return new StoredAttachment(CHAT_URL_PREFIX + filename, originalName, file.getSize());
    }

    /**
     * Xoá file media khỏi đĩa khi Banner bị xoá.
     */
    public void deleteByPublicUrl(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith("/uploads/media/")) {
            return;
        }
        String filename = publicUrl.substring("/uploads/media/".length());
        Path file = uploadDir.resolve(filename).normalize();
        try {
            if (file.startsWith(uploadDir) && Files.exists(file)) {
                Files.delete(file);
            }
        } catch (IOException ignored) {
            // Không throw exception khi dọn dẹp file
        }
    }

    // ===== nội bộ =====

    /** Ghi tệp xuống thư mục công khai, trả về đường dẫn công khai. */
    private String copyToDisk(MultipartFile file, String filename) {
        Path targetPath = uploadDir.resolve(filename).normalize();

        // Chốt an toàn cuối: tên tệp do chính hàm này sinh từ UUID nên không thể thoát
        // ra ngoài, nhưng kiểm tra vẫn giữ để một lần sửa sau này không âm thầm mở lại
        // đường ghi tệp tuỳ ý lên đĩa server.
        if (!targetPath.startsWith(uploadDir)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Tên tệp không hợp lệ");
        }

        writeFile(file, targetPath);
        return "/uploads/media/" + filename;
    }

    private void writeFile(MultipartFile file, Path targetPath) {
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Lỗi khi lưu trữ file media lên đĩa: " + e.getMessage());
        }
    }

    /** Đuôi tệp kèm dấu chấm, rỗng nếu không có. */
    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot);
    }

    /** Gộp .jpeg về .jpg để trên đĩa chỉ có một dạng đuôi cho cùng một định dạng. */
    private static String normalizeExtension(String extension) {
        return ".jpeg".equals(extension) ? ".jpg" : extension;
    }

    /**
     * Đọc vài byte đầu tệp và đối chiếu chữ ký định dạng ảnh.
     *
     * Không dùng {@code ImageIO.read} để xác thực: nó giải mã toàn bộ ảnh vào bộ nhớ,
     * nên một tệp 10MB dựng có chủ ý (decompression bomb) sẽ ngốn hàng trăm MB heap.
     * Đọc 12 byte đủ để biết đây có phải ảnh hay không.
     */
    /**
     * Đọc vài byte đầu tệp và đối chiếu chữ ký định dạng video.
     *
     * MP4 (và cả họ ISO-BMFF: M4V, MOV): 4 byte đầu là độ dài box, rồi đến chữ
     * {@code ftyp} ở offset 4. KHÔNG kiểm 4 byte đầu vì chúng là số độ dài, khác nhau
     * ở mỗi tệp.
     *
     * WebM (và Matroska nói chung): 4 byte đầu là EBML magic {@code 1A 45 DF A3}.
     *
     * KHÔNG giải mã video để xác thực: giải mã một tệp 50MB tốn hàng trăm MB bộ nhớ
     * và nhiều giây CPU. 12 byte đủ để biết đây có phải container video hay không.
     */
    private static boolean looksLikeVideo(MultipartFile file) {
        byte[] head = new byte[MAGIC_BYTES_LENGTH];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, MAGIC_BYTES_LENGTH);
        } catch (IOException cannotRead) {
            return false;
        }

        // WebM / Matroska: EBML magic ngay ở đầu tệp.
        if (startsWith(head, read, new byte[]{(byte) 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3})) {
            return true;
        }

        // MP4 / ISO-BMFF: "ftyp" ở offset 4.
        return read >= 8
                && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
    }

    private static boolean looksLikeImage(MultipartFile file) {
        byte[] head = new byte[MAGIC_BYTES_LENGTH];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, MAGIC_BYTES_LENGTH);
        } catch (IOException cannotRead) {
            return false;
        }
        if (read < 4) {
            return false;
        }

        for (byte[] signature : IMAGE_MAGIC_BYTES.values()) {
            if (startsWith(head, read, signature)) {
                return true;
            }
        }

        // WebP: "RIFF" ở đầu, rồi 4 byte độ dài, rồi "WEBP".
        return read >= MAGIC_BYTES_LENGTH
                && startsWith(head, read, new byte[]{'R', 'I', 'F', 'F'})
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
    }

    private static boolean startsWith(byte[] data, int dataLength, byte[] prefix) {
        if (dataLength < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
