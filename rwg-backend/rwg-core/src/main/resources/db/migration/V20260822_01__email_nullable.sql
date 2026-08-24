-- =============================================================================
-- Cho phép users.email NULL + nhận mật khẩu rút tiền ngay khi đăng ký.
--
-- VÌ SAO: form đăng ký của khu người chơi (theo thiết kế đã chốt) chỉ gồm tên
-- đăng nhập, mật khẩu, nhập lại mật khẩu và mật khẩu rút tiền — KHÔNG có email.
-- Trước đây email là NOT NULL nên form này không thể đăng ký được.
--
-- GIỮ NGUYÊN UNIQUE trên email: MySQL cho phép NHIỀU dòng NULL trong unique
-- index (chuẩn SQL coi NULL là "không xác định" nên không so sánh trùng nhau),
-- nên nhiều tài khoản không email vẫn tồn tại song song, trong khi hai tài khoản
-- CÙNG một email thật vẫn bị chặn. Đây là lý do không cần bỏ ràng buộc unique.
--
-- Ghi chú: quy ước khóa chính composite (id, created_at) ở DECISIONS.md mục b chỉ
-- áp dụng khi TẠO MỚI bảng high-volume; đây là ALTER trên bảng users có sẵn.
-- =============================================================================
ALTER TABLE users
    MODIFY COLUMN email VARCHAR(255) NULL;
