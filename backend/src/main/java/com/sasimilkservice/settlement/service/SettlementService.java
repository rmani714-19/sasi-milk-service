package com.sasimilkservice.settlement.service;

import com.sasimilkservice.advance.spi.AdvanceLedger;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AdvanceRecoveryInput;
import com.sasimilkservice.advance.spi.AdvanceRecoveryCalculator.AdvanceRecoveryResult;
import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.center.service.CenterService;
import com.sasimilkservice.center.spi.CenterSettings;
import com.sasimilkservice.collection.spi.CollectionSettlementGateway;
import com.sasimilkservice.collection.spi.CollectionSettlementGateway.SettlementCollectionRow;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.money.MoneyMath;
import com.sasimilkservice.common.money.RoundingPolicy;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.deduction.spi.DeductionCalculator.DeductionResult;
import com.sasimilkservice.deduction.spi.DeductionLedger;
import com.sasimilkservice.deduction.spi.DeductionLedger.PendingDeductions;
import com.sasimilkservice.producer.spi.BankAccountResolver;
import com.sasimilkservice.settlement.domain.Settlement;
import com.sasimilkservice.settlement.domain.SettlementAdjustment;
import com.sasimilkservice.settlement.domain.SettlementItem;
import com.sasimilkservice.settlement.repository.SettlementAdjustmentRepository;
import com.sasimilkservice.settlement.repository.SettlementItemRepository;
import com.sasimilkservice.settlement.repository.SettlementRepository;
import com.sasimilkservice.settlement.spi.SettlementPayoutGateway;
import com.sasimilkservice.settlement.service.SettlementCalculator.CollectionContribution;
import com.sasimilkservice.settlement.service.SettlementCalculator.SettlementLineInput;
import com.sasimilkservice.settlement.service.SettlementCalculator.SettlementLineResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Weekly-settlement orchestration. Loads collections, resolves deductions and
 * advance recovery through their module SPIs, calls the pure
 * {@link SettlementCalculator}, persists the run, links source records, and
 * drives the lifecycle DRAFT → CALCULATED → REVIEWED → APPROVED.
 */
@Service
@Transactional(readOnly = true)
public class SettlementService implements SettlementPayoutGateway {

    private final SettlementRepository settlements;
    private final SettlementItemRepository items;
    private final SettlementAdjustmentRepository adjustments;
    private final SettlementNumberGenerator numberGenerator;
    private final SettlementCalculator calculator;
    private final CollectionSettlementGateway collections;
    private final DeductionLedger deductions;
    private final AdvanceLedger advances;
    private final AdvanceRecoveryCalculator advanceRecoveryCalculator;
    private final CenterService centerService;
    private final BankAccountResolver bankAccounts;
    private final AuditRecorder audit;

    public SettlementService(SettlementRepository settlements,
                             SettlementItemRepository items,
                             SettlementAdjustmentRepository adjustments,
                             SettlementNumberGenerator numberGenerator,
                             SettlementCalculator calculator,
                             CollectionSettlementGateway collections,
                             DeductionLedger deductions,
                             AdvanceLedger advances,
                             AdvanceRecoveryCalculator advanceRecoveryCalculator,
                             CenterService centerService,
                             BankAccountResolver bankAccounts,
                             AuditRecorder audit) {
        this.settlements = settlements;
        this.items = items;
        this.adjustments = adjustments;
        this.numberGenerator = numberGenerator;
        this.calculator = calculator;
        this.collections = collections;
        this.deductions = deductions;
        this.advances = advances;
        this.advanceRecoveryCalculator = advanceRecoveryCalculator;
        this.centerService = centerService;
        this.bankAccounts = bankAccounts;
        this.audit = audit;
    }

    public List<Settlement> findAll() {
        return settlements.findAllByOrderByPeriodStartDesc();
    }

    public Settlement require(long id) {
        return settlements.findById(id).orElseThrow(() -> NotFoundException.of("Settlement", id));
    }

    public Settlement requireByPublicId(UUID publicId) {
        return settlements.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Settlement " + publicId + " not found"));
    }

    public List<SettlementItem> itemsOf(long settlementId) {
        return items.findBySettlementIdOrderByProducerIdAsc(settlementId);
    }

    @Transactional
    public Settlement create(LocalDate periodStart, LocalDate periodEnd) {
        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessRuleException("periodEnd must not be before periodStart");
        }
        Settlement s = new Settlement();
        s.setSettlementNumber(numberGenerator.next(periodStart));
        s.setPeriodStart(periodStart);
        s.setPeriodEnd(periodEnd);
        applyRoundingSnapshot(s, centerService.settings().roundingPolicy());
        // DB constraint ex_settlement_no_overlap rejects overlapping non-cancelled periods
        s = settlements.save(s);
        audit.record("SETTLEMENT_CREATED", "Settlement", s.getId(),
                "Settlement %s for %s..%s".formatted(s.getSettlementNumber(), periodStart, periodEnd));
        return s;
    }

    @Transactional
    public Settlement calculate(long id) {
        Settlement s = require(id);
        if (s.getStatus() == Settlement.Status.APPROVED
                || s.getStatus().ordinal() >= Settlement.Status.PAYOUT_INITIATED.ordinal()) {
            throw new BusinessRuleException("Settlement is " + s.getStatus() + " and can no longer be recalculated");
        }
        if (s.getStatus() != Settlement.Status.DRAFT) {
            unwind(s); // recalculate: discard prior items and their links
        }

        CenterSettings settings = centerService.settings();
        RoundingPolicy policy = settings.roundingPolicy();
        applyRoundingSnapshot(s, policy);
        RoundingRule rule = RoundingRule.fromPolicy(policy);

        Map<Long, List<SettlementCollectionRow>> byProducer = collections
                .unsettledConfirmed(s.getPeriodStart(), s.getPeriodEnd()).stream()
                .collect(Collectors.groupingBy(SettlementCollectionRow::producerId, LinkedHashMap::new,
                        Collectors.toList()));

        Map<Long, List<SettlementAdjustment>> adjByProducer = adjustments
                .findBySettlementIdAndStatus(s.getId(), SettlementAdjustment.Status.ACTIVE).stream()
                .collect(Collectors.groupingBy(SettlementAdjustment::getProducerId));

        BigDecimal tLitres = BigDecimal.ZERO, tMilk = BigDecimal.ZERO, tInc = BigDecimal.ZERO;
        BigDecimal tDed = BigDecimal.ZERO, tAdv = BigDecimal.ZERO, tAdj = BigDecimal.ZERO;
        BigDecimal tRound = BigDecimal.ZERO, tNet = BigDecimal.ZERO;
        int producerCount = 0;

        for (var entry : byProducer.entrySet()) {
            long producerId = entry.getKey();
            List<SettlementCollectionRow> rows = entry.getValue();

            List<CollectionContribution> contributions = rows.stream()
                    .map(r -> new CollectionContribution(r.collectionId(), r.shift(), r.litres(),
                            r.baseAmount(), r.incentiveAmount()))
                    .toList();
            Long regionId = rows.get(0).regionId();

            PendingDeductions pending = deductions.pendingFor(producerId, s.getPeriodEnd());
            DeductionResult deductionResult = pending.result();

            List<SettlementAdjustment> producerAdjustments =
                    adjByProducer.getOrDefault(producerId, List.of());
            BigDecimal adjustmentSum = producerAdjustments.stream()
                    .map(SettlementAdjustment::signedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal gross = rows.stream()
                    .map(r -> MoneyMath.nz(r.baseAmount()).add(MoneyMath.nz(r.incentiveAmount())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal netAvailable = gross
                    .subtract(deductionResult.totalDeduction())
                    .add(adjustmentSum);

            AdvanceRecoveryResult advanceResult = advanceRecoveryCalculator.calculate(
                    new AdvanceRecoveryInput(advances.activeAccountStates(producerId), netAvailable));

            SettlementLineResult line = calculator.calculateLine(new SettlementLineInput(
                    producerId, regionId, contributions, deductionResult, advanceResult, adjustmentSum, rule));

            SettlementItem item = persistItem(s, producerId, regionId, line);

            collections.attachToSettlementItem(
                    rows.stream().map(SettlementCollectionRow::collectionId).toList(), item.getId());
            deductions.markApplied(pending.transactionIds(), item.getId());
            advances.postRecoveries(producerId, item.getId(), advanceResult.recoveries(), s.getPeriodEnd());
            producerAdjustments.forEach(a -> a.setSettlementItemId(item.getId()));

            producerCount++;
            tLitres = tLitres.add(line.totalLitres());
            tMilk = tMilk.add(line.milkAmount());
            tInc = tInc.add(line.incentiveAmount());
            tDed = tDed.add(line.totalDeduction());
            tAdv = tAdv.add(line.advanceRecovery());
            tAdj = tAdj.add(line.adjustmentAmount());
            tRound = tRound.add(line.roundOff());
            tNet = tNet.add(line.netPayable());
        }

        s.setTotalProducers(producerCount);
        s.setTotalLitres(MoneyMath.quantity(tLitres));
        s.setTotalMilkAmount(MoneyMath.money(tMilk));
        s.setTotalIncentive(MoneyMath.money(tInc));
        s.setTotalDeduction(MoneyMath.money(tDed));
        s.setTotalAdvanceRecovery(MoneyMath.money(tAdv));
        s.setTotalAdjustment(MoneyMath.money(tAdj));
        s.setTotalRoundOff(MoneyMath.money(tRound));
        s.setTotalNetPayable(MoneyMath.money(tNet));
        s.setCalcEngineVersion(calculator.engineVersion());
        s.setStatus(Settlement.Status.CALCULATED);
        s.setCalculatedAt(Instant.now());
        CurrentUser.id().ifPresent(s::setCalculatedBy);
        Map<String, Object> snap = new HashMap<>();
        snap.put("engine", calculator.engineVersion());
        snap.put("producers", producerCount);
        snap.put("roundingMode", s.getRoundingMode());
        snap.put("roundingScale", s.getRoundingScale());
        s.setCalculationSnapshot(snap);

        audit.record("SETTLEMENT_CREATED", "Settlement", s.getId(),
                "Calculated %s: %d producers, net %s".formatted(
                        s.getSettlementNumber(), producerCount, s.getTotalNetPayable()));
        return s;
    }

    @Transactional
    public Settlement review(long id) {
        Settlement s = transition(id, Settlement.Status.CALCULATED, Settlement.Status.REVIEWED);
        s.setReviewedAt(Instant.now());
        CurrentUser.id().ifPresent(s::setReviewedBy);
        return s;
    }

    @Transactional
    public Settlement approve(long id) {
        Settlement s = transition(id, Settlement.Status.REVIEWED, Settlement.Status.APPROVED);
        s.setApprovedAt(Instant.now());
        CurrentUser.id().ifPresent(s::setApprovedBy);
        audit.record("SETTLEMENT_APPROVED", "Settlement", s.getId(),
                "Settlement %s approved, net payable %s".formatted(
                        s.getSettlementNumber(), s.getTotalNetPayable()));
        return s;
    }

    @Transactional
    public Settlement cancel(long id) {
        Settlement s = require(id);
        if (s.getStatus() == Settlement.Status.APPROVED
                || s.getStatus().ordinal() >= Settlement.Status.PAYOUT_INITIATED.ordinal()) {
            throw new BusinessRuleException("An approved settlement cannot be cancelled");
        }
        unwind(s);
        s.setStatus(Settlement.Status.CANCELLED);
        s.setCancelledAt(Instant.now());
        CurrentUser.id().ifPresent(s::setCancelledBy);
        audit.record("SETTLEMENT_CREATED", "Settlement", s.getId(),
                "Settlement " + s.getSettlementNumber() + " cancelled");
        return s;
    }

    @Transactional
    public SettlementAdjustment addAdjustment(long settlementId, long producerId,
                                              SettlementAdjustment.Type type, BigDecimal amount,
                                              String reason, Long originSettlementId) {
        Settlement s = require(settlementId);
        if (s.getStatus() != Settlement.Status.DRAFT
                && s.getStatus() != Settlement.Status.CALCULATED
                && s.getStatus() != Settlement.Status.REVIEWED) {
            throw new BusinessRuleException("Adjustments can only be added before approval");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Adjustment amount must be positive");
        }
        SettlementAdjustment a = new SettlementAdjustment();
        a.setSettlementId(settlementId);
        a.setProducerId(producerId);
        a.setAdjustmentType(type);
        a.setAmount(MoneyMath.money(amount));
        a.setReason(reason);
        a.setOriginSettlementId(originSettlementId);
        CurrentUser.id().ifPresent(a::setCreatedBy);
        a = adjustments.save(a);
        audit.record("SETTLEMENT_CREATED", "SettlementAdjustment", a.getId(),
                "%s %s for producer %d on %s (recalculate to apply)".formatted(
                        type, amount, producerId, s.getSettlementNumber()));
        return a;
    }

    // ---- SettlementPayoutGateway ----

    @Override
    public ApprovedSettlement requireApprovedForPayout(long settlementId) {
        Settlement s = require(settlementId);
        if (s.getStatus() != Settlement.Status.APPROVED
                && s.getStatus() != Settlement.Status.PAYOUT_INITIATED
                && s.getStatus() != Settlement.Status.PARTIALLY_PAID
                && s.getStatus() != Settlement.Status.FAILED) {
            throw new BusinessRuleException(
                    "Settlement is " + s.getStatus() + "; it must be APPROVED before payout");
        }
        List<PayableItem> payable = items.findBySettlementIdOrderByProducerIdAsc(s.getId()).stream()
                .map(i -> new PayableItem(i.getId(), i.getProducerId(), i.getBankAccountId(),
                        i.getNetPayable(),
                        ItemPaymentStatus.valueOf(i.getPaymentStatus().name())))
                .toList();
        return new ApprovedSettlement(s.getId(), s.getSettlementNumber(), payable);
    }

    @Override
    @Transactional
    public void markPayoutInitiated(long settlementId) {
        Settlement s = require(settlementId);
        if (s.getStatus() == Settlement.Status.APPROVED) {
            s.setStatus(Settlement.Status.PAYOUT_INITIATED);
        }
    }

    @Override
    @Transactional
    public void updateItemPaymentStatus(long settlementItemId, ItemPaymentStatus status, String reference) {
        SettlementItem item = items.findById(settlementItemId)
                .orElseThrow(() -> NotFoundException.of("SettlementItem", settlementItemId));
        item.setPaymentStatus(SettlementItem.PaymentStatus.valueOf(status.name()));
        if (reference != null) {
            item.setPaymentReference(reference);
        }
    }

    @Override
    @Transactional
    public void refreshSettlementPaymentStatus(long settlementId) {
        Settlement s = require(settlementId);
        List<SettlementItem> all = items.findBySettlementIdOrderByProducerIdAsc(settlementId);
        if (all.isEmpty()) {
            return;
        }
        long success = all.stream().filter(i -> i.getPaymentStatus() == SettlementItem.PaymentStatus.SUCCESS).count();
        long failed = all.stream().filter(i -> i.getPaymentStatus() == SettlementItem.PaymentStatus.FAILED).count();
        long done = success + failed;

        if (success == all.size()) {
            s.setStatus(Settlement.Status.PAID);
        } else if (done == all.size()) {
            s.setStatus(Settlement.Status.FAILED);
        } else if (success > 0 || failed > 0) {
            s.setStatus(Settlement.Status.PARTIALLY_PAID);
        } else {
            s.setStatus(Settlement.Status.PAYOUT_INITIATED);
        }
    }

    // ---- internals ----

    private SettlementItem persistItem(Settlement s, long producerId, Long regionId,
                                       SettlementLineResult line) {
        SettlementItem item = new SettlementItem();
        item.setSettlementId(s.getId());
        item.setProducerId(producerId);
        item.setRegionId(regionId);
        item.setBankAccountId(bankAccounts.primaryActiveAccountId(producerId).orElse(null));
        item.setMorningLitres(line.morningLitres());
        item.setEveningLitres(line.eveningLitres());
        item.setTotalLitres(line.totalLitres());
        item.setCollectionCount(line.collectionCount());
        item.setAverageRate(line.averageRate());
        item.setMilkAmount(line.milkAmount());
        item.setIncentiveAmount(line.incentiveAmount());
        item.setGrossAmount(line.grossAmount());
        item.setFeedDeduction(line.feedDeduction());
        item.setVeterinaryDeduction(line.veterinaryDeduction());
        item.setOtherDeduction(line.otherDeduction());
        item.setAdvanceRecovery(line.advanceRecovery());
        item.setTotalDeduction(line.totalDeduction());
        item.setAdjustmentAmount(line.adjustmentAmount());
        item.setNetBeforeRounding(line.netBeforeRounding());
        item.setRoundOff(line.roundOff());
        item.setNetPayable(line.netPayable());
        Map<String, Object> snap = new HashMap<>();
        snap.put("gross", line.grossAmount());
        snap.put("totalDeduction", line.totalDeduction());
        snap.put("advanceRecovery", line.advanceRecovery());
        snap.put("adjustment", line.adjustmentAmount());
        snap.put("netBeforeRounding", line.netBeforeRounding());
        snap.put("roundOff", line.roundOff());
        snap.put("netPayable", line.netPayable());
        item.setCalculationSnapshot(snap);
        return items.save(item);
    }

    private void unwind(Settlement s) {
        List<SettlementItem> existing = items.findBySettlementIdOrderByProducerIdAsc(s.getId());
        for (SettlementItem item : existing) {
            collections.detachFromSettlement(item.getId());
            deductions.unapply(item.getId());
            advances.reverseRecoveries(item.getId());
        }
        adjustments.findBySettlementId(s.getId())
                .forEach(a -> a.setSettlementItemId(null));
        items.deleteBySettlementId(s.getId());
        items.flush();

        s.setTotalProducers(0);
        s.setTotalLitres(BigDecimal.ZERO);
        s.setTotalMilkAmount(BigDecimal.ZERO);
        s.setTotalIncentive(BigDecimal.ZERO);
        s.setTotalDeduction(BigDecimal.ZERO);
        s.setTotalAdvanceRecovery(BigDecimal.ZERO);
        s.setTotalAdjustment(BigDecimal.ZERO);
        s.setTotalRoundOff(BigDecimal.ZERO);
        s.setTotalNetPayable(BigDecimal.ZERO);
    }

    private Settlement transition(long id, Settlement.Status from, Settlement.Status to) {
        Settlement s = require(id);
        if (s.getStatus() != from) {
            throw new BusinessRuleException(
                    "Settlement is " + s.getStatus() + "; expected " + from + " for this action");
        }
        s.setStatus(to);
        return s;
    }

    private void applyRoundingSnapshot(Settlement s, RoundingPolicy policy) {
        s.setRoundingMode(policy.mode() == null ? "NONE" : policy.mode().name());
        s.setRoundingScale(policy.scale());
    }
}
