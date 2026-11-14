package com.sasimilkservice.collection.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.center.service.CenterService;
import com.sasimilkservice.center.spi.CenterSettings;
import com.sasimilkservice.collection.api.dto.CollectionDtos.CollectionResponse;
import com.sasimilkservice.collection.domain.CollectionSyncLog;
import com.sasimilkservice.collection.domain.MilkCollection;
import com.sasimilkservice.collection.repository.CollectionSyncLogRepository;
import com.sasimilkservice.collection.repository.MilkCollectionRepository;
import com.sasimilkservice.collection.spi.CollectionSettlementGateway;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.incentive.spi.IncentiveCalculator;
import com.sasimilkservice.incentive.spi.IncentiveCalculator.IncentiveQuery;
import com.sasimilkservice.incentive.spi.IncentiveCalculator.IncentiveResult;
import com.sasimilkservice.notification.spi.NotificationPublisher;
import com.sasimilkservice.notification.spi.NotificationPublisher.NotificationRequest;
import com.sasimilkservice.producer.spi.ProducerDirectory;
import com.sasimilkservice.producer.spi.ProducerDirectory.ProducerView;
import com.sasimilkservice.rate.spi.RateResolutionService;
import com.sasimilkservice.rate.spi.RateResolutionService.ResolveRateQuery;
import com.sasimilkservice.rate.spi.RateResolutionService.ResolvedRate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records a milk collection end to end: producer lookup, rate resolution,
 * incentive calculation, amount assembly, idempotent persistence, sync log, audit.
 */
@Service
@Transactional(readOnly = true)
public class CollectionService implements CollectionSettlementGateway,
        com.sasimilkservice.collection.spi.RouteCollectionQuery {

    private final MilkCollectionRepository collections;
    private final CollectionSyncLogRepository syncLog;
    private final ProducerDirectory producers;
    private final CenterService centerService;
    private final RateResolutionService rateResolution;
    private final IncentiveCalculator incentiveCalculator;
    private final CollectionAmountCalculator amountCalculator;
    private final CollectionNumberGenerator numberGenerator;
    private final AuditRecorder audit;
    private final NotificationPublisher notifications;

    public CollectionService(MilkCollectionRepository collections,
                             CollectionSyncLogRepository syncLog,
                             ProducerDirectory producers,
                             CenterService centerService,
                             RateResolutionService rateResolution,
                             IncentiveCalculator incentiveCalculator,
                             CollectionAmountCalculator amountCalculator,
                             CollectionNumberGenerator numberGenerator,
                             AuditRecorder audit,
                             NotificationPublisher notifications) {
        this.collections = collections;
        this.syncLog = syncLog;
        this.producers = producers;
        this.centerService = centerService;
        this.rateResolution = rateResolution;
        this.incentiveCalculator = incentiveCalculator;
        this.amountCalculator = amountCalculator;
        this.numberGenerator = numberGenerator;
        this.audit = audit;
        this.notifications = notifications;
    }

    public Page<MilkCollection> search(Long producerId, LocalDate date, String shift,
                                      MilkCollection.Status status, Pageable pageable) {
        return collections.search(producerId, date, shift, status, pageable);
    }

    /**
     * Owner browse: any date range, shift, producer number/name text, with sorting.
     * {@code sort} is one of litres_desc / litres_asc / amount_desc / amount_asc / date_asc
     * (default newest first).
     */
    public Page<MilkCollection> browse(LocalDate from, LocalDate to, String shift, MilkCollection.Status status,
                                       String q, String sort, int page, int size) {
        boolean filterProducers = q != null && !q.isBlank();
        List<Long> ids = filterProducers ? producers.searchIds(q) : List.of(-1L);
        if (filterProducers && ids.isEmpty()) {
            return Page.empty(PageRequest.of(page, size));
        }
        Sort order = switch (sort == null ? "" : sort) {
            case "litres_desc" -> Sort.by(Sort.Order.desc("milkQuantityLitres"), Sort.Order.desc("id"));
            case "litres_asc" -> Sort.by(Sort.Order.asc("milkQuantityLitres"), Sort.Order.desc("id"));
            case "amount_desc" -> Sort.by(Sort.Order.desc("netAmount"), Sort.Order.desc("id"));
            case "amount_asc" -> Sort.by(Sort.Order.asc("netAmount"), Sort.Order.desc("id"));
            case "date_asc" -> Sort.by(Sort.Order.asc("collectionDate"), Sort.Order.asc("id"));
            default -> Sort.by(Sort.Order.desc("collectionDate"), Sort.Order.desc("id"));
        };
        String shiftFilter = shift == null || shift.isBlank() ? null : shift;
        return collections.browse(from, to, shiftFilter, status, filterProducers, ids,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), order));
    }

    /**
     * Owner correction of a collection's litres / FAT / SNF. Blocked once the milk is part of a
     * settlement (or voided). If FAT + SNF are known the rate and amount are recomputed.
     */
    @Transactional
    public MilkCollection updateCollection(UUID publicId, BigDecimal litres, BigDecimal fat, BigDecimal snf) {
        MilkCollection c = requireByPublicId(publicId);
        if (c.getStatus() == MilkCollection.Status.VOID) {
            throw new BusinessRuleException("A voided collection cannot be edited");
        }
        if (c.getSettlementItemId() != null) {
            throw new BusinessRuleException(
                    "This collection is already part of a settlement run — cancel or recalculate that run first");
        }
        if (litres == null || litres.signum() <= 0) {
            throw new BusinessRuleException("Litres must be greater than zero");
        }
        if ((fat == null) != (snf == null)) {
            throw new BusinessRuleException("Enter both FAT and SNF, or leave both blank to keep the current quality");
        }
        BigDecimal effFat = fat != null ? fat : c.getFatPercentage();
        BigDecimal effSnf = snf != null ? snf : c.getSnfPercentage();
        boolean wasPending = c.isQualityPending();
        BigDecimal oldLitres = c.getMilkQuantityLitres();
        BigDecimal oldNet = c.getNetAmount();

        c.setMilkQuantityLitres(litres);
        long producerId = c.getProducerId();
        ProducerView producer = producers.findById(producerId)
                .orElseThrow(() -> new NotFoundException("Producer " + producerId + " not found"));
        if (effFat != null && effSnf != null) {
            priceCollection(c, producer, centerService.settings(), c.getCollectionDate(), litres, effFat, effSnf);
            c.setStatus(MilkCollection.Status.CONFIRMED);
        }
        collections.saveAndFlush(c);

        audit.record("COLLECTION_UPDATED", "MilkCollection", c.getId(),
                "Collection %s edited by owner: litres %s -> %s, FAT %s, SNF %s, amount %s -> %s".formatted(
                        c.getCollectionNumber(), oldLitres, litres, effFat, effSnf, oldNet, c.getNetAmount()));
        if (wasPending && !c.isQualityPending()) {
            publishCreatedNotification(c, producer);
        }
        return c;
    }

    public MilkCollection requireByPublicId(UUID publicId) {
        return collections.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Collection " + publicId + " not found"));
    }

    /** Build an API response, enriched with the producer's number + name (for challan printing). */
    public CollectionResponse toResponse(MilkCollection c, boolean duplicate) {
        ProducerView pv = producers.findById(c.getProducerId()).orElse(null);
        return CollectionResponse.from(c,
                pv == null ? null : pv.producerNumber(),
                pv == null ? null : pv.name(),
                duplicate);
    }

    @Transactional
    public RecordResult record(RecordCollectionCommand cmd) {
        UUID clientTxnId = cmd.clientTransactionId() != null ? cmd.clientTransactionId() : UUID.randomUUID();

        var existing = collections.findByClientTransactionId(clientTxnId);
        if (existing.isPresent()) {
            return new RecordResult(existing.get(), true);
        }

        ProducerView producer = producers.resolve(cmd.producerReference())
                .orElseThrow(() -> new NotFoundException(
                        "Producer '" + cmd.producerReference() + "' not found"));
        if (!producer.active()) {
            throw new BusinessRuleException("Producer " + producer.producerNumber() + " is inactive");
        }

        CenterSettings settings = centerService.settings();
        if (cmd.collectionDate().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("Collection date cannot be in the future");
        }

        // Milk quantity is enough to record a collection; FAT / SNF (and therefore
        // the rate and amount) can be added afterwards — see updateQuality().
        boolean qualityPending = cmd.fatPercentage() == null || cmd.snfPercentage() == null;

        MilkCollection c = new MilkCollection();
        c.setClientTransactionId(clientTxnId);
        c.setCollectionNumber(numberGenerator.next(cmd.collectionDate(), cmd.shift()));
        c.setProducerId(producer.id());
        c.setRegionId(producer.regionId());
        c.setCollectionDate(cmd.collectionDate());
        c.setShift(cmd.shift());
        c.setMilkQuantityLitres(cmd.litres());
        c.setSource(cmd.source() == null ? MilkCollection.Source.ONLINE : cmd.source());

        if (qualityPending) {
            c.setStatus(MilkCollection.Status.PENDING_QUALITY);
            c.setTsValueApplied(BigDecimal.ZERO);
            c.setApplicableRate(BigDecimal.ZERO);
            c.setIncentiveRateApplied(BigDecimal.ZERO);
            c.setBaseAmount(BigDecimal.ZERO);
            c.setIncentiveAmount(BigDecimal.ZERO);
            c.setDeductionAmount(BigDecimal.ZERO);
            c.setNetAmount(BigDecimal.ZERO);
            Map<String, Object> pendingSnap = new HashMap<>();
            pendingSnap.put("engine", "collection-1.0.0");
            pendingSnap.put("qualityPending", true);
            pendingSnap.put("producerNumber", producer.producerNumber());
            pendingSnap.put("regionId", producer.regionId());
            pendingSnap.put("litres", cmd.litres());
            c.setCalculationSnapshot(pendingSnap);
        } else {
            priceCollection(c, producer, settings, cmd.collectionDate(), cmd.litres(),
                    cmd.fatPercentage(), cmd.snfPercentage());
            c.setStatus(MilkCollection.Status.CONFIRMED);
        }

        try {
            c = collections.saveAndFlush(c);
        } catch (DataIntegrityViolationException race) {
            // concurrent submit of the same client_transaction_id
            MilkCollection winner = collections.findByClientTransactionId(clientTxnId)
                    .orElseThrow(() -> race);
            return new RecordResult(winner, true);
        }

        writeSyncLog(cmd, clientTxnId, CollectionSyncLog.Outcome.APPLIED, c.getId(), null);
        audit.record("COLLECTION_CREATED", "MilkCollection", c.getId(),
                "Collection " + c.getCollectionNumber() + " for producer " + producer.producerNumber()
                        + (c.isQualityPending() ? " (quality pending)" : ""));

        // The confirmation SMS carries the amount, so it is sent only once the
        // amount is known — now, or later from updateQuality().
        if (!c.isQualityPending()) {
            publishCreatedNotification(c, producer);
        }

        return new RecordResult(c, false);
    }

    /**
     * Add FAT / SNF to a collection that was recorded with litres only. The rate
     * and amount are worked out now; the collection moves to CONFIRMED and the
     * farmer's confirmation message goes out. Also used to correct the quality of
     * a not-yet-settled collection.
     */
    @Transactional
    public MilkCollection updateQuality(UUID publicId, BigDecimal fat, BigDecimal snf) {
        MilkCollection c = requireByPublicId(publicId);
        if (c.getStatus() == MilkCollection.Status.VOID) {
            throw new BusinessRuleException("A voided collection cannot be updated");
        }
        if (c.getSettlementItemId() != null) {
            throw new BusinessRuleException("This collection is already part of a settlement");
        }
        boolean wasPending = c.isQualityPending();
        long producerId = c.getProducerId();
        ProducerView producer = producers.findById(producerId)
                .orElseThrow(() -> new NotFoundException("Producer " + producerId + " not found"));

        priceCollection(c, producer, centerService.settings(), c.getCollectionDate(),
                c.getMilkQuantityLitres(), fat, snf);
        c.setStatus(MilkCollection.Status.CONFIRMED);
        try {
            collections.saveAndFlush(c);
        } catch (DataIntegrityViolationException dup) {
            throw new BusinessRuleException(
                    "A confirmed collection already exists for this producer, date and shift");
        }

        audit.record("COLLECTION_QUALITY_UPDATED", "MilkCollection", c.getId(),
                "Quality set for " + c.getCollectionNumber() + " (FAT " + fat + ", SNF " + snf + ")");
        if (wasPending) {
            publishCreatedNotification(c, producer);
        }
        return c;
    }

    /** Resolve the rate + incentive for the given quality and fill the amount fields on {@code c}. */
    private void priceCollection(MilkCollection c, ProducerView producer, CenterSettings settings,
                                 LocalDate date, BigDecimal litres, BigDecimal fat, BigDecimal snf) {
        if (!settings.fatInRange(fat)) {
            throw new BusinessRuleException("FAT " + fat + " is outside the allowed range");
        }
        if (!settings.snfInRange(snf)) {
            throw new BusinessRuleException("SNF " + snf + " is outside the allowed range");
        }

        ResolvedRate rate = rateResolution.resolve(new ResolveRateQuery(
                producer.regionId(), date, fat, snf, settings.allowMissingRate()));

        IncentiveResult incentive = incentiveCalculator.calculate(new IncentiveQuery(
                producer.id(), producer.regionId(), date, litres,
                settings.incentiveStacking(), settings.incentiveMaxPerLitre()));

        var breakdown = amountCalculator.calculate(new CollectionAmountCalculator.CollectionAmountInput(
                litres, rate, incentive, BigDecimal.ZERO));

        c.setFatPercentage(fat);
        c.setSnfPercentage(snf);
        c.setRateChartId(rate.rateChartId());
        c.setTsValueApplied(breakdown.tsValueApplied());
        c.setRateScopeApplied(rate.scope().name());
        c.setApplicableRate(breakdown.applicableRate());
        c.setIncentiveRateApplied(breakdown.incentiveRateApplied());
        c.setBaseAmount(breakdown.baseAmount());
        c.setIncentiveAmount(breakdown.incentiveAmount());
        c.setDeductionAmount(breakdown.deductionAmount());
        c.setNetAmount(breakdown.netAmount());
        c.setCalculationSnapshot(snapshot(litres, fat, snf, producer, rate, incentive, breakdown));
    }

    private void publishCreatedNotification(MilkCollection c, ProducerView producer) {
        Map<String, Object> notifyData = new HashMap<>();
        notifyData.put("producerName", producer.name());
        notifyData.put("producerNumber", producer.producerNumber());
        notifyData.put("date", c.getCollectionDate().toString());
        notifyData.put("shift", c.getShift());
        notifyData.put("litres", c.getMilkQuantityLitres());
        notifyData.put("fat", c.getFatPercentage());
        notifyData.put("snf", c.getSnfPercentage());
        notifyData.put("ratePerLitre", c.getApplicableRate());
        notifyData.put("milkAmount", c.getBaseAmount());
        notifyData.put("incentiveAmount", c.getIncentiveAmount());
        notifyData.put("deductionAmount", c.getDeductionAmount());
        notifyData.put("amount", c.getNetAmount());
        notifyData.put("collectionNumber", c.getCollectionNumber());
        notifications.publish(new NotificationRequest("COLLECTION_CREATED", "COLLECTION_CREATED",
                producer.id(), null, "MilkCollection", c.getId(), notifyData));
    }

    @Transactional
    public MilkCollection voidCollection(UUID publicId, String reason) {
        MilkCollection c = requireByPublicId(publicId);
        if (c.getStatus() != MilkCollection.Status.CONFIRMED) {
            throw new BusinessRuleException("Only a CONFIRMED collection can be voided");
        }
        if (c.getSettlementItemId() != null) {
            throw new BusinessRuleException("This collection is already part of a settlement");
        }
        c.setStatus(MilkCollection.Status.VOID);
        c.setVoidReason(reason);
        c.setVoidedAt(Instant.now());
        CurrentUser.id().ifPresent(c::setVoidedBy);
        audit.record("COLLECTION_UPDATED", "MilkCollection", c.getId(),
                "Collection " + c.getCollectionNumber() + " voided: " + reason);
        return c;
    }

    // ---- CollectionSettlementGateway ----

    @Override
    public int pendingQualityCount(LocalDate periodStart, LocalDate periodEnd) {
        return (int) collections.countByStatusAndCollectionDateBetween(
                MilkCollection.Status.PENDING_QUALITY, periodStart, periodEnd);
    }

    @Override
    public int pendingQualityCount(LocalDate periodStart, LocalDate periodEnd, long regionId) {
        return (int) collections.countByStatusAndCollectionDateBetweenAndRegionId(
                MilkCollection.Status.PENDING_QUALITY, periodStart, periodEnd, regionId);
    }

    @Override
    public List<SettlementCollectionRow> unsettledConfirmed(LocalDate periodStart, LocalDate periodEnd) {
        return collections.findByStatusAndSettlementItemIdIsNullAndCollectionDateBetween(
                        MilkCollection.Status.CONFIRMED, periodStart, periodEnd).stream()
                .map(c -> new SettlementCollectionRow(c.getId(), c.getProducerId(), c.getRegionId(),
                        c.getShift(), c.getMilkQuantityLitres(), c.getBaseAmount(), c.getIncentiveAmount()))
                .toList();
    }

    @Override
    @Transactional
    public void attachToSettlementItem(java.util.Collection<Long> collectionIds, long settlementItemId) {
        collections.findAllById(collectionIds).forEach(c -> c.setSettlementItemId(settlementItemId));
    }

    @Override
    @Transactional
    public void detachFromSettlement(long settlementItemId) {
        collections.findBySettlementItemId(settlementItemId)
                .forEach(c -> c.setSettlementItemId(null));
    }

    @Override
    public List<SettlementItemCollection> collectionsForSettlementItem(long settlementItemId) {
        return collections.findBySettlementItemId(settlementItemId).stream()
                .sorted(java.util.Comparator
                        .comparing(MilkCollection::getCollectionDate)
                        .thenComparing(MilkCollection::getShift))
                .map(c -> new SettlementItemCollection(c.getId(), c.getCollectionNumber(),
                        c.getCollectionDate(), c.getShift(), c.getMilkQuantityLitres(),
                        c.getFatPercentage(), c.getSnfPercentage(), c.getApplicableRate(),
                        c.getBaseAmount(), c.getIncentiveAmount()))
                .toList();
    }

    private Map<String, Object> snapshot(BigDecimal litres, BigDecimal fat, BigDecimal snf,
                                         ProducerView producer, ResolvedRate rate, IncentiveResult incentive,
                                         CollectionAmountCalculator.CollectionAmountBreakdown b) {
        Map<String, Object> s = new HashMap<>();
        s.put("engine", "collection-1.0.0");
        s.put("producerNumber", producer.producerNumber());
        s.put("regionId", producer.regionId());
        s.put("litres", litres);
        s.put("fat", fat);
        s.put("snf", snf);
        s.put("tsValue", b.tsValueApplied());
        s.put("rateScope", rate.scope().name());
        s.put("rateMissing", rate.missing());
        s.put("applicableRate", b.applicableRate());
        s.put("incentiveCommonRate", incentive.commonRatePerLitre());
        s.put("incentiveIndividualRate", incentive.individualRatePerLitre());
        s.put("incentiveEffectiveRate", incentive.effectiveRatePerLitre());
        s.put("incentiveCappedByMax", incentive.cappedByMax());
        s.put("baseAmount", b.baseAmount());
        s.put("incentiveAmount", b.incentiveAmount());
        s.put("deductionAmount", b.deductionAmount());
        s.put("netAmount", b.netAmount());
        return s;
    }

    private void writeSyncLog(RecordCollectionCommand cmd, UUID clientTxnId,
                              CollectionSyncLog.Outcome outcome, Long collectionId, String error) {
        CollectionSyncLog logRow = new CollectionSyncLog();
        logRow.setClientTransactionId(clientTxnId);
        logRow.setDeviceId(cmd.deviceId());
        logRow.setOutcome(outcome);
        logRow.setCollectionId(collectionId);
        logRow.setErrorDetail(error);
        Map<String, Object> payload = new HashMap<>();
        payload.put("producerReference", cmd.producerReference());
        payload.put("collectionDate", cmd.collectionDate().toString());
        payload.put("shift", cmd.shift());
        payload.put("litres", cmd.litres());
        payload.put("fat", cmd.fatPercentage());
        payload.put("snf", cmd.snfPercentage());
        logRow.setPayload(payload);
        syncLog.save(logRow);
    }

    public record RecordCollectionCommand(
            UUID clientTransactionId,
            String producerReference,
            LocalDate collectionDate,
            String shift,
            BigDecimal litres,
            BigDecimal fatPercentage,
            BigDecimal snfPercentage,
            MilkCollection.Source source,
            String deviceId) {
    }

    public record RecordResult(MilkCollection collection, boolean duplicate) {
    }

    @Override
    public List<RouteDay> litresByDay(java.util.Collection<Long> regionIds, LocalDate from, LocalDate to) {
        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }
        return collections.routeLitres(regionIds, from, to).stream()
                .map(r -> new RouteDay((LocalDate) r[0], (Long) r[1], (String) r[2], (BigDecimal) r[3],
                        ((Number) r[4]).intValue()))
                .toList();
    }
}
