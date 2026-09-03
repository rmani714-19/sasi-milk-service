package com.sasimilkservice.driver.api;

import com.sasimilkservice.driver.domain.Driver;
import com.sasimilkservice.driver.service.DriverService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService service;

    public DriverController(DriverService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MANAGE_DRIVERS','VIEW_PAYMENTS','VIEW_DASHBOARD')")
    public List<DriverResponse> list(@RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        var rows = activeOnly ? service.findActive() : service.findAll();
        return rows.stream().map(DriverResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MANAGE_DRIVERS','VIEW_PAYMENTS')")
    public DriverResponse get(@PathVariable long id) {
        return DriverResponse.from(service.require(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_DRIVERS')")
    public DriverResponse create(@Valid @RequestBody DriverRequest r) {
        return DriverResponse.from(service.create(r.name(), r.mobile(), r.joiningDate(), r.vehicleId(),
                r.salaryType(), r.monthlySalary(), r.perDayRate(), r.note()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_DRIVERS')")
    public DriverResponse update(@PathVariable long id, @Valid @RequestBody DriverRequest r) {
        return DriverResponse.from(service.update(id, r.name(), r.mobile(), r.joiningDate(), r.vehicleId(),
                r.salaryType(), r.monthlySalary(), r.perDayRate(), r.status(), r.note()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MANAGE_DRIVERS')")
    public void deactivate(@PathVariable long id) {
        service.deactivate(id);
    }

    public record DriverRequest(
            @NotBlank String name,
            String mobile,
            LocalDate joiningDate,
            Long vehicleId,
            Driver.SalaryType salaryType,
            BigDecimal monthlySalary,
            BigDecimal perDayRate,
            Driver.Status status,
            String note) {
    }

    public record DriverResponse(
            Long id, String code, String name, String mobile, LocalDate joiningDate,
            Long vehicleId, String salaryType, BigDecimal monthlySalary, BigDecimal perDayRate,
            String status, String note) {

        static DriverResponse from(Driver d) {
            return new DriverResponse(d.getId(), d.getCode(), d.getName(), d.getMobile(), d.getJoiningDate(),
                    d.getVehicleId(), d.getSalaryType().name(), d.getMonthlySalary(), d.getPerDayRate(),
                    d.getStatus().name(), d.getNote());
        }
    }
}
