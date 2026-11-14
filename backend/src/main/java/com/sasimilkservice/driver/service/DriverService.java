package com.sasimilkservice.driver.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.crypto.FieldCrypto;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.driver.domain.Driver;
import com.sasimilkservice.driver.repository.DriverRepository;
import com.sasimilkservice.region.spi.RegionDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class DriverService {

    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    private final DriverRepository drivers;
    private final FieldCrypto crypto;
    private final AuditRecorder audit;
    private final RegionDirectory regions;

    public DriverService(DriverRepository drivers, FieldCrypto crypto, AuditRecorder audit, RegionDirectory regions) {
        this.drivers = drivers;
        this.crypto = crypto;
        this.audit = audit;
        this.regions = regions;
    }

    /** Set the regions (areas) this driver collects from. Replaces the previous route. */
    @Transactional
    public Driver setRoute(long id, java.util.Collection<Long> regionIds) {
        Driver d = require(id);
        java.util.Set<Long> wanted = new java.util.HashSet<>(regionIds == null ? java.util.List.of() : regionIds);
        for (Long r : wanted) {
            if (r == null || !regions.isActive(r)) {
                throw new BusinessRuleException("Region " + r + " is not an active region");
            }
        }
        d.getRouteRegionIds().clear();
        d.getRouteRegionIds().addAll(wanted);
        audit.record("DRIVER_UPDATED", "Driver", id,
                "Driver %s route set to %d region(s)".formatted(d.getCode(), wanted.size()));
        return d;
    }

    public List<Driver> findAll() {
        return drivers.findAllByOrderByNameAsc();
    }

    public List<Driver> findActive() {
        return drivers.findByStatusOrderByNameAsc(Driver.Status.ACTIVE);
    }

    public Driver require(long id) {
        return drivers.findById(id).orElseThrow(() -> NotFoundException.of("Driver", id));
    }

    /** Full (decrypted) bank details — call site must gate on the right permission. */
    public BankView bankView(long id) {
        Driver d = require(id);
        return new BankView(
                d.getAccountHolderName(),
                d.getAccountNumberEnc() == null ? null : crypto.decrypt(d.getAccountNumberEnc()),
                d.getIfscCode(), d.getBankName(), d.getBranchName(),
                d.getBankUpdatedAt(), d.getBankUpdatedBy());
    }

    @Transactional
    public Driver create(String name, String mobile, LocalDate joiningDate, Long vehicleId,
                         Driver.SalaryType salaryType, BigDecimal monthlySalary, BigDecimal perDayRate,
                         String note, BankInput bank) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Driver name is required");
        }
        Driver d = new Driver();
        d.setCode("D%04d".formatted(drivers.nextDriverNumberValue()));
        applyFields(d, name, mobile, joiningDate, vehicleId, salaryType, monthlySalary, perDayRate, note);
        applyBank(d, bank, true);
        d = drivers.save(d);
        audit.record("DRIVER_CREATED", "Driver", d.getId(), "Driver %s (%s) added".formatted(d.getName(), d.getCode()));
        return d;
    }

    @Transactional
    public Driver update(long id, String name, String mobile, LocalDate joiningDate, Long vehicleId,
                         Driver.SalaryType salaryType, BigDecimal monthlySalary, BigDecimal perDayRate,
                         Driver.Status status, String note, BankInput bank) {
        Driver d = require(id);
        applyFields(d, name, mobile, joiningDate, vehicleId, salaryType, monthlySalary, perDayRate, note);
        applyBank(d, bank, false);
        if (status != null) {
            d.setStatus(status);
        }
        audit.record("DRIVER_UPDATED", "Driver", id, "Driver %s updated".formatted(d.getCode()));
        return d;
    }

    @Transactional
    public void deactivate(long id) {
        Driver d = require(id);
        d.setStatus(Driver.Status.INACTIVE);
        audit.record("DRIVER_UPDATED", "Driver", id, "Driver %s deactivated".formatted(d.getCode()));
    }

    private void applyFields(Driver d, String name, String mobile, LocalDate joiningDate, Long vehicleId,
                             Driver.SalaryType salaryType, BigDecimal monthlySalary, BigDecimal perDayRate, String note) {
        d.setName(name.trim());
        d.setMobile(mobile == null || mobile.isBlank() ? null : mobile.trim());
        d.setJoiningDate(joiningDate);
        d.setVehicleId(vehicleId);
        d.setSalaryType(salaryType == null ? Driver.SalaryType.FIXED : salaryType);
        d.setMonthlySalary(monthlySalary == null ? BigDecimal.ZERO : monthlySalary.max(BigDecimal.ZERO));
        d.setPerDayRate(perDayRate == null ? null : perDayRate.max(BigDecimal.ZERO));
        d.setNote(note == null || note.isBlank() ? null : note.trim());
    }

    /**
     * Apply the bank block. On create every field is required. On edit the account
     * number may be omitted ("keep the current one"); the holder / IFSC / bank name
     * still must be present so a driver never ends up with a partial bank block.
     * {@code bank_updated_at/by} are stamped whenever any stored value actually changes.
     */
    private void applyBank(Driver d, BankInput bank, boolean isCreate) {
        if (bank == null) {
            if (isCreate) {
                throw new BusinessRuleException("Bank details are required");
            }
            return;
        }
        String holder = trim(bank.accountHolderName());
        String ifsc = bank.ifscCode() == null ? null : bank.ifscCode().trim().toUpperCase();
        String bankName = trim(bank.bankName());
        String branch = trim(bank.branchName());
        String rawAccount = bank.accountNumber() == null ? "" : bank.accountNumber().replaceAll("\\s", "");

        boolean keepExistingAccount = !isCreate && rawAccount.isEmpty() && d.getAccountNumberEnc() != null;

        if (isBlank(holder)) {
            throw new BusinessRuleException("Account holder name is required");
        }
        if (isBlank(bankName)) {
            throw new BusinessRuleException("Bank name is required");
        }
        if (isBlank(ifsc) || !IFSC.matcher(ifsc).matches()) {
            throw new BusinessRuleException("A valid IFSC code is required (e.g. HDFC0001234)");
        }
        if (!keepExistingAccount && !rawAccount.matches("\\d{9,18}")) {
            throw new BusinessRuleException("Account number must be 9–18 digits");
        }

        boolean changed = !Objects.equals(d.getAccountHolderName(), holder)
                || !Objects.equals(d.getIfscCode(), ifsc)
                || !Objects.equals(d.getBankName(), bankName)
                || !Objects.equals(d.getBranchName(), branch);

        d.setAccountHolderName(holder);
        d.setIfscCode(ifsc);
        d.setBankName(bankName);
        d.setBranchName(branch);

        if (!keepExistingAccount) {
            String newHash = crypto.blindIndex(rawAccount);
            if (!Objects.equals(d.getAccountNumberHash(), newHash)) {
                changed = true;
                d.setAccountNumberEnc(crypto.encrypt(rawAccount));
                d.setAccountNumberLast4(FieldCrypto.lastFour(rawAccount));
                d.setAccountNumberHash(newHash);
            }
        }

        if (changed || isCreate) {
            d.setBankUpdatedAt(Instant.now());
            CurrentUser.id().ifPresent(d::setBankUpdatedBy);
            if (!isCreate) {
                audit.record("DRIVER_BANK_UPDATED", "Driver", d.getId(),
                        "Bank details changed for driver %s (%s / %s)".formatted(
                                d.getCode(), d.getBankName(), d.maskedAccount()));
            }
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record BankInput(
            String accountHolderName,
            String accountNumber,
            String ifscCode,
            String bankName,
            String branchName) {
    }

    public record BankView(
            String accountHolderName,
            String accountNumber,
            String ifscCode,
            String bankName,
            String branchName,
            Instant bankUpdatedAt,
            Long bankUpdatedBy) {
    }
}
