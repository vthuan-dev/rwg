package com.rwg.media.service;

import com.rwg.banner.domain.BannerMediaType;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Dịch vụ lưu trữ file media (Video, Ảnh) lên thư mục đĩa cục bộ `./uploads/media/`.
 */
@Service
public class MediaStorageService {

    private static final Path UPLOAD_DIR = Paths.get("./uploads/media").toAbsolutePath().normalize();

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of("video/mp4", "video/webm");
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/jpg", "image/webp");

    public record StoredMediaResult(String publicUrl, BannerMediaType mediaType) {}

    public MediaStorageService() {
        try {
            Files.createDirectories(UPLOAD_DIR);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo thư mục lưu trữ media: " + UPLOAD_DIR, e);
        }
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

        String filename = UUID.randomUUID() + extension;
        Path targetPath = UPLOAD_DIR.resolve(filename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Lỗi khi lưu trữ file media lên đĩa: " + e.getMessage());
        }

        String publicUrl = "/uploads/media/" + filename;
        return new StoredMediaResult(publicUrl, mediaType);
    }

    /**
     * Xoá file media khỏi đĩa khi Banner bị xoá.
     */
    public void deleteByPublicUrl(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith("/uploads/media/")) {
            return;
        }
        String filename = publicUrl.substring("/uploads/media/".length());
        Path file = UPLOAD_DIR.resolve(filename).normalize();
        try {
            if (file.startsWith(UPLOAD_DIR) && Files.exists(file)) {
                Files.delete(file);
            }
        } catch (IOException ignored) {
            // Không throw exception khi dọn dẹp file
        }
    }
}
