package com.sasimilkservice.center.api;

import com.sasimilkservice.center.domain.CenterBankAccount;
import com.sasimilkservice.center.service.CenterBankAccountService;
import com.sasimilkservice.center.service.CenterBankAccountService.AccountInput;
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

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/center/bank-accounts")
public class CenterBankAccountController {

    private static final String READ =
            "hasAnyAuthority('MANAGE_RATES','VIEW_PAYMENTS','VERIFY_PAYMENT','APPROVE_SETTLEMENT','PROCESS_PAYMENT','GENERATE_SETTLEMENT','ALL')";
    private static final String MANAGE = "hasAnyAuthority('MANAGE_RATES','ALL')";

    private final CenterBankAccountService service;

    public CenterBankAccountController(CenterBankAccountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<AccountResponse> list() {
        return service.list().stream().map(AccountResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public AccountResponse create(@Valid @RequestBody AccountRequest r) {
        return AccountResponse.from(service.create(r.toInput()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public AccountResponse update(@PathVariable long id, @Valid @RequestBody AccountRequest r) {
        return AccountResponse.from(service.update(id, r.toInput()));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize(MANAGE)
    public AccountResponse verify(@PathVariable long id) {
        return AccountResponse.from(service.verify(id));
    }

    @PostMapping("/{id}/unverify")
    @PreAuthorize(MANAGE)
    public AccountResponse unverify(@PathVariable long id) {
        return AccountResponse.from(service.unverify(id));
    }

    @PostMapping("/{id}/primary")
    @PreAuthorize(MANAGE)
    public AccountResponse primary(@PathVariable long id) {
        return AccountResponse.from(service.setPrimary(id));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGE)
    public void deactivate(@PathVariable long id) {
        service.deactivate(id);
    }

    public record AccountRequest(
            @NotBlank String accountHolderName,
            String accountNumber,
            @NotBlank String ifsc,
            @NotBlank String bankName,
            String label) {

        AccountInput toInput() {
            return new AccountInput(accountHolderName, accountNumber, ifsc, bankName, label);
        }
    }

    public record AccountResponse(
            Long id, String accountHolderName, String accountNumberMasked, String ifsc, String bankName,
            String label, String status, boolean verified, boolean primary, Instant verifiedAt) {

        static AccountResponse from(CenterBankAccount a) {
            return new AccountResponse(a.getId(), a.getAccountHolderName(), a.maskedNumber(), a.getIfsc(),
                    a.getBankName(), a.getLabel(), a.getStatus().name(), a.isVerified(), a.isPrimary(),
                    a.getVerifiedAt());
        }
    }
}
