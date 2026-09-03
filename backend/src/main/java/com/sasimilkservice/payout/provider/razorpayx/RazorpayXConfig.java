package com.sasimilkservice.payout.provider.razorpayx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sasimilkservice.common.crypto.FieldCrypto;
import com.sasimilkservice.payout.provider.PayoutProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Registers the RazorpayX {@link PayoutProvider} only when
 * {@code sasi.payout.razorpayx.enabled=true}. When absent, the MANUAL provider
 * from {@code PayoutProviderConfig} is used and nothing here loads — so a missing
 * key never breaks startup.
 */
@Configuration
@ConditionalOnProperty(prefix = "sasi.payout.razorpayx", name = "enabled", havingValue = "true")
public class RazorpayXConfig {

    @Bean
    RazorpayXClient razorpayXClient(RestClient.Builder builder, ObjectMapper mapper, RazorpayXProperties props) {
        return new RazorpayXClient(builder, mapper, props);
    }

    @Bean
    PayoutProvider razorpayXPayoutProvider(RazorpayXClient client,
                                           PaymentProviderAccountRepository providerAccounts,
                                           FieldCrypto crypto,
                                           ObjectMapper mapper,
                                           RazorpayXProperties props) {
        return new RazorpayXPayoutProvider(client, providerAccounts, crypto, mapper, props);
    }
}
