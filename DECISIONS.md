# DECISIONS.md — QUY ƯỚC BẮT BUỘC DỰ ÁN RWG

> Tài liệu này KHÓA các quyết định nghiệp vụ và kỹ thuật. Mọi code trong `rwg-backend`
> (và frontend sau này) PHẢI tuân theo. Muốn thay đổi phải cập nhật file này trước,
> có ghi rõ ngày và lý do.

---

## M1 — Thời điểm trừ tiền cược

- Tiền cược bị **trừ khỏi ví NGAY KHI ĐẶT CƯỢC**: hệ thống ghi transaction loại `BET`
  ở trạng thái `LOCKED` (tiền đã rời số dư khả dụng, giữ trong trạng thái khóa).
- **KHÔNG** chờ đến "No more bets" hay khi có kết quả mới trừ tiền.
- Khi settle: thắng → credit tiền thắng (xem M2); thua → BET chuyển `SETTLED` (mất stake);
  hủy/void → hoàn stake về ví.

## M2 — Quy ước stake (trả thưởng)

- Cược thắng = **tiền lời theo odds + HOÀN NGUYÊN STAKE** (stake-inclusive payout).
- Ví dụ: cược $10 vào Straight Up roulette (35:1) thắng → nhận tổng cộng
  `$10 + $10 × 35 = $360` (không phải $350).
- Cược thua: mất toàn bộ stake. Push/Tie hoàn stake, không lời.

## M3 — Baccarat: Tableau luật bài thứ ba

- Trước khi code bất kỳ logic Baccarat nào, **PHẢI** có bảng Tableau đầy đủ của luật
  bài thứ ba cho cả Player và Banker tại `docs/baccarat-tableau.md`.
- Ghi chú: file `docs/baccarat-tableau.md` sẽ được tạo ở bước triển khai module game
  (không thuộc phạm vi Bước 0/Bước 1). Cấm code luật rút bài thứ ba của Banker
  dựa trên trí nhớ hoặc rút gọn.

## M4 — Tie Baccarat

- Chốt trả thưởng cửa Tie: **8:1** (theo quy ước stake M2: cược $10 thắng Tie → nhận $90).

## M5 — Loyalty points

- Điểm loyalty tính theo **TIỀN CƯỢC (wagered amount)**, KHÔNG tính theo tiền thắng.
- Bảng tỷ lệ khởi điểm: Slots **10 điểm / $10 cược**; Table games **5 điểm / $10 cược**.
- Làm tròn điểm theo HALF_UP.

## M6 — Hoa hồng Banker (Baccarat)

- Hoa hồng Banker **5%**, trừ **trên tiền lời của ván thắng** (không trừ trên stake).
- Trả thưởng tuân theo quy ước stake-inclusive M2: cược Banker $10 thắng → nhận
  tổng cộng `$10 + ($10 × 1) − 5% × $10 = $19.50` (stake hoàn nguyên + lời $1 trừ hoa hồng $0.05).
- Ghi sổ cái: phần hoa hồng là một dòng debit riêng (ref_type phù hợp) để truy vết.

## M7 — Pair side-bets Baccarat

- Chặng này CHỈ làm: **Player Pair** và **Banker Pair**, trả **11:1** (stake-inclusive
  theo M2: cược $10 trúng → nhận tổng $120).
- Pair = 2 lá ĐẦU của cửa đó cùng hạng (rank), không phân biệt chất.
- **KHÔNG** làm Perfect Pair / Either Pair / Big / Small trong chặng này (xem danh sách HOÃN).

## M8 — Slots: free spins + paytable + RTP

- Free spins: **3/4/5 scatter = 10/15/20 free spins**, có **retrigger** (scatter xuất
  hiện trong free spins cộng thêm theo cùng bảng).
- Paytable cụ thể + **RTP mục tiêu 96% (±0.5)** định nghĩa trong file cấu hình
  **JSON seed** (không hard-code trong code).
- RTP BẮT BUỘC được khóa bằng **test mô phỏng 1 triệu spin** (sai số ngoài ±0.5 → test đỏ).

## M9 — Welcome bonus (người được mời)

- Welcome bonus cho người được mời: **cố định $10** (KHÔNG theo % first deposit),
  **wagering requirement 3x** (phải cược đủ $30 trước khi rút phần bonus).
- Giá trị này đã được user CHỐT qua leader (2026-08); nếu user đổi sẽ cập nhật mục này.

## M10 — Loyalty & cashback tuần

- Tích điểm loyalty theo M5: Slots **10 điểm / $10 cược**, Table games **5 điểm / $10 cược**.
- **Cashback tuần = % trên NET LOSS** (thua ròng tuần) theo tier **5–15%**, CÓ CAP
  tiền tối đa mỗi tuần. KHÔNG trả cashback theo wagered amount.
- Định nghĩa chi tiết tier/cap nằm trong cấu hình của module loyalty (chặng 2).

## Danh sách HOÃN chính thức (chưa làm chặng này)

- CPA/Hybrid commission.
- QR thanh toán server-side.
- Progressive jackpot, autospin.
- Baccarat roadmap / squeeze UI.
- Tích hợp provider thanh toán thật & captcha thật (dev đang dùng stub).
- Multi-wallet / multi-currency.
- Bảng tích lũy thưởng ~10% wagered — nếu làm sau: **cap ≤1% wagered** + wagering requirement.
- Sports betting.
- VR/NFT.
- Risk scoring engine (chuyển sang chặng admin).

## Quy tắc tiền tệ (bắt buộc toàn bộ codebase)

- Mọi giá trị tiền tệ dùng `java.math.BigDecimal`, lưu DB kiểu `DECIMAL(20,8)`.
- Làm tròn: `RoundingMode.HALF_UP`; scale chuẩn 8 chữ số thập phân khi lưu.
- **CẤM TUYỆT ĐỐI** dùng `float`/`double` (và `Float`/`Double`) cho bất kỳ giá trị
  tiền tệ nào. ArchUnit sẽ enforce trong các package tiền tệ (`..money..`, `..wallet..`,
  `..game..`, `..bet..`).
- Không dùng constructor `new BigDecimal(double)`; chỉ dùng `BigDecimal.valueOf(...)`
  hoặc `new BigDecimal("...")` từ chuỗi.
- So sánh tiền dùng `compareTo`, không dùng `equals` (khác scale).

## Database (bắt buộc)

- **MySQL 8.x** (chuẩn: **8.4 LTS**) — cập nhật 2026-08: chuyển từ PostgreSQL sang MySQL
  theo yêu cầu vận hành.
- Schema chuẩn: engine **InnoDB**, charset **utf8mb4** (`utf8mb4_0900_ai_ci`),
  tiền tệ `DECIMAL(20,8)`, dữ liệu cấu hình/audit dạng `JSON`, khóa chính
  `BIGINT AUTO_INCREMENT` (bảng log) hoặc `CHAR(36)` UUID sinh phía ứng dụng,
  `CHECK` constraint bắt buộc (ví dụ `balance >= 0`; MySQL enforce từ 8.0.16).
- Schema quản lý 100% bằng Flyway (module `flyway-mysql`); Hibernate `ddl-auto: none`.
- Test mặc định KHÔNG cần Docker: H2 in-memory `MODE=MySQL` chạy chính bộ migration
  MySQL. Integration test trên MySQL 8.4 thật (Testcontainers) nằm trong Maven profile riêng.

## Quy ước kiến trúc & code

- Modular monolith, 1 Maven project. Package: `com.rwg.<module>.{api|service|domain|repository|dto}`.
- Controller hậu tố `Controller`; DTO hậu tố `Request`/`Response`.
- Entity KHÔNG được lộ ra API (không trả thẳng entity từ Controller).
- Layering: `api → service → repository`. ArchUnit enforce:
  - package `..api..` cấm phụ thuộc thẳng `..repository..`;
  - cấm kiểu tiền tệ float/double trong package tiền tệ (xem trên).
- Lỗi nghiệp vụ ném `ApiException(ErrorCode...)`; response lỗi chuẩn
  `{code, message, details, traceId}`.
- Mọi sự kiện nhạy cảm (login, register, đổi mật khẩu, đặt/rút withdrawal password,
  giao dịch ví) ghi `audit_log` append-only qua `AuditTrailService`.

## Quyết định bổ sung từ code review (2026-08)

### (a) Đặt tên migration Flyway theo ngày

- Từ **V2 trở đi**, migration đặt tên theo ngày: `VYYYYMMDD_NN__mo_ta.sql`
  (vd `V20260820_01__them_bang_bets.sql`). V1 giữ nguyên `V1__init_schema.sql`.
- Không chèn migration vào giữa các version đã chạy ở môi trường khác — luôn nối tiếp.

### (b) Bảng volume lớn luôn PK composite (id, created_at)

- Các bảng volume lớn (ledger `wallet_transactions`, `audit_log`; sau này `bets`,
  kết quả ván chơi...) LUÔN dùng `PRIMARY KEY (id, created_at)` để partition-ready
  theo thời gian (MySQL partition theo RANGE trên cột nằm trong PK).
- Mọi UNIQUE constraint của các bảng này PHẢI kèm `created_at` (vd
  `UNIQUE (idempotency_key, created_at)`) để hợp lệ khi partition.
- Đã áp dụng ngay trong V1 khi bảng còn rỗng — không chờ dữ liệu thật.

### (c) Divergence H2 MODE=MySQL vs MySQL thật

- H2 `MODE=MySQL` đủ để chạy migration + test luồng mặc định KHÔNG cần Docker,
  nhưng KHÁC MySQL thật ở: collation `utf8mb4_0900_ai_ci` (case/accent-insensitive
  của MySQL không tái hiện đúng trên H2), kiểu `JSON`, ngữ nghĩa `DATETIME`.
- Vì vậy test về uniqueness/collation/JSON PHẢI nằm trong `*IT` chạy Testcontainers
  trên MySQL 8.4 thật. CI sau này cần job riêng `-Ptestcontainers`; job mặc định
  chỉ chạy bộ H2.

### (d) audit_log append-only đang enforce bằng convention

- `audit_log` append-only hiện được enforce bằng CONVENTION: service chỉ INSERT
  (AuditTrailService), KHÔNG có trigger mức DB — H2 không hỗ trợ trigger MySQL nên
  không viết trigger trong migration dùng chung. Chấp nhận ở MVP; khi bỏ H2 hoặc
  có migration riêng MySQL, thêm trigger chặn UPDATE/DELETE.

### (e) Bảng refresh_tokens là placeholder

- Bảng `refresh_tokens` trong V1 là PLACEHOLDER cho audit/thu hồi tập trung ở bước
  sau. Hiện trạng thái refresh (rotation/reuse) nằm ở store Redis/in-memory
  (`RedisRefreshTokenStore`/`InMemoryRefreshTokenStore`). FK giữ `ON DELETE CASCADE`
  (session vô nghĩa khi user bị xóa); wallets/wallet_transactions dùng
  `ON DELETE RESTRICT` (sổ cái không bao giờ xóa theo user — soft-delete status=CLOSED).

### (f) Thêm dependency mới phải kiểm tra tương thích Jackson 3

- Dự án dùng Jackson 3 (`tools.jackson`, qua `spring-boot-starter-jackson` của
  Boot 4). Mọi dependency mới có đụng chạm serialization PHẢI được kiểm tra tương
  thích Jackson 3 trước khi thêm (nhiều thư viện vẫn bind Jackson 2 `com.fasterxml`).

## Ghi chú về tài liệu nghiệp vụ

- `LOGIC-GAME-VA-HOA-HONG.md` và `KE-HOACH-CHUC-NANG-CASINO.md` chỉ dùng làm
  **đặc tả nghiệp vụ tham khảo**.
- Pseudocode trong `LOGIC-GAME-VA-HOA-HONG.md` **CÓ LỖI — KHÔNG ĐƯỢC COPY VÀO CODE**.
  Các lỗi đã biết: `executeFre eSpin` (tên hàm gãy), `getUsernameM asked` (lỗi đánh máy),
  biểu thức tính ngưỡng payout **sai độ ưu tiên toán tử**. Chỉ lấy ý nghĩa nghiệp vụ,
  viết lại code từ đặc tả đã chốt trong file này.
