package com.rwg.identity.service;

import com.rwg.config.SecurityConfig;
import com.rwg.config.SecurityProperties;
import com.rwg.identity.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Phát hành JWT access token (HS256, TTL 15 phút mặc định).
 * Claims: sub=userId, username, roles=["ROLE_" + role], iss, iat, exp, sid (tuỳ chọn).
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final SecurityProperties props;

    public JwtService(JwtEncoder encoder, SecurityProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    /**
     * Phát token KHÔNG gắn phiên. Token như vậy không bao giờ bị quy tắc một phiên chặn.
     *
     * Dùng cho phiên hỗ trợ khách quên mật khẩu: phiên đó mở được chỉ bằng tên đăng nhập,
     * nên nếu nó chốt phiên thì bất kỳ ai biết tên đăng nhập của người khác đều đá được
     * người đó ra — lặp lại liên tục thành một cách chặn người chơi truy cập.
     */
    public String issueAccessToken(User user) {
        return issueAccessToken(user, null);
    }

    /**
     * Phát token gắn với một phiên cụ thể.
     *
     * {@code sessionId} là familyId của chuỗi refresh rotation — xem
     * {@code ActiveSessionStore} để biết vì sao dùng lại định danh đó thay vì sinh mới.
     * Truyền {@code null} thì claim bị bỏ hẳn, KHÔNG ghi vào một chuỗi rỗng: một claim
     * rỗng vẫn là claim có mặt, và phía kiểm tra sẽ đem chuỗi rỗng đi so với phiên hiện
     * hành rồi từ chối.
     */
    public String issueAccessToken(User user, String sessionId) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .claim(SecurityConfig.USERNAME_CLAIM, user.getUsername())
                .claim(SecurityConfig.ROLE_CLAIM, List.of("ROLE_" + user.getRole().name()));
        if (sessionId != null) {
            claims.claim(SecurityConfig.SESSION_CLAIM, sessionId);
        }
        // Bắt buộc khai báo alg HS256: ImmutableSecret không tự chọn JWK nếu thiếu header.
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims.build())).getTokenValue();
    }

    public long accessTokenExpiresInSeconds() {
        return props.accessTokenTtl().toSeconds();
    }
}
