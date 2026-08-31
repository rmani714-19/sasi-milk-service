package com.sasimilkservice.region.service;

import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.error.ConflictException;
import com.sasimilkservice.common.error.NotFoundException;
import com.sasimilkservice.region.domain.Region;
import com.sasimilkservice.region.repository.RegionRepository;
import com.sasimilkservice.region.spi.RegionDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class RegionService implements RegionDirectory {

    private final RegionRepository repository;
    private final AuditRecorder audit;

    public RegionService(RegionRepository repository, AuditRecorder audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public List<Region> findAll() {
        return repository.findAll();
    }

    public Region require(long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Region", id));
    }

    @Transactional
    public Region create(String code, String name, String description) {
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Region code '" + code + "' already exists");
        }
        Region r = new Region();
        r.setCode(code);
        r.setName(name);
        r.setDescription(description);
        r = repository.save(r);
        audit.record("REGION_CREATED", "Region", r.getId(), "Region " + code + " created");
        return r;
    }

    @Transactional
    public Region update(long id, String name, String description, Region.Status status) {
        Region r = require(id);
        r.setName(name);
        r.setDescription(description);
        if (status != null) {
            r.setStatus(status);
        }
        return r;
    }

    // ---- RegionDirectory ----

    @Override
    public Optional<RegionView> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id)
                .map(r -> new RegionView(r.getId(), r.getCode(), r.getName(),
                        r.getStatus() == Region.Status.ACTIVE));
    }

    @Override
    public boolean isActive(Long id) {
        return findById(id).map(RegionView::active).orElse(false);
    }
}
