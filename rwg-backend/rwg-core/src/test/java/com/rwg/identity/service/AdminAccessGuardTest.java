package com.rwg.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAccessGuardTest {

    @Test
    @DisplayName("Genting2004 (không phân biệt hoa thường) là Master Admin")
    void gentingIsMasterAdmin() {
        assertThat(AdminAccessGuard.isMasterAdmin("genting2004")).isTrue();
        assertThat(AdminAccessGuard.isMasterAdmin("Genting2004")).isTrue();
        assertThat(AdminAccessGuard.isMasterAdmin("GENTING2004")).isTrue();
        assertThat(AdminAccessGuard.isMasterAdmin("  Genting2004  ")).isTrue();
    }

    @Test
    @DisplayName("Tài khoản khác không phải là Master Admin")
    void othersAreNotMasterAdmin() {
        assertThat(AdminAccessGuard.isMasterAdmin("admin")).isFalse();
        assertThat(AdminAccessGuard.isMasterAdmin("ADMIN")).isFalse();
        assertThat(AdminAccessGuard.isMasterAdmin("finance")).isFalse();
        assertThat(AdminAccessGuard.isMasterAdmin((String) null)).isFalse();
        assertThat(AdminAccessGuard.isMasterAdmin("")).isFalse();
        assertThat(AdminAccessGuard.isMasterAdmin("   ")).isFalse();
    }

    @Test
    @DisplayName("Kiểm tra với Jwt token")
    void jwtExtractionWorks() {
        Jwt masterJwt = new Jwt(
                "mock-token-master",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "user-1", "username", "Genting2004")
        );
        assertThat(AdminAccessGuard.isMasterAdmin(masterJwt)).isTrue();

        Jwt subAdminJwt = new Jwt(
                "mock-token-sub",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "user-2", "username", "admin")
        );
        assertThat(AdminAccessGuard.isMasterAdmin(subAdminJwt)).isFalse();

        assertThat(AdminAccessGuard.isMasterAdmin((Jwt) null)).isFalse();
    }
}
