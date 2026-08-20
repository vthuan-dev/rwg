# KẾ HOẠCH: FULL CHỨC NĂNG ADMIN + TÁCH 2 BACKEND (ADMIN / USER)

> Tài liệu kế hoạch (planning). Bám theo `DECISIONS.md` (quy ước BẮT BUỘC) và hiện trạng
> code `rwg-backend`. Chưa phải là spec khóa — sẽ chốt từng phần trước khi code.
> Ngày tạo: 2026-08-20.

---

## PHẦN A — FULL CHỨC NĂNG ADMIN (BackOffice)

### A.0. Hiện trạng (đang có)

| Endpoint | Module | Ghi chú |
|---|---|---|
| `GET /api/v1/admin/health` | identity | Health check khu admin |
| `GET /api/v1/admin/users` | identity | List user, **read-only**, phân trang (PageResponse) |
| `POST /api/v1/admin/withdrawals/{id}/approve` | payment | Duyệt lệnh rút |
| `POST /api/v1/admin/withdrawals/{id}/reject` | payment | Từ chối lệnh rút |

**Phân quyền:** `SecurityConfig` chặn tập trung `/api/v1/admin/** → hasRole("ADMIN")`.
**Vai trò hiện có:** `UserRole = { PLAYER, ADMIN }` (chỉ 2 mức).

**Khoảng trống lớn:**
- Chưa có API phong quyền ADMIN (đang phải sửa DB tay — xem test `AuthFlowTest`, `WithdrawalFlowTest`).
- Chưa có quản lý user (khóa/mở, KYC, xem chi tiết ví/giao dịch).
- Chưa có nghiệp vụ tài chính admin (điều chỉnh số dư có kiểm soát, xem sổ cái, duyệt nạp).
- Chưa có quản lý game (bàn, vòng, void/settle thủ công).
- Chưa có bonus/loyalty/cashback config, báo cáo, xem audit log, responsible gaming, AML.
- RBAC quá thô (chỉ ADMIN) — không tách được Finance / Support / Risk.

### A.1. Nền tảng cần làm trước (P0 — bắt buộc)

**A.1.1. RBAC chi tiết (thay 2-role bằng role + permission)**
- Đề xuất bậc vai trò:
  - `SUPER_ADMIN` — toàn quyền, quản lý admin khác + system config.
  - `ADMIN` — vận hành chung.
  - `FINANCE` — duyệt nạp/rút, điều chỉnh ví, báo cáo tài chính.
  - `SUPPORT` — xem user, hỗ trợ, reset, không đụng tiền.
  - `RISK` — AML/fraud flags, self-exclusion, khóa tài khoản.
- Cách làm tối thiểu (giữ đơn giản): mở rộng `UserRole` + bảng `admin_permissions`
  (hoặc enum permission) và chuyển từ `hasRole` sang `@PreAuthorize("hasAuthority('perm:...')")`
  theo từng endpoint. JWT claim mang danh sách authorities.
- **Audit BẮT BUỘC:** mọi hành động admin ghi `audit_log` qua `AuditTrailService`
  (actor = adminUserId, target, before/after) — theo quy ước DECISIONS.

**A.1.2. Chuẩn API admin**
- Prefix `/api/v1/admin/**`; mọi list dùng `PageResponse` (đã thống nhất).
- Lỗi nghiệp vụ ném `ApiException(ErrorCode…)` → `{code, message, details, traceId}`.
- Không lộ entity; DTO hậu tố `Request`/`Response`.
- Tiền tệ `BigDecimal` / `DECIMAL(20,8)`, `HALF_UP` (cấm float/double).

### A.2. Quản lý User (P1)

- `GET /admin/users` (đã có) + filter (email, username, status, role, kycLevel, ngày tạo).
- `GET /admin/users/{id}` — chi tiết user (profile, role, KYC level, số dư ví, cờ risk).
- `PATCH /admin/users/{id}/status` — `ACTIVE | LOCKED | SUSPENDED | CLOSED` (kèm lý do).
- `PATCH /admin/users/{id}/role` — phong/hạ quyền (chỉ SUPER_ADMIN) → **thay việc sửa DB tay**.
- `POST /admin/users/{id}/reset-password` / `reset-withdrawal-password` (gửi luồng reset).
- `POST /admin/users/{id}/force-logout` — thu hồi refresh token (dùng token store hiện có).
- **KYC:** `GET /admin/kyc/pending`, `GET /admin/kyc/{id}`, `POST /admin/kyc/{id}/approve|reject`
  (lý do), nâng KYC level (L1/L2/L3 theo KE-HOACH module 1).

### A.3. Nghiệp vụ tài chính (P1 — FINANCE)

- **Rút tiền:** đã có approve/reject → bổ sung `GET /admin/withdrawals?status=` (queue), chi tiết,
  gắn bank account + kiểm tra KYC/rollover trước khi duyệt (module 2 KE-HOACH).
- **Nạp tiền:** `GET /admin/deposits?status=` — soát giao dịch nạp (hiện là stub), xử lý
  callback provider thật (đang HOÃN — chỉ khung).
- **Điều chỉnh ví thủ công (manual adjustment):** `POST /admin/wallets/{userId}/adjust`
  (credit/debit có lý do bắt buộc) — ghi transaction + `audit_log`; double-entry giữ nguyên.
- **Sổ cái:** `GET /admin/wallets/{userId}/transactions` (đã có PageResponse pattern),
  `GET /admin/ledger` toàn hệ thống (filter theo loại/ngày).

### A.4. Quản lý Game (P2)

- **Bàn chơi:** `GET/POST/PUT /admin/game/tables`, `PATCH .../status`
  (`ACTIVE | MAINTENANCE | CLOSED`), sửa min/max bet, `name_i18n` (en/vi/zh/ja).
- **Vòng chơi:** `GET /admin/game/rounds?tableId=` (giám sát), `POST .../rounds/{id}/void`
  (hoàn stake theo M1), `POST .../rounds/{id}/settle` (settle thủ công khi sự cố — cực kỳ hạn chế + audit).
- **Giám sát cược:** `GET /admin/game/bets?filter` (theo user/table/round/status), phát hiện bất thường.

### A.5. Bonus / Loyalty / Cashback (P2)

- Cấu hình **Welcome bonus** ($10, wagering 3x — M9), reload/cashback tier (M10: 5–15% net loss, có cap).
- Loyalty rate (M5: Slots 10đ/$10, Table 5đ/$10) — chỉnh qua config, không hard-code.
- `GET/POST /admin/promotions`, `GET /admin/bonuses?userId=`, thu hồi bonus gian lận.
- **Lưu ý DECISIONS:** bảng "tích lũy thưởng ~10% wagered" nếu làm phải **cap ≤1% wagered + wagering requirement**.

### A.6. Báo cáo & Dashboard (P3)

- KPIs: GGR/NGR, tổng nạp/rút, active players (DAU/MAU), số dư hệ thống, hoa hồng Banker (M6).
- `GET /admin/reports/financial?from=&to=`, `/reports/gaming`, `/reports/players`.
- Export CSV (module 2.4 KE-HOACH). Trả dữ liệu số → có thể visualize.

### A.7. Compliance / Responsible Gaming / AML (P3 — RISK)

- Deposit/loss/session limits, self-exclusion (24h/7d/30d/permanent) — xem & override.
- AML: cờ giao dịch lớn, source-of-funds; risk scoring engine (DECISIONS: đã dời sang chặng admin).
- `GET /admin/risk/flags`, `POST /admin/users/{id}/self-exclude`, xem `audit_log`
  (`GET /admin/audit?actor=&target=&from=`).

### A.8. System config (P4 — SUPER_ADMIN)

- Quản lý admin accounts & permissions, feature flags, tham số hệ thống, xem health chi tiết.

### A.9. Thứ tự triển khai đề xuất

| Phase | Nội dung | Vai trò hưởng lợi |
|---|---|---|
| **P0** | RBAC chi tiết + audit + API phong quyền | Nền tảng |
| **P1** | User management + KYC + Finance (nạp/rút/điều chỉnh ví) | SUPPORT, FINANCE |
| **P2** | Game management + Bonus/Loyalty config | ADMIN |
| **P3** | Reports/Dashboard + Compliance/AML | RISK, quản lý |
| **P4** | System config + quản lý admin | SUPER_ADMIN |

---

## PHẦN B — TÁCH 2 BACKEND (ADMIN JAR + USER JAR)

### B.0. Vấn đề & mục tiêu

- **Hiện tại:** modular monolith, **1 Maven project → 1 jar** (`rwg-backend-0.1.0-SNAPSHOT.jar`)
  phục vụ cả API player lẫn admin.
- **Mục tiêu:** khi build ra **2 jar chạy độc lập**: `rwg-user-app` (player-facing) và
  `rwg-admin-app` (BackOffice), **dùng chung code lõi & chung DB**.
- **Lợi ích:** cô lập bảo mật (admin chỉ mở trong mạng nội bộ/VPN, port riêng), scale độc lập,
  giảm bề mặt tấn công cho player app, deploy tách biệt.

### B.1. Phương án đề xuất — Maven multi-module (monorepo) ✅

Giữ **1 repo**, chuyển thành parent POM + nhiều module:

```
rwg-backend/                (parent pom, <packaging>pom</packaging>)
├── rwg-core/               (jar thư viện — KHÔNG phải Spring Boot app)
│   └── com.rwg.*           domain, repository, service, common,
│                           config bảo mật dùng chung, dto, i18n, Flyway migrations
├── rwg-user-app/           (Spring Boot app → jar 1, port 8080)
│   └── com.rwg.user        main class + controller player:
│                           auth, users/me, wallet, games, bank-accounts, payments/callback
└── rwg-admin-app/          (Spring Boot app → jar 2, port 8081)
    └── com.rwg.admin        main class + controller admin: /api/v1/admin/**
```

- `rwg-core` là dependency của cả 2 app. Business logic/service/domain **không nhân đôi**.
- Mỗi app có `@SpringBootApplication` + `application.yml` riêng (port, security, OpenAPI, swagger).
- **Kết quả `mvn package`:** 2 jar bootable (user-app, admin-app) + core jar (thư viện nội bộ).

### B.2. Các quyết định kiến trúc cần chốt

1. **Chung DB:** cả 2 app trỏ cùng MySQL. **Chỉ 1 app chạy Flyway** (đề xuất: `rwg-core` sở hữu
   migration; user-app bật `flyway.enabled=true`, admin-app `false` để tránh migrate song song),
   HOẶC tách 1 job migrate riêng khi deploy.
2. **Security tách biệt:** user-app dùng JWT player; admin-app enforce RBAC admin (Phần A.1.1) +
   chỉ expose trong mạng nội bộ (ingress/VPN/allowlist IP).
3. **Cấu hình chung** (JWT secret, datasource, UUID→CHAR fix) đặt ở `rwg-core` (auto-config) hoặc
   config server; tránh copy-paste.
4. **ArchUnit:** giữ luật layering ở core; thêm luật "controller admin không nằm trong user-app" và ngược lại.
5. **Jackson 3** (DECISIONS f): giữ nguyên toàn bộ module.
6. **Realtime:** WebSocket/STOMP game nằm ở user-app.

### B.3. Các bước refactor (không đổi hành vi nghiệp vụ)

1. Tạo parent POM (`<packaging>pom</packaging>`), khai báo `<modules>`.
2. Tạo `rwg-core`: chuyển `domain/repository/service/common/config-chung/dto/i18n` + `db/migration` vào; đóng gói jar thường (tắt `spring-boot-maven-plugin` repackage).
3. Tạo `rwg-user-app`: main class + di chuyển controller player (`AuthController`, `UserController`, `WalletController`, `GameController`, `BankAccountController`, `PaymentController`) + `application.yml` (8080). depends on core.
4. Tạo `rwg-admin-app`: main class + di chuyển controller admin (`AdminController`, `AdminWithdrawalController` + các controller admin mới ở Phần A) + `application.yml` (8081). depends on core.
5. Chia test: unit/service test theo core; flow test (MockMvc) theo app tương ứng. Giữ H2 MODE=MySQL mặc định.
6. `docker-compose`/`Dockerfile`: 2 service (user, admin) từ 2 jar, chung MySQL.
7. Chạy `mvn verify` toàn reactor — bảo đảm xanh như hiện tại.

### B.4. Phương án thay thế (và vì sao không chọn)

- **1 jar, 2 main + profile lọc controller:** dễ rò rỉ endpoint admin sang player, khó cô lập bảo mật → không nên.
- **2 repo/microservice tách hẳn + chia DB:** overhead lớn (đồng bộ code lõi, phân tán transaction) — chưa cần ở giai đoạn này.
- **Kết luận:** **multi-module monorepo (B.1)** là điểm cân bằng tốt nhất: 2 deployable, code lõi dùng chung, 1 repo, ít rủi ro.

### B.5. Rủi ro cần lưu ý

- Flyway chạy 2 nơi cùng lúc → phải chốt "ai migrate" (B.2.1).
- Bean/config dùng chung phải nằm ở core kèm auto-config, tránh mỗi app cấu hình lệch nhau.
- Chia lại package/test có thể ồn ào ở 1 commit lớn — nên làm trên nhánh riêng, review kỹ, build xanh mới merge.

---

## TÓM TẮT HÀNH ĐỘNG TIẾP THEO

1. **Chốt RBAC (P0)** — quyết định bộ role/permission (A.1.1) trước khi build chức năng admin.
2. **Làm P1 admin** — User management + Finance (thay việc sửa DB tay để phong ADMIN).
3. **Tách backend** — dựng khung Maven multi-module (B.1/B.3) trên nhánh riêng.
```
