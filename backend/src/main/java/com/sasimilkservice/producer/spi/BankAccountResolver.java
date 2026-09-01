package com.sasimilkservice.producer.spi;

import java.util.Optional;

/** Published SPI of the {@code producer} module for the payout flow. */
public interface BankAccountResolver {

    /** @return the id of the producer's active primary bank account, if any. */
    Optional<Long> primaryActiveAccountId(Long producerId);

    /**
     * Full bank details for initiating a payout. The account number is decrypted
     * here — call only from the payout module, server-side.
     */
    Optional<PayoutBankAccount> payoutDetails(Long bankAccountId);

    record PayoutBankAccount(
            Long bankAccountId,
            String accountHolderName,
            String accountNumber,
            String ifsc,
            String bankName) {
    }
}
