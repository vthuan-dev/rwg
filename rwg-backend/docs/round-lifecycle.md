# Round Lifecycle — Đặc tả vòng đời bàn chơi (Phase c)

> Tài liệu KHÓA vòng đời một round Roulette của RWG backend. Mọi thay đổi phải
> cập nhật file này trước. Tuân thủ DECISIONS.md (M1 trừ tiền ngay khi đặt cược,
> M2 stake-inclusive payout, tiền tệ BigDecimal/Money, ledger append-only).

## 1. Các phase của một round

```
BETTING_OPEN (45s) -> BETTING_CLOSED (2s) -> SPINNING (8s) -> RESULT (3s) -> SETTLE (5s) -> round mới
```

| Phase          | Duration (prod) | Property                      | Ý nghĩa |
|----------------|-----------------|-------------------------------|---------|
| BETTING_OPEN   | 45s             | `rwg.game.round.betting-open` | Nhận cược. Đặt cược trừ ví NGAY (M1), ledger ref_type=BET. |
| BETTING_CLOSED | 2s              | `rwg.game.round.betting-closed` | "No more bets" — API từ chối cược mới (lỗi i18n `validation.bet.round_closed`). |
| SPINNING       | 8s              | `rwg.game.round.spinning`     | Quay số: sinh số 0–36 bằng `SecureRandom`, lưu `winning_number` khi vào RESULT. |
| RESULT         | 3s              | `rwg.game.round.result`       | Công bố kết quả (broadcast 1 gói/bàn kèm timestamp server). |
| SETTLE         | 5s              | `rwg.game.round.settle`       | Trả thưởng/thua async batch ngoài request thread. |

Thời lượng MỖI phase là config-driven (`rwg.game.round.*`, kiểu Duration) để
integration test rút ngắn còn 100–300ms/phase. Production default đúng bảng trên.

## 2. Single-writer per table

- Mỗi bàn có ĐÚNG 1 executor (single-thread `ScheduledExecutorService`) chạy vòng
  lặp phase — mọi ghi lên row `rounds` của bàn đó chỉ từ thread này (single-writer).
- **MVP CHẠY 1 INSTANCE**: scheduler KHÔNG có khóa phân tán. Triển khai nhiều
  instance sẽ nhân đôi round — chặng sau phải thêm leader-election / DB lock
  (`GET_LOCK` MySQL hoặc lock bảng `scheduler_locks`). Ghi nhận là hạn chế đã biết của MVP.
- Chuyển phase phát sự kiện broadcast `/topic/game/table/{tableId}` — 1 gói/bàn,
  payload kèm `serverTime` + `phaseEndsAt` để client tự countdown.

## 3. Đặt cược (BetService)

- Chỉ nhận khi phase = BETTING_OPEN; loại cược/số tiền/selection được validate
  (lỗi i18n `validation.bet.*`).
- Trừ tiền qua `WalletService.debit` NGAY khi đặt (M1), idempotency_key:
  `"BET:{roundId}:{userId}:{seq}"` — seq do client gửi để retry an toàn
  (guard table `wallet_ledger_guard` PK thuần chặn double-debit ở tầng DB).
- Bet được lưu status=PENDING cùng transaction với debit.

## 4. Settlement (SettlementService)

- Chạy ASYNC batch trên executor riêng (ngoài request thread):
  thắng → credit tổng tiền thắng mỗi user 1 lần, key `"WIN:{roundId}:{userId}"`
  (payout stake-inclusive M2 qua `Money.winningPayoutAtOdds`); thua → bet SETTLED,
  KHÔNG credit. Settle đúng 1 lần nhờ guard idempotency tầng DB.
- Claim round bằng UPDATE điều kiện nguyên tử `OPEN -> SETTLED` CÙNG transaction
  với credit/update bets; crash giữa chừng → toàn bộ tx rollback, round vẫn OPEN.
- Metric `settlement_lag`: log độ trễ (ms) từ `result_at` của round đến lúc
  settle hoàn tất.
- Sau settle, gọi SPI `WagerSettledListener` (userId, gameId, amountBet, amountWon)
  — hook cho loyalty phase (e); chưa có impl nào vẫn chạy bình thường.

## 5. Hủy vòng (void/refund)

- Hủy round: UPDATE nguyên tử `OPEN -> VOIDED` (thua race = round đã settle),
  sau đó hoàn tiền: tổng stake mỗi user credit 1 lần ref_type=REFUND,
  key `"REFUND:{roundId}:{userId}"`, bets -> VOIDED.
- Không thể hủy round đã SETTLED (tiền đã trả theo kết quả).

## 6. An toàn khi app tắt giữa chừng

- Mọi thao tác tiền (debit cược, credit thắng, refund) đi qua WalletService với
  guard idempotency tầng DB nên KHÔNG bao giờ nhân đôi dù retry sau restart.
- Bets đã debit mà CHƯA settle (app chết giữa round): round còn status=OPEN.
  Khi khởi động lại, scheduler phát hiện round OPEN "mồ côi" (không thuộc vòng
  lặp hiện tại) và VOID + REFUND toàn bộ bets của round đó — tiền về ví người chơi.
- Job reconciliation chạy mỗi 5 phút so tổng ledger (credit − debit) với balance
  từng ví; chênh lệch → log WARN (tự sửa KHÔNG bắt buộc chặng này, chỉ cảnh báo).

## 7. Sự kiện WebSocket (chống storm)

- Sự kiện vòng đời/phase/kết quả: 1 gói/bàn trên `/topic/game/table/{tableId}`.
- `BET_PLACED`: aggregate theo cửa sổ 250ms (số lệnh + tổng stake), KHÔNG phát
  từng lệnh.
- Thắng + số dư mới: unicast `/user/queue/game/results` và `/user/queue/wallet`.
- STOMP CONNECT bắt buộc JWT (WsAuthChannelInterceptor); HTTP handshake `/ws`
  phải qua Spring Security (đã bỏ rule permitAll).
