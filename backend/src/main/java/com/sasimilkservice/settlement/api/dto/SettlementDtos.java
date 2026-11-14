package com.sasimilkservice.settlement.api.dto;

import com.sasimilkservice.collection.spi.CollectionSettlementGateway.SettlementItemCollection;
import com.sasimilkservice.producer.spi.ProducerDirectory.ProducerView;
import com.sasimilkservice.settlement.domain.Settlement;
import com.sasimilkservice.settlement.domain.SettlementAdjustment;
import com.sasimilkservice.settlement.domain.SettlementBankSubmission;
import com.sasimilkservice.settlement.domain.SettlementItem;
import com.sasimilkservice.settlement.domain.SettlementStatusHistory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Settlement API payloads. */
public final class SettlementDtos {

    private SettlementDtos() {}

    public record CreateSettlementRequest(
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @Pattern(regexp = "^(WEEKLY|MONTHLY|ALL)$",
                    message = "paymentFrequency must be WEEKLY, MONTHLY or ALL") String paymentFrequency,
            /** Optional: pay only this region/area. Omit for every region. */
            Long regionId) {

        public Settlement.PaymentFrequency frequency() {
            return paymentFrequency == null || paymentFrequency.isBlank()
                    ? Settlement.PaymentFrequency.ALL
                    : Settlement.PaymentFrequency.valueOf(paymentFrequency.trim().toUpperCase());
        }
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
            String paymentFrequency,
            LocalDate periodStart,
            LocalDate periodEnd,
            String status,
            int totalProducers,
            BigDecimal totalLitres,
            BigDecimal totalNetPayable,
            Long regionId,
            String specStatus) {

        public static SettlementSummary from(Settlement s) {
            return new SettlementSummary(s.getPublicId().toString(), s.getId(), s.getSettlementNumber(),
                    s.getPaymentFrequency().name(), s.getPeriodStart(), s.getPeriodEnd(), s.getStatus().name(),
                    s.getTotalProducers(), s.getTotalLitres(), s.getTotalNetPayable(), s.getRegionId(),
                    SpecStatus.batch(s.getStatus()));
        }
    }

    public record SettlementDetail(
            String id,
            long internalId,
            String settlementNumber,
            String paymentFrequency,
            LocalDate periodStart,
            LocalDate periodEnd,
            String status,
            String roundingMode,
            int roundingScale,
            int totalProducers,
            int verifiedCount,
            int pendingVerificationCount,
            BigDecimal totalLitres,
            BigDecimal totalMilkAmount,
            BigDecimal totalIncentive,
            BigDecimal totalDeduction,
            BigDecimal totalAdvanceRecovery,
            BigDecimal totalAdjustment,
            BigDecimal totalRoundOff,
            BigDecimal totalNetPayable,
            int billsGenerated,
            BankSubmissionResponse bankSubmission,
            Long regionId,
            String specStatus,
            List<ItemResponse> items) {

        public static SettlementDetail from(Settlement s, List<SettlementItem> items) {
            return from(s, items, Map.of(), null);
        }

        public static SettlementDetail from(Settlement s, List<SettlementItem> items,
                                            Map<Long, ProducerView> producers,
                                            SettlementBankSubmission submission) {
            int verified = (int) items.stream()
                    .filter(i -> i.getVerificationStatus() == SettlementItem.VerificationStatus.VERIFIED)
                    .count();
            return new SettlementDetail(s.getPublicId().toString(), s.getId(), s.getSettlementNumber(),
                    s.getPaymentFrequency().name(), s.getPeriodStart(), s.getPeriodEnd(), s.getStatus().name(),
                    s.getRoundingMode(), s.getRoundingScale(), s.getTotalProducers(),
                    verified, items.size() - verified,
                    s.getTotalLitres(), s.getTotalMilkAmount(), s.getTotalIncentive(),
                    s.getTotalDeduction(), s.getTotalAdvanceRecovery(), s.getTotalAdjustment(),
                    s.getTotalRoundOff(), s.getTotalNetPayable(),
                    (int) items.stream().filter(i -> i.getBillNumber() != null).count(),
                    submission == null ? null : BankSubmissionResponse.from(submission),
                    s.getRegionId(), SpecStatus.batch(s.getStatus()),
                    items.stream().map(i -> ItemResponse.from(i, producers.get(i.getProducerId()), s.getStatus())).toList());
        }
    }

    public record ItemResponse(
            Long id,
            Long producerId,
            String producerNumber,
            String producerName,
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
            String paymentStatus,
            String verificationStatus,
            BigDecimal verifiedAmount,
            Instant verifiedAt,
            String billNumber,
            String paymentReference,
            String specStatus) {

        static ItemResponse from(SettlementItem i) {
            return from(i, null, null);
        }

        static ItemResponse from(SettlementItem i, ProducerView p, Settlement.Status batch) {
            return new ItemResponse(i.getId(), i.getProducerId(),
                    p == null ? null : p.producerNumber(), p == null ? null : p.name(),
                    i.getRegionId(),
                    i.getMorningLitres(), i.getEveningLitres(), i.getTotalLitres(),
                    i.getCollectionCount(), i.getAverageRate(), i.getMilkAmount(),
                    i.getIncentiveAmount(), i.getGrossAmount(), i.getFeedDeduction(),
                    i.getVeterinaryDeduction(), i.getOtherDeduction(), i.getAdvanceRecovery(),
                    i.getTotalDeduction(), i.getAdjustmentAmount(), i.getNetBeforeRounding(),
                    i.getRoundOff(), i.getNetPayable(), i.getPaymentStatus().name(),
                    i.getVerificationStatus().name(), i.getVerifiedAmount(), i.getVerifiedAt(),
                    i.getBillNumber(), i.getPaymentReference(),
                    SpecStatus.line(i.getPaymentStatus(), batch));
        }
    }

    public record BankSubmissionResponse(
            LocalDate submittedOn, String bankReference, String note, int producerCount,
            BigDecimal totalAmount, Long submittedBy, Instant createdAt) {

        public static BankSubmissionResponse from(SettlementBankSubmission b) {
            return new BankSubmissionResponse(b.getSubmittedOn(), b.getBankReference(), b.getNote(),
                    b.getProducerCount(), b.getTotalAmount(), b.getSubmittedBy(), b.getCreatedAt());
        }
    }

    public record StatusHistoryResponse(
            Long id, String subjectType, Long subjectId, String fromStatus, String toStatus,
            String note, Long changedBy, Instant changedAt) {

        public static StatusHistoryResponse from(SettlementStatusHistory h) {
            return new StatusHistoryResponse(h.getId(), h.getSubjectType().name(), h.getSubjectId(),
                    h.getFromStatus(), h.getToStatus(), h.getNote(), h.getChangedBy(), h.getChangedAt());
        }
    }

    /** One collection behind a producer payment — the daily breakdown. */
    public record DailyCollectionRow(
            long collectionId,
            String collectionNumber,
            LocalDate collectionDate,
            String shift,
            BigDecimal litres,
            BigDecimal fatPercentage,
            BigDecimal snfPercentage,
            BigDecimal ratePerLitre,
            BigDecimal milkAmount) {

        public static DailyCollectionRow from(SettlementItemCollection c) {
            return new DailyCollectionRow(c.collectionId(), c.collectionNumber(), c.collectionDate(),
                    c.shift(), c.litres(), c.fatPercentage(), c.snfPercentage(), c.applicableRate(),
                    c.baseAmount().add(c.incentiveAmount()));
        }
    }
}
