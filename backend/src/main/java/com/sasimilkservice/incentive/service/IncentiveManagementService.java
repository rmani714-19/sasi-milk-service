package com.sasimilkservice.incentive.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.money.MoneyMath;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.incentive.domain.Incentive;
import com.sasimilkservice.incentive.repository.IncentiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * CRUD + lifecycle for the {@link Incentive} catalog. Editing or deleting an
 * incentive never touches historical financial records — an incentive that could
 * already have applied to real transactions can only be deactivated.
 */
@Service
@Transactional(readOnly = true)
public class IncentiveManagementService {

    private final IncentiveRepository incentives;
    private final AuditRecorder audit;

    public IncentiveManagementService(IncentiveRepository incentives, AuditRecorder audit) {
        this.incentives = incentives;
        this.audit = audit;
    }

    public List<Incentive> list() {
        return incentives.findAllByOrderByCreatedAtDescIdDesc();
    }

    public Incentive require(long id) {
        return incentives.findById(id).orElseThrow(() -> NotFoundException.of("Incentive", id));
    }

    @Transactional
    public Incentive create(Command c) {
        validatePeriod(c.effectiveFrom(), c.effectiveTo());
        Incentive i = new Incentive();
        i.setName(requireName(c.name()));
        i.setTarget(c.target());
        i.setIncentiveType(c.incentiveType());
        i.setAmount(nonNegative(c.amount()));
        i.setEffectiveFrom(c.effectiveFrom());
        i.setEffectiveTo(c.effectiveTo());
        i.setDescription(trimToNull(c.description()));
        i.setPriority(c.priority() == null ? 0 : c.priority());
        i.setStatus(c.status() == null ? Incentive.Status.ACTIVE : c.status());
        if (i.getStatus() == Incentive.Status.ACTIVE) {
            i.setActivatedAt(Instant.now());
            CurrentUser.id().ifPresent(i::setActivatedBy);
        }
        i = incentives.save(i);
        audit.record("INCENTIVE_CREATED", "Incentive", i.getId(),
                "%s (%s / %s, %s) %s..%s".formatted(i.getName(), i.getTarget(), i.getIncentiveType(),
                        i.getAmount(), i.getEffectiveFrom(), i.getEffectiveTo()));
        return i;
    }

    /** Edit the mutable fields. Type and target are fixed once created. */
    @Transactional
    public Incentive update(long id, Command c) {
        Incentive i = require(id);
        validatePeriod(c.effectiveFrom(), c.effectiveTo());
        i.setName(requireName(c.name()));
        i.setAmount(nonNegative(c.amount()));
        i.setEffectiveFrom(c.effectiveFrom());
        i.setEffectiveTo(c.effectiveTo());
        i.setDescription(trimToNull(c.description()));
        if (c.priority() != null) {
            i.setPriority(c.priority());
        }
        if (c.status() != null && c.status() != i.getStatus()) {
            applyStatus(i, c.status());
        }
        audit.record("INCENTIVE_UPDATED", "Incentive", i.getId(),
                "%s updated (amount %s, %s..%s)%s".formatted(i.getName(), i.getAmount(),
                        i.getEffectiveFrom(), i.getEffectiveTo(),
                        i.isUsed(LocalDate.now()) ? " — already in use, applies to future only" : ""));
        return i;
    }

    @Transactional
    public Incentive activate(long id) {
        Incentive i = require(id);
        validatePeriod(i.getEffectiveFrom(), i.getEffectiveTo());
        if (i.getEffectiveTo().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("Cannot activate — the effective period has already ended");
        }
        applyStatus(i, Incentive.Status.ACTIVE);
        audit.record("INCENTIVE_ACTIVATED", "Incentive", i.getId(), i.getName() + " activated");
        return i;
    }

    @Transactional
    public Incentive deactivate(long id) {
        Incentive i = require(id);
        applyStatus(i, Incentive.Status.INACTIVE);
        audit.record("INCENTIVE_DEACTIVATED", "Incentive", i.getId(), i.getName() + " deactivated");
        return i;
    }

    @Transactional
    public void delete(long id, String reason) {
        Incentive i = require(id);
        if (i.isUsed(LocalDate.now())) {
            throw new BusinessRuleException("This incentive has already been active during a live period and "
                    + "cannot be permanently deleted. Deactivate it instead.");
        }
        audit.record("INCENTIVE_DELETED", "Incentive", i.getId(),
                "%s deleted%s".formatted(i.getName(), reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        incentives.delete(i);
    }

    // ---- internals ----

    private void applyStatus(Incentive i, Incentive.Status status) {
        i.setStatus(status);
        Instant now = Instant.now();
        if (status == Incentive.Status.ACTIVE) {
            if (i.getActivatedAt() == null) {
                i.setActivatedAt(now);
            }
            CurrentUser.id().ifPresent(i::setActivatedBy);
        } else {
            i.setDeactivatedAt(now);
            CurrentUser.id().ifPresent(i::setDeactivatedBy);
        }
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null) {
            throw new BusinessRuleException("Effective From date is required");
        }
        if (to == null) {
            throw new BusinessRuleException("Effective To date is required");
        }
        if (to.isBefore(from)) {
            throw new BusinessRuleException("Effective To must be on or after Effective From");
        }
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Incentive name is required");
        }
        return name.trim();
    }

    private BigDecimal nonNegative(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new BusinessRuleException("Incentive amount must be zero or more");
        }
        return MoneyMath.rate(amount);
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public record Command(
            String name,
            Incentive.Target target,
            Incentive.IncentiveType incentiveType,
            BigDecimal amount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Incentive.Status status,
            String description,
            Integer priority) {
    }
}
