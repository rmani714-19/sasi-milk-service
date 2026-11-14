package com.sasimilkservice.settlement.bill;

import com.sasimilkservice.center.spi.CenterProfileDirectory;
import com.sasimilkservice.collection.spi.CollectionSettlementGateway;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.producer.spi.ProducerDirectory;
import com.sasimilkservice.producer.spi.ProducerDirectory.ProducerView;
import com.sasimilkservice.region.spi.RegionDirectory;
import com.sasimilkservice.settlement.domain.Settlement;
import com.sasimilkservice.settlement.domain.SettlementAdjustment;
import com.sasimilkservice.settlement.domain.SettlementItem;
import com.sasimilkservice.settlement.repository.SettlementAdjustmentRepository;
import com.sasimilkservice.settlement.service.SettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles producer payment bills from a settlement's stored lines and renders them as PDF.
 * Read-only: nothing is recalculated and no status changes — numbering happens in
 * {@link SettlementService#generateBills(long)}.
 */
@Service
@Transactional(readOnly = true)
public class SettlementBillService {

    private final SettlementService settlements;
    private final SettlementAdjustmentRepository adjustments;
    private final CollectionSettlementGateway collections;
    private final ProducerDirectory producers;
    private final RegionDirectory regions;
    private final CenterProfileDirectory center;
    private final BillPdfRenderer renderer;

    public SettlementBillService(SettlementService settlements, SettlementAdjustmentRepository adjustments,
                                 CollectionSettlementGateway collections, ProducerDirectory producers,
                                 RegionDirectory regions,
                                 CenterProfileDirectory center, BillPdfRenderer renderer) {
        this.settlements = settlements;
        this.adjustments = adjustments;
        this.collections = collections;
        this.producers = producers;
        this.regions = regions;
        this.center = center;
        this.renderer = renderer;
    }

    /** One producer's bill. A preview (no bill number) is allowed before bills are generated. */
    public byte[] billPdf(long settlementId, long itemId) {
        Settlement s = settlements.require(settlementId);
        SettlementItem item = settlements.itemsOf(settlementId).stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("SettlementItem", itemId));
        return renderer.render(List.of(assemble(s, item)));
    }

    /** Every producer's bill in one PDF, one per page. Requires bills to have been generated. */
    public byte[] allBillsPdf(long settlementId) {
        Settlement s = settlements.require(settlementId);
        List<SettlementItem> lines = settlements.itemsOf(settlementId);
        if (lines.isEmpty() || lines.stream().anyMatch(i -> i.getBillNumber() == null)) {
            throw new BusinessRuleException("Generate the bills first, then download them together");
        }
        List<BillData> bills = new ArrayList<>();
        for (SettlementItem item : lines) {
            bills.add(assemble(s, item));
        }
        return renderer.render(bills);
    }

    /** File name used for a download of one producer's bill. */
    public String billFileName(long settlementId, long itemId) {
        return settlements.itemsOf(settlementId).stream()
                .filter(i -> i.getId().equals(itemId))
                .map(i -> i.getBillNumber() != null ? i.getBillNumber() : "bill-preview-" + i.getId())
                .findFirst()
                .orElse("bill");
    }

    private BillData assemble(Settlement s, SettlementItem item) {
        CenterProfileDirectory.CenterProfile c = center.profile();
        ProducerView producer = producers.findById(item.getProducerId()).orElse(null);
        String region = item.getRegionId() == null ? null
                : regions.findById(item.getRegionId()).map(RegionDirectory.RegionView::name).orElse(null);

        List<BillData.Line> lines = collections.collectionsForSettlementItem(item.getId()).stream()
                .map(r -> new BillData.Line(r.collectionDate(), r.shift(), r.litres(), r.fatPercentage(),
                        r.snfPercentage(), r.applicableRate(), r.baseAmount(), r.incentiveAmount()))
                .toList();

        List<BillData.AdjustmentLine> adj = adjustments
                .findBySettlementIdAndStatus(s.getId(), SettlementAdjustment.Status.ACTIVE).stream()
                .filter(a -> item.getId().equals(a.getSettlementItemId())
                        || (a.getSettlementItemId() == null && item.getProducerId().equals(a.getProducerId())))
                .map(a -> new BillData.AdjustmentLine(a.getAdjustmentType().name(), a.getAmount(), a.getReason()))
                .toList();

        String title = switch (s.getPaymentFrequency()) {
            case WEEKLY -> "Weekly Milk Payment Bill";
            case MONTHLY -> "Monthly Milk Payment Bill";
            default -> "Milk Payment Bill";
        };
        return new BillData(title, c.name(), c.address(), c.mobileNumber(),
                item.getBillNumber(), s.getSettlementNumber(), s.getPeriodStart(), s.getPeriodEnd(),
                item.getBillGeneratedAt() == null ? LocalDate.now()
                        : item.getBillGeneratedAt().atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate(),
                producer == null ? String.valueOf(item.getProducerId()) : producer.producerNumber(),
                producer == null ? "Producer #" + item.getProducerId() : producer.name(),
                region,
                lines, item.getTotalLitres(), item.getMilkAmount(), item.getIncentiveAmount(),
                item.getFeedDeduction(), item.getVeterinaryDeduction(), item.getOtherDeduction(),
                item.getAdvanceRecovery(), item.getAdjustmentAmount(), adj,
                item.getRoundOff(), item.getNetPayable(),
                friendlyStatus(item.getPaymentStatus()), item.getPaymentReference());
    }

    private static String friendlyStatus(SettlementItem.PaymentStatus st) {
        return switch (st) {
            case PENDING -> "Pending";
            case INITIATED, PROCESSING -> "Submitted to bank";
            case SUCCESS -> "Paid";
            case FAILED -> "Failed";
            case ON_HOLD -> "On hold";
            case CANCELLED -> "Cancelled";
        };
    }
}
