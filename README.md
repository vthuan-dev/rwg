# RWG — Nền tảng Casino Online

Monorepo dự án **RWG**: backend Spring Boot (Java 21, modular monolith) cho nền tảng casino,
hiện đã hoàn thành Bước 1 (Auth/User) và chuẩn bị Bước 2 (Wallet + Game logic).

## Tài liệu dự án

| Tài liệu | Nội dung |
|---|---|
| [TOM-TAT-DU-AN.md](TOM-TAT-DU-AN.md) | Tóm tắt nhanh dự án, trạng thái hiện tại |
| [PHAN-TICH-DU-AN.md](PHAN-TICH-DU-AN.md) | Phân tích tổng thể dự án |
| [KE-HOACH-CHUC-NANG-CASINO.md](KE-HOACH-CHUC-NANG-CASINO.md) | Kế hoạch phát triển các chức năng casino theo từng bước |
| [LOGIC-GAME-VA-HOA-HONG.md](LOGIC-GAME-VA-HOA-HONG.md) | Đặc tả logic game, RTP và hệ thống hoa hồng (commission) |
| [DANH-GIA-DAY-DU.md](DANH-GIA-DAY-DU.md) | Đánh giá đầy đủ hiện trạng codebase |
| [DECISIONS.md](DECISIONS.md) | **Các quyết định/quy ước kỹ thuật & nghiệp vụ BẮT BUỘC** — đọc trước khi code |

## Bắt đầu nhanh (backend)

1. Đọc [rwg-backend/README.md](rwg-backend/README.md) — stack, cấu trúc module, API hiện có.
2. Làm theo [rwg-backend/docs/DEV-SETUP.md](rwg-backend/docs/DEV-SETUP.md) để cài môi trường dev.
3. Kiểm tra môi trường:

```powershell
cd rwg-backend
pwsh scripts/dev-check.ps1
```

## Quy trình dev hiện tại

- **KHÔNG dùng Docker cho dev hàng ngày.** Chạy trực tiếp bằng Maven với profile `dev`
  trên MySQL local:

  ```powershell
  mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
  ```

- Test mặc định (`mvn verify`) chạy trên H2 MODE=MySQL — không cần Docker, không cần MySQL cài sẵn.
- **Docker (`docker-compose.yml`) chỉ dùng cho triển khai (deploy) sau này**, không nằm trong luồng dev.
