package com.rwg.wallet.service;

import com.rwg.wallet.domain.Wallet;
import com.rwg.wallet.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Tạo ví mới trong transaction RIÊNG (fix review M6).
 *
 * Lỗi bản cũ: tạo ví trong transaction của caller -> khi thua race
 * uq_wallets_user_id, DataIntegrityViolationException nổ tại flush (ngoài
 * try/catch), transaction bị đánh dấu rollback-only khiến fallback
 * findByUserId hỏng theo.
 *
 * Bản này: transaction RIÊNG qua {@link TransactionTemplate} (độc lập hoàn
 * toàn với transaction của caller) + saveAndFlush để DataIntegrityViolationException
 * nổ ĐÚNG CHỖ, bắt được NGOÀI transaction. KHÔNG dùng @Transactional vì
 * ngoại lệ bị bắt BÊN TRONG phương thức @Transactional vẫn khiến transaction
 * bị đánh dấu rollback-only -> commit ném UnexpectedRollbackException lọt ra
 * ngoài (lỗi phát hiện khi test 2 lệnh nạp song song).
 */
@Service
public class WalletCreator {

    private final WalletRepository walletRepository;
    private final TransactionTemplate newTx;

    public WalletCreator(WalletRepository walletRepository,
                         PlatformTransactionManager transactionManager) {
        this.walletRepository = walletRepository;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Tạo ví mới. Trả ví vừa tạo; trả {@code null} nếu thua race
     * (ví đã được luồng khác tạo — caller đọc lại ví hiện có).
     */
    public Wallet createNew(UUID userId) {
        try {
            return newTx.execute(status -> walletRepository.saveAndFlush(new Wallet(userId)));
        } catch (DataIntegrityViolationException e) {
            // Thua race uq_wallets_user_id: transaction riêng đã rollback SẠCH,
            // transaction ngoài (nếu có) không bị ảnh hưởng.
            return null;
        }
    }
}
