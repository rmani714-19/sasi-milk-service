package com.sasimilkservice.expense.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.expense.domain.Expense;
import com.sasimilkservice.expense.domain.Expense.Category;
import com.sasimilkservice.expense.domain.Expense.PaymentMethod;
import com.sasimilkservice.expense.domain.Expense.Status;
import com.sasimilkservice.expense.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository repository;
    private final AuditRecorder audit;

    public ExpenseService(ExpenseRepository repository, AuditRecorder audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public List<Expense> list() {
        return repository.findAllByOrderByExpenseDateDescIdDesc();
    }

    public Expense require(long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Expense", id));
    }

    @Transactional
    public Expense create(Category category, String description, BigDecimal amount, LocalDate expenseDate,
                          PaymentMethod method, String reference, Status status) {
        validateAmount(amount);
        Expense e = new Expense();
        e.setExpenseNumber("EXP%05d".formatted(repository.nextExpenseNumberValue()));
        e.setCategory(category);
        e.setDescription(blankToNull(description));
        e.setAmount(amount);
        e.setExpenseDate(expenseDate == null ? LocalDate.now() : expenseDate);
        e.setPaymentMethod(method == null ? PaymentMethod.BANK_TRANSFER : method);
        e.setPaymentReference(blankToNull(reference));
        e.setStatus(status == null ? Status.PAID : status);
        e = repository.save(e);
        audit.record("EXPENSE_CREATED", "Expense", e.getId(),
                "%s %s (%s)".formatted(e.getCategory(), e.getAmount(), e.getExpenseNumber()));
        return e;
    }

    @Transactional
    public Expense update(long id, Category category, String description, BigDecimal amount, LocalDate expenseDate,
                          PaymentMethod method, String reference, Status status) {
        Expense e = require(id);
        if (e.getStatus() == Status.CANCELLED) {
            throw new BusinessRuleException("A cancelled expense cannot be edited");
        }
        validateAmount(amount);
        e.setCategory(category);
        e.setDescription(blankToNull(description));
        e.setAmount(amount);
        if (expenseDate != null) {
            e.setExpenseDate(expenseDate);
        }
        if (method != null) {
            e.setPaymentMethod(method);
        }
        e.setPaymentReference(blankToNull(reference));
        if (status != null) {
            e.setStatus(status);
        }
        audit.record("EXPENSE_UPDATED", "Expense", id, "Expense %s updated".formatted(e.getExpenseNumber()));
        return e;
    }

    @Transactional
    public void cancel(long id) {
        Expense e = require(id);
        if (e.getStatus() == Status.CANCELLED) {
            throw new BusinessRuleException("This expense is already cancelled");
        }
        e.setStatus(Status.CANCELLED);
        audit.record("EXPENSE_CANCELLED", "Expense", id, "Expense %s cancelled".formatted(e.getExpenseNumber()));
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Expense amount must be greater than zero");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
