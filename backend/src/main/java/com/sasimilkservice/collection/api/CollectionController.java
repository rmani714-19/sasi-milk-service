package com.sasimilkservice.collection.api;

import com.sasimilkservice.collection.api.dto.CollectionDtos.CollectionResponse;
import com.sasimilkservice.collection.api.dto.CollectionDtos.CreateCollectionRequest;
import com.sasimilkservice.collection.api.dto.CollectionDtos.VoidCollectionRequest;
import com.sasimilkservice.collection.domain.MilkCollection;
import com.sasimilkservice.collection.service.CollectionService;
import com.sasimilkservice.collection.service.CollectionService.RecordResult;
import com.sasimilkservice.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private final CollectionService service;

    public CollectionController(CollectionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_COLLECTION')")
    public ResponseEntity<CollectionResponse> create(@Valid @RequestBody CreateCollectionRequest request) {
        RecordResult result = service.record(request.toCommand());
        CollectionResponse body = CollectionResponse.from(result.collection(), result.duplicate());
        return ResponseEntity
                .status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(body);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_COLLECTION','VIEW_COLLECTIONS')")
    public PageResponse<CollectionResponse> list(
            @RequestParam(required = false) Long producerId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) MilkCollection.Status status,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.search(producerId, date, shift, status, pageable),
                c -> CollectionResponse.from(c, false));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyAuthority('VIEW_COLLECTION','VIEW_COLLECTIONS')")
    public CollectionResponse get(@PathVariable UUID publicId) {
        return CollectionResponse.from(service.requireByPublicId(publicId), false);
    }

    @PostMapping("/{publicId}/void")
    @PreAuthorize("hasAnyAuthority('MANAGE_PRODUCERS','VIEW_COLLECTIONS')")
    public CollectionResponse voidCollection(@PathVariable UUID publicId,
                                             @Valid @RequestBody VoidCollectionRequest request) {
        return CollectionResponse.from(service.voidCollection(publicId, request.reason()), false);
    }
}
