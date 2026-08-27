-- ============================================================================
-- RWG Backend - V20260827_01: cau hinh sua duoc tu khu quan tri (app_settings).
--
-- VI SAO CAN BANG NAY: doan chu chao khach o khung chat dang nam trong file
-- dich cua frontend. Doi mot dau phay trong do la mot lan sua ma, build lai va
-- trien khai lai toan bo frontend — trong khi noi dung do la viec cua nguoi
-- van hanh, khong phai cua lap trinh vien.
--
-- BANG KHOA-GIA TRI, KHONG PHAI MOT COT RIENG CHO TUNG THU:
-- Moi doan chu cau hinh duoc trong tuong lai (dieu khoan, thong bao bao tri,
-- loi chao) se la MOT DONG moi trong bang nay, khong phai mot migration moi.
-- Doi lai, khong co rang buoc kieu du lieu o tang DB — chap nhan duoc vi moi
-- gia tri deu la chu tu do do nguoi that go vao va duoc kiem o tang service.
--
-- KHONG CO cot ngon ngu: yeu cau la nguoi van hanh chi sua tieng Viet. Them
-- cot locale ngay bay gio la du doan mot nhu cau chua ton tai, va no bat moi
-- lan doc phai quyet dinh "lay ban nao neu thieu ban dich".
--
-- MOI THAY DOI MOT CAU LENH RIENG — xem ly do o V20260824_01.
-- ============================================================================

CREATE TABLE app_settings (
    -- Khoa dinh danh, vi du 'chat.promo.text'. Dung dau cham phan cap de doc
    -- ra duoc pham vi cua tung khoa ngay tren ten no.
    setting_key VARCHAR(64) NOT NULL,

    -- TEXT chu khong VARCHAR: doan chao hien tai da khoang 500 ky tu va co
    -- nhieu dong. Gioi han cung o tang DB se lam nguoi van hanh mat cong go
    -- vi mot loi khong ai giai thich duoc cho ho.
    setting_value TEXT NOT NULL,

    updated_at DATETIME(6) NOT NULL,

    -- Ten dang nhap cua nguoi sua gan nhat, CHUP LAI luc sua.
    -- Luu ten chu khong luu id: day la thu doc duoc ngay khi tra, va nguoi sua
    -- co the da roi viec roi bi doi ten sau do.
    updated_by_username VARCHAR(50) NULL,

    PRIMARY KEY (setting_key)
);

-- Seed doan chao hien tai, sao y nguyen van tu vi.json.
--
-- SEED NGAY TRONG MIGRATION, khong de bang rong roi cho nguoi van hanh tu go:
-- de rong thi ngay sau khi trien khai, moi khach mo chat se thay mot bong bong
-- trong hoac khong co bong bong nao — mot buoc lui so voi truoc.
INSERT INTO app_settings (setting_key, setting_value, updated_at, updated_by_username)
VALUES (
    'chat.promo.text',
    'KÍNH GỬI QUÝ KHÁCH HÀNG !
Kể từ ngày 01/01/2026 . Quý có thể đăng ký nhận phần thưởng sau khi nạp đủ mức tích lũy tối thiểu . Hoàn thành các mức tích lũy tiếp theo và nhận phần thưởng tương ứng
- Mức tích lũy đạt 10.000 USD tổng số tiền bạn có thể nhận bao gồm 388 USD + 888 USD sẽ được thêm vào tài khoản !
LƯU Ý : Phần thưởng này chỉ nhận được một lần duy nhất kể từ khi đăng kí trong thời gian diễn ra sự kiện !',
    CURRENT_TIMESTAMP(6),
    NULL
);
