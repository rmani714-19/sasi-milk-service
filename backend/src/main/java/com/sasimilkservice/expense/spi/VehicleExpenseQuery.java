package com.sasimilkservice.expense.spi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Published SPI of the {@code expense} module: what was spent on a vehicle — shown on driver salary statements. */
public interface VehicleExpenseQuery {

    /** Non-cancelled expenses tagged to the vehicle, oldest first. */
    List<VehicleExpense> forVehicle(long vehicleId, LocalDate from, LocalDate to);

    record VehicleExpense(long id, String number, LocalDate date, String category, String description,
                          BigDecimal amount) {
    }
}
