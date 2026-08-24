-- =============================================================================
-- TẠO TÀI KHOẢN ADMIN CHO DEV LOCAL
-- =============================================================================
--
-- ⚠ CẢNH BÁO: Script này CHỈ dành cho MÁY DEV LOCAL.
--
-- Mật khẩu "admin123" nằm trong mọi danh sách mật khẩu yếu được dùng để dò tự
-- động. TUYỆT ĐỐI KHÔNG chạy script này trên staging hay production. Trên môi
-- trường thật, tạo tài khoản nhân sự qua khu quản trị (AdminUserController) với
-- mật khẩu mạnh do người dùng tự đặt.
--
-- Thông tin đăng nhập sau khi chạy:
--   Username : admin
--   Password : admin123
--   Role     : ADMIN (toàn quyền)
--   Đường vào: http://localhost:3000/admin/2026/login
--
-- Cách chạy:
--   mysql -u root -p -D rwg_dev < scripts/create-dev-admin.sql
--
-- -----------------------------------------------------------------------------
-- VỀ password_hash BÊN DƯỚI
-- -----------------------------------------------------------------------------
-- Đây là hash BCrypt strength 12 của chuỗi "admin123", khớp với
-- SecurityConfig.BCRYPT_STRENGTH. BCrypt có salt ngẫu nhiên nên mỗi lần băm cho
-- ra chuỗi khác nhau — giá trị dưới đây là MỘT hash hợp lệ, không phải hash duy
-- nhất. Nếu cần đổi mật khẩu, sinh hash mới bằng BCryptPasswordEncoder(12) chứ
-- đừng tự sửa tay chuỗi này.
--
-- KHÔNG dùng hàm băm của MySQL: MySQL không có BCrypt, và SHA2/MD5 sẽ tạo ra
-- hash mà PasswordEncoder không đọc được -> đăng nhập luôn thất bại.
-- =============================================================================

-- Bảng users dùng CHAR(36) cho id (xem hibernate.type.preferred_uuid_jdbc_type).
-- UUID cố định để chạy lại script nhiều lần không sinh thêm bản ghi mới.
INSERT INTO users (
    id,
    username,
    email,
    password_hash,
    role,
    status,
    kyc_level,
    locale,
    created_at,
    updated_at
) VALUES (
    '00000000-0000-4000-8000-000000000001',
    'admin',
    'admin@rwg.local',
    '$2a$12$P4Kd67n/uFrEIDrQnWj6dOlAy3Ky.lxTQczcNpWf42gEAp3umvXC6',
    'ADMIN',
    'ACTIVE',
    'NONE',
    'en',
    UTC_TIMESTAMP(),
    UTC_TIMESTAMP()
)
-- Chạy lại được nhiều lần: nếu tài khoản đã tồn tại thì đặt lại mật khẩu và
-- quyền về trạng thái đã biết, thay vì báo lỗi trùng khoá.
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    role          = VALUES(role),
    status        = VALUES(status),
    updated_at    = UTC_TIMESTAMP();

-- Xác minh kết quả. Cột password_hash CỐ TÌNH không select ra để không in hash
-- lên màn hình hay log terminal.
SELECT username, email, role, status, locale
FROM users
WHERE username = 'admin';
