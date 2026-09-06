package com.sasimilkservice.paymentops.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.center.spi.BusinessBankAccountDirectory;
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
 * staff for a chosen (verified) centre bank account.
 */
@Service
@Transactional(readOnly = true)
public class PaymentOpsService {

    private final BankBalanceCheckRepository checks;
    private final BusinessBankAccountDirectory bankAccounts;
    private final AuditRecorder audit;

    public PaymentOpsService(BankBalanceCheckRepository checks, BusinessBankAccountDirectory bankAccounts,
                             AuditRecorder audit) {
        this.checks = checks;
        this.bankAccounts = bankAccounts;
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
    public BankBalanceCheck record(SubjectType type, long subjectId, Long centerBankAccountId,
                                   String bankAccountLabel, BigDecimal availableBalance,
                                   BigDecimal requiredAmount, Method method, String note) {
        String label = bankAccountLabel == null ? null : bankAccountLabel.trim();
        if (centerBankAccountId != null) {
            BusinessBankAccountDirectory.UsableAccount acc = bankAccounts.usable(centerBankAccountId)
                    .orElseThrow(() -> new BusinessRuleException(
                            "Selected bank account is not available — it must be an active, verified account"));
            label = acc.label();
        }
        if (label == null || label.isBlank()) {
            throw new BusinessRuleException("Select the bank account you are paying from");
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
        c.setCenterBankAccountId(centerBankAccountId);
        c.setBankAccountLabel(label);
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
