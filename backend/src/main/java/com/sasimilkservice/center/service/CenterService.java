package com.sasimilkservice.center.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.center.domain.Center;
import com.sasimilkservice.center.repository.CenterRepository;
import com.sasimilkservice.center.spi.CenterSettings;
import com.sasimilkservice.common.crypto.FieldCrypto;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.common.security.CurrentUser;
import com.sasimilkservice.incentive.spi.IncentiveCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class CenterService {

    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    private final CenterRepository repository;
    private final FieldCrypto crypto;
    private final AuditRecorder audit;

    public CenterService(CenterRepository repository, FieldCrypto crypto, AuditRecorder audit) {
        this.repository = repository;
        this.crypto = crypto;
        this.audit = audit;
    }

    public Center get() {
        return repository.findTopByOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("Center is not configured"));
    }

    public CenterSettings settings() {
        Center c = get();
        return new CenterSettings(
                c.getCurrencyCode(),
                "NONE".equals(c.getRoundingMode()) ? null : RoundingMode.valueOf(c.getRoundingMode()),
                c.getRoundingScale(),
                IncentiveCalculator.Stacking.valueOf(c.getIncentiveStacking()),
                c.getIncentiveMaxPerLitre(),
                c.isAllowMissingRate(),
                c.getFatMin(), c.getFatMax(), c.getSnfMin(), c.getSnfMax());
    }

    /** Full, decrypted payout account — call site must gate on the right permission. */
    public PayoutAccountView payoutAccountView() {
        Center c = get();
        if (!c.isPayoutAccountComplete()) {
            return null;
        }
        return new PayoutAccountView(
                c.getPayoutAccountHolderName(),
                crypto.decrypt(c.getPayoutAccountNumberEnc()),
                c.getPayoutIfsc(),
                c.getPayoutBankName(),
                c.getPayoutAccountUpdatedAt());
    }

    @Transactional
    public Center update(UpdateCenterCommand cmd) {
        Center c = get();
        c.setCenterName(cmd.centerName());
        c.setOwnerName(cmd.ownerName());
        c.setMobileNumber(cmd.mobileNumber());
        c.setEmail(cmd.email());
        c.setAddress(cmd.address());
        if (cmd.settlementDayOfWeek() != null) {
            c.setSettlementDayOfWeek(cmd.settlementDayOfWeek());
        }
        if (cmd.roundingMode() != null) {
            c.setRoundingMode(cmd.roundingMode());
        }
        if (cmd.roundingScale() != null) {
            c.setRoundingScale(cmd.roundingScale());
        }
        if (cmd.incentiveStacking() != null) {
            c.setIncentiveStacking(cmd.incentiveStacking());
        }
        if (cmd.incentiveMaxPerLitre() != null) {
            c.setIncentiveMaxPerLitre(cmd.incentiveMaxPerLitre());
        }
        if (cmd.allowMissingRate() != null) {
            c.setAllowMissingRate(cmd.allowMissingRate());
        }
        applyPayoutAccount(c, cmd.payoutAccount());
        audit.record("CENTER_UPDATED", "Center", c.getId(), "Center details updated");
        return c;
    }

    /**
     * The payout account block is optional as a whole (an existing centre may not have
     * one configured yet). But once any field is supplied, holder/IFSC/bank name must
     * all be present — a partially-filled account is never saved. On edit the account
     * number may be omitted to keep the one already on file.
     */
    private void applyPayoutAccount(Center c, PayoutAccountInput in) {
        if (in == null || isEntirelyBlank(in)) {
            return;
        }
        String holder = trim(in.accountHolderName());
        String ifsc = in.ifsc() == null ? null : in.ifsc().trim().toUpperCase();
        String bankName = trim(in.bankName());
        String rawAccount = in.accountNumber() == null ? "" : in.accountNumber().replaceAll("\\s", "");
        boolean keepExistingAccount = rawAccount.isEmpty() && c.getPayoutAccountNumberEnc() != null;

        if (holder == null || holder.isBlank()) {
            throw new BusinessRuleException("Payout account holder name is required");
        }
        if (bankName == null || bankName.isBlank()) {
            throw new BusinessRuleException("Payout account bank name is required");
        }
        if (ifsc == null || !IFSC.matcher(ifsc).matches()) {
            throw new BusinessRuleException("A valid payout account IFSC code is required (e.g. HDFC0001234)");
        }
        if (!keepExistingAccount && !rawAccount.matches("\\d{9,18}")) {
            throw new BusinessRuleException("Payout account number must be 9–18 digits");
        }

        boolean changed = !Objects.equals(c.getPayoutAccountHolderName(), holder)
                || !Objects.equals(c.getPayoutIfsc(), ifsc)
                || !Objects.equals(c.getPayoutBankName(), bankName);

        c.setPayoutAccountHolderName(holder);
        c.setPayoutIfsc(ifsc);
        c.setPayoutBankName(bankName);

        if (!keepExistingAccount) {
            String newHash = crypto.blindIndex(rawAccount);
            if (!Objects.equals(c.getPayoutAccountNumberHash(), newHash)) {
                changed = true;
                c.setPayoutAccountNumberEnc(crypto.encrypt(rawAccount));
                c.setPayoutAccountNumberLast4(FieldCrypto.lastFour(rawAccount));
                c.setPayoutAccountNumberHash(newHash);
            }
        }

        if (changed) {
            c.setPayoutAccountUpdatedAt(Instant.now());
            CurrentUser.id().ifPresent(c::setPayoutAccountUpdatedBy);
            audit.record("CENTER_PAYOUT_ACCOUNT_UPDATED", "Center", c.getId(),
                    "Payout account changed (%s / %s)".formatted(c.getPayoutBankName(), c.maskedPayoutAccount()));
        }
    }

    private static boolean isEntirelyBlank(PayoutAccountInput in) {
        return isBlank(in.accountHolderName()) && isBlank(in.accountNumber())
                && isBlank(in.ifsc()) && isBlank(in.bankName());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    public record UpdateCenterCommand(
            String centerName,
            String ownerName,
            String mobileNumber,
            String email,
            String address,
            Integer settlementDayOfWeek,
            String roundingMode,
            Integer roundingScale,
            String incentiveStacking,
            java.math.BigDecimal incentiveMaxPerLitre,
            Boolean allowMissingRate,
            PayoutAccountInput payoutAccount) {
    }

    public record PayoutAccountInput(
            String accountHolderName,
            String accountNumber,
            String ifsc,
            String bankName) {
    }

    public record PayoutAccountView(
            String accountHolderName,
            String accountNumber,
            String ifsc,
            String bankName,
            Instant updatedAt) {
    }
}
