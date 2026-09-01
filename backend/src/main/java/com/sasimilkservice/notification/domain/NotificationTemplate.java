package com.sasimilkservice.notification.domain;

import com.sasimilkservice.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedBy;

/** Editable {@code {{mustache}}} template for one (event code, channel, locale). */
@Entity
@Table(name = "notification_templates")
@Getter
@Setter
public class NotificationTemplate extends BaseEntity {

    @Version
    @Column(nullable = false)
    private long version;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    public enum Channel { SMS, WHATSAPP, EMAIL }

    public enum Status { ACTIVE, INACTIVE }

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(nullable = false)
    private String locale = "en";

    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(name = "provider_template_id")
    private String providerTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
}
