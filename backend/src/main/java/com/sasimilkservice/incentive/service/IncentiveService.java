package com.sasimilkservice.incentive.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.incentive.domain.IncentiveRule;
import com.sasimilkservice.incentive.domain.ProducerIncentive;
import com.sasimilkservice.incentive.repository.IncentiveRuleRepository;
import com.sasimilkservice.incentive.repository.ProducerIncentiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class IncentiveService {

    private final IncentiveRuleRepository commonRules;
    private final ProducerIncentiveRepository individualRules;
    private final AuditRecorder audit;

    public IncentiveService(IncentiveRuleRepository commonRules,
                            ProducerIncentiveRepository individualRules,
                            AuditRecorder audit) {
        this.commonRules = commonRules;
        this.individualRules = individualRules;
        this.audit = audit;
    }

    public List<IncentiveRule> listCommon() {
        return commonRules.findByStatusOrderByEffectiveDateDesc(IncentiveRule.Status.ACTIVE);
    }

    public List<ProducerIncentive> listIndividual(long producerId) {
        return individualRules.findByProducerIdOrderByEffectiveDateDesc(producerId);
    }

    @Transactional
    public IncentiveRule createCommon(IncentiveRule.Scope scope, Long regionId, BigDecimal rate,
                                      LocalDate effectiveDate, LocalDate endDate, String description) {
        if (scope == IncentiveRule.Scope.REGION && regionId == null) {
            throw new BusinessRuleException("A REGION incentive requires a regionId");
        }
        validateDates(effectiveDate, endDate);
        IncentiveRule r = new IncentiveRule();
        r.setScope(scope);
        r.setRegionId(scope == IncentiveRule.Scope.REGION ? regionId : null);
        r.setIncentiveRatePerLitre(rate);
        r.setEffectiveDate(effectiveDate);
        r.setEndDate(endDate);
        r.setDescription(description);
        r = commonRules.save(r);
        audit.record("INCENTIVE_CREATED", "IncentiveRule", r.getId(),
                "Common incentive " + rate + "/L from " + effectiveDate);
        return r;
    }

    @Transactional
    public ProducerIncentive createIndividual(long producerId, BigDecimal rate,
                                              LocalDate effectiveDate, LocalDate endDate, String description) {
        validateDates(effectiveDate, endDate);
        ProducerIncentive p = new ProducerIncentive();
        p.setProducerId(producerId);
        p.setIncentiveRatePerLitre(rate);
        p.setEffectiveDate(effectiveDate);
        p.setEndDate(endDate);
        p.setDescription(description);
        p = individualRules.save(p);
        audit.record("INCENTIVE_CREATED", "ProducerIncentive", p.getId(),
                "Individual incentive " + rate + "/L for producer " + producerId);
        return p;
    }

    @Transactional
    public void cancelCommon(long id) {
        IncentiveRule r = commonRules.findById(id)
                .orElseThrow(() -> NotFoundException.of("IncentiveRule", id));
        r.setStatus(IncentiveRule.Status.CANCELLED);
        r.setCancelledAt(Instant.now());
        CurrentUser.id().ifPresent(r::setCancelledBy);
        audit.record("INCENTIVE_UPDATED", "IncentiveRule", id, "Common incentive cancelled");
    }

    @Transactional
    public void cancelIndividual(long id) {
        ProducerIncentive p = individualRules.findById(id)
                .orElseThrow(() -> NotFoundException.of("ProducerIncentive", id));
        p.setStatus(ProducerIncentive.Status.CANCELLED);
        p.setCancelledAt(Instant.now());
        CurrentUser.id().ifPresent(p::setCancelledBy);
        audit.record("INCENTIVE_UPDATED", "ProducerIncentive", id, "Individual incentive cancelled");
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (to != null && to.isBefore(from)) {
            throw new BusinessRuleException("endDate must not be before effectiveDate");
        }
    }
}
