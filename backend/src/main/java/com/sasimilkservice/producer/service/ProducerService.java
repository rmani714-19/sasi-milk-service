package com.sasimilkservice.producer.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.crypto.FieldCrypto;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.producer.domain.Producer;
import com.sasimilkservice.producer.domain.ProducerBankAccount;
import com.sasimilkservice.producer.domain.ProducerRegionHistory;
import com.sasimilkservice.producer.repository.ProducerBankAccountRepository;
import com.sasimilkservice.producer.repository.ProducerRegionHistoryRepository;
import com.sasimilkservice.producer.repository.ProducerRepository;
import com.sasimilkservice.producer.spi.BankAccountResolver;
import com.sasimilkservice.producer.spi.ProducerDirectory;
import com.sasimilkservice.region.spi.RegionDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProducerService implements ProducerDirectory, BankAccountResolver {

    private final ProducerRepository producers;
    private final ProducerBankAccountRepository bankAccounts;
    private final ProducerRegionHistoryRepository regionHistory;
    private final RegionDirectory regions;
    private final FieldCrypto crypto;
    private final AuditRecorder audit;

    public ProducerService(ProducerRepository producers,
                           ProducerBankAccountRepository bankAccounts,
                           ProducerRegionHistoryRepository regionHistory,
                           RegionDirectory regions,
                           FieldCrypto crypto,
                           AuditRecorder audit) {
        this.producers = producers;
        this.bankAccounts = bankAccounts;
        this.regionHistory = regionHistory;
        this.regions = regions;
        this.crypto = crypto;
        this.audit = audit;
    }

    public Page<Producer> search(Producer.Status status, Long regionId, String q, Pageable pageable) {
        String query = (q == null || q.isBlank()) ? "" : q.trim();
        return producers.search(status, regionId, query, pageable);
    }

    public Producer require(long id) {
        return producers.findById(id).orElseThrow(() -> NotFoundException.of("Producer", id));
    }

    @Transactional
    public Producer create(CreateProducerCommand cmd) {
        validateRegion(cmd.regionId());
        Producer p = new Producer();
        p.setProducerNumber(nextProducerNumber());
        p.setProducerName(cmd.producerName());
        p.setMobileNumber(cmd.mobileNumber());
        p.setEmail(cmd.email());
        p.setAddress(cmd.address());
        p.setRegionId(cmd.regionId());
        p.setNotes(cmd.notes());
        p.setQrCode("SASI-" + p.getPublicId());
        if (cmd.joiningDate() != null) {
            p.setJoiningDate(cmd.joiningDate());
        }
        p = producers.save(p);
        audit.record("PRODUCER_CREATED", "Producer", p.getId(),
                "Producer " + p.getProducerNumber() + " created");
        return p;
    }

    @Transactional
    public Producer update(long id, UpdateProducerCommand cmd) {
        Producer p = require(id);
        p.setProducerName(cmd.producerName());
        p.setMobileNumber(cmd.mobileNumber());
        p.setEmail(cmd.email());
        p.setAddress(cmd.address());
        p.setNotes(cmd.notes());

        if (!Objects.equals(p.getRegionId(), cmd.regionId())) {
            validateRegion(cmd.regionId());
            ProducerRegionHistory h = new ProducerRegionHistory();
            h.setProducerId(p.getId());
            h.setOldRegionId(p.getRegionId());
            h.setNewRegionId(cmd.regionId());
            h.setReason(cmd.regionChangeReason());
            regionHistory.save(h);
            p.setRegionId(cmd.regionId());
        }
        audit.record("PRODUCER_UPDATED", "Producer", p.getId(),
                "Producer " + p.getProducerNumber() + " updated");
        return p;
    }

    @Transactional
    public Producer deactivate(long id) {
        Producer p = require(id);
        p.setStatus(Producer.Status.INACTIVE);
        audit.record("PRODUCER_DEACTIVATED", "Producer", p.getId(),
                "Producer " + p.getProducerNumber() + " deactivated");
        return p;
    }

    public List<ProducerBankAccount> bankAccountsOf(long producerId) {
        require(producerId);
        return bankAccounts.findByProducerIdOrderByIdDesc(producerId);
    }

    @Transactional
    public ProducerBankAccount addBankAccount(long producerId, AddBankAccountCommand cmd) {
        Producer p = require(producerId);
        String digits = cmd.accountNumber().replaceAll("\\s", "");
        if (digits.length() < 6) {
            throw new BusinessRuleException("Bank account number looks invalid");
        }

        bankAccounts.findByProducerIdAndPrimaryTrueAndStatus(producerId, ProducerBankAccount.Status.ACTIVE)
                .ifPresent(existing -> existing.setPrimary(false));

        ProducerBankAccount acc = new ProducerBankAccount();
        acc.setProducerId(producerId);
        acc.setAccountHolderName(cmd.accountHolderName());
        acc.setAccountNumberEnc(crypto.encrypt(digits));
        acc.setAccountNumberLast4(FieldCrypto.lastFour(digits));
        acc.setAccountNumberHash(crypto.blindIndex(digits));
        acc.setIfscCode(cmd.ifscCode().toUpperCase());
        acc.setBankName(cmd.bankName());
        acc.setBankBranch(cmd.bankBranch());
        acc.setPrimary(true);
        acc = bankAccounts.save(acc);

        audit.record("BANK_DETAILS_UPDATED", "Producer", p.getId(),
                "Bank account ending " + acc.getAccountNumberLast4() + " added for "
                        + p.getProducerNumber());
        return acc;
    }

    private void validateRegion(Long regionId) {
        if (regionId != null && !regions.isActive(regionId)) {
            throw new BusinessRuleException("Region " + regionId + " does not exist or is inactive");
        }
    }

    private String nextProducerNumber() {
        return "P%05d".formatted(producers.nextProducerNumberValue());
    }

    // ---- ProducerDirectory ----

    @Override
    public Optional<ProducerView> findById(Long id) {
        return id == null ? Optional.empty() : producers.findById(id).map(this::toView);
    }

    @Override
    public Optional<ProducerView> resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        String ref = reference.trim();
        Optional<Producer> found = producers.findByProducerNumber(ref);
        if (found.isEmpty()) {
            found = producers.findByQrCode(ref);
        }
        if (found.isEmpty()) {
            try {
                found = producers.findByPublicId(UUID.fromString(ref));
            } catch (IllegalArgumentException ignored) {
                // not a UUID
            }
        }
        return found.map(this::toView);
    }

    private ProducerView toView(Producer p) {
        return new ProducerView(p.getId(), p.getPublicId().toString(), p.getProducerNumber(),
                p.getProducerName(), p.getRegionId(), p.getStatus() == Producer.Status.ACTIVE);
    }

    @Override
    public Optional<ProducerContact> contact(Long producerId) {
        return producers.findById(producerId)
                .map(p -> new ProducerContact(p.getId(), p.getProducerName(),
                        p.getMobileNumber(), p.getEmail()));
    }

    // ---- BankAccountResolver ----

    @Override
    public Optional<Long> primaryActiveAccountId(Long producerId) {
        return bankAccounts
                .findByProducerIdAndPrimaryTrueAndStatus(producerId, ProducerBankAccount.Status.ACTIVE)
                .map(ProducerBankAccount::getId);
    }

    @Override
    public Optional<PayoutBankAccount> payoutDetails(Long bankAccountId) {
        return bankAccounts.findById(bankAccountId)
                .map(a -> new PayoutBankAccount(a.getId(), a.getAccountHolderName(),
                        crypto.decrypt(a.getAccountNumberEnc()), a.getIfscCode(), a.getBankName()));
    }

    // ---- commands ----

    public record CreateProducerCommand(
            String producerName, String mobileNumber, String email, String address,
            Long regionId, java.time.LocalDate joiningDate, String notes) {
    }

    public record UpdateProducerCommand(
            String producerName, String mobileNumber, String email, String address,
            Long regionId, String regionChangeReason, String notes) {
    }

    public record AddBankAccountCommand(
            String accountHolderName, String accountNumber, String ifscCode,
            String bankName, String bankBranch) {
    }
}
