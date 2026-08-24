package com.rwg.media.api;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.media.service.MediaStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Controller phục vụ file media công khai (phát Video HTML5 & hiển thị Ảnh banner).
 * Đường dẫn: GET /uploads/media/{filename}
 *
 * Thư mục lấy từ {@link MediaStorageService} thay vì tự khai một hằng số riêng: trước
 * đây hai lớp mỗi bên khai một hằng số cùng giá trị, tức là hai nguồn sự thật cho cùng
 * một thư mục — đổi cấu hình ở một chỗ mà quên chỗ kia thì tệp ghi vào một nơi và được
 * đọc từ một nơi khác.
 */
@RestController
@Tag(name = "Media", description = "Xem và phát file Video/Ảnh công khai")
public class MediaController {

    private final MediaStorageService mediaStorageService;

    public MediaController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @GetMapping("/uploads/media/{filename:.+}")
    @Operation(summary = "Phát file video / xem ảnh banner công khai (hỗ trợ HTTP Range Streaming)")
    public ResponseEntity<Resource> serveMedia(@PathVariable("filename") String filename) {
        Path uploadDir = mediaStorageService.uploadDir();
        Path filePath = uploadDir.resolve(filename).normalize();
        if (!filePath.startsWith(uploadDir) || !Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy file media yêu cầu");
        }

        Resource resource = new FileSystemResource(filePath);
        MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }
}
