package com.rwg.media.api;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
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
import java.nio.file.Paths;

/**
 * Controller phục vụ file media công khai (phát Video HTML5 & hiển thị Ảnh banner).
 * Đường dẫn: GET /uploads/media/{filename}
 */
@RestController
@Tag(name = "Media", description = "Xem và phát file Video/Ảnh công khai")
public class MediaController {

    private static final Path UPLOAD_DIR = Paths.get("./uploads/media").toAbsolutePath().normalize();

    @GetMapping("/uploads/media/{filename:.+}")
    @Operation(summary = "Phát file video / xem ảnh banner công khai (hỗ trợ HTTP Range Streaming)")
    public ResponseEntity<Resource> serveMedia(@PathVariable("filename") String filename) {
        Path filePath = UPLOAD_DIR.resolve(filename).normalize();
        if (!filePath.startsWith(UPLOAD_DIR) || !Files.exists(filePath) || !Files.isReadable(filePath)) {
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
