# RWG Backend - Hướng dẫn cài đặt môi trường DEV (Windows, KHÔNG cần Docker)

Tài liệu này mô tả cách chuẩn bị máy dev để chạy `rwg-backend` hoàn toàn **local, không Docker**.
Docker chỉ dùng khi deploy (xem `docker-compose.yml`) — KHÔNG cần cho dev hàng ngày.

## 1. Yêu cầu tối thiểu

| Công cụ | Phiên bản | Ghi chú |
|---|---|---|
| JDK | **21** (Temurin/Khuyến nghị) | `java -version` phải ra 21.x |
| Maven | 3.9+ | `mvn -version` |
| **MySQL Server** | **8.0.16 trở lên (chuẩn: 8.4 LTS)** | Bắt buộc — schema dùng `DECIMAL(20,8)`, `JSON`, `CHECK`, `AUTO_INCREMENT`, InnoDB utf8mb4 |
| Redis | Optional | Không có Redis app tự dùng fallback in-memory (chỉ dev 1 instance) |

> ℹ️ **Ghi chú:** Máy dev đang có sẵn **MySQL 9.x (Innovation release)** vẫn chạy dev bình thường; tuy nhiên khuyến nghị chuẩn hóa về **MySQL 8.4 LTS** cho các môi trường sau này để được hỗ trợ dài hạn.

Kiểm tra nhanh tất cả:

```powershell
pwsh scripts/dev-check.ps1
```

## 2. Cài MySQL 8.4 trên Windows

1. Tải **MySQL Installer** chính thức: <https://dev.mysql.com/downloads/installer/>
   (chọn bản "mysql-installer-community", component **MySQL Server 8.4 LTS**; có thể bỏ dấu tick "MySQL Shell/Workbench" nếu không cần).
2. Chạy installer, cấu hình mặc định là đủ cho dev:
   - Config Type: **Development Machine**
   - Port: **3306**
   - Authentication: **Use Strong Password Encryption** (mặc định)
   - Đặt mật khẩu root khi được hỏi (ghi nhớ mật khẩu này).
3. Kiểm tra sau cài:

```powershell
mysql --version        # phải hiện ver 8.x (>= 8.0.16)
```

Nếu `mysql` không có trong PATH, thêm thư mục cài đặt (mặc định `C:\Program Files\MySQL\MySQL Server 8.4\bin`) vào PATH hoặc gọi bằng đường dẫn đầy đủ.

## 3. Tạo database dev `rwg_dev`

Mở MySQL client (CMD/PowerShell) bằng user root của bạn:

```powershell
mysql -u root -p
```

Rồi chạy:

```sql
CREATE DATABASE rwg_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- (Tuỳ chọn) tạo user dev riêng thay vì dùng root:
-- CREATE USER 'dev'@'localhost' IDENTIFIED BY '<mat-khau-manh>';
-- GRANT ALL PRIVILEGES ON rwg_dev.* TO 'dev'@'localhost';
-- FLUSH PRIVILEGES;
```

Kiểm tra:

```sql
SHOW DATABASES LIKE 'rwg_dev';
```

## 4. Cấu hình kết nối DB cho app

Profile `dev` (`application-dev.yml`) dùng:

- URL: `jdbc:mysql://localhost:3306/rwg_dev`
- User: `${DB_USER:root}`
- Password: `${DB_PASSWORD:1001}`

> ⚠️ **QUAN TRỌNG về mật khẩu `1001`:**
> - `1001` là **default CHỈ dành cho DEV LOCAL** trên máy cá nhân — đặt sẵn để dev chạy được ngay, không cần biến môi trường.
> - **Mọi môi trường khác** (staging, production, Docker) **BẮT BUỘC** override qua biến môi trường `DB_PASSWORD` (quy ước: CHỈ dùng `DB_USER`/`DB_PASSWORD` ở mọi file).
> - **KHÔNG BAO GIỜ** dùng mật khẩu này cho staging/production, và nó KHÔNG xuất hiện trong `docker-compose.yml`.
> - Nếu mật khẩu MySQL local của bạn khác `1001`: đặt biến môi trường trước khi chạy:
>
> ```powershell
> $env:DB_USER = "root"
> $env:DB_PASSWORD = "<mat-khau-cua-ban>"
> ```

Nếu bạn tạo user `dev` riêng ở bước 3: `$env:DB_USER = "dev"`.

### JWT secret

- Cấu hình base (`application.yml`) đặt `rwg.security.jwt-secret: ${JWT_SECRET}` **KHÔNG có default** —
  thiếu biến môi trường `JWT_SECRET` thì app **fail-fast khi khởi động** (giống `DB_PASSWORD`).
- CHỈ profile `dev` (`application-dev.yml`) được phép có default dev rõ ràng — KHÔNG dùng cho môi trường thật.
- Docker/staging/prod: bắt buộc set `JWT_SECRET` (xem `.env.app.example`, tối thiểu 32 ký tự cho HS256).

### Khóa mã hóa số tài khoản ngân hàng (RWG_BANK_ENC_KEY)

- Cấu hình base (`application.yml`) đặt `rwg.crypto.bank-enc-key: ${RWG_BANK_ENC_KEY}` **KHÔNG có default** —
  thiếu biến môi trường `RWG_BANK_ENC_KEY` thì app **fail-fast khi khởi động** (giống `JWT_SECRET`).
- CHỈ profile `dev` được phép có default dev rõ ràng (đánh dấu CẤM DÙNG CHO MÔI TRƯỜNG THẬT).
- Docker/staging/prod: bắt buộc set `RWG_BANK_ENC_KEY` (xem `.env.app.example`, tối thiểu 32 ký tự).
- ⚠️ Khóa này mã hóa số TK ngân hàng bằng AES-256-GCM: **đổi khóa sau khi đã có dữ liệu**
  sẽ khiến số TK cũ KHÔNG giải mã được — chốt khóa trước khi nhập dữ liệu thật.

### Shared-secret webhook thanh toán (RWG_PAYMENT_CALLBACK_SECRET)

- Webhook `POST /api/v1/payments/callback` yêu cầu header `X-Callback-Secret` khớp
  `rwg.payment.callback-secret`; thiếu/sai -> **401**.
- Cấu hình base đặt `rwg.payment.callback-secret: ${RWG_PAYMENT_CALLBACK_SECRET}` **KHÔNG có default** —
  thiếu biến môi trường thì app **fail-fast khi khởi động** (giống `JWT_SECRET`).
- CHỈ profile `dev`/`test` có default riêng; Docker/staging/prod bắt buộc set biến môi trường
  (xem `.env.app.example`) và cấu hình cùng giá trị phía provider thanh toán.

## 5. Redis (optional)

- Chưa cài Redis: **không cần làm gì** — đặt `RWG_REDIS_ENABLED=false` (đã là default của profile dev), app dùng store in-memory cho refresh token + rate limit.
- Muốn có Redis trên Windows: dùng **Memurai** (<https://www.memurai.com/>) hoặc WSL2 + Redis, sau đó `$env:RWG_REDIS_ENABLED = "true"`.
- Hạn chế của fallback in-memory: state KHÔNG chia sẻ giữa nhiều instance — chỉ chấp nhận ở dev.

## 6. Chạy app + chạy test

```powershell
# Chạy app (cần MySQL local đã tạo rwg_dev)
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"

# Chạy test ĐẦY ĐỦ - KHÔNG cần Docker, KHÔNG cần MySQL cài sẵn:
# test dùng H2 in-memory ở MODE=MySQL chạy chính bộ migration MySQL thật.
mvn verify

# (Tuỳ chọn, cần Docker) Chạy thêm integration test trên MySQL 8.4 + Redis thật:
mvn verify -Ptestcontainers
```

Sau khi app chạy:

- Health: <http://localhost:8080/actuator/health>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

Flyway sẽ tự chạy `db/migration/V1__init_schema.sql` (schema MySQL 8) ở lần khởi động đầu.

## 7. Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `Communications link failure` khi khởi động dev | MySQL chưa chạy hoặc sai cổng — chạy `scripts/dev-check.ps1`, kiểm tra service MySQL |
| `Access denied for user` | Sai user/mật khẩu — đặt lại `$env:DB_USER` / `$env:DB_PASSWORD` |
| `Unknown database 'rwg_dev'` | Chưa tạo DB — làm bước 3 |
| `Failed to obtain JDBC connection ... flyway` | Kiểm tra đã thêm `flyway-mysql` trong pom (bắt buộc với Flyway + MySQL) |
| Test fail liên quan JSON/CHECK | Đảm bảo đang dùng H2 mode MySQL (`application-test.yml`) — không sửa tay URL |
