package com.rwg.game.service;

import com.rwg.wallet.domain.Wallet;
import com.rwg.wallet.repository.WalletRepository;
import com.rwg.wallet.repository.WalletTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Job reconciliation (Phase c, docs/round-lifecycle.md mục 6): mỗi 5 phút so
 * tổng ledger append-only (credit − debit theo wallet) với balance hiện tại.
 * Chênh lệch -> CHỈ log WARN (tự sửa KHÔNG thuộc phạm vi chặng này).
 */
@Component
@EnableScheduling
public class LedgerReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(LedgerReconciliationJob.class);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    public LedgerReconciliationJob(WalletRepository walletRepository,
                                   WalletTransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Scheduled(fixedDelayString = "${rwg.game.reconciliation-interval:PT5M}")
    @Transactional(readOnly = true)
    public void checkLedgerAgainstBalances() {
        Map<UUID, BigDecimal> netByWallet = new HashMap<>();
        for (Object[] row : transactionRepository.sumNetByWallet()) {
            netByWallet.put((UUID) row[0], (BigDecimal) row[1]);
        }

        int mismatches = 0;
        for (Wallet wallet : walletRepository.findAll()) {
            BigDecimal ledgerNet = netByWallet.getOrDefault(wallet.getId(), BigDecimal.ZERO);
            // So bằng compareTo (khác scale); ví mới chưa có dòng ledger -> net 0.
            if (wallet.getBalance().compareTo(ledgerNet) != 0) {
                mismatches++;
                log.warn("LEDGER_MISMATCH walletId={} userId={} balance={} ledgerNet={}",
                        wallet.getId(), wallet.getUserId(),
                        wallet.getBalance().toPlainString(), ledgerNet.toPlainString());
            }
        }
        if (mismatches == 0) {
            log.debug("ledger reconciliation OK wallets={}", walletRepository.count());
        } else {
            log.warn("ledger reconciliation finished with {} mismatched wallet(s)", mismatches);
        }
    }
}
