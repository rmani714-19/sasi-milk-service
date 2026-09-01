package com.sasimilkservice.settlement.service;

import com.sasimilkservice.settlement.repository.SettlementRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Human-readable settlement number, e.g. {@code WS-2026-0007}. */
@Component
public class SettlementNumberGenerator {

    private final SettlementRepository repository;

    public SettlementNumberGenerator(SettlementRepository repository) {
        this.repository = repository;
    }

    public String next(LocalDate periodStart) {
        return "WS-%d-%04d".formatted(periodStart.getYear(), repository.nextSettlementNumberValue());
    }
}
