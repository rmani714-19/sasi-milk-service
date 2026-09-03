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
import org.springframework.data.domain.Pageable;
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
public class CollectionService implements CollectionSettlementGateway {

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
        if (!settings.fatInRange(cmd.fatPercentage())) {
            throw new BusinessRuleException("FAT " + cmd.fatPercentage() + " is outside the allowed range");
        }
        if (!settings.snfInRange(cmd.snfPercentage())) {
            throw new BusinessRuleException("SNF " + cmd.snfPercentage() + " is outside the allowed range");
        }
        if (cmd.collectionDate().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("Collection date cannot be in the future");
        }

        ResolvedRate rate = rateResolution.resolve(new ResolveRateQuery(
                producer.regionId(), cmd.collectionDate(),
                cmd.fatPercentage(), cmd.snfPercentage(), settings.allowMissingRate()));

        IncentiveResult incentive = incentiveCalculator.calculate(new IncentiveQuery(
                producer.id(), producer.regionId(), cmd.collectionDate(), cmd.litres(),
                settings.incentiveStacking(), settings.incentiveMaxPerLitre()));

        var breakdown = amountCalculator.calculate(new CollectionAmountCalculator.CollectionAmountInput(
                cmd.litres(), rate, incentive, BigDecimal.ZERO));

        MilkCollection c = new MilkCollection();
        c.setClientTransactionId(clientTxnId);
        c.setCollectionNumber(numberGenerator.next(cmd.collectionDate(), cmd.shift()));
        c.setProducerId(producer.id());
        c.setRegionId(producer.regionId());
        c.setCollectionDate(cmd.collectionDate());
        c.setShift(cmd.shift());
        c.setMilkQuantityLitres(cmd.litres());
        c.setFatPercentage(cmd.fatPercentage());
        c.setSnfPercentage(cmd.snfPercentage());
        c.setRateChartId(rate.rateChartId());
        c.setTsValueApplied(breakdown.tsValueApplied());
        c.setRateScopeApplied(rate.scope().name());
        c.setApplicableRate(breakdown.applicableRate());
        c.setIncentiveRateApplied(breakdown.incentiveRateApplied());
        c.setBaseAmount(breakdown.baseAmount());
        c.setIncentiveAmount(breakdown.incentiveAmount());
        c.setDeductionAmount(breakdown.deductionAmount());
        c.setNetAmount(breakdown.netAmount());
        c.setSource(cmd.source() == null ? MilkCollection.Source.ONLINE : cmd.source());
        c.setCalculationSnapshot(snapshot(cmd, producer, rate, incentive, breakdown));

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
                "Collection " + c.getCollectionNumber() + " for producer " + producer.producerNumber());

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

        return new RecordResult(c, false);
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

    private Map<String, Object> snapshot(RecordCollectionCommand cmd, ProducerView producer,
                                         ResolvedRate rate, IncentiveResult incentive,
                                         CollectionAmountCalculator.CollectionAmountBreakdown b) {
        Map<String, Object> s = new HashMap<>();
        s.put("engine", "collection-1.0.0");
        s.put("producerNumber", producer.producerNumber());
        s.put("regionId", producer.regionId());
        s.put("litres", cmd.litres());
        s.put("fat", cmd.fatPercentage());
        s.put("snf", cmd.snfPercentage());
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
}
