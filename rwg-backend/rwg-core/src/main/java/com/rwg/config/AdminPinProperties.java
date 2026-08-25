package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mã xác nhận cho các thao tác quản trị KHÔNG HOÀN TÁC ĐƯỢC (prefix rwg.admin).
 *
 * <h2>VÌ SAO Ở CẤU HÌNH, KHÔNG VIẾT THẲNG TRONG MÃ</h2>
 * <ol>
 *   <li><b>Mã trong tệp .java sẽ vào git.</b> Mọi người đọc được repo đều thấy nó, gồm cả
 *       người nhận bàn giao sau này và bất kỳ ai lấy được một bản sao. Một mã mà nhiều
 *       người biết thì không còn là lớp xác nhận nào cả.</li>
 *   <li><b>Đổi mã không cần triển khai lại.</b> Nghi ngờ mã bị lộ là lúc cần đổi ngay,
 *       không phải lúc chờ một lượt build.</li>
 *   <li>Mỗi môi trường có mã riêng, nên mã ở máy phát triển không mở được gì trên máy chủ
 *       thật.</li>
 * </ol>
 *
 * Đặt qua biến môi trường trên máy chủ:
 * <pre>
 *   RWG_ADMIN_DESTRUCTIVE_PIN=171204
 * </pre>
 *
 * <h2>MÃ NGẮN KHÔNG PHẢI LỚP BẢO VỆ CHÍNH</h2>
 * Sáu chữ số chỉ có một triệu khả năng — một đoạn script dò hết trong khoảng một giây. Thứ
 * thực sự chặn việc dò là <b>giới hạn số lần thử</b> trong
 * {@code AdminDestructivePinService}, không phải độ dài của mã. Mã này chỉ để ngăn cái bấm
 * nhầm: nó buộc người vận hành dừng lại và gõ một thứ mà họ phải chủ động nhớ.
 *
 * Lớp bảo vệ thật vẫn là xác thực và phân quyền — chỉ ADMIN gọi được endpoint này.
 */
@ConfigurationProperties(prefix = "rwg.admin")
public record AdminPinProperties(

        /**
         * Mã xác nhận thao tác không hoàn tác được.
         *
         * Để trống nghĩa là CHẶN HẲN các thao tác đó, không phải "cho qua không cần mã" —
         * xem {@link #configured()}. Một cấu hình thiếu không được biến việc xóa tài khoản
         * thành thao tác một cú bấm.
         */
        String destructivePin
) {

    /**
     * Đã cấu hình mã hay chưa.
     *
     * Phát biểu quy ước "chưa cấu hình thì chặn" MỘT lần tại đây, thay vì để mỗi chỗ dùng
     * tự kiểm null. Chỗ nào quên kiểm sẽ mở toang thao tác nguy hiểm nhất của hệ thống, và
     * sai sót đó không hiện ra ở bất kỳ đâu cho tới khi có người xóa mất một tài khoản.
     */
    public boolean configured() {
        return destructivePin != null && !destructivePin.isBlank();
    }
}
