package com.sasimilkservice.center.spi;

import java.util.Optional;

/**
 * Published SPI of the {@code center} module: lets the payment modules resolve a
 * centre bank account the operator selected when recording a bank-balance check,
 * without depending on the account's JPA entity or seeing the account number.
 */
public interface BusinessBankAccountDirectory {

    /** A verified, active account the operator may pay from. */
    record UsableAccount(long id, String label) {
    }

    /** The account for {@code id} if it exists and is ACTIVE + verified; empty otherwise. */
    Optional<UsableAccount> usable(long id);
}
