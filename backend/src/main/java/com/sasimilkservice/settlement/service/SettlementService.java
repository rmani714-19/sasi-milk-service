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
import com.sasimilkservice.paymentops.service.PreValidation;
import com.sasimilkservice.producer.spi.ProducerDirectory;
import com.sasimilkservice.settlement.domain.Settlement;
import com.sasimilkservice.settlement.domain.SettlementAdjustment;
import com.sasimilkservice.settlement.domain.SettlementItem;
import com.sasimilkservice.settlement.repository.SettlementAdjustmentRepository;
import com.sasimilkservice.settlement.repository.SettlementBankSubmissionRepository;
import com.sasimilkservice.settlement.repository.SettlementStatusHistoryRepository;
import com.sasimilkservice.settlement.domain.SettlementBankSubmission;
import com.sasimilkservice.settlement.domain.SettlementStatusHistory;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final SettlementBankSubmissionRepository submissions;
    private final SettlementStatusHistoryRepository history;
    private final SettlementNumberGenerator numberGenerator;
    private final SettlementCalculator calculator;
    private final CollectionSettlementGateway collections;
    private final DeductionLedger deductions;
    private final AdvanceLedger advances;
    private final AdvanceRecoveryCalculator advanceRecoveryCalculator;
    private final CenterService centerService;
    private final ProducerDirectory producerDirectory;
    private final com.sasimilkservice.region.spi.RegionDirectory regionDirectory;
    private final AuditRecorder audit;

    public SettlementService(SettlementRepository settlements,
                             SettlementItemRepository items,
                             SettlementAdjustmentRepository adjustments,
                             SettlementBankSubmissionRepository submissions,
                             SettlementStatusHistoryRepository history,
                             SettlementNumberGenerator numberGenerator,
                             SettlementCalculator calculator,
                             CollectionSettlementGateway collections,
                             DeductionLedger deductions,
                             AdvanceLedger advances,
                             AdvanceRecoveryCalculator advanceRecoveryCalculator,
                             CenterService centerService,
                             ProducerDirectory producerDirectory,
                             com.sasimilkservice.region.spi.RegionDirectory regionDirectory,
                             AuditRecorder audit) {
        this.settlements = settlements;
        this.items = items;
        this.adjustments = adjustments;
        this.submissions = submissions;
        this.history = history;
        this.numberGenerator = numberGenerator;
        this.calculator = calculator;
        this.collections = collections;
        this.deductions = deductions;
        this.advances = advances;
        this.advanceRecoveryCalculator = advanceRecoveryCalculator;
        this.centerService = centerService;
        this.producerDirectory = producerDirectory;
        this.regionDirectory = regionDirectory;
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
        return create(periodStart, periodEnd, Settlement.PaymentFrequency.ALL);
    }

    @Transactional
    public Settlement create(LocalDate periodStart, LocalDate periodEnd, Settlement.PaymentFrequency frequency) {
        return create(periodStart, periodEnd, frequency, null);
    }

    /** @param regionId limit the batch to one region/area; null pays every region */
    @Transactional
    public Settlement create(LocalDate periodStart, LocalDate periodEnd, Settlement.PaymentFrequency frequency,
                             Long regionId) {
        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessRuleException("periodEnd must not be before periodStart");
        }
        Settlement.PaymentFrequency freq = frequency == null ? Settlement.PaymentFrequency.ALL : frequency;
        if (regionId != null && !regionDirectory.isActive(regionId)) {
            throw new BusinessRuleException("Region " + regionId + " is not an active region");
        }
        if (settlements.existsOverlapOfOtherScope(freq, periodStart, periodEnd, regionId != null)) {
            throw new BusinessRuleException(regionId != null
                    ? "An all-regions payment batch already covers this period — use it, or cancel it first"
                    : "Region payment batches already exist for this period — an all-regions batch would overlap them");
        }
        Settlement s = new Settlement();
        s.setRegionId(regionId);
        s.setSettlementNumber(numberGenerator.next(periodStart));
        s.setPeriodStart(periodStart);
        s.setPeriodEnd(periodEnd);
        s.setPaymentFrequency(frequency == null ? Settlement.PaymentFrequency.ALL : frequency);
        applyRoundingSnapshot(s, centerService.settings().roundingPolicy());
        // DB constraint ex_settlement_no_overlap rejects overlapping non-cancelled periods
        // for the SAME payment_frequency scope (weekly and monthly runs may overlap).
        s = settlements.save(s);
        record(s.getId(), SettlementStatusHistory.SubjectType.SETTLEMENT, s.getId(), null,
                s.getStatus().name(), "Payment batch created for " + periodStart + " to " + periodEnd);
        audit.record("SETTLEMENT_CREATED", "Settlement", s.getId(),
                "%s settlement %s for %s..%s".formatted(
                        s.getPaymentFrequency(), s.getSettlementNumber(), periodStart, periodEnd));
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

        int pendingQuality = s.getRegionId() == null
                ? collections.pendingQualityCount(s.getPeriodStart(), s.getPeriodEnd())
                : collections.pendingQualityCount(s.getPeriodStart(), s.getPeriodEnd(), s.getRegionId());
        if (pendingQuality > 0) {
            throw new BusinessRuleException(pendingQuality + " collection(s) in "
                    + s.getPeriodStart() + " to " + s.getPeriodEnd()
                    + " still need FAT / SNF entered. Add the milk quality before calculating this run.");
        }

        CenterSettings settings = centerService.settings();
        RoundingPolicy policy = settings.roundingPolicy();
        applyRoundingSnapshot(s, policy);
        RoundingRule rule = RoundingRule.fromPolicy(policy);

        Map<Long, List<SettlementCollectionRow>> byProducer = collections
                .unsettledConfirmed(s.getPeriodStart(), s.getPeriodEnd()).stream()
                .collect(Collectors.groupingBy(SettlementCollectionRow::producerId, LinkedHashMap::new,
                        Collectors.toList()));

        // A region batch pays only the collections recorded in its region.
        if (s.getRegionId() != null) {
            byProducer.values().forEach(list -> list.removeIf(r -> !s.getRegionId().equals(r.regionId())));
            byProducer.values().removeIf(List::isEmpty);
        }

        // A weekly / monthly run only pays producers configured for that frequency.
        if (s.getPaymentFrequency() != Settlement.PaymentFrequency.ALL) {
            Set<Long> eligible = new HashSet<>(
                    producerDirectory.producerIdsByPaymentFrequency(s.getPaymentFrequency().name()));
            byProducer.keySet().retainAll(eligible);
        }

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
        changeStatus(s, Settlement.Status.CALCULATED, null);
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

    private static final Set<Settlement.Status> VERIFIABLE = Set.of(
            Settlement.Status.CALCULATED, Settlement.Status.REVIEWED,
            Settlement.Status.UNDER_VERIFICATION, Settlement.Status.VERIFIED);

    /** Mark one producer line verified (litres + amount checked by the payment processor). */
    @Transactional
    public SettlementItem verifyItem(long settlementId, long itemId) {
        Settlement s = require(settlementId);
        if (!VERIFIABLE.contains(s.getStatus())) {
            throw new BusinessRuleException(
                    "Settlement is " + s.getStatus() + "; producer lines can only be verified before approval");
        }
        SettlementItem item = requireItem(settlementId, itemId);
        item.setVerificationStatus(SettlementItem.VerificationStatus.VERIFIED);
        item.setVerifiedAmount(item.getNetPayable());
        item.setVerifiedAt(Instant.now());
        CurrentUser.id().ifPresent(item::setVerifiedBy);
        recomputeVerificationState(s);
        audit.record("PAYMENT_VERIFIED", "SettlementItem", item.getId(),
                "Producer %d line verified on %s: %s litres, net %s".formatted(
                        item.getProducerId(), s.getSettlementNumber(),
                        item.getTotalLitres(), item.getNetPayable()));
        return item;
    }

    @Transactional
    public SettlementItem unverifyItem(long settlementId, long itemId) {
        Settlement s = require(settlementId);
        if (!VERIFIABLE.contains(s.getStatus())) {
            throw new BusinessRuleException("Settlement is " + s.getStatus() + "; verification is locked");
        }
        SettlementItem item = requireItem(settlementId, itemId);
        item.setVerificationStatus(SettlementItem.VerificationStatus.PENDING_VERIFICATION);
        item.setVerifiedAmount(null);
        item.setVerifiedAt(null);
        item.setVerifiedBy(null);
        item.setBillNumber(null);
        item.setBillGeneratedAt(null);
        item.setBillGeneratedBy(null);
        recomputeVerificationState(s);
        audit.record("PAYMENT_VERIFICATION_CLEARED", "SettlementItem", item.getId(),
                "Producer %d line un-verified on %s".formatted(item.getProducerId(), s.getSettlementNumber()));
        return item;
    }

    /** Verify every remaining producer line in one action. */
    @Transactional
    public Settlement verifyAll(long settlementId) {
        Settlement s = require(settlementId);
        if (!VERIFIABLE.contains(s.getStatus())) {
            throw new BusinessRuleException(
                    "Settlement is " + s.getStatus() + "; producer lines can only be verified before approval");
        }
        List<SettlementItem> lines = items.findBySettlementIdOrderByProducerIdAsc(settlementId);
        if (lines.isEmpty()) {
            throw new BusinessRuleException("Nothing to verify — calculate the settlement first");
        }
        Instant now = Instant.now();
        Long by = CurrentUser.id().orElse(null);
        for (SettlementItem item : lines) {
            if (item.getVerificationStatus() != SettlementItem.VerificationStatus.VERIFIED) {
                item.setVerificationStatus(SettlementItem.VerificationStatus.VERIFIED);
                item.setVerifiedAmount(item.getNetPayable());
                item.setVerifiedAt(now);
                item.setVerifiedBy(by);
            }
        }
        recomputeVerificationState(s);
        audit.record("PAYMENT_VERIFIED", "Settlement", s.getId(),
                "All %d producer lines verified on %s".formatted(lines.size(), s.getSettlementNumber()));
        return s;
    }

    /** The pre-approval checklist for this settlement (see {@link PreValidation}). */
    public PreValidation.Report preValidate(long id) {
        Settlement s = require(id);
        List<SettlementItem> lines = items.findBySettlementIdOrderByProducerIdAsc(s.getId());
        int verified = (int) lines.stream()
                .filter(i -> i.getVerificationStatus() == SettlementItem.VerificationStatus.VERIFIED).count();
        boolean allVerified = !lines.isEmpty() && verified == lines.size();
        boolean noDrift = lines.stream().allMatch(i ->
                i.getVerificationStatus() != SettlementItem.VerificationStatus.VERIFIED
                        || (i.getVerifiedAmount() != null
                        && i.getVerifiedAmount().compareTo(i.getNetPayable()) == 0));
        BigDecimal sumNet = lines.stream().map(SettlementItem::getNetPayable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumLitres = lines.stream().map(SettlementItem::getTotalLitres)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long conflicted = lines.stream().filter(i ->
                i.getPaymentStatus() == SettlementItem.PaymentStatus.ON_HOLD
                        || i.getPaymentStatus() == SettlementItem.PaymentStatus.FAILED).count();

        return PreValidation.builder()
                .blocking("recipients", "All eligible recipients identified",
                        !lines.isEmpty() && lines.size() == s.getTotalProducers(),
                        "No producer lines — calculate the settlement", "/settlements/" + id)
                .blocking("verified", "All producer payments verified", allVerified,
                        (lines.size() - verified) + " of " + lines.size() + " not verified", "/settlements/" + id)
                .blocking("no_drift", "No amount changed after verification", noDrift,
                        "A verified amount has since changed — re-verify", "/settlements/" + id)
                .blocking("totals_match", "Batch total matches recipient totals",
                        MoneyMath.money(sumNet).compareTo(MoneyMath.money(s.getTotalNetPayable())) == 0,
                        "Sum of lines " + MoneyMath.money(sumNet) + " != batch " + s.getTotalNetPayable(), null)
                .info("litres", "Total litres verified",
                        MoneyMath.quantity(sumLitres).compareTo(MoneyMath.quantity(s.getTotalLitres())) == 0,
                        "lines " + MoneyMath.quantity(sumLitres) + " / batch " + s.getTotalLitres())
                .info("no_conflict", "No unresolved payment conflict", conflicted == 0,
                        conflicted == 0 ? "none" : conflicted + " line(s) FAILED / ON_HOLD")
                .info("period", "Payment period valid",
                        !s.getPeriodEnd().isBefore(s.getPeriodStart()),
                        s.getPeriodStart() + " -> " + s.getPeriodEnd())
                .build();
    }

    @Transactional
    public Settlement approve(long id) {
        return approve(id, true);
    }

    @Transactional
    public Settlement approve(long id, boolean confirmed) {
        Settlement s = require(id);
        boolean legacyAll = s.getPaymentFrequency() == Settlement.PaymentFrequency.ALL;
        boolean fromReviewed = legacyAll && s.getStatus() == Settlement.Status.REVIEWED;
        if (s.getStatus() != Settlement.Status.VERIFIED && !fromReviewed) {
            throw new BusinessRuleException(
                    "Settlement is " + s.getStatus() + "; every producer line must be VERIFIED before approval");
        }
        List<SettlementItem> lines = items.findBySettlementIdOrderByProducerIdAsc(s.getId());
        if (lines.isEmpty()) {
            throw new BusinessRuleException("Cannot approve an empty settlement");
        }
        if (!fromReviewed) {
            for (SettlementItem item : lines) {
                if (item.getVerificationStatus() != SettlementItem.VerificationStatus.VERIFIED) {
                    throw new BusinessRuleException(
                            "Producer " + item.getProducerId() + " is not verified");
                }
                if (item.getVerifiedAmount() == null
                        || item.getVerifiedAmount().compareTo(item.getNetPayable()) != 0) {
                    throw new BusinessRuleException(("Producer %d amount changed since verification "
                            + "(verified %s, now %s) — re-verify before approving").formatted(
                            item.getProducerId(), item.getVerifiedAmount(), item.getNetPayable()));
                }
            }
            if (preValidate(id).result() != PreValidation.Result.READY_FOR_APPROVAL) {
                throw new BusinessRuleException("Pre-validation is BLOCKED — resolve the failing checks before approval");
            }
        }
        if (!confirmed) {
            throw new BusinessRuleException("Approval must be explicitly confirmed by the owner / manager");
        }
        changeStatus(s, Settlement.Status.APPROVED, null);
        s.setApprovedAt(Instant.now());
        CurrentUser.id().ifPresent(s::setApprovedBy);
        audit.record("SETTLEMENT_APPROVED", "Settlement", s.getId(),
                "Settlement %s approved (owner-confirmed), net payable %s".formatted(
                        s.getSettlementNumber(), s.getTotalNetPayable()));
        return s;
    }

    private SettlementItem requireItem(long settlementId, long itemId) {
        SettlementItem item = items.findById(itemId)
                .orElseThrow(() -> NotFoundException.of("SettlementItem", itemId));
        if (!item.getSettlementId().equals(settlementId)) {
            throw NotFoundException.of("SettlementItem", itemId);
        }
        return item;
    }

    /** Roll the batch status across CALCULATED / UNDER_VERIFICATION / VERIFIED from its lines. */
    private void recomputeVerificationState(Settlement s) {
        if (!VERIFIABLE.contains(s.getStatus())) {
            return;
        }
        List<SettlementItem> lines = items.findBySettlementIdOrderByProducerIdAsc(s.getId());
        if (lines.isEmpty()) {
            changeStatus(s, Settlement.Status.CALCULATED, null);
            return;
        }
        long verified = lines.stream()
                .filter(i -> i.getVerificationStatus() == SettlementItem.VerificationStatus.VERIFIED)
                .count();
        if (verified == lines.size()) {
            changeStatus(s, Settlement.Status.VERIFIED, null);
        } else if (verified > 0) {
            changeStatus(s, Settlement.Status.UNDER_VERIFICATION, null);
        } else {
            changeStatus(s, Settlement.Status.CALCULATED, null);
        }
    }

    public List<CollectionSettlementGateway.SettlementItemCollection> collectionsForItem(long settlementId, long itemId) {
        requireItem(settlementId, itemId);
        return collections.collectionsForSettlementItem(itemId);
    }

    @Transactional
    public Settlement cancel(long id) {
        Settlement s = require(id);
        if (s.getStatus() == Settlement.Status.APPROVED
                || s.getStatus().ordinal() >= Settlement.Status.PAYOUT_INITIATED.ordinal()) {
            throw new BusinessRuleException("An approved settlement cannot be cancelled");
        }
        unwind(s);
        changeStatus(s, Settlement.Status.CANCELLED, null);
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
                .map(i -> new PayableItem(i.getId(), i.getProducerId(), i.getRegionId(),
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
            changeStatus(s, Settlement.Status.PAYOUT_INITIATED, null);
        }
    }

    @Override
    @Transactional
    public void updateItemPaymentStatus(long settlementItemId, ItemPaymentStatus status, String reference) {
        SettlementItem item = items.findById(settlementItemId)
                .orElseThrow(() -> NotFoundException.of("SettlementItem", settlementItemId));
        SettlementItem.PaymentStatus before = item.getPaymentStatus();
        item.setPaymentStatus(SettlementItem.PaymentStatus.valueOf(status.name()));
        if (reference != null) {
            item.setPaymentReference(reference);
        }
        if (before != item.getPaymentStatus()) {
            record(item.getSettlementId(), SettlementStatusHistory.SubjectType.ITEM, item.getId(),
                    before.name(), item.getPaymentStatus().name(),
                    reference == null ? null : "Bank reference " + reference);
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
            changeStatus(s, Settlement.Status.PAID, null);
        } else if (done == all.size()) {
            changeStatus(s, Settlement.Status.FAILED, null);
        } else if (success > 0 || failed > 0) {
            changeStatus(s, Settlement.Status.PARTIALLY_PAID, null);
        } else {
            changeStatus(s, Settlement.Status.PAYOUT_INITIATED, null);
        }
    }

    // ---- reconciliation ----

    /**
     * Does the settlement agree with what has been paid? Totals overall and per region, from the stored lines:
     * expected (batch net payable), the sum of the producer lines, and how much is confirmed paid, still
     * waiting, or failed. Lines with nothing to pay are ignored.
     */
    public Reconciliation reconciliation(long settlementId) {
        Settlement s = require(settlementId);
        List<SettlementItem> lines = items.findBySettlementIdOrderByProducerIdAsc(settlementId);
        java.util.Map<Long, RegionTally> byRegion = new java.util.TreeMap<>(java.util.Comparator.nullsFirst(Long::compareTo));
        RegionTally all = new RegionTally();
        for (SettlementItem i : lines) {
            BigDecimal net = MoneyMath.money(i.getNetPayable());
            if (net.signum() <= 0) {
                continue;
            }
            RegionTally t = byRegion.computeIfAbsent(i.getRegionId(), k -> new RegionTally());
            for (RegionTally x : List.of(t, all)) {
                x.producers++;
                x.expected = x.expected.add(net);
                switch (i.getPaymentStatus()) {
                    case SUCCESS -> {
                        x.paidCount++;
                        x.paid = x.paid.add(net);
                    }
                    case FAILED -> {
                        x.failedCount++;
                        x.failed = x.failed.add(net);
                    }
                    case CANCELLED -> x.cancelled = x.cancelled.add(net);
                    default -> {
                        x.pendingCount++;
                        x.pending = x.pending.add(net);
                    }
                }
            }
        }
        List<RegionReconciliation> regions = byRegion.entrySet().stream()
                .map(e -> e.getValue().toRegion(e.getKey())).toList();
        BigDecimal batchTotal = MoneyMath.money(s.getTotalNetPayable());
        boolean linesMatchBatch = all.expected.add(all.cancelled).compareTo(batchTotal) == 0
                || all.expected.compareTo(batchTotal) == 0;
        boolean complete = all.producers > 0 && all.pendingCount == 0 && all.failedCount == 0
                && all.paid.compareTo(all.expected) == 0;
        return new Reconciliation(batchTotal, all.expected, all.paid, all.pending, all.failed,
                all.producers, all.paidCount, all.pendingCount, all.failedCount,
                linesMatchBatch, complete, all.expected.subtract(all.paid), regions);
    }

    private static final class RegionTally {
        int producers;
        int paidCount;
        int pendingCount;
        int failedCount;
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal failed = BigDecimal.ZERO;
        BigDecimal cancelled = BigDecimal.ZERO;

        RegionReconciliation toRegion(Long regionId) {
            String status = producers == 0 ? "NOTHING_TO_PAY"
                    : paidCount == producers ? "COMPLETED"
                    : paidCount > 0 ? "PARTIALLY_CONFIRMED"
                    : failedCount > 0 && pendingCount == 0 ? "FAILED" : "NOT_CONFIRMED";
            return new RegionReconciliation(regionId, producers, paidCount, pendingCount, failedCount,
                    expected, paid, pending, failed, status);
        }
    }

    public record Reconciliation(BigDecimal batchTotal, BigDecimal linesTotal, BigDecimal paidTotal,
                                 BigDecimal pendingTotal, BigDecimal failedTotal,
                                 int producers, int paidCount, int pendingCount, int failedCount,
                                 boolean linesMatchBatch, boolean complete, BigDecimal difference,
                                 List<RegionReconciliation> regions) {
    }

    public record RegionReconciliation(Long regionId, int producers, int paidCount, int pendingCount,
                                       int failedCount, BigDecimal expected, BigDecimal paid,
                                       BigDecimal pending, BigDecimal failed, String status) {
    }

    // ---- bills, bank submission and history ----

    private static final Set<Settlement.Status> BILLABLE = Set.of(
            Settlement.Status.VERIFIED, Settlement.Status.APPROVED, Settlement.Status.PAYOUT_INITIATED,
            Settlement.Status.PARTIALLY_PAID, Settlement.Status.PAID);

    /** Change a run's status and leave a trail; a no-op when the status is already that. */
    private void changeStatus(Settlement s, Settlement.Status to, String note) {
        Settlement.Status from = s.getStatus();
        if (from == to) {
            return;
        }
        s.setStatus(to);
        record(s.getId(), SettlementStatusHistory.SubjectType.SETTLEMENT, s.getId(), from.name(), to.name(), note);
    }

    private void record(Long settlementId, SettlementStatusHistory.SubjectType type, Long subjectId,
                        String from, String to, String note) {
        if (settlementId == null) {
            return;
        }
        SettlementStatusHistory h = new SettlementStatusHistory();
        h.setSettlementId(settlementId);
        h.setSubjectType(type);
        h.setSubjectId(subjectId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setNote(note);
        CurrentUser.id().ifPresent(h::setChangedBy);
        history.save(h);
    }

    public List<SettlementStatusHistory> historyOf(long settlementId) {
        require(settlementId);
        return history.findBySettlementIdOrderByIdDesc(settlementId);
    }

    public Optional<SettlementBankSubmission> bankSubmissionOf(long settlementId) {
        return submissions.findBySettlementId(settlementId);
    }

    /**
     * Give every producer line a bill number. Only allowed once every line is verified, so a bill
     * always reflects checked collections. Safe to repeat — lines that already have a number keep it.
     *
     * @return how many bills were newly numbered
     */
    @Transactional
    public int generateBills(long settlementId) {
        Settlement s = require(settlementId);
        if (!BILLABLE.contains(s.getStatus())) {
            throw new BusinessRuleException(
                    "Bills can be generated once every producer's collections are verified");
        }
        List<SettlementItem> lines = items.findBySettlementIdOrderByProducerIdAsc(settlementId);
        if (lines.isEmpty()) {
            throw new BusinessRuleException("There are no producer lines to bill");
        }
        if (lines.stream().anyMatch(i -> i.getVerificationStatus() != SettlementItem.VerificationStatus.VERIFIED)) {
            throw new BusinessRuleException("Verify every producer before generating bills");
        }
        int generated = 0;
        int index = 0;
        for (SettlementItem line : lines) {
            index++;
            if (line.getBillNumber() != null) {
                continue;
            }
            line.setBillNumber("%s-B%03d".formatted(s.getSettlementNumber(), index));
            line.setBillGeneratedAt(Instant.now());
            CurrentUser.id().ifPresent(line::setBillGeneratedBy);
            generated++;
        }
        if (generated > 0) {
            record(settlementId, SettlementStatusHistory.SubjectType.SETTLEMENT, settlementId,
                    s.getStatus().name(), s.getStatus().name(), generated + " bill(s) generated");
            audit.record("SETTLEMENT_BILLS_GENERATED", "Settlement", settlementId,
                    "%d bill(s) generated for %s".formatted(generated, s.getSettlementNumber()));
        }
        return generated;
    }

    @Override
    @Transactional
    public void recordBankSubmission(long settlementId, LocalDate submittedOn, String reference, String note) {
        Settlement s = require(settlementId);
        if (s.getStatus() != Settlement.Status.APPROVED || submissions.findBySettlementId(settlementId).isPresent()) {
            // Re-running payouts for a batch that is already with the bank (a held line got a bank account,
            // a failed one is retried) — the original submission record stands.
            return;
        }
        List<SettlementItem> payable = items.findBySettlementIdOrderByProducerIdAsc(settlementId).stream()
                .filter(i -> MoneyMath.money(i.getNetPayable()).signum() > 0)
                .toList();
        SettlementBankSubmission sub = new SettlementBankSubmission();
        sub.setSettlementId(settlementId);
        sub.setSubmittedOn(submittedOn == null ? LocalDate.now() : submittedOn);
        sub.setBankReference(reference == null || reference.isBlank() ? null : reference.trim());
        sub.setNote(note == null || note.isBlank() ? null : note.trim());
        sub.setProducerCount(payable.size());
        sub.setTotalAmount(payable.stream().map(SettlementItem::getNetPayable)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        CurrentUser.id().ifPresent(sub::setSubmittedBy);
        submissions.save(sub);
        audit.record("SETTLEMENT_SUBMITTED_TO_BANK", "Settlement", settlementId,
                "%s submitted to the bank on %s (%d producers, %s)".formatted(
                        s.getSettlementNumber(), sub.getSubmittedOn(), sub.getProducerCount(), sub.getTotalAmount()));
    }

    // ---- internals ----

    private SettlementItem persistItem(Settlement s, long producerId, Long regionId,
                                       SettlementLineResult line) {
        SettlementItem item = new SettlementItem();
        item.setSettlementId(s.getId());
        item.setProducerId(producerId);
        item.setRegionId(regionId);
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
        changeStatus(s, to, null);
        return s;
    }

    private void applyRoundingSnapshot(Settlement s, RoundingPolicy policy) {
        s.setRoundingMode(policy.mode() == null ? "NONE" : policy.mode().name());
        s.setRoundingScale(policy.scale());
    }
}
