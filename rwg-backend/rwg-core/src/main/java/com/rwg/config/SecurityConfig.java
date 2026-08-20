package com.rwg.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật: JWT resource server (HS256) + phân quyền theo vai trò.
 * Mật khẩu băm BCrypt strength 12 (DECISIONS.md / Bước 1).
 *
 * Vai trò: PLAYER / ADMIN / FINANCE / SUPPORT / RISK. Khu /api/v1/admin/** phân quyền
 * THEO ROUTE (xem securityFilterChain) để tách quyền chạm tiền khỏi quyền xem —
 * đây là điểm thực thi duy nhất, không rải @PreAuthorize ở controller.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** BCrypt strength 12 theo yêu cầu Bước 1. */
    public static final int BCRYPT_STRENGTH = 12;

    public static final String ROLE_CLAIM = "roles";
    public static final String USERNAME_CLAIM = "username";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public JwtDecoder jwtDecoder(SecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(props.hmacKey()).build();
        // Validate issuer (mặc định kèm chữ ký + exp): token phát hành bởi issuer khác bị từ chối.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecurityProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(props.hmacKey()));
    }

    /**
     * Chuyển claim "roles" (vd ["ROLE_PLAYER"]) thành GrantedAuthority,
     * và dùng claim "username" làm tên principal.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter granted = new JwtGrantedAuthoritiesConverter();
        granted.setAuthoritiesClaimName(ROLE_CLAIM);
        granted.setAuthorityPrefix(""); // roles đã có sẵn prefix ROLE_

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(granted);
        converter.setPrincipalClaimName(USERNAME_CLAIM);
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   SecurityProperties props) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoint auth công khai
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Xem banner & phát video/ảnh media công khai trên trang người chơi
                        .requestMatchers("/api/v1/banners/active", "/uploads/media/**").permitAll()
                        // Webhook provider thanh toán (provider gọi, không có JWT) — idempotent
                        // theo providerTxnId; chặng sau thêm xác thực chữ ký provider.
                        .requestMatchers("/api/v1/payments/callback").permitAll()

                        // ===== KHU ADMIN: phân quyền theo route (chặng 5) =====
                        //
                        // THỨ TỰ QUAN TRỌNG: Spring Security lấy matcher KHỚP ĐẦU TIÊN, nên
                        // mọi matcher cụ thể PHẢI đứng trước /api/v1/admin/** ở cuối. Đặt sai
                        // thứ tự thì matcher chung "ăn" hết và việc tách vai trò vô hiệu.
                        //
                        // Vì sao tách: trước đây mọi ADMIN đều vừa cộng được tiền vào ví vừa tự
                        // duyệt được lệnh rút -> một người có thể chuyển tiền ra khỏi sàn.

                        // Chỉ ADMIN (super) được phân quyền — nếu FINANCE tự nâng mình thành
                        // ADMIN thì toàn bộ việc tách vai trò trở nên vô nghĩa.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/users/*/role").hasRole("ADMIN")
                        // Đổi % hoa hồng ảnh hưởng tiền chi cho mọi đại lý -> chỉ ADMIN.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/affiliate/config").hasRole("ADMIN")
                        // Hạn mức cược quyết định mức thiệt hại tối đa mỗi lệnh cược -> chỉ ADMIN.
                        // Nâng maxBet lên rất cao là một đường rút tiền không cần chạm ví nào.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/games/tables/*/limits").hasRole("ADMIN")

                        // Bật/tắt bàn: thêm RISK — phát hiện bàn bất thường là việc của họ, và
                        // tắt bàn không chuyển đồng nào nên không thuộc nhóm thao tác tiền.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/games/tables/*/status")
                            .hasAnyRole("ADMIN", "RISK")

                        // Khu risk (chống đa tài khoản): ADMIN + RISK, gồm cả thao tác GHI.
                        // Đây là lần đầu RISK được ghi dữ liệu — trước giờ chỉ đọc báo cáo.
                        // Hợp lý vì đánh giá gian lận đúng là việc của họ, và các thao tác
                        // này KHÔNG chuyển một đồng nào nên không cần quy trình 4 mắt.
                        .requestMatchers("/api/v1/admin/risk/**").hasAnyRole("ADMIN", "RISK")

                        // Thao tác CHẠM TIỀN: ADMIN hoặc FINANCE. SUPPORT/RISK bị chặn ở đây.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/wallet/adjust")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/approvals/*/approve")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/approvals/*/reject")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/withdrawals/*/approve")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/withdrawals/*/reject")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/affiliate/commissions/run")
                            .hasAnyRole("ADMIN", "FINANCE")

                        // Thao tác quản lý user (không chạm tiền): thêm SUPPORT.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/users/*/status")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/users/*/kyc")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/withdrawal-password/reset")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT")

                        // Còn lại trong khu admin (chủ yếu GET tra cứu/báo cáo): mọi nhân sự
                        // quản trị, gồm RISK chỉ đọc. Các POST/PATCH ghi đã bị chặn hẹp ở trên.
                        .requestMatchers("/api/v1/admin/**")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT", "RISK")

                        // Health + docs công khai
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // WebSocket handshake /ws KHÔNG còn permitAll (Phase c): handshake phải kèm JWT;
                        // STOMP CONNECT xác thực tiếp bởi WsAuthChannelInterceptor.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder(props))
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
