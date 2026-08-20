package com.rwg.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCredentials;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Redis phía Bucket4j (rate-limit phân tán).
 * Chỉ kích hoạt khi rwg.redis.enabled=true (mặc định). Dev không có Redis thì
 * đặt rwg.redis.enabled=false -> app dùng fallback in-memory (xem InMemoryRateLimitStore).
 *
 * StringRedisTemplate (dùng cho refresh-token store) do Spring Data Redis tự cấu hình
 * từ spring.data.redis.url.
 */
@Configuration
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient bucket4jRedisClient(
            @Value("${spring.data.redis.url:redis://localhost:6379}") String redisUrl,
            @Value("${spring.data.redis.password:}") String redisPassword) {
        // Hỗ trợ Redis requirepass (docker compose hardening): password lấy cùng nguồn
        // với StringRedisTemplate (spring.data.redis.password).
        // Lettuce 7: setPassword(char[]) đã bị gỡ — dùng RedisCredentialsProvider.
        RedisURI uri = RedisURI.create(redisUrl);
        if (redisPassword != null && !redisPassword.isBlank()) {
            char[] password = redisPassword.toCharArray();
            uri.setCredentialsProvider(RedisCredentialsProvider.from(
                    () -> RedisCredentials.just(null, password)));
        }
        return RedisClient.create(uri);
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucket4jRedisConnection(RedisClient bucket4jRedisClient) {
        return bucket4jRedisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    /**
     * ProxyManager quản lý các bucket rate-limit lưu trong Redis (key dạng String).
     */
    @Bean
    public ProxyManager<String> loginRateLimitProxyManager(
            StatefulRedisConnection<String, byte[]> bucket4jRedisConnection) {
        return LettuceBasedProxyManager.builderFor(bucket4jRedisConnection).build();
    }
}
