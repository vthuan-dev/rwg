package com.rwg.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Cấu hình bảo mật: JWT resource server (HS256) + phân quyền PLAYER/ADMIN.
 * Mật khẩu băm BCrypt strength 12 (DECISIONS.md / Bước 1).
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
                        // Webhook provider thanh toán (provider gọi, không có JWT) — idempotent
                        // theo providerTxnId; chặng sau thêm xác thực chữ ký provider.
                        .requestMatchers("/api/v1/payments/callback").permitAll()
                        // Khu vực ADMIN: bắt buộc ROLE_ADMIN (điểm thực thi phân quyền).
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
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
