package com.sasimilkservice.center.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.center.domain.CenterBankAccount;
import com.sasimilkservice.center.domain.CenterBankAccount.Status;
import com.sasimilkservice.center.repository.CenterBankAccountRepository;
import com.sasimilkservice.center.spi.BusinessBankAccountDirectory;
import com.sasimilkservice.common.crypto.FieldCrypto;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class CenterBankAccountService implements BusinessBankAccountDirectory {

    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    private final CenterBankAccountRepository repository;
    private final FieldCrypto crypto;
    private final AuditRecorder audit;

    public CenterBankAccountService(CenterBankAccountRepository repository, FieldCrypto crypto, AuditRecorder audit) {
        this.repository = repository;
        this.crypto = crypto;
        this.audit = audit;
    }

    public List<CenterBankAccount> list() {
        return repository.findAllByOrderByPrimaryDescIdAsc();
    }

    public List<CenterBankAccount> usableAccounts() {
        return repository.findByStatusAndVerifiedTrueOrderByPrimaryDescIdAsc(Status.ACTIVE);
    }

    public CenterBankAccount require(long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("CenterBankAccount", id));
    }

    // ---- BusinessBankAccountDirectory ----

    @Override
    public Optional<UsableAccount> usable(long id) {
        return repository.findById(id)
                .filter(CenterBankAccount::isUsable)
                .map(a -> new UsableAccount(a.getId(), a.displayLabel()));
    }

    // ---- commands ----

    @Transactional
    public CenterBankAccount create(AccountInput in) {
        String holder = required(in.accountHolderName(), "Account holder name");
        String ifsc = normalizeIfsc(in.ifsc());
        String bankName = required(in.bankName(), "Bank name");
        String rawAccount = in.accountNumber() == null ? "" : in.accountNumber().replaceAll("\\s", "");
        if (!rawAccount.matches("\\d{9,18}")) {
            throw new BusinessRuleException("Account number must be 9–18 digits");
        }
        if (!IFSC.matcher(ifsc).matches()) {
            throw new BusinessRuleException("A valid IFSC code is required (e.g. HDFC0001234)");
        }

        CenterBankAccount a = new CenterBankAccount();
        a.setAccountHolderName(holder);
        a.setIfsc(ifsc);
        a.setBankName(bankName);
        a.setLabel(blankToNull(in.label()));
        a.setAccountNumberEnc(crypto.encrypt(rawAccount));
        a.setAccountNumberLast4(FieldCrypto.lastFour(rawAccount));
        a.setAccountNumberHash(crypto.blindIndex(rawAccount));
        a.setStatus(Status.ACTIVE);
        a.setVerified(false);
        boolean first = repository.count() == 0;
        a = repository.save(a);
        if (first) {
            // the only account so far — it will still need verifying before it can be used
            a.setPrimary(true);
        }
        audit.record("CENTER_BANK_ACCOUNT_ADDED", "CenterBankAccount", a.getId(),
                "Added %s / %s".formatted(a.getBankName(), a.maskedNumber()));
        return a;
    }

    @Transactional
    public CenterBankAccount update(long id, AccountInput in) {
        CenterBankAccount a = require(id);
        String holder = required(in.accountHolderName(), "Account holder name");
        String ifsc = normalizeIfsc(in.ifsc());
        String bankName = required(in.bankName(), "Bank name");
        if (!IFSC.matcher(ifsc).matches()) {
            throw new BusinessRuleException("A valid IFSC code is required (e.g. HDFC0001234)");
        }
        String rawAccount = in.accountNumber() == null ? "" : in.accountNumber().replaceAll("\\s", "");
        boolean keepAccount = rawAccount.isEmpty();
        if (!keepAccount && !rawAccount.matches("\\d{9,18}")) {
            throw new BusinessRuleException("Account number must be 9–18 digits");
        }

        boolean detailsChanged = !Objects.equals(a.getAccountHolderName(), holder)
                || !Objects.equals(a.getIfsc(), ifsc)
                || !Objects.equals(a.getBankName(), bankName);

        a.setAccountHolderName(holder);
        a.setIfsc(ifsc);
        a.setBankName(bankName);
        a.setLabel(blankToNull(in.label()));

        if (!keepAccount) {
            String newHash = crypto.blindIndex(rawAccount);
            if (!Objects.equals(a.getAccountNumberHash(), newHash)) {
                detailsChanged = true;
                a.setAccountNumberEnc(crypto.encrypt(rawAccount));
                a.setAccountNumberLast4(FieldCrypto.lastFour(rawAccount));
                a.setAccountNumberHash(newHash);
            }
        }

        if (detailsChanged && a.isVerified()) {
            // the account is no longer the one that was verified — require re-verification
            a.setVerified(false);
            a.setVerifiedAt(null);
            a.setVerifiedBy(null);
        }
        audit.record("CENTER_BANK_ACCOUNT_UPDATED", "CenterBankAccount", id,
                "Updated %s / %s%s".formatted(a.getBankName(), a.maskedNumber(),
                        detailsChanged && !a.isVerified() ? " (re-verification required)" : ""));
        return a;
    }

    @Transactional
    public CenterBankAccount verify(long id) {
        CenterBankAccount a = require(id);
        if (a.getStatus() != Status.ACTIVE) {
            throw new BusinessRuleException("An inactive account cannot be verified");
        }
        if (!a.isVerified()) {
            a.setVerified(true);
            a.setVerifiedAt(Instant.now());
            CurrentUser.id().ifPresent(a::setVerifiedBy);
            audit.record("CENTER_BANK_ACCOUNT_VERIFIED", "CenterBankAccount", id,
                    "Verified %s / %s".formatted(a.getBankName(), a.maskedNumber()));
        }
        return a;
    }

    @Transactional
    public CenterBankAccount unverify(long id) {
        CenterBankAccount a = require(id);
        if (a.isVerified()) {
            a.setVerified(false);
            a.setVerifiedAt(null);
            a.setVerifiedBy(null);
            audit.record("CENTER_BANK_ACCOUNT_UNVERIFIED", "CenterBankAccount", id,
                    "Verification removed from %s / %s".formatted(a.getBankName(), a.maskedNumber()));
        }
        return a;
    }

    @Transactional
    public CenterBankAccount setPrimary(long id) {
        CenterBankAccount a = require(id);
        if (!a.isUsable()) {
            throw new BusinessRuleException("Only a verified, active account can be made primary");
        }
        repository.findByPrimaryTrueAndStatus(Status.ACTIVE).ifPresent(current -> {
            if (!current.getId().equals(id)) {
                current.setPrimary(false);
            }
        });
        a.setPrimary(true);
        audit.record("CENTER_BANK_ACCOUNT_PRIMARY", "CenterBankAccount", id,
                "%s / %s set as primary".formatted(a.getBankName(), a.maskedNumber()));
        return a;
    }

    @Transactional
    public void deactivate(long id) {
        CenterBankAccount a = require(id);
        if (a.getStatus() == Status.INACTIVE) {
            return;
        }
        a.setStatus(Status.INACTIVE);
        a.setPrimary(false);
        audit.record("CENTER_BANK_ACCOUNT_DEACTIVATED", "CenterBankAccount", id,
                "Deactivated %s / %s".formatted(a.getBankName(), a.maskedNumber()));
    }

    private static String required(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new BusinessRuleException(field + " is required");
        }
        return v.trim();
    }

    private static String normalizeIfsc(String v) {
        return v == null ? "" : v.trim().toUpperCase();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public record AccountInput(String accountHolderName, String accountNumber, String ifsc,
                               String bankName, String label) {
    }
}
