# RWG Backend

Backend cho nền tảng casino RWG — modular monolith trên Spring Boot 4.x, Java 21.
Các quy ước nghiệp vụ/kỹ thuật BẮT BUỘC nằm ở [`../DECISIONS.md`](../DECISIONS.md).

## Stack

| Thành phần | Lựa chọn |
|---|---|
| Runtime | Java 21 (Temurin), Spring Boot 4.1.x, virtual threads |
| Build | Maven |
| DB | **MySQL 8.x (chuẩn: 8.4 LTS)** + Flyway (module `flyway-mysql`; schema trong `src/main/resources/db/migration`) |
| Cache/Session | Redis 7 — OPTIONAL ở dev (fallback in-memory), bắt buộc khi chạy nhiều instance |
| Auth | JWT (oauth2-resource-server / Nimbus, HS256), BCrypt strength 12 |
| Realtime | WebSocket + STOMP (`/ws`) |
| API docs | springdoc-openapi — Swagger UI tại `/swagger-ui.html` |
| Test | JUnit 5, AssertJ, Mockito, ArchUnit; H2 MODE=MySQL (mặc định), Testcontainers MySQL 8.4 + Redis (profile riêng) |

## Cấu trúc module

```
com.rwg
├── common/    # ApiException, ErrorCode, GlobalExceptionHandler, PageResponse, Money
├── config/    # SecurityConfig, WebSocketConfig, RedisConfig, OpenApiConfig
└── identity/  # Module Auth & User (api | service | domain | repository | dto)
```

Quy ước: package `com.rwg.<module>.{api|service|domain|repository|dto}`; Controller hậu tố
`Controller`; DTO hậu tố `Request`/`Response`; entity KHÔNG lộ ra API; layering
`api → service → repository` (được enforce bằng ArchUnit).

## Chạy DEV local (KHÔNG cần Docker)

Hướng dẫn đầy đủ: [`docs/DEV-SETUP.md`](docs/DEV-SETUP.md). Tóm tắt:

1. Cài **MySQL 8.4** (MySQL Installer: <https://dev.mysql.com/downloads/installer/>) — service chạy cổng 3306.
2. Tạo database: `CREATE DATABASE rwg_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`
3. Kiểm tra môi trường: `pwsh scripts/dev-check.ps1`
4. Chạy app:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
# Health:  http://localhost:8080/actuator/health
# Swagger: http://localhost:8080/swagger-ui.html
```

> ⚠️ **Mật khẩu DB trong profile dev:** `application-dev.yml` dùng `password: ${DB_PASSWORD:1001}` —
> `1001` là **default CHỈ dành cho DEV LOCAL** trên máy cá nhân để chạy được ngay.
> **Mọi môi trường khác** (staging/production/Docker) **BẮT BUỘC** override qua biến môi trường
> `DB_PASSWORD`; **KHÔNG** dùng mật khẩu này cho môi trường thật (và nó không có trong `docker-compose.yml`).
>
> **JWT secret:** cấu hình base đặt `jwt-secret: ${JWT_SECRET}` KHÔNG có default — thiếu `JWT_SECRET`
> app fail-fast khi khởi động. Chỉ profile dev có default dev rõ ràng; mọi môi trường khác bắt buộc
> set biến môi trường (xem `.env.app.example`, tối thiểu 32 ký tự).

Redis là optional ở dev (mặc định `RWG_REDIS_ENABLED=false` → dùng store in-memory).

## Chạy bằng Docker (chỉ cho deploy)

```powershell
cd rwg-backend
Copy-Item .env.example .env          # biến hạ tầng: MYSQL_ROOT_PASSWORD / DB_PASSWORD / REDIS_PASSWORD
Copy-Item .env.app.example .env.app  # biến CHỈ của app: DB_USER / DB_PASSWORD / JWT_SECRET / REDIS_PASSWORD
# đổi secret ở CẢ HAI file rồi:
docker compose up -d --build
curl http://localhost:8080/actuator/health     # {"status":"UP"}
```

Hardening: app chỉ nhận `.env.app` (không bao giờ thấy `MYSQL_ROOT_PASSWORD`); app kết nối DB bằng
user riêng `rwg_app` tạo qua `docker/mysql-init` (không chạy bằng root); Redis không publish port
ra host và bắt buộc `requirepass`.

## Kiểm thử

```powershell
mvn verify                 # KHÔNG cần Docker, KHÔNG cần MySQL cài sẵn
mvn verify -Ptestcontainers   # cần Docker: chạy IT trên MySQL 8.4 + Redis thật
```

- Mặc định `mvn verify`: test chạy trên **H2 in-memory MODE=MySQL** với CHÍNH bộ migration
  MySQL thật (đã kiểm chứng tương thích), surefire loại trừ `*IT`.
- Profile `testcontainers`: failsafe chạy `*IT` với MySQL 8.4 + Redis thật qua Testcontainers
  (register → login → JWT → refresh rotation qua Redis).

## API Auth chính (Bước 1)

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/v1/auth/register` | Đăng ký (username + email + password) |
| POST | `/api/v1/auth/login` | Đăng nhập → JWT access 15 phút + refresh token |
| POST | `/api/v1/auth/refresh` | Rotation refresh token (token cũ bị thu hồi) |
| POST | `/api/v1/auth/logout` | Thu hồi refresh token |
| GET | `/api/v1/users/me` | Thông tin user hiện tại (cần JWT) |
| POST | `/api/v1/users/me/withdrawal-password` | Đặt withdrawal password (yêu cầu password đăng nhập) |
| GET | `/api/v1/admin/health` | Health khu admin (yêu cầu ROLE_ADMIN) |
| GET | `/api/v1/admin/users` | Danh sách user phân trang read-only (yêu cầu ROLE_ADMIN) |

Rate-limit đăng nhập (Bucket4j, Redis hoặc in-memory) với HAI bucket song song:
- Bucket **IP + username**: quá **5** lần sai → server ENFORCE captcha (thiếu `captchaToken` hợp lệ →
  429 `CAPTCHA_REQUIRED`, kể cả đúng mật khẩu); đủ **10** lần sai → khóa.
- Bucket **CHỈ username** (toàn cục, 20 lần sai/15 phút): chống attacker đổi IP.
- Khóa ghi lock marker riêng TTL đúng **15 phút** (HTTP 423, không dựa refill bucket);
  đăng nhập thành công reset cả bucket lẫn lock marker.
- Refresh token rotation theo family: dùng lại token ĐÃ rotate → thu hồi toàn bộ family,
  buộc đăng nhập lại (chống token bị đánh cắp).

## Ghi chú kỹ thuật

- Tiền tệ: `BigDecimal` + `DECIMAL(20,8)`, `RoundingMode.HALF_UP`; cấm float/double (xem DECISIONS.md).
- Schema MySQL: InnoDB, utf8mb4, `CHECK (balance >= 0)`, `BIGINT AUTO_INCREMENT`, `JSON` cho audit details;
  UUID `CHAR(36)` sinh phía Java (Hibernate `GenerationType.UUID`).
- Schema ví (`wallets`, `wallet_transactions` — double-entry ledger) đã có sẵn trong
  `V1__init_schema.sql` để chuẩn bị cho Bước 2; chưa có code logic ví ở bước này.
- `audit_log` append-only: chỉ INSERT qua `AuditTrailService` (transaction `REQUIRES_NEW` để
  sự kiện LOGIN_FAILED/LOGIN_LOCKED vẫn được ghi khi luồng đăng nhập rollback), không UPDATE/DELETE.
- Cột `JSON` được Hibernate bind dạng chuỗi nhờ `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` —
  tương thích cả MySQL và H2 MODE=MySQL.
