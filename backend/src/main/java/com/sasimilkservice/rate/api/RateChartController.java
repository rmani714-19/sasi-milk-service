package com.sasimilkservice.rate.api;

import com.sasimilkservice.center.service.CenterService;
import com.sasimilkservice.rate.domain.RateChart;
import com.sasimilkservice.rate.service.RateChartService;
import com.sasimilkservice.rate.spi.RateResolutionService;
import com.sasimilkservice.rate.spi.RateResolutionService.ResolveRateQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rates")
public class RateChartController {

    private final RateChartService service;
    private final RateResolutionService resolution;
    private final CenterService centerService;

    public RateChartController(RateChartService service,
                              RateResolutionService resolution,
                              CenterService centerService) {
        this.service = service;
        this.resolution = resolution;
        this.centerService = centerService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MANAGE_RATES','VIEW_DASHBOARD')")
    public List<RateChartResponse> list() {
        return service.findAll().stream().map(RateChartResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MANAGE_RATES','VIEW_DASHBOARD')")
    public RateChartResponse get(@PathVariable long id) {
        return RateChartResponse.from(service.require(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_RATES')")
    public RateChartResponse create(@Valid @RequestBody CreateRateChartRequest r) {
        return RateChartResponse.from(service.create(r.scope(), r.regionId(), r.name(), r.tsValue(),
                r.effectiveFrom(), r.effectiveTo(), r.note()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_RATES')")
    public RateChartResponse update(@PathVariable long id, @Valid @RequestBody UpdateRateChartRequest r) {
        return RateChartResponse.from(service.update(id, r.name(), r.tsValue(), r.effectiveTo(), r.note()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('MANAGE_RATES')")
    public RateChartResponse archive(@PathVariable long id) {
        return RateChartResponse.from(service.archive(id));
    }

    /** Rate preview for the collection screen. */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyAuthority('CREATE_COLLECTION','MANAGE_RATES')")
    public RatePreviewResponse preview(@Valid @RequestBody RatePreviewRequest r) {
        var settings = centerService.settings();
        var resolved = resolution.resolve(new ResolveRateQuery(
                r.regionId(),
                r.collectionDate() == null ? LocalDate.now() : r.collectionDate(),
                r.fat(), r.snf(), settings.allowMissingRate()));
        return new RatePreviewResponse(resolved.ratePerLitre(), resolved.tsValue(),
                resolved.scope().name(), resolved.missing());
    }

    public record CreateRateChartRequest(
            @NotNull RateChart.Scope scope,
            Long regionId,
            String name,
            @NotNull @DecimalMin("0.0") BigDecimal tsValue,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String note) {
    }

    public record UpdateRateChartRequest(
            String name,
            @DecimalMin("0.0") BigDecimal tsValue,
            LocalDate effectiveTo,
            String note) {
    }

    public record RatePreviewRequest(
            Long regionId,
            LocalDate collectionDate,
            @NotNull @DecimalMin("0.0") BigDecimal fat,
            @NotNull @DecimalMin("0.0") BigDecimal snf) {
    }

    public record RatePreviewResponse(
            BigDecimal ratePerLitre, BigDecimal tsValue, String scope, boolean missing) {
    }

    public record RateChartResponse(
            Long id, String scope, Long regionId, String name, BigDecimal tsValue,
            LocalDate effectiveFrom, LocalDate effectiveTo, String status, String note) {

        static RateChartResponse from(RateChart c) {
            return new RateChartResponse(c.getId(), c.getScope().name(), c.getRegionId(), c.getName(),
                    c.getTsValue(), c.getEffectiveFrom(), c.getEffectiveTo(), c.getStatus().name(),
                    c.getNote());
        }
    }
}
