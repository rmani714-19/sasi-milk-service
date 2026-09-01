package com.sasimilkservice.settlement.api.dto;

import com.sasimilkservice.settlement.domain.Settlement;
import com.sasimilkservice.settlement.domain.SettlementAdjustment;
import com.sasimilkservice.settlement.domain.SettlementItem;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Settlement API payloads. */
public final class SettlementDtos {

    private SettlementDtos() {}

    public record CreateSettlementRequest(
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd) {
    }

    public record AddAdjustmentRequest(
            @Positive long producerId,
            @NotNull SettlementAdjustment.Type type,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
            @NotBlank String reason,
            Long originSettlementId) {
    }

    public record SettlementSummary(
            String id,
            long internalId,
            String settlementNumber,
            LocalDate periodStart,
            LocalDate periodEnd,
            String status,
            int totalProducers,
            BigDecimal totalLitres,
            BigDecimal totalNetPayable) {

        public static SettlementSummary from(Settlement s) {
            return new SettlementSummary(s.getPublicId().toString(), s.getId(), s.getSettlementNumber(),
                    s.getPeriodStart(), s.getPeriodEnd(), s.getStatus().name(),
                    s.getTotalProducers(), s.getTotalLitres(), s.getTotalNetPayable());
        }
    }

    public record SettlementDetail(
            String id,
            long internalId,
            String settlementNumber,
            LocalDate periodStart,
            LocalDate periodEnd,
            String status,
            String roundingMode,
            int roundingScale,
            int totalProducers,
            BigDecimal totalLitres,
            BigDecimal totalMilkAmount,
            BigDecimal totalIncentive,
            BigDecimal totalDeduction,
            BigDecimal totalAdvanceRecovery,
            BigDecimal totalAdjustment,
            BigDecimal totalRoundOff,
            BigDecimal totalNetPayable,
            List<ItemResponse> items) {

        public static SettlementDetail from(Settlement s, List<SettlementItem> items) {
            return new SettlementDetail(s.getPublicId().toString(), s.getId(), s.getSettlementNumber(),
                    s.getPeriodStart(), s.getPeriodEnd(), s.getStatus().name(),
                    s.getRoundingMode(), s.getRoundingScale(), s.getTotalProducers(),
                    s.getTotalLitres(), s.getTotalMilkAmount(), s.getTotalIncentive(),
                    s.getTotalDeduction(), s.getTotalAdvanceRecovery(), s.getTotalAdjustment(),
                    s.getTotalRoundOff(), s.getTotalNetPayable(),
                    items.stream().map(ItemResponse::from).toList());
        }
    }

    public record ItemResponse(
            Long id,
            Long producerId,
            Long regionId,
            BigDecimal morningLitres,
            BigDecimal eveningLitres,
            BigDecimal totalLitres,
            int collectionCount,
            BigDecimal averageRate,
            BigDecimal milkAmount,
            BigDecimal incentiveAmount,
            BigDecimal grossAmount,
            BigDecimal feedDeduction,
            BigDecimal veterinaryDeduction,
            BigDecimal otherDeduction,
            BigDecimal advanceRecovery,
            BigDecimal totalDeduction,
            BigDecimal adjustmentAmount,
            BigDecimal netBeforeRounding,
            BigDecimal roundOff,
            BigDecimal netPayable,
            String paymentStatus) {

        static ItemResponse from(SettlementItem i) {
            return new ItemResponse(i.getId(), i.getProducerId(), i.getRegionId(),
                    i.getMorningLitres(), i.getEveningLitres(), i.getTotalLitres(),
                    i.getCollectionCount(), i.getAverageRate(), i.getMilkAmount(),
                    i.getIncentiveAmount(), i.getGrossAmount(), i.getFeedDeduction(),
                    i.getVeterinaryDeduction(), i.getOtherDeduction(), i.getAdvanceRecovery(),
                    i.getTotalDeduction(), i.getAdjustmentAmount(), i.getNetBeforeRounding(),
                    i.getRoundOff(), i.getNetPayable(), i.getPaymentStatus().name());
        }
    }
}
