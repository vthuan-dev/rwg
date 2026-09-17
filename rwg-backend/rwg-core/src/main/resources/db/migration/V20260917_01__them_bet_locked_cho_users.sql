-- Thêm cờ bet_locked cho bảng users để admin có thể khóa cược riêng cho từng khách hàng
-- (Tài khoản vẫn đăng nhập và thao tác bình thường, nhưng không thể đặt cược: cược bị nuốt ngầm, không trừ tiền và không thắng/thua).
ALTER TABLE users ADD COLUMN bet_locked BOOLEAN NOT NULL DEFAULT FALSE;
