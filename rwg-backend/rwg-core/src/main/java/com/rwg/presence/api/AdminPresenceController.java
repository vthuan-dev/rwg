package com.rwg.presence.api;

import com.rwg.presence.dto.PresenceEntryResponse;
import com.rwg.presence.service.PresenceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Trạng thái online/offline của người chơi, cho khu quản trị.
 *
 * <h2>VÌ SAO CÓ ĐIỂM CUỐI RIÊNG, KHI BẢNG DANH SÁCH ĐÃ TRẢ VỀ TRẠNG THÁI NÀY</h2>
 * Trạng thái có mặt đổi liên tục nên giao diện phải làm mới định kỳ. Tải lại cả danh sách
 * người dùng chỉ để đổi một chấm màu sẽ chạy lại truy vấn tìm kiếm, truy vấn ví và cả phép
 * phân trang — mỗi 30 giây, cho mỗi nhân sự đang mở trang. Điểm cuối này chỉ đọc Redis.
 *
 * Nó cũng giữ cho việc làm mới KHÔNG động tới danh sách đang hiện: người vận hành đang gõ
 * trong ô tìm kiếm không bị bảng nhảy dưới tay.
 *
 * <h2>PHÂN QUYỀN</h2>
 * Enforce tập trung trong {@code SecurityConfig}: {@code /api/v1/admin/**} đã yêu cầu một
 * trong bốn vai trò nhân sự. Đây là dữ liệu chỉ đọc, không chạm tiền, nên không cần hạn chế
 * hẹp hơn. KHÔNG rải {@code @PreAuthorize} ở đây.
 */
@RestController
@RequestMapping("/api/v1/admin/presence")
@Tag(name = "Admin - Presence", description = "Người chơi nào đang online")
public class AdminPresenceController {

    /**
     * Chặn danh sách id quá dài.
     *
     * Trang danh sách người dùng tối đa 100 dòng ({@code AdminUserController.MAX_PAGE_SIZE}),
     * nên 100 là đủ cho mọi màn hình thật. Không có giới hạn thì một lời gọi với hàng nghìn
     * id sẽ thành một lệnh MGET khổng lồ giữ kết nối Redis dùng chung.
     */
    private static final int MAX_IDS = 100;

    private final PresenceQueryService presenceQueryService;

    public AdminPresenceController(PresenceQueryService presenceQueryService) {
        this.presenceQueryService = presenceQueryService;
    }

    /**
     * Trạng thái có mặt của đúng những người chơi được hỏi.
     *
     * NHẬN DANH SÁCH ID chứ không trả về "mọi người đang online": giao diện chỉ cần trạng
     * thái của các dòng đang hiện trên màn hình. Trả về toàn bộ người online sẽ gửi kèm dữ
     * liệu của người không có trên trang, và lượng dữ liệu đó tăng theo số người đang chơi
     * chứ không theo kích thước màn hình.
     */
    @GetMapping
    @Operation(summary = "Trạng thái online của một nhóm người chơi (tối đa 100 id)")
    public List<PresenceEntryResponse> presence(@RequestParam List<UUID> ids) {
        List<UUID> limited = ids.size() > MAX_IDS ? ids.subList(0, MAX_IDS) : ids;
        return presenceQueryService.snapshot(limited);
    }
}
