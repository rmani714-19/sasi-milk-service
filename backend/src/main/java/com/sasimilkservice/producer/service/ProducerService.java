package com.sasimilkservice.producer.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.crypto.FieldCrypto;
import com.sasimilkservice.common.error.BusinessRuleException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.producer.domain.Producer;
import com.sasimilkservice.producer.domain.ProducerRegionHistory;
import com.sasimilkservice.producer.repository.ProducerRegionHistoryRepository;
import com.sasimilkservice.producer.repository.ProducerRepository;
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
public class ProducerService implements ProducerDirectory {

    private final ProducerRepository producers;
    private final ProducerRegionHistoryRepository regionHistory;
    private final RegionDirectory regions;
    private final FieldCrypto crypto;
    private final AuditRecorder audit;

    public ProducerService(ProducerRepository producers,
                           ProducerRegionHistoryRepository regionHistory,
                           RegionDirectory regions,
                           FieldCrypto crypto,
                           AuditRecorder audit) {
        this.producers = producers;
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
        p.setAlternateMobile(cmd.alternateMobile());
        p.setUpiId(cmd.upiId());
        p.setEmail(cmd.email());
        p.setAddress(cmd.address());
        p.setRegionId(cmd.regionId());
        p.setNotes(cmd.notes());
        p.setPaymentFrequency(parseFrequency(cmd.paymentFrequency(), Producer.PaymentFrequency.WEEKLY));
        p.setQrCode("SASI-" + p.getPublicId());
        applyAadhaar(p, cmd.aadhaarNumber());
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
        p.setAlternateMobile(cmd.alternateMobile());
        p.setUpiId(cmd.upiId());
        p.setEmail(cmd.email());
        p.setAddress(cmd.address());
        p.setNotes(cmd.notes());
        if (cmd.aadhaarNumber() != null && !cmd.aadhaarNumber().isBlank()) {
            applyAadhaar(p, cmd.aadhaarNumber());
        }
        if (cmd.paymentFrequency() != null && !cmd.paymentFrequency().isBlank()) {
            Producer.PaymentFrequency next = parseFrequency(cmd.paymentFrequency(), p.getPaymentFrequency());
            if (next != p.getPaymentFrequency()) {
                // Historical settlement_items are immutable snapshots, so past payments are
                // untouched; the new frequency only affects which future runs pick this producer up.
                audit.record("PRODUCER_UPDATED", "Producer", p.getId(),
                        "Payment frequency %s -> %s".formatted(p.getPaymentFrequency(), next));
                p.setPaymentFrequency(next);
            }
        }

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

    private Producer.PaymentFrequency parseFrequency(String raw, Producer.PaymentFrequency fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Producer.PaymentFrequency.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("paymentFrequency must be WEEKLY or MONTHLY");
        }
    }

    private void validateRegion(Long regionId) {
        if (regionId != null && !regions.isActive(regionId)) {
            throw new BusinessRuleException("Region " + regionId + " does not exist or is inactive");
        }
    }

    private String nextProducerNumber() {
        return "P%05d".formatted(producers.nextProducerNumberValue());
    }

    private void applyAadhaar(Producer p, String aadhaarNumber) {
        if (aadhaarNumber == null || aadhaarNumber.isBlank()) {
            return;
        }
        String digits = aadhaarNumber.replaceAll("\\D", "");
        if (digits.length() != 12) {
            throw new BusinessRuleException("Aadhaar number must be 12 digits");
        }
        p.setAadhaarEnc(crypto.encrypt(digits));
        p.setAadhaarLast4(FieldCrypto.lastFour(digits));
        p.setAadhaarHash(crypto.blindIndex(digits));
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
    public List<Long> producerIdsByPaymentFrequency(String frequency) {
        Producer.PaymentFrequency f = parseFrequency(frequency, null);
        return f == null ? List.of() : producers.findIdsByPaymentFrequency(f);
    }

    @Override
    public List<Long> searchIds(String text) {
        return text == null || text.isBlank() ? List.of() : producers.findIdsMatching(text.trim());
    }

    @Override
    public Optional<ProducerContact> contact(Long producerId) {
        return producers.findById(producerId)
                .map(p -> new ProducerContact(p.getId(), p.getProducerName(),
                        p.getMobileNumber(), p.getEmail()));
    }

    // ---- commands ----

    public record CreateProducerCommand(
            String producerName, String mobileNumber, String alternateMobile,
            String email, String address, String upiId, String aadhaarNumber,
            Long regionId, java.time.LocalDate joiningDate, String notes,
            String paymentFrequency) {
    }

    public record UpdateProducerCommand(
            String producerName, String mobileNumber, String alternateMobile, String email,
            String address, String upiId, String aadhaarNumber,
            Long regionId, String regionChangeReason, String notes,
            String paymentFrequency) {
    }
}
