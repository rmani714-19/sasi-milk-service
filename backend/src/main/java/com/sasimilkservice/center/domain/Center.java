package com.sasimilkservice.center.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single-row center configuration. Holds the business rules that the client may
 * change without a code release (rounding, incentive cap/stacking, FAT/SNF
 * bounds, missing-rate policy).
 */
@Entity
@Table(name = "center")
@Getter
@Setter
public class Center extends AuditableEntity {

    @Column(name = "center_name", nullable = false)
    private String centerName;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "mobile_number")
    private String mobileNumber;

    private String email;

    private String address;

    @Column(name = "logo_object_key")
    private String logoObjectKey;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode = "INR";

    @Column(nullable = false)
    private String timezone = "Asia/Kolkata";

    @Column(name = "settlement_day_of_week", nullable = false)
    private int settlementDayOfWeek = 1;

    @Column(name = "rounding_mode", nullable = false)
    private String roundingMode = "HALF_EVEN";

    @Column(name = "rounding_scale", nullable = false)
    private int roundingScale = 0;

    @Column(name = "incentive_stacking", nullable = false)
    private String incentiveStacking = "ADD";

    @Column(name = "incentive_max_per_litre", nullable = false)
    private BigDecimal incentiveMaxPerLitre = new BigDecimal("2.0000");

    @Column(name = "allow_missing_rate", nullable = false)
    private boolean allowMissingRate;

    @Column(name = "fat_min", nullable = false)
    private BigDecimal fatMin;

    @Column(name = "fat_max", nullable = false)
    private BigDecimal fatMax;

    @Column(name = "snf_min", nullable = false)
    private BigDecimal snfMin;

    @Column(name = "snf_max", nullable = false)
    private BigDecimal snfMax;

    // ---- the centre's own payout bank account (reused by bank-balance verification) ----

    @Column(name = "payout_account_holder_name")
    private String payoutAccountHolderName;

    /** AES-GCM encrypted — never returned to clients. */
    @Column(name = "payout_account_number_enc")
    private String payoutAccountNumberEnc;

    @Column(name = "payout_account_number_last4")
    private String payoutAccountNumberLast4;

    @Column(name = "payout_account_number_hash")
    private String payoutAccountNumberHash;

    @Column(name = "payout_ifsc")
    private String payoutIfsc;

    @Column(name = "payout_bank_name")
    private String payoutBankName;

    @Column(name = "payout_account_updated_at")
    private Instant payoutAccountUpdatedAt;

    @Column(name = "payout_account_updated_by")
    private Long payoutAccountUpdatedBy;

    public boolean isPayoutAccountComplete() {
        return notBlank(payoutAccountHolderName) && notBlank(payoutAccountNumberEnc)
                && notBlank(payoutIfsc) && notBlank(payoutBankName);
    }

    public String maskedPayoutAccount() {
        return payoutAccountNumberLast4 == null || payoutAccountNumberLast4.isBlank()
                ? null : "********" + payoutAccountNumberLast4;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
