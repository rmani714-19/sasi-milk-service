package com.sasimilkservice.audit.service;

import com.sasimilkservice.audit.domain.AuditLog;
import com.sasimilkservice.audit.repository.AuditLogRepository;
import com.sasimilkservice.audit.spi.AuditRecorder;
import com.sasimilkservice.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditRecorderImpl implements AuditRecorder {

    private final AuditLogRepository repository;

    public AuditRecorderImpl(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(AuditEvent event) {
        AuditLog log = new AuditLog();
        log.setAction(event.action());
        log.setEntityType(event.entityType());
        log.setEntityId(event.entityId());
        log.setOldValue(event.oldValue());
        log.setNewValue(event.newValue());
        log.setSummary(event.summary());
        CurrentUser.id().ifPresent(log::setUserId);
        CurrentUser.username().ifPresent(log::setUsername);
        currentRequest().ifPresent(attrs -> {
            log.setIpAddress(attrs.getRequest().getRemoteAddr());
            log.setUserAgent(attrs.getRequest().getHeader("User-Agent"));
        });
        repository.save(log);
    }

    private java.util.Optional<ServletRequestAttributes> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            return java.util.Optional.of(sra);
        }
        return java.util.Optional.empty();
    }
}
