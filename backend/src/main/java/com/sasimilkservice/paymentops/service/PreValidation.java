package com.sasimilkservice.paymentops.service;

import java.util.ArrayList;
import java.util.List;

/** Result of the pre-approval checklist for a payment batch (settlement or salary batch). */
public final class PreValidation {

    private PreValidation() {}

    public enum Result { READY_FOR_APPROVAL, BLOCKED }

    /**
     * @param key      stable identifier
     * @param label    human label shown in the checklist
     * @param passed   whether the check is satisfied
     * @param blocking a failing blocking check forces {@link Result#BLOCKED}
     * @param detail   short explanation shown when it fails (or extra context)
     * @param link     optional in-app path to the record that must be fixed
     */
    public record Check(String key, String label, boolean passed, boolean blocking, String detail, String link) {
    }

    public record Report(Result result, List<Check> checks) {

        public static Report of(List<Check> checks) {
            boolean blocked = checks.stream().anyMatch(c -> c.blocking() && !c.passed());
            return new Report(blocked ? Result.BLOCKED : Result.READY_FOR_APPROVAL, checks);
        }
    }

    /** Fluent builder for the checklist. */
    public static final class Builder {
        private final List<Check> checks = new ArrayList<>();

        public Builder blocking(String key, String label, boolean passed, String failDetail, String link) {
            checks.add(new Check(key, label, passed, true, passed ? null : failDetail, link));
            return this;
        }

        public Builder info(String key, String label, boolean passed, String detail) {
            checks.add(new Check(key, label, passed, false, detail, null));
            return this;
        }

        public Report build() {
            return Report.of(checks);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
