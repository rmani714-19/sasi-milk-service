package com.sasimilkservice.collection.spi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Published SPI of the {@code collection} module for the settlement engine:
 * read CONFIRMED, not-yet-settled collections in a period using the amounts
 * recorded at collection time, and link/unlink them to a settlement item.
 */
public interface CollectionSettlementGateway {

    List<SettlementCollectionRow> unsettledConfirmed(LocalDate periodStart, LocalDate periodEnd);

    /** Collections in the period still recorded with litres only (FAT / SNF not yet entered). */
    int pendingQualityCount(LocalDate periodStart, LocalDate periodEnd);

    void attachToSettlementItem(Collection<Long> collectionIds, long settlementItemId);

    void detachFromSettlement(long settlementItemId);

    /** Every collection that fed a settlement line — the daily breakdown behind a producer payment. */
    List<SettlementItemCollection> collectionsForSettlementItem(long settlementItemId);

    record SettlementItemCollection(
            long collectionId,
            String collectionNumber,
            LocalDate collectionDate,
            String shift,
            BigDecimal litres,
            BigDecimal fatPercentage,
            BigDecimal snfPercentage,
            BigDecimal applicableRate,
            BigDecimal baseAmount,
            BigDecimal incentiveAmount) {
    }

    /**
     * @param baseAmount      milk_collections.base_amount (rate snapshot as collected)
     * @param incentiveAmount milk_collections.incentive_amount
     */
    record SettlementCollectionRow(
            long collectionId,
            long producerId,
            Long regionId,
            String shift,
            BigDecimal litres,
            BigDecimal baseAmount,
            BigDecimal incentiveAmount) {
    }
}
