package com.nerd.erp.domain.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "document_sequence")
@Getter
@Setter
public class DocumentSequence {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sequence_type", nullable = false, length = 20)
    private DocumentSequenceType sequenceType;

    @Column(nullable = false)
    private int year;

    @Column(name = "last_value", nullable = false)
    private long lastValue = 0;
}
