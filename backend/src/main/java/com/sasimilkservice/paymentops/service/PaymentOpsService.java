package com.sasimilkservice.paymentops.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.money.MoneyMath;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.paymentops.domain.BankBalanceCheck;
import com.sasimilkservice.paymentops.domain.BankBalanceCheck.Method;
import com.sasimilkservice.paymentops.domain.BankBalanceCheck.SubjectType;
import com.sasimilkservice.paymentops.repository.BankBalanceCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Records and reads the owner bank-balance verification that gates approval of a
 * payment batch. The balance is never invented — it is entered by authorized
 * staff (or, when available, taken from a bank integration).
 */
@Service
@Transactional(readOnly = true)
public class PaymentOpsService {

    private final BankBalanceCheckRepository checks;
    private final AuditRecorder audit;

    public PaymentOpsService(BankBalanceCheckRepository checks, AuditRecorder audit) {
        this.checks = checks;
        this.audit = audit;
    }

    public Optional<BankBalanceCheck> latest(SubjectType type, long subjectId) {
        return checks.findFirstBySubjectTypeAndSubjectIdOrderByIdDesc(type, subjectId);
    }

    /** True when a balance check exists for the subject and it covers the required amount. */
    public boolean balanceCovered(SubjectType type, long subjectId, BigDecimal required) {
        return latest(type, subjectId)
                .filter(BankBalanceCheck::isSufficient)
                .filter(c -> c.getRequiredAmount().compareTo(MoneyMath.money(required)) == 0)
                .isPresent();
    }

    @Transactional
    public BankBalanceCheck record(SubjectType type, long subjectId, String bankAccountLabel,
                                   BigDecimal availableBalance, BigDecimal requiredAmount,
                                   Method method, String note) {
        if (bankAccountLabel == null || bankAccountLabel.isBlank()) {
            throw new BusinessRuleException("Bank account is required");
        }
        if (availableBalance == null || availableBalance.signum() < 0) {
            throw new BusinessRuleException("Enter the available bank balance");
        }
        BigDecimal available = MoneyMath.money(availableBalance);
        BigDecimal required = MoneyMath.money(requiredAmount);
        BigDecimal remaining = available.subtract(required);

        BankBalanceCheck c = new BankBalanceCheck();
        c.setSubjectType(type);
        c.setSubjectId(subjectId);
        c.setBankAccountLabel(bankAccountLabel.trim());
        c.setAvailableBalance(available);
        c.setRequiredAmount(required);
        c.setRemainingAfter(remaining);
        c.setSufficient(remaining.signum() >= 0);
        c.setMethod(method == null ? Method.MANUAL : method);
        c.setNote(note == null || note.isBlank() ? null : note.trim());
        c.setVerifiedAt(Instant.now());
        CurrentUser.id().ifPresent(c::setVerifiedBy);
        c = checks.save(c);

        audit.record("PAYMENT_BANK_BALANCE_VERIFIED", type == SubjectType.SETTLEMENT ? "Settlement" : "DriverSalaryBatch",
                subjectId, "%s balance %s vs required %s -> %s (%s)".formatted(
                        c.getBankAccountLabel(), available, required,
                        c.isSufficient() ? "sufficient, remaining " + remaining : "INSUFFICIENT",
                        c.getMethod()));
        return c;
    }
}
