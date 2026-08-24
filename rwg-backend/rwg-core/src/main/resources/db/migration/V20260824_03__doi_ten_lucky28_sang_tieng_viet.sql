-- ============================================================================
-- RWG Backend - V20260824_03: đổi tên tiếng Việt của ba bàn họ Lucky 28.
--
-- Ba bàn này trước đây để nguyên tên tiếng Anh ở khóa "vi", nên người chơi đọc
-- tiếng Việt vẫn thấy "Lucky 28". Nay đổi thành "May mắn 28".
--
-- TẠI SAO LÀ MỘT TEP MIGRATION MỚI chứ không sửa V20260820_07/08: Flyway lưu
-- checksum của từng tệp đã chạy trong flyway_schema_history. Sửa nội dung một
-- migration ĐÃ CHẠY làm checksum lệch và Flyway TỪ CHỐI khởi động với lỗi
-- "Migration checksum mismatch".
--
-- GHI ĐÈ CẢ ĐỐI TƯỢNG JSON thay vì dùng JSON_SET: H2 (test chạy MODE=MySQL)
-- không có hàm JSON_SET. Các migration seed sẵn có cũng CAST cả đối tượng, nên
-- cách này chạy được trên cả MySQL và H2.
--
-- Lọc theo game_type chứ KHÔNG theo id: id trong seed là UUID viết tay cho máy dev,
-- môi trường khác có thể có bàn cùng loại với id khác.
--
-- TAIWAN_TIMES GIỮ NGUYÊN: đó là tên riêng, không phải từ mô tả như "lucky".
-- ============================================================================

UPDATE game_tables
SET name_i18n = CAST('{"en":"Lucky 28","vi":"May mắn 28","zh":"幸运28","ja":"ラッキー28"}' AS JSON),
    updated_at = CURRENT_TIMESTAMP(6)
WHERE game_type = 'LUCKY28';

UPDATE game_tables
SET name_i18n = CAST('{"en":"British Lucky 28","vi":"May mắn 28 Anh","zh":"英国幸运28","ja":"英国ラッキー28"}' AS JSON),
    updated_at = CURRENT_TIMESTAMP(6)
WHERE game_type = 'BRITISH_LUCKY28';

UPDATE game_tables
SET name_i18n = CAST('{"en":"Korean Lucky 28","vi":"May mắn 28 Hàn","zh":"韩国幸运28","ja":"韓国ラッキー28"}' AS JSON),
    updated_at = CURRENT_TIMESTAMP(6)
WHERE game_type = 'KL28';
