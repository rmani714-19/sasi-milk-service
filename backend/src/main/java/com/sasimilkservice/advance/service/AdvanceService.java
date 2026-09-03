package com.sasimilkservice.advance.service;

import com.sasimilkservice.advance.domain.AdvanceAccount;
import com.sasimilkservice.advance.domain.AdvanceTransaction;
import com.sasimilkservice.advance.repository.AdvanceAccountRepository;
import com.sasimilkservice.advance.repository.AdvanceTransactionRepository;
import com.sasimilkservice.advance.spi.AdvanceLedger;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AccountRecovery;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AdvanceAccountState;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.RecoveryMode;
import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.money.MoneyMath;
import com.sasimilkservice.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdvanceService implements AdvanceLedger {

    private final AdvanceAccountRepository accounts;
    private final AdvanceTransactionRepository transactions;
    private final AuditRecorder audit;

    public AdvanceService(AdvanceAccountRepository accounts,
                          AdvanceTransactionRepository transactions,
                          AuditRecorder audit) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.audit = audit;
    }

    public List<AdvanceAccount> listForProducer(long producerId) {
        return accounts.findByProducerIdOrderByAdvanceDateAscIdAsc(producerId);
    }

    public List<AdvanceAccount> listAllAccounts() {
        return accounts.findAllByOrderByAdvanceDateAscIdAsc();
    }

    public List<AdvanceTransaction> ledgerForProducer(long producerId) {
        return transactions.findByProducerIdOrderByIdDesc(producerId);
    }

    public List<AdvanceTransaction> allLedger() {
        return transactions.findAllByOrderByIdDesc();
    }

    public BigDecimal totalOutstanding(long producerId) {
        return accounts.findByProducerIdAndStatusOrderByAdvanceDateAscIdAsc(
                        producerId, AdvanceAccount.Status.ACTIVE).stream()
                .map(AdvanceAccount::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalOutstandingAll() {
        return accounts.findByStatus(AdvanceAccount.Status.ACTIVE).stream()
                .map(AdvanceAccount::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public AdvanceAccount create(long producerId, BigDecimal amount, AdvanceAccount.RecoveryMode mode,
                                 BigDecimal recoveryAmount, BigDecimal recoveryPercentage,
                                 LocalDate advanceDate, String description) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Advance amount must be positive");
        }
        if (mode == AdvanceAccount.RecoveryMode.FIXED_PER_WEEK
                && (recoveryAmount == null || recoveryAmount.signum() <= 0)) {
            throw new BusinessRuleException("FIXED_PER_WEEK requires a positive recoveryAmount");
        }
        if (mode == AdvanceAccount.RecoveryMode.PERCENTAGE_OF_NET
                && (recoveryPercentage == null || recoveryPercentage.signum() <= 0
                    || recoveryPercentage.compareTo(new BigDecimal("100")) > 0)) {
            throw new BusinessRuleException("PERCENTAGE_OF_NET requires recoveryPercentage in (0, 100]");
        }

        AdvanceAccount acc = new AdvanceAccount();
        acc.setProducerId(producerId);
        acc.setOriginalAmount(MoneyMath.money(amount));
        acc.setOutstandingAmount(MoneyMath.money(amount));
        acc.setRecoveryMode(mode);
        acc.setRecoveryAmount(mode == AdvanceAccount.RecoveryMode.FIXED_PER_WEEK
                ? MoneyMath.money(recoveryAmount) : null);
        acc.setRecoveryPercentage(mode == AdvanceAccount.RecoveryMode.PERCENTAGE_OF_NET
                ? recoveryPercentage : null);
        acc.setAdvanceDate(advanceDate == null ? LocalDate.now() : advanceDate);
        acc.setDescription(description);
        acc = accounts.save(acc);

        AdvanceTransaction disbursement = new AdvanceTransaction();
        disbursement.setAdvanceAccountId(acc.getId());
        disbursement.setProducerId(producerId);
        disbursement.setTxnType(AdvanceTransaction.Type.DISBURSEMENT);
        disbursement.setAmount(acc.getOriginalAmount());
        disbursement.setBalanceAfter(acc.getOutstandingAmount());
        disbursement.setTxnDate(acc.getAdvanceDate());
        CurrentUser.id().ifPresent(disbursement::setCreatedBy);
        transactions.save(disbursement);

        audit.record("ADVANCE_CREATED", "AdvanceAccount", acc.getId(),
                "Advance %s to producer %d, recover %s".formatted(
                        acc.getOriginalAmount(),
                        producerId,
                        mode == AdvanceAccount.RecoveryMode.FIXED_PER_WEEK
                                ? acc.getRecoveryAmount() + "/week"
                                : acc.getRecoveryPercentage() + "% of net"));
        return acc;
    }

    @Transactional
    public AdvanceAccount update(long id, BigDecimal amount, AdvanceAccount.RecoveryMode mode,
                                 BigDecimal recoveryAmount, BigDecimal recoveryPercentage,
                                 LocalDate advanceDate, String description) {
        AdvanceAccount acc = accounts.findById(id)
                .orElseThrow(() -> NotFoundException.of("AdvanceAccount", id));
        if (acc.getStatus() != AdvanceAccount.Status.ACTIVE) {
            throw new BusinessRuleException("Only an active advance can be edited");
        }
        boolean untouched = acc.getOutstandingAmount().compareTo(acc.getOriginalAmount()) == 0
                && transactions.findByAdvanceAccountId(id).stream()
                        .noneMatch(t -> t.getTxnType() != AdvanceTransaction.Type.DISBURSEMENT);

        if (amount != null && amount.signum() > 0
                && amount.compareTo(acc.getOriginalAmount()) != 0) {
            if (!untouched) {
                throw new BusinessRuleException(
                        "The advance amount cannot be changed once recovery has started");
            }
            acc.setOriginalAmount(MoneyMath.money(amount));
            acc.setOutstandingAmount(MoneyMath.money(amount));
            transactions.findByAdvanceAccountId(id).stream()
                    .filter(t -> t.getTxnType() == AdvanceTransaction.Type.DISBURSEMENT)
                    .forEach(t -> {
                        t.setAmount(acc.getOriginalAmount());
                        t.setBalanceAfter(acc.getOutstandingAmount());
                    });
        }

        if (mode == AdvanceAccount.RecoveryMode.FIXED_PER_WEEK
                && (recoveryAmount == null || recoveryAmount.signum() <= 0)) {
            throw new BusinessRuleException("FIXED_PER_WEEK requires a positive recoveryAmount");
        }
        if (mode == AdvanceAccount.RecoveryMode.PERCENTAGE_OF_NET
                && (recoveryPercentage == null || recoveryPercentage.signum() <= 0
                    || recoveryPercentage.compareTo(new BigDecimal("100")) > 0)) {
            throw new BusinessRuleException("PERCENTAGE_OF_NET requires recoveryPercentage in (0, 100]");
        }
        acc.setRecoveryMode(mode);
        acc.setRecoveryAmount(mode == AdvanceAccount.RecoveryMode.FIXED_PER_WEEK
                ? MoneyMath.money(recoveryAmount) : null);
        acc.setRecoveryPercentage(mode == AdvanceAccount.RecoveryMode.PERCENTAGE_OF_NET
                ? recoveryPercentage : null);
        if (advanceDate != null) {
            acc.setAdvanceDate(advanceDate);
        }
        acc.setDescription(description);
        audit.record("ADVANCE_UPDATED", "AdvanceAccount", id,
                "Advance updated for producer %d".formatted(acc.getProducerId()));
        return acc;
    }

    @Transactional
    public void delete(long id) {
        AdvanceAccount acc = accounts.findById(id)
                .orElseThrow(() -> NotFoundException.of("AdvanceAccount", id));
        List<AdvanceTransaction> txns = transactions.findByAdvanceAccountId(id);
        boolean onlyDisbursement = txns.stream()
                .allMatch(t -> t.getTxnType() == AdvanceTransaction.Type.DISBURSEMENT);
        if (acc.getStatus() != AdvanceAccount.Status.ACTIVE || !onlyDisbursement
                || acc.getOutstandingAmount().compareTo(acc.getOriginalAmount()) != 0) {
            throw new BusinessRuleException(
                    "An advance can only be deleted before any recovery has been applied");
        }
        audit.record("ADVANCE_DELETED", "AdvanceAccount", id,
                "Advance of %s for producer %d deleted".formatted(acc.getOriginalAmount(), acc.getProducerId()));
        transactions.deleteAll(txns);
        accounts.delete(acc);
    }

    // ---- AdvanceLedger ----

    @Override
    public List<AdvanceAccountState> activeAccountStates(long producerId) {
        return accounts.findByProducerIdAndStatusOrderByAdvanceDateAscIdAsc(
                        producerId, AdvanceAccount.Status.ACTIVE).stream()
                .map(a -> new AdvanceAccountState(
                        a.getId(),
                        a.getOutstandingAmount(),
                        RecoveryMode.valueOf(a.getRecoveryMode().name()),
                        a.getRecoveryAmount(),
                        a.getRecoveryPercentage()))
                .toList();
    }

    @Override
    @Transactional
    public void postRecoveries(long producerId, long settlementItemId,
                               List<AccountRecovery> recoveries, LocalDate date) {
        for (AccountRecovery r : recoveries) {
            if (r.recoveredAmount() == null || r.recoveredAmount().signum() == 0) {
                continue;
            }
            AdvanceAccount acc = accounts.findById(r.advanceAccountId())
                    .orElseThrow(() -> NotFoundException.of("AdvanceAccount", r.advanceAccountId()));
            acc.setOutstandingAmount(MoneyMath.money(r.balanceAfter()));
            if (r.closesAccount()) {
                acc.setStatus(AdvanceAccount.Status.CLOSED);
                acc.setClosedAt(Instant.now());
            }

            AdvanceTransaction txn = new AdvanceTransaction();
            txn.setAdvanceAccountId(acc.getId());
            txn.setProducerId(producerId);
            txn.setTxnType(AdvanceTransaction.Type.RECOVERY);
            txn.setAmount(MoneyMath.money(r.recoveredAmount()));
            txn.setBalanceAfter(acc.getOutstandingAmount());
            txn.setTxnDate(date);
            txn.setSettlementItemId(settlementItemId);
            CurrentUser.id().ifPresent(txn::setCreatedBy);
            transactions.save(txn);

            audit.record("ADVANCE_RECOVERY", "AdvanceAccount", acc.getId(),
                    "Recovered %s (settlement item %d)".formatted(r.recoveredAmount(), settlementItemId));
        }
    }

    @Override
    @Transactional
    public void reverseRecoveries(long settlementItemId) {
        List<AdvanceTransaction> recovered = transactions.findBySettlementItemIdAndTxnType(
                settlementItemId, AdvanceTransaction.Type.RECOVERY);
        for (AdvanceTransaction rec : recovered) {
            AdvanceAccount acc = accounts.findById(rec.getAdvanceAccountId())
                    .orElseThrow(() -> NotFoundException.of("AdvanceAccount", rec.getAdvanceAccountId()));
            acc.setOutstandingAmount(MoneyMath.money(acc.getOutstandingAmount().add(rec.getAmount())));
            if (acc.getStatus() == AdvanceAccount.Status.CLOSED) {
                acc.setStatus(AdvanceAccount.Status.ACTIVE);
                acc.setClosedAt(null);
            }

            AdvanceTransaction reversal = new AdvanceTransaction();
            reversal.setAdvanceAccountId(acc.getId());
            reversal.setProducerId(rec.getProducerId());
            reversal.setTxnType(AdvanceTransaction.Type.REVERSAL);
            reversal.setAmount(rec.getAmount());
            reversal.setBalanceAfter(acc.getOutstandingAmount());
            reversal.setTxnDate(LocalDate.now());
            reversal.setReversalOfId(rec.getId());
            CurrentUser.id().ifPresent(reversal::setCreatedBy);
            transactions.save(reversal);

            // release the FK to the settlement item that is about to be deleted
            rec.setSettlementItemId(null);
        }
    }
}
