package com.classhub.audit;

import com.classhub.security.ClassHubUserDetails;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(AuditAction action, String entityType, UUID entityId, String summary) {
        Actor actor = currentActor();
        auditLogRepository.save(new AuditLog(
                actor.userId(),
                actor.email(),
                action,
                entityType,
                entityId,
                truncate(summary, 500)));
    }

    private static Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            return new Actor(null, null);
        }
        return new Actor(principal.getId(), principal.getUsername());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    private record Actor(UUID userId, String email) {
    }
}
