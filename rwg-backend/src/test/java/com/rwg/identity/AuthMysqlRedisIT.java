package com.rwg.identity;

import com.redis.testcontainers.RedisContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.mysql.MySQLContainer;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test với MySQL 8.4 + Redis THẬT (Testcontainers).
 * CHỈ chạy khi Docker đang bật và dùng Maven profile "testcontainers":
 *   mvn verify -Ptestcontainers
 * Mặc định `mvn verify` KHÔNG chạy test này (surefire exclude *IT).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("testcontainers")
class AuthMysqlRedisIT {

    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    static RedisContainer redis = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        // Dùng bộ migration MySQL THẬT (db/migration)
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        // Bật Redis thật cho refresh-token store + rate-limit
        registry.add("rwg.redis.enabled", () -> "true");
        registry.add("spring.data.redis.url", redis::getRedisURI);
        registry.add("management.health.redis.enabled", () -> "true");
    }

    @BeforeAll
    static void startContainers() {
        mysql.start();
        redis.start();
    }

    @AfterAll
    static void stopContainers() {
        redis.stop();
        mysql.stop();
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void flywayMysqlRunsAndAuthFlowWorksWithRedis() throws Exception {
        String username = "it" + UUID.randomUUID().toString().substring(0, 8);

        // Register (Flyway V1 MySQL phải chạy thành công trước đó)
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"MatKhau@12345"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated());

        // Login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"MatKhau@12345"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String refresh = tokens.get("refreshToken").asText();

        // /me với JWT
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        // Refresh rotation qua Redis store
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isOk());

        // Token cũ đã thu hồi trong Redis
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        // Health UP (bao gồm Redis thật)
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
