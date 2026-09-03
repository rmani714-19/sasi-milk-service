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
import com.sasimilkservice.paymentops.domain.BankBalanceCheck.SubjectType;
import com.sasimilkservice.paymentops.service.PaymentOpsService;
import com.sasimilkservice.paymentops.service.PreValidation;
import com.sasimilkservice.producer.spi.BankAccountResolver;
import com.sasimilkservice.producer.spi.ProducerDirectory;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final SettlementNumberGenerator numberGenerator;
    private final SettlementCalculator calculator;
    private final CollectionSettlementGateway collections;
    private final DeductionLedger deductions;
    private final AdvanceLedger advances;
    private final AdvanceRecoveryCalculator advanceRecoveryCalculator;
    private final CenterService centerService;
    private final BankAccountResolver bankAccounts;
    private final ProducerDirectory producerDirectory;
    private final PaymentOpsService paymentOps;
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
                             ProducerDirectory producerDirectory,
                             PaymentOpsService paymentOps,
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
        this.producerDirectory = producerDirectory;
        this.paymentOps = paymentOps;
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
        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessRuleException("periodEnd must not be before periodStart");
        }
        Settlement s = new Settlement();
        s.setSettlementNumber(numberGenerator.next(periodStart));
        s.setPeriodStart(periodStart);
        s.setPeriodEnd(periodEnd);
        s.setPaymentFrequency(frequency == null ? Settlement.PaymentFrequency.ALL : frequency);
        applyRoundingSnapshot(s, centerService.settings().roundingPolicy());
        // DB constraint ex_settlement_no_overlap rejects overlapping non-cancelled periods
        // for the SAME payment_frequency scope (weekly and monthly runs may overlap).
        s = settlements.save(s);
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

        CenterSettings settings = centerService.settings();
        RoundingPolicy policy = settings.roundingPolicy();
        applyRoundingSnapshot(s, policy);
        RoundingRule rule = RoundingRule.fromPolicy(policy);

        Map<Long, List<SettlementCollectionRow>> byProducer = collections
                .unsettledConfirmed(s.getPeriodStart(), s.getPeriodEnd()).stream()
                .collect(Collectors.groupingBy(SettlementCollectionRow::producerId, LinkedHashMap::new,
                        Collectors.toList()));

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
        long missingBank = lines.stream().filter(i -> i.getBankAccountId() == null).count();
        long conflicted = lines.stream().filter(i ->
                i.getPaymentStatus() == SettlementItem.PaymentStatus.ON_HOLD
                        || i.getPaymentStatus() == SettlementItem.PaymentStatus.FAILED).count();
        var balance = paymentOps.latest(SubjectType.SETTLEMENT, id);
        boolean balanceVerified = balance.isPresent();
        boolean balanceSufficient = balance.filter(b -> b.isSufficient())
                .filter(b -> b.getRequiredAmount().compareTo(MoneyMath.money(s.getTotalNetPayable())) == 0)
                .isPresent();

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
                .blocking("bank_balance_verified", "Bank balance verified", balanceVerified,
                        "Record the owner's available bank balance", "/settlements/" + id)
                .blocking("bank_balance_sufficient", "Sufficient bank balance", balanceSufficient,
                        "Latest balance check does not cover " + MoneyMath.money(s.getTotalNetPayable()),
                        "/settlements/" + id)
                .info("litres", "Total litres verified",
                        MoneyMath.quantity(sumLitres).compareTo(MoneyMath.quantity(s.getTotalLitres())) == 0,
                        "lines " + MoneyMath.quantity(sumLitres) + " / batch " + s.getTotalLitres())
                .info("bank_details", "Bank details complete for every recipient", missingBank == 0,
                        missingBank == 0 ? "all set" : missingBank + " producer(s) have no primary bank account")
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
            if (!paymentOps.balanceCovered(SubjectType.SETTLEMENT, id, s.getTotalNetPayable())) {
                throw new BusinessRuleException("Owner bank balance is not verified as sufficient for "
                        + MoneyMath.money(s.getTotalNetPayable()) + " — verify the bank balance before approval");
            }
            if (preValidate(id).result() != PreValidation.Result.READY_FOR_APPROVAL) {
                throw new BusinessRuleException("Pre-validation is BLOCKED — resolve the failing checks before approval");
            }
        }
        if (!confirmed) {
            throw new BusinessRuleException("Approval must be explicitly confirmed by the owner / manager");
        }
        s.setStatus(Settlement.Status.APPROVED);
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
            s.setStatus(Settlement.Status.CALCULATED);
            return;
        }
        long verified = lines.stream()
                .filter(i -> i.getVerificationStatus() == SettlementItem.VerificationStatus.VERIFIED)
                .count();
        if (verified == lines.size()) {
            s.setStatus(Settlement.Status.VERIFIED);
        } else if (verified > 0) {
            s.setStatus(Settlement.Status.UNDER_VERIFICATION);
        } else {
            s.setStatus(Settlement.Status.CALCULATED);
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
