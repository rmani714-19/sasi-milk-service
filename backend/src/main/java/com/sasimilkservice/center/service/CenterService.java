package com.sasimilkservice.center.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.center.domain.Center;
import com.sasimilkservice.center.repository.CenterRepository;
import com.sasimilkservice.center.spi.CenterProfileDirectory;
import com.sasimilkservice.center.spi.CenterSettings;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.incentive.spi.IncentiveCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;

@Service
@Transactional(readOnly = true)
public class CenterService implements CenterProfileDirectory {

    private final CenterRepository repository;
    private final AuditRecorder audit;

    public CenterService(CenterRepository repository, AuditRecorder audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public Center get() {
        return repository.findTopByOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("Center is not configured"));
    }

    @Override
    public CenterProfile profile() {
        Center c = get();
        return new CenterProfile(c.getCenterName(), c.getAddress(), c.getMobileNumber());
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
        audit.record("CENTER_UPDATED", "Center", c.getId(), "Center details updated");
        return c;
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
            Boolean allowMissingRate) {
    }
}
