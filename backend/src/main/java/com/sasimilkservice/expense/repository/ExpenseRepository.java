package com.sasimilkservice.expense.repository;

import com.sasimilkservice.expense.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findAllByOrderByExpenseDateDescIdDesc();

    @Query(value = "select nextval('expense_number_seq')", nativeQuery = true)
    long nextExpenseNumberValue();
}
