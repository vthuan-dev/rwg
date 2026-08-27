package com.rwg.settings.repository;

import com.rwg.settings.domain.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Truy cập bảng cấu hình chữ.
 *
 * Không có phương thức tự viết nào: khoá chính là chính {@code settingKey}, nên
 * {@code findById} đã là đường tra đúng và nhanh nhất.
 */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
