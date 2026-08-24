-- =============================================================================
-- Thêm ba trường hồ sơ cá nhân: họ tên, quốc gia, số điện thoại.
--
-- VÌ SAO: trang "Chỉnh sửa hồ sơ" của khu người chơi có ba ô này, nhưng bảng
-- users chỉ có username/email/locale nên không có chỗ nào lưu chúng.
--
-- CẢ BA ĐỀU NULL: hàng nghìn tài khoản đã tồn tại chưa từng khai các thông tin
-- này. Đặt NOT NULL sẽ làm chính câu ALTER này thất bại trên dữ liệu hiện có,
-- và cũng sai về nghiệp vụ — đăng ký chỉ cần tên đăng nhập và mật khẩu.
--
-- country_code LƯU MÃ ISO 3166-1 alpha-2 ('VN'), KHÔNG lưu tên hiển thị:
-- người dùng đổi ngôn ngữ thì cùng một quốc gia sẽ được ghi thành "Vietnam"
-- hoặc "Việt Nam" hoặc "ベトナム" tuỳ lúc họ bấm lưu, nên cột trở nên vô dụng
-- cho việc lọc hay thống kê. Tên hiển thị thuộc về tầng giao diện.
--
-- KHÔNG đặt UNIQUE trên phone: hai người dùng chung một số là chuyện bình
-- thường (vợ chồng, người thân, số của cửa hàng). Chặn cứng ở tầng DB sẽ khoá
-- người dùng thật. Việc phát hiện nhiều tài khoản dùng chung thông tin là
-- nhiệm vụ của AccountLinkDetector bên rủi ro — nơi có thể đánh dấu để người
-- vận hành xem xét thay vì từ chối thẳng.
--
-- phone dài 20: đủ cho số quốc tế dài nhất (E.164 tối đa 15 chữ số) cộng dấu
-- cộng và vài dấu cách hoặc gạch ngang người dùng hay gõ kèm.
--
-- BA CÂU ALTER RIÊNG, không gộp thành một câu nhiều ADD COLUMN: H2 (dùng cho
-- test tích hợp) không nhận cú pháp gộp và sẽ báo lỗi cú pháp, dù MySQL chấp
-- nhận. Mọi migration hiện có của dự án cũng viết tách như vậy.
--
-- Ghi chú: quy ước khoá chính composite (id, created_at) ở DECISIONS.md mục b
-- chỉ áp dụng khi TẠO MỚI bảng high-volume; đây là ALTER trên bảng users có sẵn.
-- =============================================================================
ALTER TABLE users ADD COLUMN full_name VARCHAR(100) NULL;

ALTER TABLE users ADD COLUMN country_code CHAR(2) NULL;

ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL;
