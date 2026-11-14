package com.sasimilkservice.collection.spi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Published SPI of the {@code collection} module: milk collected in a set of regions (a driver's route). */
public interface RouteCollectionQuery {

    /** Litres per day, region and shift for valid (non-void, non-draft) collections. */
    List<RouteDay> litresByDay(Collection<Long> regionIds, LocalDate from, LocalDate to);

    record RouteDay(LocalDate date, long regionId, String shift, BigDecimal litres, int collections) {
    }
}
