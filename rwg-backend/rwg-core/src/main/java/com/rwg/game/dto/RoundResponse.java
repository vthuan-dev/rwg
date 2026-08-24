package com.rwg.game.dto;

import java.time.Instant;

/**
 * Vòng hiện tại của bàn (GET /api/v1/games/tables/{id}/rounds/current).
 *
 * {@code phaseEndsAt} là thời điểm pha HIỆN TẠI kết thúc — trong pha đặt cược, đây
 * chính là lúc đóng cửa cược. {@code roundEndsAt} là thời điểm CẢ VÒNG kết thúc, muộn
 * hơn vì còn các pha quay số, công bố kết quả và thanh toán. Client vẽ hai đồng hồ
 * riêng từ hai mốc này.
 *
 * Cả hai bắt buộc phải do server tính: thời lượng các pha nằm trong
 * {@code rwg.game.round.*}, nếu client tự cộng theo con số viết cứng thì mọi lần đổi
 * cấu hình là đồng hồ sai mà không ai biết.
 *
 * Cả hai null khi vòng đã đóng (SETTLED/VOIDED) — lúc đó không còn gì để đếm.
 *
 * Dùng kèm {@code serverTime}: client lấy hiệu giữa các mốc này và {@code serverTime}
 * thay vì so với đồng hồ máy mình, vì đồng hồ máy người dùng thường lệch.
 *
 * @param startedAt thời điểm vòng BẮT ĐẦU. Đây là thứ client hiện làm "giờ ván", chứ
 *     không phải {@code serverTime}: hai vòng liền nhau đọc trong cùng một giây sẽ có
 *     {@code serverTime} gần như bằng nhau và hiện ra hai ván trông như cùng giờ.
 *     KHÁC {@code roundEndsAt}: mốc này là quá khứ và không bao giờ null, kể cả với
 *     vòng đã đóng, nên dùng được cho cả trang lịch sử.
 * @param roundSeconds độ dài trọn một vòng, tính bằng giây. Trả kèm để client hiện
 *     "mỗi vòng N giây" theo cấu hình thật thay vì viết cứng — cấu hình hiện tại là 63
 *     giây, không phải một phút chẵn.
 */
public record RoundResponse(
        String roundId,
        String tableId,
        long roundSeq,
        String phase,
        String status,
        Integer winningNumber,
        String baccaratPlayerCards,
        String baccaratBankerCards,
        Integer baccaratPlayerScore,
        Integer baccaratBankerScore,
        Boolean baccaratPlayerPair,
        Boolean baccaratBankerPair,
        String baccaratResult,
        String kl28Numbers,
        Integer kl28Sum,
        Instant startedAt,
        Instant phaseEndsAt,
        Instant roundEndsAt,
        long roundSeconds,
        Instant serverTime) {
}
