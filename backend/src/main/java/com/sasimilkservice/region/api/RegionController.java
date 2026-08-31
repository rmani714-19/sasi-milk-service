package com.sasimilkservice.region.api;

import com.sasimilkservice.region.domain.Region;
import com.sasimilkservice.region.service.RegionService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

    private final RegionService service;

    public RegionController(RegionService service) {
        this.service = service;
    }

    @GetMapping
    public List<RegionResponse> list() {
        return service.findAll().stream().map(RegionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RegionResponse get(@PathVariable long id) {
        return RegionResponse.from(service.require(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_RATES')")
    public RegionResponse create(@Valid @RequestBody CreateRegionRequest request) {
        return RegionResponse.from(service.create(request.code(), request.name(), request.description()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_RATES')")
    public RegionResponse update(@PathVariable long id, @Valid @RequestBody UpdateRegionRequest request) {
        return RegionResponse.from(service.update(id, request.name(), request.description(), request.status()));
    }

    public record CreateRegionRequest(@NotBlank String code, @NotBlank String name, String description) {
    }

    public record UpdateRegionRequest(@NotBlank String name, String description, Region.Status status) {
    }

    public record RegionResponse(Long id, String code, String name, String description, String status) {
        static RegionResponse from(Region r) {
            return new RegionResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                    r.getStatus().name());
        }
    }
}
