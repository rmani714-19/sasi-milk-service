package com.sasimilkservice.driversalary.statement;

import com.sasimilkservice.center.spi.CenterProfileDirectory;
import com.sasimilkservice.collection.spi.RouteCollectionQuery;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.driver.domain.Driver;
import com.sasimilkservice.driver.repository.DriverRepository;
import com.sasimilkservice.driversalary.domain.DriverAdvance;
import com.sasimilkservice.driversalary.domain.DriverSalaryBatch;
import com.sasimilkservice.driversalary.domain.DriverSalaryItem;
import com.sasimilkservice.driversalary.repository.DriverAdvanceRepository;
import com.sasimilkservice.driversalary.service.DriverSalaryService;
import com.sasimilkservice.expense.spi.VehicleExpenseQuery;
import com.sasimilkservice.region.spi.RegionDirectory;
import com.sasimilkservice.vehicle.spi.VehicleStatusQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds and renders driver salary statements. Read-only — it never changes a salary line. */
@Service
@Transactional(readOnly = true)
public class SalaryStatementService {

    private final DriverSalaryService salaries;
    private final DriverRepository drivers;
    private final DriverAdvanceRepository advances;
    private final RouteCollectionQuery routeCollections;
    private final VehicleExpenseQuery vehicleExpenses;
    private final RegionDirectory regions;
    private final VehicleStatusQuery vehicles;
    private final CenterProfileDirectory center;
    private final SalaryStatementPdfRenderer renderer;

    public SalaryStatementService(DriverSalaryService salaries, DriverRepository drivers,
                                  DriverAdvanceRepository advances, RouteCollectionQuery routeCollections,
                                  VehicleExpenseQuery vehicleExpenses, RegionDirectory regions,
                                  VehicleStatusQuery vehicles, CenterProfileDirectory center,
                                  SalaryStatementPdfRenderer renderer) {
        this.salaries = salaries;
        this.drivers = drivers;
        this.advances = advances;
        this.routeCollections = routeCollections;
        this.vehicleExpenses = vehicleExpenses;
        this.regions = regions;
        this.vehicles = vehicles;
        this.center = center;
        this.renderer = renderer;
    }

    public byte[] statementPdf(long batchId, long itemId) {
        DriverSalaryBatch b = salaries.require(batchId);
        DriverSalaryItem it = salaries.items(batchId).stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> NotFoundException.of("DriverSalaryItem", itemId));
        return renderer.render(List.of(assemble(b, it)));
    }

    public byte[] allStatementsPdf(long batchId) {
        DriverSalaryBatch b = salaries.require(batchId);
        List<SalaryStatementData> all = new ArrayList<>();
        for (DriverSalaryItem it : salaries.items(batchId)) {
            all.add(assemble(b, it));
        }
        if (all.isEmpty()) {
            throw new NotFoundException("This salary batch has no driver lines — calculate it first");
        }
        return renderer.render(all);
    }

    public String fileName(long batchId, long itemId) {
        DriverSalaryBatch b = salaries.require(batchId);
        return salaries.items(batchId).stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .flatMap(i -> drivers.findById(i.getDriverId()))
                .map(d -> "salary-statement-" + d.getCode() + "-" + b.getPeriodMonth().toString().substring(0, 7))
                .orElse("salary-statement");
    }

    private SalaryStatementData assemble(DriverSalaryBatch b, DriverSalaryItem it) {
        Driver d = drivers.findById(it.getDriverId())
                .orElseThrow(() -> NotFoundException.of("Driver", it.getDriverId()));
        LocalDate from = b.getPeriodMonth();
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        CenterProfileDirectory.CenterProfile c = center.profile();

        // route: litres per day and region, morning and evening side by side
        Map<Long, String> regionNames = new LinkedHashMap<>();
        for (Long rid : new java.util.TreeSet<>(d.getRouteRegionIds())) {
            regionNames.put(rid, regions.findById(rid).map(RegionDirectory.RegionView::name).orElse("Region " + rid));
        }
        Map<String, BigDecimal[]> byDayRegion = new TreeMap<>();
        for (RouteCollectionQuery.RouteDay r : routeCollections.litresByDay(d.getRouteRegionIds(), from, to)) {
            String key = r.date() + "|" + r.regionId();
            BigDecimal[] cell = byDayRegion.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            cell["MORNING".equals(r.shift()) ? 0 : 1] = cell["MORNING".equals(r.shift()) ? 0 : 1].add(r.litres());
        }
        List<SalaryStatementData.RouteLine> route = new ArrayList<>();
        byDayRegion.forEach((k, v) -> {
            String[] parts = k.split("\\|");
            route.add(new SalaryStatementData.RouteLine(LocalDate.parse(parts[0]),
                    regionNames.getOrDefault(Long.parseLong(parts[1]), "Region " + parts[1]), v[0], v[1]));
        });

        List<SalaryStatementData.ExpenseLine> expenses = it.getVehicleId() == null ? List.of()
                : vehicleExpenses.forVehicle(it.getVehicleId(), from, to).stream()
                .map(e -> new SalaryStatementData.ExpenseLine(e.date(), e.category(), e.description(), e.amount()))
                .toList();

        List<SalaryStatementData.AdvanceLine> adv = new ArrayList<>();
        for (DriverAdvance a : advances.findByDriverIdOrderByAdvanceDateDescIdDesc(d.getId())) {
            boolean thisRun = it.getId().equals(a.getDeductedInItemId());
            boolean pending = a.getStatus() == DriverAdvance.Status.OPEN && !a.getAdvanceDate().isAfter(to);
            if (thisRun || pending) {
                adv.add(new SalaryStatementData.AdvanceLine(a.getAdvanceDate(), a.getReason(), a.getAmount(),
                        thisRun ? "Deducted in this salary" : "To be deducted"));
            }
        }
        java.util.Collections.reverse(adv);

        String vehicle = it.getVehicleId() == null ? null : vehicles.allStatuses().stream()
                .filter(v -> it.getVehicleId().equals(v.vehicleId()))
                .map(v -> v.registrationNumber() + " (" + v.code() + ")").findFirst().orElse("Vehicle #" + it.getVehicleId());

        return new SalaryStatementData(c.name(), c.address(), c.mobileNumber(),
                b.getBatchNumber(), b.getPeriodMonth(), d.getCode(), d.getName(), d.getMobile(), vehicle,
                it.getSalaryType(), it.getBasicSalary(), it.getWorkingDays(), it.getAllowances(), it.getOvertime(),
                it.getOtherEarnings(), it.getGrossAmount(), it.getSalaryAdvance(), it.getLoanRecovery(),
                it.getPenalty(), it.getOtherDeduction(), it.getTotalDeduction(), it.getNetSalary(),
                it.getPaymentStatus().name(), it.getPaymentMethod() == null ? null : it.getPaymentMethod().name(),
                it.getPaymentReference(), new ArrayList<>(regionNames.values()), route, expenses, adv);
    }
}
