package com.rwg.presence.service;

import com.rwg.config.PresenceProperties;
import com.rwg.presence.dto.PresenceEntryResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Biến mốc hoạt động thô thành kết luận online/offline.
 *
 * Tách khỏi {@link PresenceStore} vì hai việc khác nhau: store chỉ biết lưu và đọc một con
 * số, còn lớp này giữ QUY TẮC "im lặng bao lâu thì coi là đã rời đi". Nhờ tách ra mà quy
 * tắc đó chỉ tồn tại ở MỘT chỗ và được cả bảng danh sách người dùng lẫn điểm cuối làm mới
 * dùng chung — hai nơi tự tính riêng thì sẽ có lúc lệch nhau.
 */
@Service
public class PresenceQueryService {

    private final PresenceStore presenceStore;
    private final PresenceProperties properties;

    public PresenceQueryService(PresenceStore presenceStore, PresenceProperties properties) {
        this.presenceStore = presenceStore;
        this.properties = properties;
    }

    /**
     * Mốc hoạt động cuối của một nhóm người chơi; khoá vắng mặt nghĩa là chưa rõ.
     *
     * Dùng bởi bảng danh sách người dùng, nơi đã có sẵn một lượt truy vấn cho cả trang.
     */
    public Map<UUID, Instant> lastSeen(Collection<UUID> userIds) {
        return presenceStore.lastSeen(userIds);
    }

    /** Mốc này còn đủ mới để coi là đang online hay không. */
    public boolean isOnline(Instant lastSeenAt) {
        if (lastSeenAt == null) {
            return false;
        }
        // So bằng khoảng thời gian giữa hai mốc, KHÔNG bằng `isAfter(now - window)`: cách
        // này chịu được cả mốc ở TƯƠNG LAI (đồng hồ máy ghi chạy nhanh hơn máy đọc vài
        // giây), vốn sẽ bị `isAfter` coi là hợp lệ vô thời hạn.
        Duration silence = Duration.between(lastSeenAt, Instant.now());
        return silence.abs().compareTo(properties.onlineWindow()) <= 0;
    }

    /**
     * Trạng thái có mặt của một nhóm người chơi, dùng cho điểm cuối làm mới định kỳ.
     *
     * Trả về ĐỦ mọi id được hỏi, kể cả người không có mốc nào. Bỏ bớt sẽ buộc phía hiển thị
     * suy luận "id không có trong danh sách trả về nghĩa là offline" — một quy ước ngầm dễ
     * lẫn với trường hợp lời gọi bị lỗi một phần.
     */
    public List<PresenceEntryResponse> snapshot(Collection<UUID> userIds) {
        Map<UUID, Instant> seen = presenceStore.lastSeen(userIds);
        return userIds.stream()
                .distinct()
                .map(id -> {
                    Instant at = seen.get(id);
                    return new PresenceEntryResponse(id, isOnline(at), at);
                })
                .toList();
    }
}
