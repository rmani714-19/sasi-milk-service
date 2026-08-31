package com.sasimilkservice.collection.service;

import com.sasimilkservice.collection.repository.MilkCollectionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Human-readable challan number, e.g. {@code MC-20260831-M-000123}. */
@Component
public class CollectionNumberGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MilkCollectionRepository repository;

    public CollectionNumberGenerator(MilkCollectionRepository repository) {
        this.repository = repository;
    }

    public String next(LocalDate date, String shift) {
        long seq = repository.nextCollectionNumberValue();
        char shiftInitial = shift == null || shift.isEmpty() ? 'X' : Character.toUpperCase(shift.charAt(0));
        return "MC-%s-%s-%06d".formatted(DAY.format(date), shiftInitial, seq);
    }
}
