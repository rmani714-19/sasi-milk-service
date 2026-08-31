package com.sasimilkservice.producer.spi;

import java.util.Optional;

/** Published SPI of the {@code producer} module for the payout flow. */
public interface BankAccountResolver {

    /** @return the id of the producer's active primary bank account, if any. */
    Optional<Long> primaryActiveAccountId(Long producerId);
}
