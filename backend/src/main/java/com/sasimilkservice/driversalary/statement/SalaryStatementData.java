package com.sasimilkservice.driversalary.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything printed on one driver's monthly salary statement. */
public record SalaryStatementData(
        String centerName, String centerAddress, String centerMobile,
        String batchNumber, LocalDate month,
        String driverCode, String driverName, String driverMobile, String vehicle,
        String salaryType, BigDecimal basicSalary, BigDecimal workingDays,
        BigDecimal allowances, BigDecimal overtime, BigDecimal otherEarnings, BigDecimal gross,
        BigDecimal salaryAdvance, BigDecimal loanRecovery, BigDecimal penalty, BigDecimal otherDeduction,
        BigDecimal totalDeduction, BigDecimal net,
        String paymentStatus, String paymentMethod, String paymentReference,
        List<String> routeRegions, List<RouteLine> routeLines,
        List<ExpenseLine> expenses, List<AdvanceLine> advances) {

    public record RouteLine(LocalDate date, String region, BigDecimal morning, BigDecimal evening) {
        public BigDecimal total() {
            return morning.add(evening);
        }
    }

    public record ExpenseLine(LocalDate date, String category, String description, BigDecimal amount) {
    }

    public record AdvanceLine(LocalDate date, String reason, BigDecimal amount, String status) {
    }

    public BigDecimal routeLitres() {
        return routeLines.stream().map(RouteLine::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal expenseTotal() {
        return expenses.stream().map(ExpenseLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
