package com.sasimilkservice.advance.spi;

import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AccountRecovery;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AdvanceAccountState;

import java.time.LocalDate;
import java.util.List;

/** Published SPI of the {@code advance} module for the settlement engine. */
public interface AdvanceLedger {

    /** Active advance accounts for the producer, oldest first — input to the recovery calculator. */
    List<AdvanceAccountState> activeAccountStates(long producerId);

    /**
     * Post the computed recoveries: one RECOVERY ledger row per account, decrement
     * outstanding, close accounts that reach zero, link rows to the settlement item.
     */
    void postRecoveries(long producerId, long settlementItemId, List<AccountRecovery> recoveries, LocalDate date);

    /** Reverse a settlement item's recoveries: REVERSAL rows, restore outstanding, reopen closed accounts. */
    void reverseRecoveries(long settlementItemId);
}
