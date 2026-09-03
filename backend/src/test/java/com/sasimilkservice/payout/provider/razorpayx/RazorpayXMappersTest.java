package com.sasimilkservice.payout.provider.razorpayx;

import com.sasimilkservice.payout.provider.PayoutProvider.ProviderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RazorpayXMappersTest {

    @Test
    void status_mapping_covers_the_razorpay_lifecycle() {
        assertThat(RazorpayXStatusMapper.map("queued")).isEqualTo(ProviderStatus.PENDING);
        assertThat(RazorpayXStatusMapper.map("pending")).isEqualTo(ProviderStatus.PENDING);
        assertThat(RazorpayXStatusMapper.map("scheduled")).isEqualTo(ProviderStatus.PENDING);
        assertThat(RazorpayXStatusMapper.map("processing")).isEqualTo(ProviderStatus.PROCESSING);
        assertThat(RazorpayXStatusMapper.map("processed")).isEqualTo(ProviderStatus.SUCCESS);
        assertThat(RazorpayXStatusMapper.map("reversed")).isEqualTo(ProviderStatus.REVERSED);
        assertThat(RazorpayXStatusMapper.map("failed")).isEqualTo(ProviderStatus.FAILED);
        assertThat(RazorpayXStatusMapper.map("cancelled")).isEqualTo(ProviderStatus.FAILED);
        assertThat(RazorpayXStatusMapper.map("rejected")).isEqualTo(ProviderStatus.FAILED);
    }

    @Test
    void unknown_status_is_never_optimistically_success() {
        assertThat(RazorpayXStatusMapper.map("something_new")).isEqualTo(ProviderStatus.PROCESSING);
        assertThat(RazorpayXStatusMapper.map(null)).isEqualTo(ProviderStatus.PENDING);
    }

    @Test
    void error_mapper_flags_transient_errors_as_retryable() {
        assertThat(RazorpayXErrorMapper.map(500, "SERVER_ERROR", "boom").retryable()).isTrue();
        assertThat(RazorpayXErrorMapper.map(502, "GATEWAY_ERROR", "bad gateway").retryable()).isTrue();
        assertThat(RazorpayXErrorMapper.map(429, "BAD_REQUEST_RATE_LIMIT_EXCEEDED", "slow down").retryable()).isTrue();
    }

    @Test
    void error_mapper_flags_permanent_errors_as_not_retryable_and_classifies_them() {
        var ifsc = RazorpayXErrorMapper.map(400, "BAD_REQUEST_ERROR", "The ifsc provided is invalid");
        assertThat(ifsc.retryable()).isFalse();
        assertThat(ifsc.failureCode()).isEqualTo("INVALID_IFSC");

        var acct = RazorpayXErrorMapper.map(400, "BAD_REQUEST_ERROR", "account number is invalid");
        assertThat(acct.failureCode()).isEqualTo("INVALID_ACCOUNT_NUMBER");

        var low = RazorpayXErrorMapper.map(400, "BAD_REQUEST_ERROR", "Your account has insufficient balance");
        assertThat(low.failureCode()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    void exception_carries_the_retryable_flag() {
        assertThat(new RazorpayXException(503, "SERVER_ERROR", "down").retryable()).isTrue();
        assertThat(new RazorpayXException(400, "BAD_REQUEST_ERROR", "bad account").retryable()).isFalse();
        assertThat(new RazorpayXException("timeout", new RuntimeException()).retryable()).isTrue();
    }
}
