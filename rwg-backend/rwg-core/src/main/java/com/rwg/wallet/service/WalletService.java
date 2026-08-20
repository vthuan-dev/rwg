package com.rwg.wallet.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.common.money.Money;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.wallet.domain.Wallet;
import com.rwg.wallet.domain.WalletLedgerGuard;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.domain.WalletTransaction;
import com.rwg.wallet.dto.WalletResponse;
import com.rwg.wallet.dto.WalletTransactionResponse;
import com.rwg.wallet.repository.WalletLedgerGuardRepository;
import com.rwg.wallet.repository.WalletRepository;
import com.rwg.wallet.repository.WalletTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Lõi tiền của hệ thống (chặng 2 Phase b, đã sửa theo review 3 chiều). Nguyên tắc:
 * - debit dùng 1 câu UPDATE điều kiện (balance >= amt) trả affected rows; 0 -> INSUFFICIENT_BALANCE.
 *   Chống double-spend kể cả 2 thread song song (row lock của UPDATE).
 * - ledger (wallet_transactions) insert CÙNG transaction với UPDATE số dư.
 * - IDEMPOTENCY THỰC SỰ Ở TẦNG DB (fix C1): insert guard vào wallet_ledger_guard
 *   (PK THUẦN trên idempotency_key) TRƯỚC CÙNG trong transaction; trùng key ->
 *   DataIntegrityViolationException -> rollback -> trả số dư hiện có (idempotent
 *   success). existsByIdempotencyKey trên ledger chỉ còn là fast-path.
 * - Path ĐỌC (getBalance/getWallet/listTransactions) KHÔNG tạo ví (fix M6):
 *   ví chưa tồn tại -> balance 0 / trang rỗng.
 * - Mọi nghiệp vụ tiền đều audit qua AuditTrailService.
 */
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletLedgerGuardRepository guardRepository;
    private final WalletCreator walletCreator;
    private final AuditTrailService audit;
    private final TransactionTemplate txWrite;
    private final TransactionTemplate txRead;

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository transactionRepository,
                         WalletLedgerGuardRepository guardRepository,
                         WalletCreator walletCreator,
                         AuditTrailService audit,
                         PlatformTransactionManager transactionManager) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.guardRepository = guardRepository;
        this.walletCreator = walletCreator;
        this.audit = audit;
        // debit/credit dùng TransactionTemplate (thay vì @Transactional tự gọi) để
        // bắt DataIntegrityViolationException NGOÀI transaction — transaction thua
        // race đã rollback xong mới đọc số dư hiện có, tránh session bị hỏng.
        this.txWrite = new TransactionTemplate(transactionManager);
        this.txWrite.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.txRead = new TransactionTemplate(transactionManager);
        this.txRead.setReadOnly(true);
    }

    // ===== ĐỌC =====

    /**
     * Lấy hoặc tạo ví (lazy, idempotent — 2 luồng song song chỉ sinh đúng 1 ví).
     * Việc tạo ví chạy trong transaction RIÊNG (REQUIRES_NEW, xem {@link WalletCreator})
     * nên thua race uq_wallets_user_id KHÔNG đánh dấu rollback-only transaction ngoài.
     */
    @Transactional
    public Wallet getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            Wallet created = walletCreator.createNew(userId);
            if (created != null) {
                return created;
            }
            // Thua race condition (uq_wallets_user_id): ví đã được luồng khác tạo.
            return walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
        });
    }

    /** Số dư hiện tại. KHÔNG tạo ví (fix M6): ví chưa có -> 0. */
    @Transactional(readOnly = true)
    public Money getBalance(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(w -> Money.of(w.getBalance()))
                .orElse(Money.zero());
    }

    /** Thông tin ví. KHÔNG tạo ví (fix M6): ví chưa có -> ví ảo balance 0. */
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(w -> new WalletResponse(w.getId().toString(), w.getUserId().toString(),
                        Money.of(w.getBalance()).amount().toPlainString(), w.getCurrency()))
                .orElseGet(() -> new WalletResponse(null, userId.toString(),
                        Money.zero().amount().toPlainString(), Wallet.DEFAULT_CURRENCY));
    }

    /** Lịch sử giao dịch. KHÔNG tạo ví (fix M6): ví chưa có -> trang rỗng. */
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> listTransactions(UUID userId, int page, int size) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> {
                    Page<WalletTransaction> txs = transactionRepository.findByWalletId(wallet.getId(),
                            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
                    return PageResponse.from(txs, WalletService::toResponse);
                })
                .orElseGet(() -> new PageResponse<>(java.util.List.of(), page, size, 0, 0, true));
    }

    /**
     * Khóa row ví của user bằng SELECT ... FOR UPDATE (fix M3) — serialize các
     * nghiệp vụ đọc-kiểm-tra-ghi (vd hạn mức rút ngày) trong transaction của caller.
     */
    @Transactional
    public Wallet lockWallet(UUID userId) {
        getOrCreateWallet(userId);
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
    }

    // ===== GHI (debit / credit) =====

    /**
     * Trừ tiền. Idempotent theo idempotencyKey: cùng key không trừ 2 lần
     * (guard DB duy nhất toàn cục — fix C1). Trả số dư sau khi trừ.
     * Ném INSUFFICIENT_BALANCE nếu thiếu tiền.
     */
    public Money debit(UUID userId, Money amount, WalletRefType refType, String refId, String idempotencyKey) {
        requirePositive(amount);
        Wallet wallet = getOrCreateWallet(userId);

        // Fast-path: nghiệp vụ này đã ghi sổ rồi -> không trừ lại.
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return Money.of(readBalance(wallet.getId()));
        }

        try {
            Money result = txWrite.execute(status -> {
                // 1) Guard TRƯỚC CÙNG: PK thuần trên idempotency_key -> chỉ 1 transaction
                //    thắng; trùng key -> DataIntegrityViolationException -> rollback cả tx.
                guardRepository.saveAndFlush(new WalletLedgerGuard(idempotencyKey));

                // 2) UPDATE điều kiện nguyên tử (balance >= amt).
                int updated = walletRepository.debitIfSufficient(wallet.getId(), amount.amount(), Instant.now());
                if (updated == 0) {
                    throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
                }
                Money balanceAfter = Money.of(walletRepository.findBalanceById(wallet.getId()));

                // 3) Dòng ledger CÙNG transaction với UPDATE số dư.
                transactionRepository.save(WalletTransaction.debit(
                        wallet.getId(), amount.amount(), balanceAfter.amount(), refType, refId, idempotencyKey));

                audit.record(userId, null, AuditTrailService.WALLET_DEBIT, "WALLET", wallet.getId().toString(),
                        Map.of("amount", amount.amount().toPlainString(), "refType", refType.name(),
                                "refId", refId, "balanceAfter", balanceAfter.amount().toPlainString()), null);
                return balanceAfter;
            });
            return result;
        } catch (DataIntegrityViolationException duplicateKey) {
            return onDuplicateIdempotencyKey(wallet.getId(), duplicateKey);
        }
    }

    /**
     * Cộng tiền. Idempotent theo idempotencyKey: cùng key không cộng 2 lần
     * (guard DB duy nhất toàn cục — fix C1). Trả số dư sau khi cộng.
     */
    public Money credit(UUID userId, Money amount, WalletRefType refType, String refId, String idempotencyKey) {
        requirePositive(amount);
        Wallet wallet = getOrCreateWallet(userId);

        // Fast-path: nghiệp vụ này đã ghi sổ rồi -> không cộng lại.
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return Money.of(readBalance(wallet.getId()));
        }

        try {
            Money result = txWrite.execute(status -> {
                // 1) Guard TRƯỚC CÙNG (PK thuần idempotency_key) — xem debit().
                guardRepository.saveAndFlush(new WalletLedgerGuard(idempotencyKey));

                // 2) Cộng tiền (luôn thành công vì ví đã tồn tại).
                int updated = walletRepository.credit(wallet.getId(), amount.amount(), Instant.now());
                if (updated == 0) {
                    throw new ApiException(ErrorCode.INTERNAL_ERROR);
                }
                Money balanceAfter = Money.of(walletRepository.findBalanceById(wallet.getId()));

                // 3) Dòng ledger CÙNG transaction với UPDATE số dư.
                transactionRepository.save(WalletTransaction.credit(
                        wallet.getId(), amount.amount(), balanceAfter.amount(), refType, refId, idempotencyKey));

                audit.record(userId, null, AuditTrailService.WALLET_CREDIT, "WALLET", wallet.getId().toString(),
                        Map.of("amount", amount.amount().toPlainString(), "refType", refType.name(),
                                "refId", refId, "balanceAfter", balanceAfter.amount().toPlainString()), null);
                return balanceAfter;
            });
            return result;
        } catch (DataIntegrityViolationException duplicateKey) {
            return onDuplicateIdempotencyKey(wallet.getId(), duplicateKey);
        }
    }

    /**
     * Thua race idempotency_key (fix C1): transaction guard đã rollback sạch
     * (chưa kịp đụng số dư/ledger) -> trả số dư hiện có do thread thắng ghi sổ.
     * Nếu đang NẰM TRONG transaction ngoài (propagation REQUIRED join), transaction
     * đó đã bị đánh dấu rollback-only -> ném lại cho caller xử lý thay vì đọc tiếp.
     */
    private Money onDuplicateIdempotencyKey(UUID walletId, DataIntegrityViolationException duplicateKey) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw duplicateKey;
        }
        return Money.of(readBalance(walletId));
    }

    /** Đọc số dư trong transaction readOnly riêng (bypass persistence context). */
    private BigDecimal readBalance(UUID walletId) {
        BigDecimal balance = txRead.execute(status -> walletRepository.findBalanceById(walletId));
        return balance == null ? BigDecimal.ZERO : balance;
    }

    // ===== helpers =====

    private void requirePositive(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }

    static WalletTransactionResponse toResponse(WalletTransaction tx) {
        return new WalletTransactionResponse(
                tx.getId().toString(),
                tx.getCreatedAt(),
                tx.getDebit().toPlainString(),
                tx.getCredit().toPlainString(),
                tx.getBalanceAfter().toPlainString(),
                tx.getRefType().name(),
                tx.getRefId(),
                tx.getStatus().name());
    }
}
