package com.sasimilkservice.expense.api;

import com.sasimilkservice.expense.domain.Expense;
import com.sasimilkservice.expense.service.ExpenseService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MANAGE_EXPENSES','VIEW_PAYMENTS','ALL')")
    public List<ExpenseResponse> list() {
        return service.list().stream().map(ExpenseResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_EXPENSES')")
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest r) {
        return ExpenseResponse.from(service.create(r.category(), r.description(), r.amount(),
                r.expenseDate(), r.paymentMethod(), r.paymentReference(), r.status(), r.vehicleId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_EXPENSES')")
    public ExpenseResponse update(@PathVariable long id, @Valid @RequestBody ExpenseRequest r) {
        return ExpenseResponse.from(service.update(id, r.category(), r.description(), r.amount(),
                r.expenseDate(), r.paymentMethod(), r.paymentReference(), r.status(), r.vehicleId()));
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MANAGE_EXPENSES')")
    public void cancel(@PathVariable long id) {
        service.cancel(id);
    }

    public record ExpenseRequest(
            @NotNull Expense.Category category,
            String description,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
            LocalDate expenseDate,
            Expense.PaymentMethod paymentMethod,
            String paymentReference,
            Expense.Status status,
            Long vehicleId) {
    }

    public record ExpenseResponse(
            Long id, String expenseNumber, String category, String description, BigDecimal amount,
            LocalDate expenseDate, String paymentMethod, String paymentReference, String status, Instant paidAt,
            Long vehicleId) {

        static ExpenseResponse from(Expense e) {
            return new ExpenseResponse(e.getId(), e.getExpenseNumber(), e.getCategory().name(), e.getDescription(),
                    e.getAmount(), e.getExpenseDate(), e.getPaymentMethod().name(), e.getPaymentReference(),
                    e.getStatus().name(), e.getPaidAt(), e.getVehicleId());
        }
    }
}
