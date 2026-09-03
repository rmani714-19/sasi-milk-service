package com.sasimilkservice.incentive.api;

import com.sasimilkservice.incentive.domain.Incentive;
import com.sasimilkservice.incentive.service.IncentiveManagementService;
import com.sasimilkservice.incentive.service.IncentiveManagementService.Command;
import com.sasimilkservice.user.spi.UserDirectory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The managed Incentives screen. Lives alongside the legacy per-litre rate
 * endpoints ({@code /api/v1/incentives/common} and {@code /individual}); the
 * routes are disjoint.
 */
@RestController
@RequestMapping("/api/v1/incentives")
public class IncentiveCatalogController {

    private final IncentiveManagementService service;
    private final UserDirectory users;

    public IncentiveCatalogController(IncentiveManagementService service, UserDirectory users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MANAGE_INCENTIVES','VIEW_DASHBOARD')")
    public List<IncentiveResponse> list(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) String target,
                                        @RequestParam(required = false) String type,
                                        @RequestParam(required = false) String status) {
        LocalDate today = LocalDate.now();
        List<Incentive> all = service.list();
        Map<Long, String> names = users.displayNames(
                all.stream().map(Incentive::getCreatedBy).filter(java.util.Objects::nonNull).distinct().toList());
        String q = search == null ? "" : search.trim().toLowerCase();
        return all.stream()
                .filter(i -> q.isEmpty()
                        || i.getName().toLowerCase().contains(q)
                        || (i.getDescription() != null && i.getDescription().toLowerCase().contains(q)))
                .filter(i -> blankOrAll(target) || i.getTarget().name().equalsIgnoreCase(target))
                .filter(i -> blankOrAll(type) || i.getIncentiveType().name().equalsIgnoreCase(type))
                .filter(i -> blankOrAll(status)
                        || i.effectiveStatus(today).name().equalsIgnoreCase(status))
                .map(i -> IncentiveResponse.from(i, today, names.get(i.getCreatedBy())))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MANAGE_INCENTIVES','VIEW_DASHBOARD')")
    public IncentiveResponse get(@PathVariable long id) {
        Incentive i = service.require(id);
        return IncentiveResponse.from(i, LocalDate.now(), users.displayName(i.getCreatedBy()).orElse(null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_INCENTIVES')")
    public IncentiveResponse create(@Valid @RequestBody IncentiveRequest r) {
        Incentive i = service.create(r.toCommand());
        return IncentiveResponse.from(i, LocalDate.now(), users.displayName(i.getCreatedBy()).orElse(null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_INCENTIVES')")
    public IncentiveResponse update(@PathVariable long id, @Valid @RequestBody IncentiveRequest r) {
        Incentive i = service.update(id, r.toCommand());
        return IncentiveResponse.from(i, LocalDate.now(), users.displayName(i.getCreatedBy()).orElse(null));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('MANAGE_INCENTIVES')")
    public IncentiveResponse activate(@PathVariable long id) {
        Incentive i = service.activate(id);
        return IncentiveResponse.from(i, LocalDate.now(), users.displayName(i.getCreatedBy()).orElse(null));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('MANAGE_INCENTIVES')")
    public IncentiveResponse deactivate(@PathVariable long id) {
        Incentive i = service.deactivate(id);
        return IncentiveResponse.from(i, LocalDate.now(), users.displayName(i.getCreatedBy()).orElse(null));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ALL')")
    public void delete(@PathVariable long id, @RequestParam(required = false) String reason) {
        service.delete(id, reason);
    }

    private static boolean blankOrAll(String s) {
        return s == null || s.isBlank() || s.equalsIgnoreCase("ALL");
    }

    // ---- DTOs ----

    public record IncentiveRequest(
            @NotNull String name,
            @NotNull Incentive.Target target,
            @NotNull Incentive.IncentiveType incentiveType,
            @NotNull BigDecimal amount,
            @NotNull LocalDate effectiveFrom,
            @NotNull LocalDate effectiveTo,
            Incentive.Status status,
            String description,
            Integer priority) {

        Command toCommand() {
            return new Command(name, target, incentiveType, amount, effectiveFrom, effectiveTo,
                    status, description, priority);
        }
    }

    public record IncentiveResponse(
            Long id,
            String publicId,
            String name,
            String target,
            String incentiveType,
            BigDecimal amount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status,
            String effectiveStatus,
            String description,
            int priority,
            boolean used,
            Long createdBy,
            String createdByName,
            Instant createdAt,
            Instant updatedAt) {

        static IncentiveResponse from(Incentive i, LocalDate today, String createdByName) {
            return new IncentiveResponse(i.getId(), i.getPublicId().toString(), i.getName(),
                    i.getTarget().name(), i.getIncentiveType().name(), i.getAmount(),
                    i.getEffectiveFrom(), i.getEffectiveTo(), i.getStatus().name(),
                    i.effectiveStatus(today).name(), i.getDescription(), i.getPriority(),
                    i.isUsed(today), i.getCreatedBy(), createdByName,
                    i.getCreatedAt(), i.getUpdatedAt());
        }
    }
}
