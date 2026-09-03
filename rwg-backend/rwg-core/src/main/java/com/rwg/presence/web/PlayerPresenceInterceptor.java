package com.rwg.presence.web;

import com.rwg.presence.service.PresenceStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Đánh dấu người chơi còn hoạt động ở MỖI request đã xác thực.
 *
 * <h2>VÌ SAO LÀ INTERCEPTOR CHỨ KHÔNG PHẢI FILTER</h2>
 * Interceptor của Spring MVC chạy SAU toàn bộ chuỗi filter của Spring Security, nên
 * {@code SecurityContext} đã có sẵn danh tính đã xác thực. Một {@code Filter} thì phải tự
 * xác định chỗ chen vào giữa chuỗi filter bảo mật, và nếu đứng sai vị trí sẽ đọc được
 * context rỗng — tức im lặng không ghi gì mà không báo lỗi.
 *
 * <h2>CHỈ GHI CHO PLAYER</h2>
 * Nhân sự cũng gọi được các điểm cuối của người chơi. Không lọc vai trò thì một quản trị
 * viên đang thao tác sẽ hiện thành "người chơi đang online" trong bảng danh sách của chính
 * họ.
 *
 * <h2>CHỈ CHẠY Ở APP NGƯỜI CHƠI</h2>
 * Bean này bị loại khỏi app quản trị (xem {@code AdminApplication.excludeFilters}). Ở đó
 * nó vô nghĩa: request tới app quản trị là của nhân sự, và người chơi không bao giờ gọi
 * vào đó.
 */
@Component
public class PlayerPresenceInterceptor implements HandlerInterceptor {

    private static final String PLAYER_AUTHORITY = "ROLE_PLAYER";

    private final PresenceStore presenceStore;

    public PlayerPresenceInterceptor(PresenceStore presenceStore) {
        this.presenceStore = presenceStore;
    }

    /**
     * Ghi ở {@code afterCompletion} chứ không phải {@code preHandle}.
     *
     * Việc ghi này không phải điều kiện để phục vụ request, nên nó thuộc phần sau khi
     * response đã được dựng. Đặt ở {@code preHandle} thì mọi request đều phải chờ một lượt
     * khứ hồi tới Redis TRƯỚC khi bắt đầu làm việc thật.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth) || !auth.isAuthenticated()) {
            return;
        }

        boolean isPlayer = jwtAuth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PLAYER_AUTHORITY::equals);
        if (!isPlayer) {
            return;
        }

        // `sub` của token là userId (xem AuthService lúc phát hành). Không tra DB ở đây:
        // định danh đã nằm trong token đã được xác thực chữ ký, và một truy vấn cho mỗi
        // request là chi phí không cần thiết.
        try {
            presenceStore.touch(UUID.fromString(jwtAuth.getToken().getSubject()));
        } catch (IllegalArgumentException notAUuid) {
            // Token có `sub` không phải UUID: không thể xảy ra với token do hệ thống này
            // phát hành. Bỏ qua thay vì ném — đây là đường phụ, không được làm hỏng
            // response đã hoàn tất.
        }
    }
}
