package com.sasimilkservice.incentive.api;

import com.sasimilkservice.incentive.domain.IncentiveRule;
import com.sasimilkservice.incentive.domain.ProducerIncentive;
import com.sasimilkservice.incentive.service.IncentiveService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/incentives")
public class IncentiveController {

    private final IncentiveService service;

    public IncentiveController(IncentiveService service) {
        this.service = service;
    }

    @GetMapping("/common")
    @PreAuthorize("hasAnyAuthority('MANAGE_INCENTIVES','VIEW_DASHBOARD')")
    public List<CommonIncentiveResponse> listCommon() {
        return service.listCommon().stream().map(CommonIncentiveResponse::from).toList();
    }

    @GetMapping("/individual")
    @PreAuthorize("hasAnyAuthority('MANAGE_INCENTIVES','VIEW_DASHBOARD')")
    public List<IndividualIncentiveResponse> listIndividual(@RequestParam long producerId) {
        return service.listIndividual(producerId).stream().map(IndividualIncentiveResponse::from).toList();
    }

    @PostMapping("/common")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_INCENTIVES')")
    public CommonIncentiveResponse createCommon(@Valid @RequestBody CreateCommonRequest r) {
        return CommonIncentiveResponse.from(service.createCommon(
                r.scope() == null ? IncentiveRule.Scope.COMMON : r.scope(),
                r.regionId(), r.ratePerLitre(), r.effectiveDate(), r.endDate(), r.description()));
    }

    @PostMapping("/individual")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_INCENTIVES')")
    public IndividualIncentiveResponse createIndividual(@Valid @RequestBody CreateIndividualRequest r) {
        return IndividualIncentiveResponse.from(service.createIndividual(
                r.producerId(), r.ratePerLitre(), r.effectiveDate(), r.endDate(), r.description()));
    }

    @PostMapping("/common/{id}/cancel")
    @PreAuthorize("hasAuthority('MANAGE_INCENTIVES')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelCommon(@PathVariable long id) {
        service.cancelCommon(id);
    }

    @PostMapping("/individual/{id}/cancel")
    @PreAuthorize("hasAuthority('MANAGE_INCENTIVES')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelIndividual(@PathVariable long id) {
        service.cancelIndividual(id);
    }

    public record CreateCommonRequest(
            IncentiveRule.Scope scope,
            Long regionId,
            @NotNull @DecimalMin("0.0") BigDecimal ratePerLitre,
            @NotNull LocalDate effectiveDate,
            LocalDate endDate,
            String description) {
    }

    public record CreateIndividualRequest(
            @Positive long producerId,
            @NotNull @DecimalMin("0.0") BigDecimal ratePerLitre,
            @NotNull LocalDate effectiveDate,
            LocalDate endDate,
            String description) {
    }

    public record CommonIncentiveResponse(
            Long id, String scope, Long regionId, BigDecimal ratePerLitre,
            LocalDate effectiveDate, LocalDate endDate, String status, String description) {

        static CommonIncentiveResponse from(IncentiveRule r) {
            return new CommonIncentiveResponse(r.getId(), r.getScope().name(), r.getRegionId(),
                    r.getIncentiveRatePerLitre(), r.getEffectiveDate(), r.getEndDate(),
                    r.getStatus().name(), r.getDescription());
        }
    }

    public record IndividualIncentiveResponse(
            Long id, Long producerId, BigDecimal ratePerLitre,
            LocalDate effectiveDate, LocalDate endDate, String status, String description) {

        static IndividualIncentiveResponse from(ProducerIncentive p) {
            return new IndividualIncentiveResponse(p.getId(), p.getProducerId(),
                    p.getIncentiveRatePerLitre(), p.getEffectiveDate(), p.getEndDate(),
                    p.getStatus().name(), p.getDescription());
        }
    }
}
