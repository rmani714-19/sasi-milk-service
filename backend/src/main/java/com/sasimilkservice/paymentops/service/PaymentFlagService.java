package com.sasimilkservice.paymentops.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.paymentops.domain.PaymentFlag;
import com.sasimilkservice.paymentops.domain.PaymentFlag.Status;
import com.sasimilkservice.paymentops.domain.PaymentFlag.SubjectType;
import com.sasimilkservice.paymentops.repository.PaymentFlagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * "Raise a query on this transaction" — a lightweight flag/note any
 * authorized user can attach to a payout, salary line, or expense, and clear
 * once it's sorted out. Not a ticketing system: one open flag per subject,
 * full history kept once resolved.
 */
@Service
@Transactional(readOnly = true)
public class PaymentFlagService {

    private final PaymentFlagRepository repository;
    private final AuditRecorder audit;

    public PaymentFlagService(PaymentFlagRepository repository, AuditRecorder audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /** Every currently-open flag, across all subject types — for decorating the activity list. */
    public List<PaymentFlag> openFlags() {
        return repository.findByStatusOrderByIdDesc(Status.OPEN);
    }

    /** Full flag history (open + resolved) for one transaction. */
    public List<PaymentFlag> historyFor(SubjectType subjectType, long subjectId) {
        return repository.findBySubjectTypeAndSubjectIdOrderByIdDesc(subjectType, subjectId);
    }

    @Transactional
    public PaymentFlag raise(SubjectType subjectType, long subjectId, String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Describe what's wrong before flagging this transaction");
        }
        repository.findBySubjectTypeAndSubjectIdAndStatus(subjectType, subjectId, Status.OPEN)
                .ifPresent(f -> {
                    throw new BusinessRuleException("This transaction is already flagged");
                });

        PaymentFlag f = new PaymentFlag();
        f.setSubjectType(subjectType);
        f.setSubjectId(subjectId);
        f.setNote(note.trim());
        CurrentUser.id().ifPresent(f::setFlaggedBy);
        f = repository.save(f);
        audit.record("PAYMENT_FLAGGED", subjectType.name(), subjectId, "Flagged: " + f.getNote());
        return f;
    }

    @Transactional
    public PaymentFlag resolve(long flagId, String resolutionNote) {
        PaymentFlag f = repository.findById(flagId)
                .orElseThrow(() -> NotFoundException.of("PaymentFlag", flagId));
        if (f.getStatus() == Status.RESOLVED) {
            throw new BusinessRuleException("This flag is already resolved");
        }
        f.setStatus(Status.RESOLVED);
        f.setResolutionNote(resolutionNote == null || resolutionNote.isBlank() ? null : resolutionNote.trim());
        f.setResolvedAt(Instant.now());
        CurrentUser.id().ifPresent(f::setResolvedBy);
        audit.record("PAYMENT_FLAG_RESOLVED", f.getSubjectType().name(), f.getSubjectId(),
                "Flag resolved: " + f.getNote());
        return f;
    }
}
