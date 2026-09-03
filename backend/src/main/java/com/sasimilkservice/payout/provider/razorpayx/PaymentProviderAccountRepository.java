package com.sasimilkservice.payout.provider.razorpayx;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentProviderAccountRepository extends JpaRepository<PaymentProviderAccount, Long> {

    Optional<PaymentProviderAccount> findByProviderAndAccountNumberHashAndIfsc(
            String provider, String accountNumberHash, String ifsc);
}
