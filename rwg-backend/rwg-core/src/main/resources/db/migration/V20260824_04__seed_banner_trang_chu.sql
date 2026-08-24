-- ============================================================================
-- RWG Backend - V20260824_04: Seed 4 banner trang chủ vào DB.
-- ============================================================================
--
-- VÌ SAO CẦN MIGRATION NÀY: bốn slide của trang chủ trước đây được gán cứng
-- trong BannerCarousel.tsx, nên khu quản trị không thấy và không quản lý được —
-- danh sách banner luôn hiện "0" trong khi trang chủ vẫn chạy 4 slide.
--
-- ĐƯỜNG DẪN KHÔNG CÓ TIỀN TỐ /uploads/media/ — CÓ CHỦ Ý.
-- Bốn tệp này nằm trong `rwg-frontend/public/` và đã commit vào git. Cả
-- BannerCarousel lẫn trang quản trị chỉ nối USER_BASE_URL vào đường dẫn bắt đầu
-- bằng "/uploads"; đường dẫn khác giữ nguyên và Next.js phục vụ trực tiếp từ
-- `public/`. Nên seed đường dẫn tương đối là chạy ngay, không phải copy tệp sang
-- thư mục uploads và không nhân đôi dung lượng trên đĩa.
--
-- HỆ QUẢ CẦN BIẾT: `MediaStorageService.deleteByPublicUrl` chỉ xoá tệp nằm dưới
-- /uploads/media/, nên xoá bốn banner này từ khu quản trị sẽ KHÔNG xoá tệp trong
-- dự án. Đó là điều mong muốn: tệp đã commit vào git, một cú bấm không nên làm
-- mất nó và buộc phải `git checkout` để lấy lại. Xoá bản ghi thì banner mất khỏi
-- trang chủ, tệp vẫn còn để seed lại.
--
-- ID CỐ ĐỊNH thay vì gọi hàm sinh UUID:
-- (1) tên hàm khác nhau giữa MySQL (UUID()) và H2 (RANDOM_UUID()), mà H2 MODE=MySQL
--     là thứ chạy trong test;
-- (2) nhìn ID là biết bản ghi đến từ migration này chứ không phải do ai đó tải lên.
--
-- Dùng INSERT ... VALUES trần theo đúng quy ước của các migration seed sẵn có
-- (xem V20260820_08). KHÔNG dùng `FROM DUAL ... WHERE NOT EXISTS`: Flyway vốn chỉ
-- chạy mỗi migration một lần nên bảo vệ đó không thêm gì, mà `DUAL` lại là chỗ
-- MySQL và H2 hành xử khác nhau.

-- 1. Video 1 — 1248x704, H.264, 5.04 giây, `moov` nằm TRƯỚC `mdat` nên trình duyệt
--    phát được ngay khi tải xong phần đầu, không phải chờ hết tệp.
INSERT INTO banners (id, title, media_type, media_url, link_url, is_active, sort_order, created_at, updated_at)
VALUES ('b0000001-0000-4000-a000-000000000001',
        'Resorts World Genting - Video 1',
        'VIDEO',
        '/element/home-banner-video-wording.mp4',
        NULL, TRUE, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

-- 2. Video 2
INSERT INTO banners (id, title, media_type, media_url, link_url, is_active, sort_order, created_at, updated_at)
VALUES ('b0000002-0000-4000-a000-000000000002',
        'Resorts World Genting - Video 2',
        'VIDEO',
        '/element/home-banner-video-wording2.mp4',
        NULL, TRUE, 2, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

-- 3. Ảnh khuyến mãi — 1280x714 WebP, ~168 KB.
INSERT INTO banners (id, title, media_type, media_url, link_url, is_active, sort_order, created_at, updated_at)
VALUES ('b0000003-0000-4000-a000-000000000003',
        'Tich Luy Phan Thuong 2026',
        'IMAGE',
        '/images/banner_promo.webp',
        NULL, TRUE, 3, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

-- 4. Ảnh Gateway — 1280x714 WebP, ~165 KB.
INSERT INTO banners (id, title, media_type, media_url, link_url, is_active, sort_order, created_at, updated_at)
VALUES ('b0000004-0000-4000-a000-000000000004',
        'Your Gateway To Fortune',
        'IMAGE',
        '/images/banner_gateway.webp',
        NULL, TRUE, 4, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
