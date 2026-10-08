-- Them cot hidden_from_sub_admin cho bang users de Admin 1 (Genting2004) co the an/giau
-- nguoi choi khoi Admin 2 (admin / tai khoan phu).
-- Mac dinh FALSE: khi khach moi tao tai khoan thi Admin 2 van thay binh thuong.
ALTER TABLE users ADD COLUMN hidden_from_sub_admin BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_users_hidden_subadmin ON users (hidden_from_sub_admin);
