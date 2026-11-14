package com.sasimilkservice.expense.repository;

import com.sasimilkservice.expense.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findAllByOrderByExpenseDateDescIdDesc();

    @Query("""
            select e from Expense e
            where e.vehicleId = :vehicleId and e.status <> com.sasimilkservice.expense.domain.Expense.Status.CANCELLED
              and e.expenseDate between :from and :to
            order by e.expenseDate, e.id
            """)
    List<Expense> findForVehicle(@Param("vehicleId") long vehicleId, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    @Query(value = "select nextval('expense_number_seq')", nativeQuery = true)
    long nextExpenseNumberValue();
}
