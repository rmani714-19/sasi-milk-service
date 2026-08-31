package com.sasimilkservice.center.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

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
}
