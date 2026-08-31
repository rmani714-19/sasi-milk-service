package com.sasimilkservice.rate.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.rate.domain.RateChart;
import com.sasimilkservice.rate.repository.RateChartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RateChartService {

    private final RateChartRepository repository;
    private final AuditRecorder audit;

    public RateChartService(RateChartRepository repository, AuditRecorder audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public List<RateChart> findAll() {
        return repository.findAllByOrderByScopeAscEffectiveFromDesc();
    }

    public RateChart require(long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("RateChart", id));
    }

    @Transactional
    public RateChart create(RateChart.Scope scope, Long regionId, String name, BigDecimal tsValue,
                            LocalDate effectiveFrom, LocalDate effectiveTo, String note) {
        if (scope == RateChart.Scope.REGION && regionId == null) {
            throw new BusinessRuleException("A REGION rate chart requires a regionId");
        }
        if (scope == RateChart.Scope.COMMON && regionId != null) {
            throw new BusinessRuleException("A COMMON rate chart must not have a regionId");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessRuleException("effectiveTo must not be before effectiveFrom");
        }
        RateChart c = new RateChart();
        c.setScope(scope);
        c.setRegionId(scope == RateChart.Scope.REGION ? regionId : null);
        c.setName(name);
        c.setTsValue(tsValue);
        c.setEffectiveFrom(effectiveFrom);
        c.setEffectiveTo(effectiveTo);
        c.setNote(note);
        // DB constraint ex_rate_chart_no_overlap rejects overlapping ACTIVE rows -> 409
        c = repository.save(c);
        audit.record("RATE_CREATED", "RateChart", c.getId(),
                "%s rate chart ts_value=%s from %s".formatted(scope, tsValue, effectiveFrom));
        return c;
    }

    @Transactional
    public RateChart update(long id, String name, BigDecimal tsValue, LocalDate effectiveTo, String note) {
        RateChart c = require(id);
        if (c.getStatus() == RateChart.Status.ARCHIVED) {
            throw new BusinessRuleException("An archived rate chart cannot be modified");
        }
        c.setName(name);
        if (tsValue != null) {
            c.setTsValue(tsValue);
        }
        c.setEffectiveTo(effectiveTo);
        c.setNote(note);
        audit.record("RATE_UPDATED", "RateChart", c.getId(), "Rate chart " + id + " updated");
        return c;
    }

    @Transactional
    public RateChart archive(long id) {
        RateChart c = require(id);
        c.setStatus(RateChart.Status.ARCHIVED);
        audit.record("RATE_UPDATED", "RateChart", c.getId(), "Rate chart " + id + " archived");
        return c;
    }
}
