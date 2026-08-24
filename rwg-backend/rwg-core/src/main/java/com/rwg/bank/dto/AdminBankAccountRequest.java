package com.rwg.bank.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin thêm tài khoản ngân hàng HỘ người chơi.
 *
 * VÌ SAO CẦN DTO RIÊNG, KHÔNG DÙNG LẠI {@link BankAccountRequest}:
 * {@code BankAccountRequest} có trường {@code withdrawalPassword} bắt buộc — mật khẩu
 * cấp hai của chính người chơi. Admin KHÔNG biết mật khẩu đó và KHÔNG được biết.
 *
 * Nếu dùng lại DTO cũ thì có hai đường, cả hai đều tệ: hoặc admin phải hỏi khách mật
 * khẩu rút tiền (phá vỡ toàn bộ mục đích của mật khẩu cấp hai — nó tồn tại để ngay cả
 * người trong hệ thống cũng không thay được tài khoản nhận tiền), hoặc phải nới trường
 * đó thành không bắt buộc và lúc đó luồng NGƯỜI CHƠI mất lớp bảo vệ.
 *
 * Thay vào đó, thẩm quyền của admin đến từ vai trò trong JWT, và mỗi lần dùng đều ghi
 * một dòng audit mang {@code adminId}.
 *
 * @param bankCode mã ngân hàng
 * @param accountNumber số tài khoản — service mã hoá AES-256-GCM trước khi lưu
 * @param holderName tên chủ tài khoản
 * @param reason lý do BẮT BUỘC, ví dụ "khách báo sai số qua chat #123".
 *     Bắt buộc vì đây là thao tác một người làm thay người khác trên dữ liệu quyết định
 *     tiền chảy về đâu. Không có lý do thì nhật ký chỉ nói "admin X đã đổi tài khoản của
 *     Y" mà không trả lời được câu hỏi quan trọng nhất khi điều tra: vì sao.
 */
public record AdminBankAccountRequest(
        @NotBlank(message = "{validation.bank.bank_code.not_blank}")
        String bankCode,

        @NotBlank(message = "{validation.bank.account_number.not_blank}")
        String accountNumber,

        @NotBlank(message = "{validation.bank.holder_name.not_blank}")
        String holderName,

        @NotBlank(message = "{validation.admin.reason.not_blank}")
        String reason
) {
}
