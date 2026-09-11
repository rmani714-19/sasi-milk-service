package com.sasimilkservice.common.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permission codes referenced by {@code roles.permissions} and {@code @PreAuthorize}.
 * {@link #ALL} is a sentinel held by the ADMIN role; it is expanded to every other
 * permission when a user's authorities are built (spec: "Implement backend
 * authorization even when UI hides the feature").
 */
public enum Permission {

    ALL,

    VIEW_DASHBOARD,

    VIEW_PRODUCERS,
    MANAGE_PRODUCERS,

    MANAGE_RATES,
    MANAGE_INCENTIVES,
    MANAGE_DEDUCTIONS,
    MANAGE_ADVANCES,
    MANAGE_EXPENSES,

    CREATE_COLLECTION,
    VIEW_COLLECTION,
    VIEW_COLLECTIONS,
    PRINT_RECEIPT,

    GENERATE_SETTLEMENT,
    REVIEW_SETTLEMENT,
    VERIFY_PAYMENT,
    APPROVE_SETTLEMENT,

    VIEW_PAYMENTS,
    PROCESS_PAYMENT,
    MANAGE_DRIVERS,

    VIEW_VEHICLES,
    MANAGE_VEHICLES,
    MANAGE_DEVICE_ASSIGNMENT,
    VIEW_LIVE_LOCATION,
    VIEW_CAMERA,
    VIEW_TRIP_HISTORY,
    VIEW_ALERTS,

    VIEW_BASIC_REPORTS,
    VIEW_REPORTS,
    GENERATE_REPORTS,
    EXPORT_REPORTS;

    public static final String ALL_CODE = "ALL";

    /** Every permission except the {@link #ALL} sentinel. */
    public static Set<String> allConcrete() {
        return Arrays.stream(values())
                .filter(p -> p != ALL)
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
