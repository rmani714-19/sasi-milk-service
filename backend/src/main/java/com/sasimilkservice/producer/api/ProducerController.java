package com.sasimilkservice.producer.api;

import com.sasimilkservice.common.api.PageResponse;
import com.sasimilkservice.producer.api.dto.ProducerDtos.BankAccountResponse;
import com.sasimilkservice.producer.api.dto.ProducerDtos.CreateBankAccountRequest;
import com.sasimilkservice.producer.api.dto.ProducerDtos.CreateProducerRequest;
import com.sasimilkservice.producer.api.dto.ProducerDtos.ProducerResponse;
import com.sasimilkservice.producer.api.dto.ProducerDtos.UpdateProducerRequest;
import com.sasimilkservice.producer.domain.Producer;
import com.sasimilkservice.producer.service.ProducerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/producers")
public class ProducerController {

    private final ProducerService service;

    public ProducerController(ProducerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_PRODUCERS','MANAGE_PRODUCERS')")
    public PageResponse<ProducerResponse> list(
            @RequestParam(required = false) Producer.Status status,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.search(status, regionId, q, pageable), ProducerResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('VIEW_PRODUCERS','MANAGE_PRODUCERS')")
    public ProducerResponse get(@PathVariable long id) {
        return ProducerResponse.from(service.require(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_PRODUCERS')")
    public ProducerResponse create(@Valid @RequestBody CreateProducerRequest request) {
        return ProducerResponse.from(service.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PRODUCERS')")
    public ProducerResponse update(@PathVariable long id, @Valid @RequestBody UpdateProducerRequest request) {
        return ProducerResponse.from(service.update(id, request.toCommand()));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('MANAGE_PRODUCERS')")
    public ProducerResponse deactivate(@PathVariable long id) {
        return ProducerResponse.from(service.deactivate(id));
    }

    @GetMapping("/{id}/bank-accounts")
    @PreAuthorize("hasAuthority('MANAGE_PRODUCERS')")
    public List<BankAccountResponse> bankAccounts(@PathVariable long id) {
        return service.bankAccountsOf(id).stream().map(BankAccountResponse::from).toList();
    }

    @PostMapping("/{id}/bank-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_PRODUCERS')")
    public BankAccountResponse addBankAccount(@PathVariable long id,
                                              @Valid @RequestBody CreateBankAccountRequest request) {
        return BankAccountResponse.from(service.addBankAccount(id, request.toCommand()));
    }
}
