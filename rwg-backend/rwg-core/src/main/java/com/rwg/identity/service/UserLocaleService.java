package com.rwg.identity.service;

import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tra cứu locale đã lưu của user (cột users.locale) kèm cache in-memory để
 * LocaleResolver KHÔNG phải truy vấn DB mỗi request. Cache được cập nhật ngay
 * khi user đổi locale qua PATCH /api/v1/users/me/locale (AuthService gọi
 * {@link #put} trong cùng transaction nghiệp vụ) — đây là con đường ghi DUY NHẤT
 * thay đổi users.locale nên cache không cần eviction/refresh khác.
 *
 * Quy mô MVP/leg-2: số user chưa lớn nên cache không cần eviction; entry bị
 * chặn bởi số user. Các chặng sau nếu cần sẽ thay bằng Caffeine có TTL.
 */
@Service
public class UserLocaleService {

    public static final String DEFAULT_LOCALE = "en";
    public static final Set<String> SUPPORTED_LOCALES = Set.of("en", "vi", "zh", "ja");

    private final UserRepository userRepository;
    private final ConcurrentMap<UUID, String> cache = new ConcurrentHashMap<>();

    public UserLocaleService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Locale đã lưu của user; user không tồn tại hoặc locale lạ -> "en". */
    public String localeOf(UUID userId) {
        return cache.computeIfAbsent(userId, id -> userRepository.findById(id)
                .map(User::getLocale)
                .filter(SUPPORTED_LOCALES::contains)
                .orElse(DEFAULT_LOCALE));
    }

    /** Cập nhật cache ngay sau khi persist (gọi từ AuthService.updateLocale). */
    public void put(UUID userId, String locale) {
        cache.put(userId, locale);
    }
}
