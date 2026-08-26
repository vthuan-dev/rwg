-- ============================================================================
-- RWG Backend - V20260826_01: vi tri dia ly cua khach trong chat ho tro.
--
-- Nhan su tra loi ho tro can biet khach dang o dau: mot nguoi hoi ve
-- "chuyen khoan Vietcombank" thi khac han mot nguoi hoi ve "ATM Campuchia",
-- va biet truoc tinh/nuoc giup tra loi dung ngay tu cau dau.
--
-- SUY TU IP, KHONG XIN QUYEN VI TRI CUA TRINH DUYET. Trinh duyet bat buoc
-- hoi nguoi dung truoc khi cho doc toa do, va phan lon khach bam Tu choi ->
-- tinh nang se rong gan het thoi gian. IP thi luon co san trong moi request.
--
-- LUU KET QUA VAO DB, KHONG TRA LAI MOI LAN MO LUONG:
--   1. Dich vu tra IP mien phi co han muc theo phut. Mot hop thu 20 dong ma
--      tra lai moi lan tai se dot het han muc trong vai phut.
--   2. Khach doi mang (4G -> wifi) thi IP doi, nhung vi tri cu VAN la du lieu
--      dung tai thoi diem do — nhan su can biet luc khach nhan tin ho o dau.
--   3. Dich vu ngoai co the sap. Da luu roi thi man hinh van hien duoc.
--
-- Cot geo_resolved_at phan biet BA trang thai, dieu ma de NULL het khong lam
-- duoc: chua tra bao gio (NULL), da tra va biet vi tri (co gia tri + co
-- country_code), da tra nhung khong xac dinh duoc (co gia tri + country_code
-- NULL). Thieu cot nay thi moi lan mo luong lai goi API cho nhung IP da biet
-- chac la khong tra ra gi (mang noi bo, IP an danh).
--
-- MOI THAY DOI MOT CAU LENH RIENG — xem ly do o V20260824_01.
-- ============================================================================

-- IP gan nhat cua khach, chup luc ho mo khung chat hoac gui tin.
-- Dai 45 ky tu du cho IPv6 dang day du co vung scope.
ALTER TABLE chat_conversations
    ADD COLUMN last_ip VARCHAR(45) NULL;

-- Ma quoc gia ISO 3166-1 alpha-2 (VN, KH, TH...). Dung MA chu khong dung ten:
-- giao dien can ma de ve co, va ten nuoc thi tuy ngon ngu dang xem.
ALTER TABLE chat_conversations
    ADD COLUMN geo_country_code CHAR(2) NULL;

-- Ten quoc gia do dich vu tra ve. Luu kem de con gi hien khi ma khong nam
-- trong bang ten cua giao dien.
ALTER TABLE chat_conversations
    ADD COLUMN geo_country_name VARCHAR(64) NULL;

-- Tinh / thanh pho truc thuoc / vung (Ho Chi Minh, Phnom Penh, Bangkok...).
ALTER TABLE chat_conversations
    ADD COLUMN geo_region VARCHAR(96) NULL;

-- Thanh pho. Tach khoi region vi nhieu nuoc co ca hai cap va nhan su can
-- muc chi tiet nhat co the.
ALTER TABLE chat_conversations
    ADD COLUMN geo_city VARCHAR(96) NULL;

-- Nha mang / ISP. Huu ich de nhan ra khach dung VPN hoac mang doanh nghiep.
ALTER TABLE chat_conversations
    ADD COLUMN geo_isp VARCHAR(128) NULL;

-- Thoi diem tra IP thanh cong lan cuoi. NULL = chua tra bao gio.
ALTER TABLE chat_conversations
    ADD COLUMN geo_resolved_at DATETIME(6) NULL;

-- IP da tra roi, luu rieng khoi last_ip. Khi last_ip khac cot nay nghia la
-- khach doi mang va can tra lai; bang nhau thi dung ket qua da luu.
ALTER TABLE chat_conversations
    ADD COLUMN geo_resolved_ip VARCHAR(45) NULL;
