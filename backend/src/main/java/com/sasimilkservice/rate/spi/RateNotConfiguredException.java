package com.sasimilkservice.rate.spi;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Thrown when no rate can be resolved for a collection and the center has not
 * enabled {@code allow_missing_rate}. Maps to HTTP 422 with a clear configuration
 * error message (spec: "Do not silently return zero when a rate is missing. Show a
 * clear configuration error.").
 */
public class RateNotConfiguredException extends RuntimeException {

    private final Long regionId;
    private final LocalDate collectionDate;
    private final BigDecimal fat;
    private final BigDecimal snf;

    public RateNotConfiguredException(Long regionId, LocalDate collectionDate,
                                     BigDecimal fat, BigDecimal snf) {
        super("No rate chart entry for region=%s date=%s fat=%s snf=%s"
                .formatted(regionId, collectionDate, fat, snf));
        this.regionId = regionId;
        this.collectionDate = collectionDate;
        this.fat = fat;
        this.snf = snf;
    }

    public Long getRegionId() { return regionId; }
    public LocalDate getCollectionDate() { return collectionDate; }
    public BigDecimal getFat() { return fat; }
    public BigDecimal getSnf() { return snf; }
}
