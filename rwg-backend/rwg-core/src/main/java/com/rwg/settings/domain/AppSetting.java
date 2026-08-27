package com.rwg.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Một mục cấu hình chữ, sửa được từ khu quản trị.
 *
 * <h2>VÌ SAO Ở CƠ SỞ DỮ LIỆU, KHÔNG Ở FILE DỊCH</h2>
 * Nội dung như lời chào khách trong khung chat là việc của người vận hành: họ đổi theo
 * đợt khuyến mãi, đôi lúc vài lần một tuần. Để trong file dịch của frontend nghĩa là mỗi
 * lần đổi một dấu phẩy là một lần sửa mã, build lại và triển khai lại — người vận hành
 * phải chờ lập trình viên cho một việc lẽ ra là của họ.
 *
 * <h2>KHÔNG ĐA NGÔN NGỮ — CÓ CHỦ Ý</h2>
 * Chỉ có một giá trị cho mỗi khoá, không phân theo ngôn ngữ. Người vận hành ở đây chỉ
 * soạn tiếng Việt, nên thêm cột ngôn ngữ ngay bây giờ là dựng sẵn một cơ chế chưa ai cần,
 * mà lại buộc mọi chỗ đọc phải quyết định "lấy bản nào khi thiếu bản dịch".
 *
 * PK là chính {@code settingKey} chứ không phải một UUID: mỗi khoá tồn tại đúng một bản,
 * và khoá tự nhiên làm điều đó thành bất biến ở tầng cơ sở dữ liệu thay vì một quy ước
 * mà code phải tự nhớ giữ.
 */
@Entity
@Table(name = "app_settings")
public class AppSetting {

    /**
     * Các khoá đang dùng.
     *
     * KHAI BÁO HẰNG SỐ, không rải chuỗi trong code: một chuỗi gõ sai (`chat.promo.txt`)
     * sẽ biên dịch bình thường rồi âm thầm trả về giá trị mặc định, và không có gì báo.
     */
    public static final String CHAT_PROMO_TEXT = "chat.promo.text";

    @Id
    @Column(name = "setting_key", length = 64, nullable = false, updatable = false)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, columnDefinition = "TEXT")
    private String settingValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Tên đăng nhập người sửa gần nhất, chụp lại lúc sửa. */
    @Column(name = "updated_by_username", length = 50)
    private String updatedByUsername;

    protected AppSetting() {
        // cho JPA
    }

    public static AppSetting of(String settingKey, String settingValue, String updatedByUsername) {
        AppSetting setting = new AppSetting();
        setting.settingKey = settingKey;
        setting.settingValue = settingValue;
        setting.updatedByUsername = updatedByUsername;
        setting.updatedAt = Instant.now();
        return setting;
    }

    /**
     * Đổi giá trị.
     *
     * Cập nhật {@code updatedAt} và {@code updatedByUsername} TRONG CÙNG phương thức này,
     * không để service tự set: ba trường đó phải luôn đổi cùng nhau, và một chỗ gọi quên
     * set người sửa sẽ để lại bản ghi không truy được ai đã đổi nội dung khách đang đọc.
     */
    public void update(String newValue, String updatedByUsername) {
        this.settingValue = newValue;
        this.updatedByUsername = updatedByUsername;
        this.updatedAt = Instant.now();
    }

    public String getSettingKey() { return settingKey; }
    public String getSettingValue() { return settingValue; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedByUsername() { return updatedByUsername; }
}
