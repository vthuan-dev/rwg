package com.rwg.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lý do admin duyệt hoặc từ chối một lệnh rút tiền.
 *
 * VÌ SAO BẮT BUỘC: duyệt một lệnh rút là chuyển tiền thật ra khỏi sàn, còn từ chối là hoàn
 * tiền vào ví người chơi. Cả hai đều không hoàn tác được qua giao diện. Không có lý do thì
 * nhật ký chỉ còn "ai" và "khi nào", mất phần "vì sao" — đúng phần cần nhất khi tra soát
 * một khoản chi bất thường vài tháng sau.
 *
 * Luồng điều chỉnh ví 4 mắt đã bắt buộc ghi lý do; luồng rút tiền trước đây không có tham số
 * nào cả, tạo ra hai chuẩn khác nhau cho hai thao tác tài chính tương đương.
 */
public record WithdrawalDecisionRequest(
        @NotBlank
        @Size(max = 255)
        String note
) {
}
