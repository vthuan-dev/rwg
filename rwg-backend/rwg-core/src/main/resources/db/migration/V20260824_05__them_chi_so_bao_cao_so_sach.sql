-- Chỉ số phục vụ báo cáo sổ sách người chơi.
--
-- VÌ SAO CẦN: hai truy vấn tổng hợp mới lọc theo các cột chưa có chỉ số phù hợp.
--
-- `bets` đã có idx_bets_user(user_id, created_at), nhưng báo cáo còn lọc thêm
-- `status = 'SETTLED'` và nhóm theo game. Với chỉ số cũ, MySQL lấy đúng khoảng
-- theo user + thời gian rồi phải đọc từng hàng để kiểm `status`.
--
-- `wallet_transactions` có idx_wallet_transactions_ref(ref_type, ref_id) — thứ tự
-- cột này phục vụ việc tra một giao dịch cụ thể, KHÔNG phục vụ việc cộng tổng
-- ADJUSTMENT của một ví. Truy vấn báo cáo lọc theo (wallet_id, ref_type, khoảng
-- thời gian) nên cần một chỉ số riêng theo đúng thứ tự đó.
--
-- Với vài chục bản ghi thì không ai thấy khác biệt. Với vài trăm nghìn ván thì mỗi
-- lần mở báo cáo là một lượt quét bảng, và nó chạy trên CÙNG cơ sở dữ liệu đang
-- phục vụ người chơi đặt cược.
--
-- MỘT CÂU LỆNH CHO MỘT THAY ĐỔI: H2 ở MODE=MySQL (chạy trong test) từ chối nhiều
-- thay đổi trong một câu ALTER TABLE.

CREATE INDEX idx_bets_user_status_created ON bets (user_id, status, created_at);

CREATE INDEX idx_wallet_txn_wallet_ref ON wallet_transactions (wallet_id, ref_type, created_at);
