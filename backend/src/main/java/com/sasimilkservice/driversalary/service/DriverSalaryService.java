package com.sasimilkservice.driversalary.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.money.MoneyMath;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.driver.domain.Driver;
import com.sasimilkservice.driver.repository.DriverRepository;
import com.sasimilkservice.driversalary.domain.DriverSalaryBatch;
import com.sasimilkservice.driversalary.domain.DriverSalaryBatch.Status;
import com.sasimilkservice.driversalary.domain.DriverSalaryItem;
import com.sasimilkservice.driversalary.domain.DriverSalaryItem.PaymentMethod;
import com.sasimilkservice.driversalary.domain.DriverSalaryItem.PaymentStatus;
import com.sasimilkservice.driversalary.repository.DriverSalaryBatchRepository;
import com.sasimilkservice.driversalary.repository.DriverSalaryItemRepository;
import com.sasimilkservice.paymentops.domain.BankBalanceCheck;
import com.sasimilkservice.paymentops.service.PaymentOpsService;
import com.sasimilkservice.paymentops.service.PreValidation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DriverSalaryService {

    private static final Set<Status> EDITABLE = EnumSet.of(Status.DRAFT, Status.CALCULATED, Status.REVIEWED);
    private static final Set<Status> PAYABLE = EnumSet.of(Status.APPROVED, Status.PAYMENT_INITIATED, Status.PARTIALLY_PAID);

    private final DriverSalaryBatchRepository batches;
    private final DriverSalaryItemRepository items;
    private final DriverRepository drivers;
    private final PaymentOpsService paymentOps;
    private final AuditRecorder audit;

    public DriverSalaryService(DriverSalaryBatchRepository batches, DriverSalaryItemRepository items,
                               DriverRepository drivers, PaymentOpsService paymentOps, AuditRecorder audit) {
        this.batches = batches;
        this.items = items;
        this.drivers = drivers;
        this.paymentOps = paymentOps;
        this.audit = audit;
    }

    public List<DriverSalaryBatch> list() {
        return batches.findAllByOrderByPeriodMonthDescIdDesc();
    }

    public DriverSalaryBatch require(long id) {
        return batches.findById(id).orElseThrow(() -> NotFoundException.of("DriverSalaryBatch", id));
    }

    public List<DriverSalaryItem> items(long batchId) {
        return items.findByBatchIdOrderByIdAsc(batchId);
    }

    /** Flat salary-payment history joined with driver + batch, newest first. */
    public List<SalaryPaymentRow> paymentHistory() {
        Map<Long, DriverSalaryBatch> batchById = batches.findAll().stream()
                .collect(Collectors.toMap(DriverSalaryBatch::getId, Function.identity()));
        Map<Long, Driver> driverById = drivers.findAll().stream()
                .collect(Collectors.toMap(Driver::getId, Function.identity()));
        return items.findAllByOrderByIdDesc().stream().filter(i -> {
            DriverSalaryBatch b = batchById.get(i.getBatchId());
            return b == null || b.getStatus() != Status.CANCELLED;
        }).map(i -> {
            DriverSalaryBatch b = batchById.get(i.getBatchId());
            Driver d = driverById.get(i.getDriverId());
            return new SalaryPaymentRow(
                    i.getId(),
                    b == null ? null : b.getBatchNumber(),
                    b == null ? null : b.getPeriodMonth(),
                    d == null ? ("#" + i.getDriverId()) : d.getCode(),
                    d == null ? ("Driver #" + i.getDriverId()) : d.getName(),
                    i.getNetSalary(),
                    i.getPaymentStatus().name(),
                    i.getPaymentMethod() == null ? null : i.getPaymentMethod().name(),
                    i.getPaymentReference(),
                    i.getPaidAt());
        }).toList();
    }

    public record SalaryPaymentRow(
            Long itemId, String batchNumber, LocalDate periodMonth, String driverCode, String driverName,
            BigDecimal netSalary, String paymentStatus, String paymentMethod, String paymentReference,
            Instant paidAt) {
    }

    @Transactional
    public DriverSalaryBatch createBatch(LocalDate anyDayInMonth) {
        LocalDate month = anyDayInMonth.withDayOfMonth(1);
        batches.findByPeriodMonthAndStatusNot(month, Status.CANCELLED).ifPresent(b -> {
            throw new BusinessRuleException("A salary batch for " + month + " already exists (" + b.getBatchNumber() + ")");
        });
        DriverSalaryBatch b = new DriverSalaryBatch();
        b.setPeriodMonth(month);
        b.setBatchNumber("DS-%d%02d-%03d".formatted(month.getYear(), month.getMonthValue(),
                batches.nextBatchNumberValue()));
        b.setStatus(Status.DRAFT);
        b = batches.save(b);
        audit.record("SALARY_BATCH_CREATED", "DriverSalaryBatch", b.getId(),
                "Driver salary batch " + b.getBatchNumber() + " for " + month);
        return b;
    }

    @Transactional
    public DriverSalaryBatch calculate(long batchId) {
        DriverSalaryBatch b = require(batchId);
        if (!EDITABLE.contains(b.getStatus())) {
            throw new BusinessRuleException("Salary batch " + b.getBatchNumber() + " cannot be recalculated in state " + b.getStatus());
        }
        int daysInMonth = b.getPeriodMonth().lengthOfMonth();
        Map<Long, DriverSalaryItem> existing = items(batchId).stream()
                .collect(Collectors.toMap(DriverSalaryItem::getDriverId, Function.identity()));
        Set<Long> activeIds = drivers.findByStatusOrderByNameAsc(Driver.Status.ACTIVE).stream()
                .map(Driver::getId).collect(Collectors.toSet());

        for (Driver d : drivers.findByStatusOrderByNameAsc(Driver.Status.ACTIVE)) {
            DriverSalaryItem it = existing.get(d.getId());
            if (it == null) {
                it = new DriverSalaryItem();
                it.setBatchId(batchId);
                it.setDriverId(d.getId());
                it.setVehicleId(d.getVehicleId());
                it.setSalaryType(d.getSalaryType().name());
                it.setWorkingDays(new BigDecimal(daysInMonth));
                it.setBasicSalary(d.getSalaryType() == Driver.SalaryType.PER_DAY
                        ? nz(d.getPerDayRate()) : nz(d.getMonthlySalary()));
                recomputeItem(it);
                items.save(it);
            } else if (it.getPaymentStatus() == PaymentStatus.PENDING) {
                recomputeItem(it); // keep operator-edited components, refresh derived totals
            }
        }
        // drop items for drivers that are no longer active AND unpaid
        for (DriverSalaryItem it : items(batchId)) {
            if (!activeIds.contains(it.getDriverId()) && it.getPaymentStatus() == PaymentStatus.PENDING) {
                items.delete(it);
            }
        }
        recomputeBatch(b);
        b.setStatus(Status.CALCULATED);
        b.setCalculatedAt(Instant.now());
        CurrentUser.id().ifPresent(b::setCalculatedBy);
        audit.record("SALARY_BATCH_CALCULATED", "DriverSalaryBatch", batchId,
                b.getBatchNumber() + ": " + b.getTotalDrivers() + " drivers, net " + b.getTotalNet());
        return b;
    }

    @Transactional
    public DriverSalaryItem updateItem(long itemId, ItemComponents c) {
        DriverSalaryItem it = requireItem(itemId);
        DriverSalaryBatch b = require(it.getBatchId());
        if (!EDITABLE.contains(b.getStatus())) {
            throw new BusinessRuleException("Salary batch is " + b.getStatus() + " — items can no longer be edited");
        }
        if (it.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BusinessRuleException("This salary line is already " + it.getPaymentStatus());
        }
        it.setWorkingDays(nz(c.workingDays()).max(BigDecimal.ZERO));
        it.setBasicSalary(nz(c.basicSalary()).max(BigDecimal.ZERO));
        it.setAllowances(nz(c.allowances()).max(BigDecimal.ZERO));
        it.setOvertime(nz(c.overtime()).max(BigDecimal.ZERO));
        it.setOtherEarnings(nz(c.otherEarnings()).max(BigDecimal.ZERO));
        it.setSalaryAdvance(nz(c.salaryAdvance()).max(BigDecimal.ZERO));
        it.setLoanRecovery(nz(c.loanRecovery()).max(BigDecimal.ZERO));
        it.setPenalty(nz(c.penalty()).max(BigDecimal.ZERO));
        it.setOtherDeduction(nz(c.otherDeduction()).max(BigDecimal.ZERO));
        it.setNote(c.note() == null || c.note().isBlank() ? null : c.note().trim());
        recomputeItem(it);
        recomputeBatch(b);
        audit.record("SALARY_ITEM_UPDATED", "DriverSalaryItem", itemId,
                "batch " + b.getBatchNumber() + " driver " + it.getDriverId() + " net " + it.getNetSalary());
        return it;
    }

    @Transactional
    public DriverSalaryBatch review(long batchId) {
        DriverSalaryBatch b = require(batchId);
        if (b.getStatus() != Status.CALCULATED) {
            throw new BusinessRuleException("Only a calculated batch can be reviewed");
        }
        b.setStatus(Status.REVIEWED);
        b.setReviewedAt(Instant.now());
        CurrentUser.id().ifPresent(b::setReviewedBy);
        audit.record("SALARY_BATCH_REVIEWED", "DriverSalaryBatch", batchId, b.getBatchNumber() + " reviewed");
        return b;
    }

    /** Pre-approval checklist for a salary batch. */
    public PreValidation.Report preValidate(long batchId) {
        DriverSalaryBatch b = require(batchId);
        List<DriverSalaryItem> lines = items(batchId);
        java.math.BigDecimal sumNet = lines.stream().map(DriverSalaryItem::getNetSalary)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        Map<Long, Driver> byId = drivers.findAll().stream()
                .collect(Collectors.toMap(Driver::getId, Function.identity()));
        long missingBank = lines.stream().filter(i -> {
            Driver d = byId.get(i.getDriverId());
            return d == null || !d.isBankComplete();
        }).count();
        long paidAlready = lines.stream()
                .filter(i -> i.getPaymentStatus() == DriverSalaryItem.PaymentStatus.PAID).count();
        var balance = paymentOps.latest(BankBalanceCheck.SubjectType.SALARY_BATCH, batchId);
        boolean balanceVerified = balance.isPresent();
        boolean balanceSufficient = paymentOps.balanceCovered(
                BankBalanceCheck.SubjectType.SALARY_BATCH, batchId, b.getTotalNet());

        return PreValidation.builder()
                .blocking("recipients", "All salary lines present",
                        !lines.isEmpty() && lines.size() == b.getTotalDrivers(),
                        "Recalculate the batch", "/payments/drivers")
                .blocking("totals_match", "Batch total matches line totals",
                        MoneyMath.money(sumNet).compareTo(MoneyMath.money(b.getTotalNet())) == 0,
                        "Sum of lines " + MoneyMath.money(sumNet) + " != batch " + b.getTotalNet(), null)
                .blocking("bank_balance_verified", "Bank balance verified", balanceVerified,
                        "Record the owner's available bank balance", "/payments/drivers")
                .blocking("bank_balance_sufficient", "Sufficient bank balance", balanceSufficient,
                        "Latest balance check does not cover " + MoneyMath.money(b.getTotalNet()), "/payments/drivers")
                .info("bank_details", "Bank details complete for every driver", missingBank == 0,
                        missingBank == 0 ? "all set" : missingBank + " driver(s) incomplete — pay those by cash")
                .info("not_paid", "No line already paid", paidAlready == 0,
                        paidAlready == 0 ? "none" : paidAlready + " line(s) already PAID")
                .build();
    }

    @Transactional
    public DriverSalaryBatch approve(long batchId) {
        return approve(batchId, true);
    }

    @Transactional
    public DriverSalaryBatch approve(long batchId, boolean confirmed) {
        DriverSalaryBatch b = require(batchId);
        if (b.getStatus() != Status.REVIEWED) {
            throw new BusinessRuleException("Only a reviewed batch can be approved");
        }
        if (items(batchId).isEmpty()) {
            throw new BusinessRuleException("Nothing to approve — calculate the batch first");
        }
        if (!paymentOps.balanceCovered(BankBalanceCheck.SubjectType.SALARY_BATCH, batchId, b.getTotalNet())) {
            throw new BusinessRuleException("Owner bank balance is not verified as sufficient for "
                    + MoneyMath.money(b.getTotalNet()) + " — verify the bank balance before approval");
        }
        if (preValidate(batchId).result() != PreValidation.Result.READY_FOR_APPROVAL) {
            throw new BusinessRuleException("Pre-validation is BLOCKED — resolve the failing checks before approval");
        }
        if (!confirmed) {
            throw new BusinessRuleException("Approval must be explicitly confirmed by the owner / manager");
        }
        b.setStatus(Status.APPROVED);
        b.setApprovedAt(Instant.now());
        CurrentUser.id().ifPresent(b::setApprovedBy);
        audit.record("SALARY_BATCH_APPROVED", "DriverSalaryBatch", batchId,
                b.getBatchNumber() + " approved (owner-confirmed), net payable " + b.getTotalNet());
        return b;
    }

    @Transactional
    public DriverSalaryBatch cancel(long batchId) {
        DriverSalaryBatch b = require(batchId);
        if (b.getStatus() == Status.PAID || b.getStatus() == Status.CANCELLED) {
            throw new BusinessRuleException("Batch " + b.getBatchNumber() + " is " + b.getStatus());
        }
        boolean anyPaid = items(batchId).stream().anyMatch(i -> i.getPaymentStatus() == PaymentStatus.PAID);
        if (anyPaid) {
            throw new BusinessRuleException("Cannot cancel — some salaries in this batch are already paid");
        }
        b.setStatus(Status.CANCELLED);
        b.setCancelledAt(Instant.now());
        CurrentUser.id().ifPresent(b::setCancelledBy);
        audit.record("SALARY_BATCH_CANCELLED", "DriverSalaryBatch", batchId, b.getBatchNumber() + " cancelled");
        return b;
    }

    @Transactional
    public DriverSalaryItem pay(long itemId, PaymentMethod method, String reference, String note) {
        DriverSalaryItem it = requireItem(itemId);
        DriverSalaryBatch b = require(it.getBatchId());
        if (!PAYABLE.contains(b.getStatus())) {
            throw new BusinessRuleException("Batch " + b.getBatchNumber() + " must be approved before salaries can be paid");
        }
        if (it.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessRuleException("This salary is already paid (" + it.getPaymentReference() + ")");
        }
        if (it.getPaymentStatus() != PaymentStatus.PENDING && it.getPaymentStatus() != PaymentStatus.FAILED) {
            throw new BusinessRuleException("Salary line is " + it.getPaymentStatus() + " — cannot pay now");
        }
        PaymentMethod m = method == null ? PaymentMethod.BANK_TRANSFER : method;
        if (m == PaymentMethod.BANK_TRANSFER) {
            Driver driver = drivers.findById(it.getDriverId())
                    .orElseThrow(() -> NotFoundException.of("Driver", it.getDriverId()));
            if (!driver.isBankComplete()) {
                throw new BusinessRuleException(
                        "Bank details are incomplete. Update the driver's bank details before processing salary.");
            }
        }
        String ref = reference == null ? "" : reference.trim();
        if (ref.isEmpty()) {
            if (m == PaymentMethod.CASH) {
                ref = "CASH-" + b.getBatchNumber() + "-" + it.getDriverId();
            } else {
                throw new BusinessRuleException("A payment reference / UTR is required for " + m);
            }
        }
        it.setPaymentStatus(PaymentStatus.PAID);
        it.setPaymentMethod(m);
        it.setPaymentReference(ref);
        it.setPaidAt(Instant.now());
        it.setFailureReason(null);
        CurrentUser.id().ifPresent(it::setPaidBy);
        if (note != null && !note.isBlank()) {
            it.setNote(note.trim());
        }

        rollBatchPaymentState(b);
        audit.record("SALARY_PAID", "DriverSalaryItem", itemId,
                "batch " + b.getBatchNumber() + " driver " + it.getDriverId() + " paid " + it.getNetSalary()
                        + " via " + m + " ref " + ref);
        return it;
    }

    @Transactional
    public DriverSalaryItem markFailed(long itemId, String reason) {
        DriverSalaryItem it = requireItem(itemId);
        DriverSalaryBatch b = require(it.getBatchId());
        if (it.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessRuleException("A paid salary cannot be marked failed");
        }
        it.setPaymentStatus(PaymentStatus.FAILED);
        it.setFailureReason(reason == null || reason.isBlank() ? "payment failed" : reason.trim());
        rollBatchPaymentState(b);
        audit.record("SALARY_FAILED", "DriverSalaryItem", itemId,
                "batch " + b.getBatchNumber() + " driver " + it.getDriverId() + " failed: " + it.getFailureReason());
        return it;
    }

    @Transactional
    public DriverSalaryItem retry(long itemId) {
        DriverSalaryItem it = requireItem(itemId);
        DriverSalaryBatch b = require(it.getBatchId());
        if (it.getPaymentStatus() != PaymentStatus.FAILED) {
            throw new BusinessRuleException("Only a failed salary line can be retried");
        }
        it.setPaymentStatus(PaymentStatus.PENDING);
        it.setFailureReason(null);
        rollBatchPaymentState(b);
        audit.record("SALARY_RETRY", "DriverSalaryItem", itemId, "batch " + b.getBatchNumber() + " driver " + it.getDriverId());
        return it;
    }

    // ---------------------------------------------------------------- helpers

    private DriverSalaryItem requireItem(long id) {
        return items.findById(id).orElseThrow(() -> NotFoundException.of("DriverSalaryItem", id));
    }

    private void recomputeItem(DriverSalaryItem it) {
        BigDecimal base = "PER_DAY".equals(it.getSalaryType())
                ? nz(it.getBasicSalary()).multiply(nz(it.getWorkingDays()))
                : nz(it.getBasicSalary());
        BigDecimal gross = base.add(nz(it.getAllowances())).add(nz(it.getOvertime())).add(nz(it.getOtherEarnings()));
        BigDecimal ded = nz(it.getSalaryAdvance()).add(nz(it.getLoanRecovery()))
                .add(nz(it.getPenalty())).add(nz(it.getOtherDeduction()));
        BigDecimal net = gross.subtract(ded).max(BigDecimal.ZERO);
        it.setGrossAmount(MoneyMath.money(gross));
        it.setTotalDeduction(MoneyMath.money(ded));
        it.setNetSalary(MoneyMath.money(net));
    }

    private void recomputeBatch(DriverSalaryBatch b) {
        List<DriverSalaryItem> list = items(b.getId());
        b.setTotalDrivers(list.size());
        b.setTotalGross(sum(list, DriverSalaryItem::getGrossAmount));
        b.setTotalAllowances(sum(list, DriverSalaryItem::getAllowances));
        b.setTotalOvertime(sum(list, DriverSalaryItem::getOvertime));
        b.setTotalDeductions(sum(list, DriverSalaryItem::getTotalDeduction));
        b.setTotalNet(sum(list, DriverSalaryItem::getNetSalary));
        b.setTotalPaid(MoneyMath.money(list.stream()
                .filter(i -> i.getPaymentStatus() == PaymentStatus.PAID)
                .map(DriverSalaryItem::getNetSalary).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    private void rollBatchPaymentState(DriverSalaryBatch b) {
        recomputeBatch(b);
        List<DriverSalaryItem> list = items(b.getId());
        long paid = list.stream().filter(i -> i.getPaymentStatus() == PaymentStatus.PAID).count();
        long failed = list.stream().filter(i -> i.getPaymentStatus() == PaymentStatus.FAILED).count();
        if (paid == 0 && failed == 0) {
            b.setStatus(Status.APPROVED);
        } else if (paid == list.size()) {
            b.setStatus(Status.PAID);
        } else if (paid > 0) {
            b.setStatus(Status.PARTIALLY_PAID);
        } else {
            b.setStatus(Status.PAYMENT_INITIATED);
        }
    }

    private static BigDecimal sum(List<DriverSalaryItem> list, Function<DriverSalaryItem, BigDecimal> f) {
        return MoneyMath.money(list.stream().map(f).map(DriverSalaryService::nz).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public record ItemComponents(
            BigDecimal workingDays, BigDecimal basicSalary, BigDecimal allowances, BigDecimal overtime,
            BigDecimal otherEarnings, BigDecimal salaryAdvance, BigDecimal loanRecovery, BigDecimal penalty,
            BigDecimal otherDeduction, String note) {
    }
}
