package com.sasimilkservice.driversalary.api;

import com.sasimilkservice.driversalary.domain.DriverAdvance;
import com.sasimilkservice.driversalary.service.DriverSalaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Advances given to drivers — deducted automatically by the next salary run that covers them. */
@RestController
@RequestMapping("/api/v1")
public class DriverAdvanceController {

    private final DriverSalaryService service;

    public DriverAdvanceController(DriverSalaryService service) {
        this.service = service;
    }

    @GetMapping("/drivers/{driverId}/advances")
    @PreAuthorize("hasAnyAuthority('MANAGE_DRIVERS','VIEW_PAYMENTS','GENERATE_SETTLEMENT')")
    public List<AdvanceResponse> list(@PathVariable long driverId) {
        return service.advancesOf(driverId).stream().map(AdvanceResponse::from).toList();
    }

    @PostMapping("/drivers/{driverId}/advances")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGE_DRIVERS','GENERATE_SETTLEMENT')")
    public AdvanceResponse give(@PathVariable long driverId, @Valid @RequestBody AdvanceRequest r) {
        return AdvanceResponse.from(service.addAdvance(driverId, r.advanceDate(), r.amount(), r.reason()));
    }

    @PostMapping("/driver-advances/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('MANAGE_DRIVERS','GENERATE_SETTLEMENT')")
    public AdvanceResponse cancel(@PathVariable long id) {
        return AdvanceResponse.from(service.cancelAdvance(id));
    }

    public record AdvanceRequest(LocalDate advanceDate, @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
                                 String reason) {
    }

    public record AdvanceResponse(Long id, Long driverId, LocalDate advanceDate, BigDecimal amount, String reason,
                                  String status, Long deductedInItemId) {
        static AdvanceResponse from(DriverAdvance a) {
            return new AdvanceResponse(a.getId(), a.getDriverId(), a.getAdvanceDate(), a.getAmount(), a.getReason(),
                    a.getStatus().name(), a.getDeductedInItemId());
        }
    }
}
