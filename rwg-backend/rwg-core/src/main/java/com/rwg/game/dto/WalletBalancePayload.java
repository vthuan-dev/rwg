package com.rwg.game.dto;

import java.time.Instant;

/**
 * Gói unicast số dư mới cho user: /user/queue/wallet.
 *
 * `reason` nói VÌ SAO số dư đổi, dùng đúng từ vựng của `WalletRefType` (WIN / REFUND /
 * JACKPOT). Không có `reason` nghĩa là khoản vào KHÔNG đến từ ván cược — hiện chỉ có
 * đường admin cộng tay (`GameEventRelay` phát một Map chỉ có khoá `balance`).
 *
 * Vì sao phải phân biệt: màn game hiện toast "NẠP TIỀN THÀNH CÔNG" cho MỌI lần số dư
 * tăng. Nhưng nạp tiền của người chơi (`DepositService`) không hề phát WebSocket — nó chỉ
 * được vòng poll phát hiện. Nên mọi gói đi qua kênh này đều là tiền do hệ thống game trả,
 * và toast đó nổ đúng lúc người chơi VỪA THẮNG cược — nói sai bản chất dòng tiền.
 */
public record WalletBalancePayload(
        String type,
        String balance,
        Instant serverTime,
        String reason) {

    /**
     * Tiền vào do CHỐT SỔ VÁN CƯỢC — tương ứng `WalletRefType.WIN`.
     *
     * Đặt tên theo `unicastXocDiaWin`/`unicastWin` (các hàm gửi gói này), chứ không có
     * nghĩa "người chơi này đã thắng": settlement gửi cho MỌI phiếu đã chốt, kể cả phiếu
     * thua (khi đó số dư không đổi). Điều màn game cần biết chỉ là "khoản này đến từ ván
     * cược", chứ không phải "từ nạp tiền".
     */
    public static final String REASON_WIN = "WIN";

    /** Hoàn tiền: ván bị huỷ, hoặc lệnh rút bị từ chối — `WalletRefType.REFUND`. */
    public static final String REASON_REFUND = "REFUND";

    /** Nổ hũ Tứ Quý — `WalletRefType.JACKPOT`. */
    public static final String REASON_JACKPOT = "JACKPOT";

    /**
     * Không kèm lý do — dùng cho khoản vào không đến từ ván cược (admin cộng tay).
     * Màn game vẫn được phép báo "vừa có tiền vào".
     */
    public static WalletBalancePayload of(String balance) {
        return new WalletBalancePayload("WALLET_BALANCE", balance, Instant.now(), null);
    }

    /** Kèm lý do — xem các hằng REASON_*. */
    public static WalletBalancePayload of(String balance, String reason) {
        return new WalletBalancePayload("WALLET_BALANCE", balance, Instant.now(), reason);
    }
}
