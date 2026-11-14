package com.sasimilkservice.expense.domain;

import com.sasimilkservice.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A misc business expense (fuel, rent, maintenance, ...) paid from the same
 * business account as farmer settlements and driver salaries — logged here
 * purely so it shows up alongside those in the Payment Activity Center.
 * Unlike a settlement/salary batch there is no verify/approve workflow: an
 * expense is recorded as already paid (or failed), and can only be cancelled
 * afterwards if it was a mistake.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
public class Expense extends AuditableEntity {

    public enum Category {
        FUEL, MAINTENANCE, RENT, UTILITIES, OFFICE_SUPPLIES, TRANSPORT, SALARY_OTHER, MISC, OTHER
    }

    public enum PaymentMethod { CASH, BANK_TRANSFER, UPI, CHEQUE, OTHER }

    public enum Status { PAID, FAILED, CANCELLED }

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "expense_number", nullable = false, updatable = false, unique = true)
    private String expenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    private String description;

    /** The vehicle this was spent on (fuel, repairs…), when it belongs to one. */
    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.BANK_TRANSFER;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PAID;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt = Instant.now();
}
