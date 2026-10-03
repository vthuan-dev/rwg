-- Them cot hidden cho phep admin an/hien dong giao dich vi khoi mat nguoi choi hoac toggle an tren giao dien
ALTER TABLE wallet_transactions ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_wallet_tx_hidden ON wallet_transactions (wallet_id, hidden, created_at);
