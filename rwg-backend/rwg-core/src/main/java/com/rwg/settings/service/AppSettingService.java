package com.rwg.settings.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.settings.domain.AppSetting;
import com.rwg.settings.dto.AppSettingResponse;
import com.rwg.settings.repository.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Đọc và sửa các đoạn chữ cấu hình được từ khu quản trị.
 *
 * <h2>DANH SÁCH KHOÁ CHO PHÉP LÀ BẮT BUỘC</h2>
 * Endpoint sửa nhận khoá từ đường dẫn. Không có danh sách trắng thì một khoá bất kỳ đều
 * tạo được bản ghi mới, tức là khu quản trị trở thành nơi ghi tự do vào bảng cấu hình —
 * và một khoá gõ sai sẽ nằm im trong bảng, không ai đọc, không ai biết để dọn.
 */
@Service
public class AppSettingService {

    /**
     * Những khoá người vận hành được sửa.
     *
     * Thêm khoá mới thì thêm vào đây VÀ seed một dòng trong migration: khoá có trong danh
     * sách mà không có bản ghi sẽ trả 404 khi mở trang, trông như lỗi hệ thống.
     */
    private static final Set<String> EDITABLE_KEYS = Set.of(AppSetting.CHAT_PROMO_TEXT);

    private final AppSettingRepository repository;
    private final AuditTrailService auditTrailService;

    public AppSettingService(AppSettingRepository repository, AuditTrailService auditTrailService) {
        this.repository = repository;
        this.auditTrailService = auditTrailService;
    }

    /**
     * Giá trị của một khoá, dùng cho cả đường công khai (người chơi) và khu quản trị.
     *
     * NÉM 404 khi không có bản ghi, KHÔNG trả chuỗi rỗng: mọi khoá hợp lệ đều được seed
     * trong migration, nên thiếu bản ghi nghĩa là migration chưa chạy hoặc có ai xoá tay —
     * cả hai đều là sự cố cần thấy, không phải trạng thái bình thường cần che đi.
     */
    @Transactional(readOnly = true)
    public AppSettingResponse get(String key) {
        AppSetting setting = repository.findById(key)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy mục cấu hình: " + key));
        return toResponse(setting);
    }

    /**
     * Sửa giá trị của một khoá.
     *
     * GHI AUDIT KÈM CẢ GIÁ TRỊ CŨ: khi nội dung sai gây khiếu nại, câu hỏi đầu tiên luôn
     * là "trước đó nó ghi gì". Chỉ lưu giá trị mới thì câu đó không trả lời được.
     *
     * Giá trị cũ bị CẮT NGẮN trong audit — xem {@link #summarize}.
     */
    @Transactional
    public AppSettingResponse update(String key, String newValue,
                                     UUID actorId, String actorUsername, String ipAddress) {
        if (!EDITABLE_KEYS.contains(key)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Mục cấu hình không được phép sửa: " + key, null,
                    "error.setting.not_editable");
        }

        AppSetting setting = repository.findById(key)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy mục cấu hình: " + key));

        String oldValue = setting.getSettingValue();
        // trim() để một khoảng trắng cuối do dán chữ không thành một lần sửa thật.
        String trimmed = newValue.trim();

        setting.update(trimmed, actorUsername);
        AppSetting saved = repository.save(setting);

        auditTrailService.record(actorId, actorUsername,
                AuditTrailService.ADMIN_SETTING_UPDATED,
                "APP_SETTING", key,
                Map.of("oldValue", summarize(oldValue),
                        "newValue", summarize(trimmed),
                        "oldLength", oldValue.length(),
                        "newLength", trimmed.length()),
                ipAddress);

        return toResponse(saved);
    }

    /**
     * Bản rút gọn để ghi vào audit.
     *
     * CẮT Ở 500 KÝ TỰ: cột {@code details} của audit là JSON, và lưu nguyên hai phiên bản
     * 4000 ký tự cho mỗi lần sửa sẽ làm bảng audit phình lên vì một thao tác không quan
     * trọng bằng các thao tác tiền tệ nằm cùng bảng. 500 ký tự đủ để nhận ra nội dung đã
     * đổi những gì, và độ dài đầy đủ vẫn được ghi riêng ở {@code oldLength}/{@code newLength}.
     */
    private static String summarize(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500) + "…";
    }

    private static AppSettingResponse toResponse(AppSetting setting) {
        return new AppSettingResponse(setting.getSettingKey(), setting.getSettingValue(),
                setting.getUpdatedAt(), setting.getUpdatedByUsername());
    }
}
