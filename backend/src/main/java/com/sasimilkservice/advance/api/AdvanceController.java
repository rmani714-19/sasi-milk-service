package com.sasimilkservice.advance.api;

import com.sasimilkservice.advance.domain.AdvanceAccount;
import com.sasimilkservice.advance.domain.AdvanceTransaction;
import com.sasimilkservice.advance.service.AdvanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/advances")
public class AdvanceController {

    private final AdvanceService service;

    public AdvanceController(AdvanceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MANAGE_ADVANCES','VIEW_DASHBOARD')")
    public ProducerAdvancesResponse list(@RequestParam(required = false) Long producerId) {
        if (producerId == null) {
            return new ProducerAdvancesResponse(
                    service.totalOutstandingAll(),
                    service.listAllAccounts().stream().map(AdvanceAccountResponse::from).toList(),
                    service.allLedger().stream().map(AdvanceTxnResponse::from).toList());
        }
        return new ProducerAdvancesResponse(
                service.totalOutstanding(producerId),
                service.listForProducer(producerId).stream().map(AdvanceAccountResponse::from).toList(),
                service.ledgerForProducer(producerId).stream().map(AdvanceTxnResponse::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_ADVANCES')")
    public AdvanceAccountResponse create(@Valid @RequestBody CreateAdvanceRequest r) {
        return AdvanceAccountResponse.from(service.create(r.producerId(), r.amount(), r.recoveryMode(),
                r.recoveryAmount(), r.recoveryPercentage(), r.advanceDate(), r.description()));
    }

    public record CreateAdvanceRequest(
            @Positive long producerId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
            @NotNull AdvanceAccount.RecoveryMode recoveryMode,
            BigDecimal recoveryAmount,
            BigDecimal recoveryPercentage,
            LocalDate advanceDate,
            String description) {
    }

    public record ProducerAdvancesResponse(
            BigDecimal totalOutstanding,
            List<AdvanceAccountResponse> accounts,
            List<AdvanceTxnResponse> ledger) {
    }

    public record AdvanceAccountResponse(
            Long id, Long producerId, LocalDate advanceDate, BigDecimal originalAmount,
            String recoveryMode, BigDecimal recoveryAmount, BigDecimal recoveryPercentage,
            BigDecimal outstandingAmount, String status, String description) {

        static AdvanceAccountResponse from(AdvanceAccount a) {
            return new AdvanceAccountResponse(a.getId(), a.getProducerId(), a.getAdvanceDate(),
                    a.getOriginalAmount(), a.getRecoveryMode().name(), a.getRecoveryAmount(),
                    a.getRecoveryPercentage(), a.getOutstandingAmount(), a.getStatus().name(),
                    a.getDescription());
        }
    }

    public record AdvanceTxnResponse(
            Long id, Long advanceAccountId, String txnType, BigDecimal amount,
            BigDecimal balanceAfter, LocalDate txnDate, Long settlementItemId) {

        static AdvanceTxnResponse from(AdvanceTransaction t) {
            return new AdvanceTxnResponse(t.getId(), t.getAdvanceAccountId(), t.getTxnType().name(),
                    t.getAmount(), t.getBalanceAfter(), t.getTxnDate(), t.getSettlementItemId());
        }
    }
}
