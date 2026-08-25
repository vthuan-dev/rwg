package com.rwg.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin đặt CÙNG MỘT tỷ lệ cho cả cặp hai chiều của một bàn trong một lượt.
 *
 * VÌ SAO CÓ ENDPOINT RIÊNG thay vì để giao diện gọi {@link SetUserOddsRequest} hai lần:
 * lời gọi thứ hai có thể thất bại (mất mạng, token hết hạn giữa hai lượt, server khởi động
 * lại). Khi đó tài khoản có Lớn = 2.1 mà Nhỏ vẫn 1.98 — một cấu hình LỆCH mà người vận hành
 * tin là đã đặt cân. Đây là kiểu lỗi tệ nhất ở màn hình này vì nó âm thầm và ảnh hưởng tiền
 * chi trả. Một request, một transaction: hoặc cả hai cửa đổi, hoặc không cửa nào đổi.
 *
 * KHÔNG có trường {@code betType}. Danh sách cửa do server suy ra từ
 * {@code TableOddsService.adjustableBetTypesFor}. Để client tự truyền danh sách sẽ mở đường
 * cho việc gửi đúng một cửa qua endpoint "cặp", tức hai endpoint làm cùng một việc.
 *
 * {@code odds} là String rồi parse sang BigDecimal ở service, giống {@link SetUserOddsRequest}:
 * nhận trực tiếp vào {@code double} sẽ làm 0.98 thành 0.9800000000000000266, và con số đó
 * dùng để tính tiền thật.
 */
public record SetUserOddsPairRequest(
        @NotBlank(message = "{validation.admin.odds.table.not_blank}")
        String tableId,

        @NotBlank(message = "{validation.admin.odds.value.not_blank}")
        String odds,

        @Size(max = 255, message = "{validation.admin.reason.size}")
        String reason
) {
}
