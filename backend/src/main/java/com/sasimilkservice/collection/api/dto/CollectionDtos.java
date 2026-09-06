package com.sasimilkservice.collection.api.dto;

import com.sasimilkservice.collection.domain.MilkCollection;
import com.sasimilkservice.collection.service.CollectionService.RecordCollectionCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Milk-collection API payloads. */
public final class CollectionDtos {

    private CollectionDtos() {}

    public enum Shift { MORNING, EVENING }

    public record CreateCollectionRequest(
            UUID clientTransactionId,
            @NotBlank String producerReference,
            @NotNull @PastOrPresent LocalDate collectionDate,
            @NotNull Shift shift,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal litres,
            @DecimalMin("0.0") BigDecimal fat,
            @DecimalMin("0.0") BigDecimal snf,
            MilkCollection.Source source,
            String deviceId) {

        public RecordCollectionCommand toCommand() {
            return new RecordCollectionCommand(clientTransactionId, producerReference, collectionDate,
                    shift.name(), litres, fat, snf, source, deviceId);
        }
    }

    public record VoidCollectionRequest(@NotBlank String reason) {
    }

    /** Adds FAT / SNF to a collection recorded with litres only. */
    public record UpdateQualityRequest(
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal fat,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal snf) {
    }

    public record BreakdownResponse(
            BigDecimal tsValue,
            String rateScope,
            BigDecimal ratePerLitre,
            BigDecimal incentiveRatePerLitre,
            BigDecimal baseAmount,
            BigDecimal incentiveAmount,
            BigDecimal deductionAmount,
            BigDecimal netAmount) {

        public static BreakdownResponse from(MilkCollection c) {
            return new BreakdownResponse(c.getTsValueApplied(), c.getRateScopeApplied(),
                    c.getApplicableRate(), c.getIncentiveRateApplied(), c.getBaseAmount(),
                    c.getIncentiveAmount(), c.getDeductionAmount(), c.getNetAmount());
        }
    }

    public record CollectionResponse(
            String id,
            String collectionNumber,
            Long producerId,
            String producerNumber,
            String producerName,
            LocalDate collectionDate,
            String shift,
            BigDecimal litres,
            BigDecimal fat,
            BigDecimal snf,
            String status,
            boolean qualityPending,
            BreakdownResponse breakdown,
            boolean duplicate) {

        public static CollectionResponse from(MilkCollection c, String producerNumber,
                                              String producerName, boolean duplicate) {
            return new CollectionResponse(c.getPublicId().toString(), c.getCollectionNumber(),
                    c.getProducerId(), producerNumber, producerName,
                    c.getCollectionDate(), c.getShift(), c.getMilkQuantityLitres(),
                    c.getFatPercentage(), c.getSnfPercentage(), c.getStatus().name(),
                    c.isQualityPending(), BreakdownResponse.from(c), duplicate);
        }
    }
}
