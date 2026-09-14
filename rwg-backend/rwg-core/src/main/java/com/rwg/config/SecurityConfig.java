package com.rwg.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.rwg.identity.service.ActiveSessionStore;
import com.rwg.identity.service.SessionRevocationStore;
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
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Claim mang định danh PHIÊN của token (giá trị là familyId của chuỗi refresh rotation).
     *
     * Tên ngắn "sid" theo thông lệ OpenID Connect. Token thiếu claim này vẫn hợp lệ — xem
     * {@link SingleSessionValidator} để biết vì sao đó là hành vi cố ý.
     */
    public static final String SESSION_CLAIM = "sid";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /**
     * Bộ giải mã JWT dùng chung cho REST và WebSocket.
     *
     * DÙNG CHUNG LÀ CỐ Ý: {@link WsAuthChannelInterceptor} gọi chính bean này ở frame STOMP
     * CONNECT. Nhờ vậy mọi quy tắc xác thực thêm vào đây đều phủ cả hai đường, không phải
     * khai báo hai lần và không có nguy cơ hai bên lệch nhau.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecurityProperties props,
                                 SessionRevocationStore revocationStore,
                                 ActiveSessionStore activeSessionStore) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(props.hmacKey()).build();

        // Validate issuer (mặc định kèm chữ ký + exp): token phát hành bởi issuer khác bị từ chối.
        //
        // Ghép thêm kiểm tra THU HỒI PHIÊN: thu hồi refresh token chỉ chặn việc gia hạn phiên,
        // còn access token đã phát vẫn sống tới 15 phút — đủ để một tài khoản vừa bị khóa đặt
        // thêm rất nhiều vòng cược. Xem {@link RevokedSessionValidator}.
        //
        // Dùng `DelegatingOAuth2TokenValidator` chứ không thay hẳn validator mặc định: thay hẳn
        // sẽ bỏ luôn kiểm tra chữ ký, hạn dùng và issuer.
        // Ghép thêm quy tắc MỘT PHIÊN: token của thiết bị cũ bị từ chối sau khi người dùng
        // đăng nhập ở thiết bị mới. Xem {@link SingleSessionValidator}.
        //
        // Dựng danh sách rồi mới gộp, thay vì viết hai nhánh if trả về hai decoder khác nhau:
        // hai nhánh sẽ nhân đôi phần khai báo validator mặc định, và một lần sửa chỉ một
        // nhánh là đủ để hai đường lệch nhau.
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(props.issuer()));
        validators.add(new RevokedSessionValidator(revocationStore));
        if (props.singleSessionActive()) {
            validators.add(new SingleSessionValidator(activeSessionStore));
        }

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
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
                                                   JwtDecoder jwtDecoder,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoint auth công khai
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/admin/auth/**").permitAll()
                        // Xem banner & phát video/ảnh media công khai trên trang người chơi.
                        // `chat-promo` cũng công khai: đó là ảnh quảng bá gửi cho MỌI khách khi
                        // mở khung chat, không phải ảnh đính kèm của một hội thoại cụ thể — ảnh
                        // đính kèm là biên lai và giấy tờ cá nhân nên vẫn nằm sau
                        // /api/v1/chat/attachments/ có kiểm tra quyền.
                        .requestMatchers("/api/v1/banners/active", "/api/v1/banners/chat-promo",
                                "/uploads/media/**").permitAll()
                        // Noi dung chu do khu quan tri soan, hien cho khach.
                        //
                        // CONG KHAI vi day la loi chao gui cho MOI khach mo khung chat — cung
                        // loai voi /banners/chat-promo ngay tren. Bat dang nhap chi lam bong bong
                        // chao xuat hien tre hon phan con lai cua hoi thoai.
                        //
                        // Chi co duong DOC la cong khai; duong sua nam duoi /api/v1/admin/settings
                        // va bi chan thanh chi ADMIN o phia duoi.
                        .requestMatchers(HttpMethod.GET, "/api/v1/settings/**").permitAll()
                        // Webhook provider thanh toán (provider gọi, không có JWT) — idempotent
                        // theo providerTxnId; chặng sau thêm xác thực chữ ký provider.
                        .requestMatchers("/api/v1/payments/callback").permitAll()
                        // Hạn mức nạp/rút: thông tin công khai, in ngay trên giao diện nên
                        // không có gì để che. Bắt đăng nhập chỉ làm trang nạp/rút phải chờ
                        // xác thực xong mới vẽ được ô nhập số tiền.
                        .requestMatchers("/api/v1/payments/limits").permitAll()

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
                        // Hạn mức cược quyết định mức thiệt hại tối đa mỗi lệnh cược -> ADMIN + OPERATOR.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/games/tables/*/limits")
                            .hasAnyRole("ADMIN", "OPERATOR")

                        // Bật/tắt bàn: ADMIN, RISK, OPERATOR.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/games/tables/*/status")
                            .hasAnyRole("ADMIN", "RISK", "OPERATOR")

                        // Khu risk (chống đa tài khoản): ADMIN, RISK, OPERATOR.
                        .requestMatchers("/api/v1/admin/risk/**")
                            .hasAnyRole("ADMIN", "RISK", "OPERATOR")

                        // Thao tác CHẠM TIỀN: ADMIN hoặc FINANCE. SUPPORT/RISK/OPERATOR bị chặn ở đây.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/wallet/adjust")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/withdrawals/*/approve")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/withdrawals/*/reject")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/affiliate/commissions/run")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/payout-methods/*/reveal")
                            .hasAnyRole("ADMIN", "FINANCE", "OPERATOR")
                        // THÊM / GỠ tài khoản nhận tiền hộ người chơi.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/payout-methods")
                            .hasAnyRole("ADMIN", "FINANCE", "OPERATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/users/*/payout-methods/*")
                            .hasAnyRole("ADMIN", "FINANCE", "OPERATOR")

                        // Thao tác quản lý user (không chạm tiền): ADMIN, FINANCE, SUPPORT, OPERATOR.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/users/*/status")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT", "OPERATOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/users/*/kyc")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/withdrawal-password/reset")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/password/change")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/withdrawal-password/change")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/chat/**")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT", "OPERATOR")
                        // CHỈ ADMIN ĐƯỢC XOÁ TIN NHẮN (OPERATOR / SUPPORT không được xoá)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/chat/**")
                            .hasRole("ADMIN")
                        // CHỈ ADMIN ĐƯỢC XOÁ TÀI KHOẢN KHÁCH
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/users/*")
                            .hasRole("ADMIN")

                        // SỔ SÁCH NGƯỜI CHƠI & LỊCH SỬ GIAO DỊCH VÍ: CHỈ ADMIN + FINANCE.
                        .requestMatchers("/api/v1/admin/reports/**")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers("/api/v1/admin/users/*/wallet/transactions")
                            .hasAnyRole("ADMIN", "FINANCE")

                        // AUDIT LOG & PHÊ DUYỆT 4 MẮT: CHỈ ADMIN TỐI CAO ĐƯỢC XEM
                        .requestMatchers("/api/v1/admin/audit/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/approvals/**").hasRole("ADMIN")

                        // BANNER TRANG CHỦ: ADMIN + OPERATOR được GHI.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/banners/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/banners/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/banners/**").hasAnyRole("ADMIN", "OPERATOR")

                        // NỘI DUNG CHỮ HIỆN CHO KHÁCH: ADMIN + OPERATOR được GHI.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/settings/**").hasAnyRole("ADMIN", "OPERATOR")

                        // Còn lại trong khu admin: mọi nhân sự quản trị (bao gồm OPERATOR)
                        .requestMatchers("/api/v1/admin/**")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT", "RISK", "OPERATOR")

                        // Health + docs công khai
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // WebSocket handshake /ws MỞ Ở TẦNG HTTP. Xác thực diễn ra ở STOMP CONNECT,
                        // do WsAuthChannelInterceptor đảm nhiệm.
                        //
                        // TẠI SAO KHÔNG ĐÒI JWT NGAY Ở HANDSHAKE (đã từng làm và phải bỏ): handshake
                        // chỉ nhận token qua HTTP header, mà `new WebSocket(url)` của trình duyệt KHÔNG
                        // CHẤP NHẬN header tự đặt — đó là giới hạn của chính chuẩn WebSocket, không
                        // phải thiếu sót cấu hình. Hệ quả: MỌI trình duyệt nhận 401 tại handshake, nên
                        // chat hỗ trợ, thông báo thời gian thực và cập nhật số dư đều không hoạt động,
                        // dù token trong localStorage hoàn toàn hợp lệ.
                        //
                        // MỞ HANDSHAKE KHÔNG LÀM MẤT LỚP BẢO VỆ NÀO. Nó chỉ cho phép MỞ socket. Không
                        // có token hợp lệ ở frame CONNECT thì interceptor ném MessageDeliveryException,
                        // client nhận ERROR và bị ngắt — không có phiên STOMP, không nhận được một byte
                        // dữ liệu nào. Interceptor cũng vẫn kiểm `audience` (chặn token PLAYER mở phiên
                        // trên broker quản trị) và vẫn chặn SUBSCRIBE vào `/topic/admin`.
                        //
                        // Chặn origin vẫn hiệu lực qua `rwg.websocket.allowed-origin-patterns` (xem
                        // WebSocketConfig), nên trang web lạ không mở được socket này.
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt
                                // Dùng THẲNG bean jwtDecoder, không gọi lại jwtDecoder(props).
                                // Gọi lại sẽ tạo một decoder MỚI không có validator thu hồi
                                // phiên, tức lớp chặn đó chỉ còn hiệu lực ở WebSocket.
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
