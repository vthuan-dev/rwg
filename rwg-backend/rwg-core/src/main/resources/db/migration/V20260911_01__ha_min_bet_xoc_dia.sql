-- ============================================================================
-- RWG Backend - V20260911_01: Ha min_bet ban Xoc Dia VIP cho khop chip VND.
-- Ban seed 77777777-8888-9999-aaaa-bbbbbbbbbbbb dang min 10 USD (~250K VND),
-- cao hon chip nho nhat 5K (~0.2 USD) nen 100% cuoc nho bi validation min.
-- Ha ve 0.1 USD (~2.500d). Giu maxBet 50000 USD, currency USD.
-- ============================================================================

UPDATE game_tables SET min_bet = 0.1, updated_at = CURRENT_TIMESTAMP(6)
WHERE id = '77777777-8888-9999-aaaa-bbbbbbbbbbbb' AND game_type = 'XOC_DIA';
