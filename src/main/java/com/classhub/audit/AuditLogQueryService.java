package com.classhub.audit;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.api.Pagination;
import com.classhub.common.exception.ApplicationException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AuditLogRepository auditLogRepository;

    public AuditLogQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public ApiPage list(
            AuditAction action, UUID actorUserId, String entityType, Integer page, Integer size) {
        int pageNumber = page == null ? DEFAULT_PAGE : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 1) {
            throw new ApplicationException(
                    ErrorCodes.VALIDATION_ERROR, "page must be >= 1", HttpStatus.BAD_REQUEST);
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new ApplicationException(
                    ErrorCodes.VALIDATION_ERROR,
                    "size must be between 1 and " + MAX_SIZE,
                    HttpStatus.BAD_REQUEST);
        }

        String normalizedEntityType =
                entityType == null || entityType.isBlank() ? null : entityType.trim().toUpperCase();

        Page<AuditLog> result = auditLogRepository.search(
                action, actorUserId, normalizedEntityType, PageRequest.of(pageNumber - 1, pageSize));
        List<AuditLogResponse> data =
                result.getContent().stream().map(AuditLogResponse::from).toList();
        Pagination pagination = new Pagination(
                pageNumber, pageSize, result.getTotalElements(), result.getTotalPages());
        return new ApiPage(data, pagination);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> recent(int limit) {
        Page<AuditLog> result =
                auditLogRepository.search(null, null, null, PageRequest.of(0, Math.max(1, limit)));
        return result.getContent().stream().map(AuditLogResponse::from).toList();
    }

    public record ApiPage(List<AuditLogResponse> data, Pagination pagination) {
    }
}
