package com.rwg.presence;

import com.rwg.config.PresenceProperties;
import com.rwg.presence.dto.PresenceEntryResponse;
import com.rwg.presence.service.PresenceQueryService;
import com.rwg.presence.service.RedisPresenceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test nhánh Redis của trạng thái có mặt — KHÔNG cần Docker.
 *
 * Trọng tâm là những chỗ mà một lỗi sẽ âm thầm gây hại:
 * - RANH GIỚI cửa sổ online: lệch một chiều là mọi người hiện offline, lệch chiều kia là
 *   người đã rời đi vẫn hiện đang online.
 * - Redis lỗi phải trả về RỖNG chứ không ném: hàm ghi nằm trên đường đi của mọi request
 *   người chơi, và hàm đọc nằm trong bảng danh sách của khu quản trị.
 * - Mốc ở TƯƠNG LAI (đồng hồ hai máy lệch) không được coi là online vô thời hạn.
 *
 * Danh sách giả lập kết quả {@code MGET} dùng {@link Arrays#asList} CHỨ KHÔNG PHẢI
 * {@code List.of}: Redis trả về null cho khoá không tồn tại, và {@code List.of} ném
 * {@code NullPointerException} khi có phần tử null.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresenceStoreTest {

    private static final String KEY_PREFIX = "rwg:presence:";

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> valueOps;

    PresenceProperties properties;
    RedisPresenceStore store;
    PresenceQueryService queryService;

    @BeforeEach
    void setUp() {
        properties = new PresenceProperties(
                Duration.ofSeconds(90), Duration.ofDays(30), Duration.ofSeconds(30));
        store = new RedisPresenceStore(redis, properties);
        queryService = new PresenceQueryService(store, properties);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("touch ghi mốc kèm TTL trong một lệnh")
    void touchWritesTimestampWithTtl() {
        UUID userId = UUID.randomUUID();

        store.touch(userId);

        // TTL phải đi cùng lệnh ghi: SET rồi EXPIRE riêng thì một lần mất kết nối giữa hai
        // lệnh để lại khoá không bao giờ hết hạn.
        verify(valueOps).set(eq(KEY_PREFIX + userId), anyString(), eq(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("mốc vừa mới ghi được coi là đang online")
    void freshTimestampIsOnline() {
        assertThat(queryService.isOnline(Instant.now())).isTrue();
    }

    @Test
    @DisplayName("mốc còn trong cửa sổ 90 giây vẫn là online")
    void timestampJustInsideWindowIsOnline() {
        // 89 giây: bên TRONG ranh giới. Cặp test này chốt đúng ranh giới, vì lệch một bên
        // là người đã rời đi vẫn hiện online, lệch bên kia là người đang chơi hiện offline.
        assertThat(queryService.isOnline(Instant.now().minusSeconds(89))).isTrue();
    }

    @Test
    @DisplayName("mốc quá cửa sổ 90 giây là offline")
    void timestampOutsideWindowIsOffline() {
        assertThat(queryService.isOnline(Instant.now().minusSeconds(91))).isFalse();
    }

    @Test
    @DisplayName("không có mốc thì không phải online")
    void nullTimestampIsOffline() {
        assertThat(queryService.isOnline(null)).isFalse();
    }

    @Test
    @DisplayName("mốc ở tương lai xa do lệch đồng hồ KHÔNG được coi là online")
    void farFutureTimestampIsNotOnline() {
        // So bằng `isAfter(now - window)` sẽ coi mọi mốc tương lai là online vô thời hạn.
        // Dùng khoảng thời gian tuyệt đối nên một mốc lệch 10 phút bị loại đúng như mốc cũ
        // 10 phút.
        assertThat(queryService.isOnline(Instant.now().plusSeconds(600))).isFalse();
    }

    @Test
    @DisplayName("lệch đồng hồ vài giây vẫn được coi là online")
    void slightClockSkewStaysOnline() {
        // Hai máy lệch NTP vài giây là bình thường. Loại luôn mốc tương lai sẽ làm người
        // đang chơi nháy sang offline mỗi khi mốc được ghi bởi máy có đồng hồ nhanh hơn.
        assertThat(queryService.isOnline(Instant.now().plusSeconds(5))).isTrue();
    }

    @Test
    @DisplayName("lastSeen đọc cả nhóm bằng một lệnh, khoá vắng mặt bị bỏ qua")
    void lastSeenReadsBatchAndSkipsMissingKeys() {
        UUID present = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        long millis = Instant.now().toEpochMilli();

        when(valueOps.multiGet(anyList()))
                .thenReturn(Arrays.asList(String.valueOf(millis), null));

        Map<UUID, Instant> seen = store.lastSeen(List.of(present, missing));

        // Khoá không tồn tại KHÔNG được đưa vào map: người gọi phân biệt "chưa rõ" bằng
        // việc khoá vắng mặt, chứ không phải bằng một giá trị null trong map.
        assertThat(seen).containsOnlyKeys(present);
        assertThat(seen.get(present).toEpochMilli()).isEqualTo(millis);
    }

    @Test
    @DisplayName("Redis lỗi thì lastSeen trả rỗng, KHÔNG ném")
    void lastSeenSwallowsRedisFailure() {
        when(valueOps.multiGet(anyList()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        // Ném ở đây sẽ làm cả bảng danh sách người dùng trả lỗi vì một chấm màu.
        assertThat(store.lastSeen(List.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    @DisplayName("Redis lỗi thì touch không ném ra ngoài")
    void touchSwallowsRedisFailure() {
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        // Hàm này chạy ở MỌI request của người chơi. Ném ra ngoài sẽ biến một sự cố Redis
        // thành lỗi 500 trên toàn bộ ứng dụng.
        store.touch(UUID.randomUUID());
    }

    @Test
    @DisplayName("giá trị hỏng trong Redis được bỏ qua thay vì làm cả lượt đọc thất bại")
    void corruptedValueIsIgnored() {
        UUID good = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        long millis = Instant.now().toEpochMilli();

        when(valueOps.multiGet(anyList()))
                .thenReturn(Arrays.asList(String.valueOf(millis), "khong-phai-so"));

        Map<UUID, Instant> seen = store.lastSeen(List.of(good, bad));

        assertThat(seen).containsOnlyKeys(good);
    }

    @Test
    @DisplayName("snapshot trả về ĐỦ mọi id được hỏi, kể cả người không có mốc")
    void snapshotCoversEveryRequestedId() {
        UUID online = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();

        when(valueOps.multiGet(anyList()))
                .thenReturn(Arrays.asList(String.valueOf(Instant.now().toEpochMilli()), null));

        List<PresenceEntryResponse> rows = queryService.snapshot(List.of(online, unknown));

        // Bỏ bớt id sẽ buộc phía hiển thị suy luận "không có trong danh sách nghĩa là
        // offline" — một quy ước ngầm dễ lẫn với lời gọi bị lỗi một phần.
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).userId()).isEqualTo(online);
        assertThat(rows.get(0).online()).isTrue();
        assertThat(rows.get(1).userId()).isEqualTo(unknown);
        assertThat(rows.get(1).online()).isFalse();
        assertThat(rows.get(1).lastSeenAt()).isNull();
    }

    @Test
    @DisplayName("nhóm rỗng hoặc null trả rỗng mà không ném")
    void emptyBatchIsHandled() {
        assertThat(store.lastSeen(List.of())).isEmpty();
        assertThat(store.lastSeen(null)).isEmpty();
    }
}
