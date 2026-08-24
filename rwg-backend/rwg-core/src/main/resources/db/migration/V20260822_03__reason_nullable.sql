-- Cho phép `user_game_odds.reason` để trống.
--
-- Trước đây lý do là bắt buộc, nhưng người vận hành thường chỉ nhích tỷ lệ vài phần trăm
-- và phải gõ một câu vô nghĩa cho qua ràng buộc — thứ đó làm nhật ký khó đọc hơn là để
-- trống. Việc truy vết vẫn đủ: audit `ADMIN_USER_ODDS_CHANGED` ghi người thực hiện, thời
-- điểm, IP, và tỷ lệ trước/sau.
--
-- MODIFY chỉ nới lỏng ràng buộc nên không cần chuyển đổi dữ liệu: mọi dòng đang có đều
-- mang lý do hợp lệ và giữ nguyên giá trị.
ALTER TABLE user_game_odds
    MODIFY COLUMN reason VARCHAR(255) NULL;
