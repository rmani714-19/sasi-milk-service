package com.sasimilkservice.deduction.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.deduction.domain.DeductionTransaction;
import com.sasimilkservice.deduction.domain.DeductionType;
import com.sasimilkservice.deduction.repository.DeductionTransactionRepository;
import com.sasimilkservice.deduction.repository.DeductionTypeRepository;
import com.sasimilkservice.deduction.spi.DeductionCalculator;
import com.sasimilkservice.deduction.spi.DeductionCalculator.DeductionInput;
import com.sasimilkservice.deduction.spi.DeductionCalculator.PendingDeduction;
import com.sasimilkservice.deduction.spi.DeductionLedger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DeductionService implements DeductionLedger {

    private final DeductionTransactionRepository transactions;
    private final DeductionTypeRepository types;
    private final DeductionCalculator calculator;
    private final AuditRecorder audit;

    public DeductionService(DeductionTransactionRepository transactions,
                            DeductionTypeRepository types,
                            DeductionCalculator calculator,
                            AuditRecorder audit) {
        this.transactions = transactions;
        this.types = types;
        this.calculator = calculator;
        this.audit = audit;
    }

    public List<DeductionType> listTypes() {
        return types.findAll();
    }

    public List<DeductionTransaction> listForProducer(long producerId) {
        return transactions.findByProducerIdOrderByTxnDateDescIdDesc(producerId);
    }

    public List<DeductionTransaction> listAll() {
        return transactions.findAllByOrderByTxnDateDescIdDesc();
    }

    @Transactional
    public DeductionTransaction create(long producerId, String typeCode, BigDecimal amount,
                                       LocalDate txnDate, String description) {
        if (amount == null || amount.signum() < 0) {
            throw new BusinessRuleException("Deduction amount must be zero or positive");
        }
        DeductionType type = types.findByCode(typeCode.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Deduction type '" + typeCode + "' not found"));

        DeductionTransaction d = new DeductionTransaction();
        d.setProducerId(producerId);
        d.setDeductionTypeId(type.getId());
        d.setAmount(amount);
        d.setTxnDate(txnDate == null ? LocalDate.now() : txnDate);
        d.setDescription(description);
        d = transactions.save(d);
        audit.record("DEDUCTION_CREATED", "DeductionTransaction", d.getId(),
                "%s %s for producer %d".formatted(type.getCode(), amount, producerId));
        return d;
    }

    @Transactional
    public void cancel(long id) {
        DeductionTransaction d = transactions.findById(id)
                .orElseThrow(() -> NotFoundException.of("DeductionTransaction", id));
        if (d.getStatus() == DeductionTransaction.Status.APPLIED) {
            throw new BusinessRuleException("An applied deduction cannot be cancelled; reverse the settlement");
        }
        d.setStatus(DeductionTransaction.Status.CANCELLED);
        audit.record("DEDUCTION_CREATED", "DeductionTransaction", id, "Deduction cancelled");
    }

    // ---- DeductionLedger (used by the settlement engine) ----

    @Override
    public PendingDeductions pendingFor(long producerId, LocalDate asOf) {
        List<DeductionTransaction> pending = transactions.findPending(producerId, asOf);
        Map<Long, String> codeById = types.findAll().stream()
                .collect(Collectors.toMap(DeductionType::getId, DeductionType::getCode));

        List<PendingDeduction> rows = pending.stream()
                .map(d -> new PendingDeduction(d.getId(),
                        codeById.getOrDefault(d.getDeductionTypeId(), "OTHER"), d.getAmount()))
                .toList();

        return new PendingDeductions(
                pending.stream().map(DeductionTransaction::getId).toList(),
                calculator.calculate(new DeductionInput(rows)));
    }

    @Override
    @Transactional
    public void markApplied(Collection<Long> deductionTransactionIds, long settlementItemId) {
        for (Long id : deductionTransactionIds) {
            transactions.findById(id).ifPresent(d -> {
                d.setStatus(DeductionTransaction.Status.APPLIED);
                d.setSettlementItemId(settlementItemId);
            });
        }
    }

    @Override
    @Transactional
    public void unapply(long settlementItemId) {
        List<DeductionTransaction> applied =
                transactions.findBySettlementItemIdIn(List.of(settlementItemId));
        for (DeductionTransaction d : applied) {
            d.setStatus(DeductionTransaction.Status.PENDING);
            d.setSettlementItemId(null);
        }
    }
}
