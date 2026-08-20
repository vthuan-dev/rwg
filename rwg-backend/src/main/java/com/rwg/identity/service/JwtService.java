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
 * Claims: sub=userId, username, roles=["ROLE_" + role], iss, iat, exp.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final SecurityProperties props;

    public JwtService(JwtEncoder encoder, SecurityProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .claim(SecurityConfig.USERNAME_CLAIM, user.getUsername())
                .claim(SecurityConfig.ROLE_CLAIM, List.of("ROLE_" + user.getRole().name()))
                .build();
        // Bắt buộc khai báo alg HS256: ImmutableSecret không tự chọn JWK nếu thiếu header.
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    public long accessTokenExpiresInSeconds() {
        return props.accessTokenTtl().toSeconds();
    }
}
