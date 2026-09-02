package com.classhub.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorUserId,
        String actorEmail,
        AuditAction action,
        String entityType,
        UUID entityId,
        String summary,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorUserId(),
                log.getActorEmail(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getSummary(),
                log.getCreatedAt());
    }
}
