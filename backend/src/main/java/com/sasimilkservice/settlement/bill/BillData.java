package com.sasimilkservice.settlement.bill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything printed on one producer's payment bill. Built from verified settlement data only. */
public record BillData(
        String title,
        String centerName,
        String centerAddress,
        String centerMobile,
        /** Null until bills are generated — the PDF then shows a preview notice. */
        String billNumber,
        String batchNumber,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate generatedOn,
        String producerNumber,
        String producerName,
        String region,
        List<Line> lines,
        BigDecimal totalLitres,
        BigDecimal milkAmount,
        BigDecimal incentive,
        BigDecimal feedDeduction,
        BigDecimal veterinaryDeduction,
        BigDecimal otherDeduction,
        BigDecimal advanceRecovery,
        BigDecimal adjustments,
        List<AdjustmentLine> adjustmentLines,
        BigDecimal roundOff,
        BigDecimal netPayable,
        String preparationStatus,
        String bankReference) {

    public record Line(LocalDate date, String shift, BigDecimal litres, BigDecimal fat, BigDecimal snf,
                       BigDecimal rate, BigDecimal milkAmount, BigDecimal incentive) {
    }

    public record AdjustmentLine(String type, BigDecimal amount, String reason) {
    }
}
