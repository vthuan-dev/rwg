package com.rwg.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
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

    /**
     * Bộ giải mã JWT dùng chung cho REST và WebSocket.
     *
     * DÙNG CHUNG LÀ CỐ Ý: {@link WsAuthChannelInterceptor} gọi chính bean này ở frame STOMP
     * CONNECT. Nhờ vậy mọi quy tắc xác thực thêm vào đây đều phủ cả hai đường, không phải
     * khai báo hai lần và không có nguy cơ hai bên lệch nhau.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecurityProperties props, SessionRevocationStore revocationStore) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(props.hmacKey()).build();

        // Validate issuer (mặc định kèm chữ ký + exp): token phát hành bởi issuer khác bị từ chối.
        //
        // Ghép thêm kiểm tra THU HỒI PHIÊN: thu hồi refresh token chỉ chặn việc gia hạn phiên,
        // còn access token đã phát vẫn sống tới 15 phút — đủ để một tài khoản vừa bị khóa đặt
        // thêm rất nhiều vòng cược. Xem {@link RevokedSessionValidator}.
        //
        // Dùng `DelegatingOAuth2TokenValidator` chứ không thay hẳn validator mặc định: thay hẳn
        // sẽ bỏ luôn kiểm tra chữ ký, hạn dùng và issuer.
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.issuer()),
                new RevokedSessionValidator(revocationStore));
        decoder.setJwtValidator(validator);
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/withdrawals/*/approve")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/withdrawals/*/reject")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/affiliate/commissions/run")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/payout-methods/*/reveal")
                            .hasAnyRole("ADMIN", "FINANCE")
                        // THÊM / GỠ tài khoản nhận tiền hộ người chơi.
                        //
                        // ĐẶT Ở ĐÂY, TRƯỚC RULE CHUNG /api/v1/admin/** Ở DƯỚI: Spring lấy
                        // matcher KHỚP ĐẦU TIÊN. Nếu để rơi vào rule chung thì cả bốn vai
                        // trò đều qua được và SUPPORT sẽ đổi được tài khoản nhận tiền của
                        // người chơi — tức chuyển được tiền của người khác về tài khoản mình.
                        //
                        // POST khớp chính xác ".../payout-methods" (không có đuôi) nên KHÔNG
                        // đè lên matcher reveal ở trên; dù có thì reveal đã đứng trước.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/payout-methods")
                            .hasAnyRole("ADMIN", "FINANCE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/users/*/payout-methods/*")
                            .hasAnyRole("ADMIN", "FINANCE")

                        // Thao tác quản lý user (không chạm tiền): thêm SUPPORT.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/users/*/status")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/users/*/kyc")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/withdrawal-password/reset")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/password/change")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/withdrawal-password/change")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/chat/**")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/chat/**")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/users/*")
                            .hasRole("ADMIN")

                        // SỔ SÁCH NGƯỜI CHƠI: chỉ ADMIN + FINANCE.
                        //
                        // Báo cáo này phơi ra TOÀN BỘ lịch sử tiền của một người chơi — số dư,
                        // tiền nạp, tiền rút, thắng thua từng game. SUPPORT và RISK không có
                        // nghiệp vụ nào cần tới nó, nên không để rơi vào matcher chung bên dưới.
                        .requestMatchers("/api/v1/admin/reports/**")
                            .hasAnyRole("ADMIN", "FINANCE")

                        // BANNER TRANG CHỦ: chỉ ADMIN được GHI.
                        //
                        // Banner hiển thị trên trang chủ CÔNG KHAI, nên tải ảnh lên đây là đặt
                        // nội dung trước mặt mọi khách truy cập. Trước đây thao tác này rơi vào
                        // matcher chung nên SUPPORT và RISK đều tải và xoá được — rộng hơn mọi
                        // thao tác ghi khác trong khu quản trị. GET vẫn mở cho cả bốn vai trò.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/banners/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/banners/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/banners/**").hasRole("ADMIN")

                        // NOI DUNG CHU HIEN CHO KHACH: chi ADMIN duoc GHI.
                        //
                        // Cung ly do nhu banner ngay tren: doan chu nay hien ra truoc MOI khach,
                        // nen sua no la dat noi dung truoc mat toan bo nguoi dung. De roi vao
                        // matcher chung ben duoi thi SUPPORT va RISK cung sua duoc — rong hon moi
                        // thao tac ghi khac trong khu quan tri. GET van mo cho ca bon vai tro.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/settings/**").hasRole("ADMIN")

                        // Còn lại trong khu admin (chủ yếu GET tra cứu/báo cáo): mọi nhân sự
                        // quản trị, gồm RISK chỉ đọc. Các POST/PATCH ghi đã bị chặn hẹp ở trên.
                        .requestMatchers("/api/v1/admin/**")
                            .hasAnyRole("ADMIN", "FINANCE", "SUPPORT", "RISK")

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
