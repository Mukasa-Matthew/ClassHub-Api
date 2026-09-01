package com.classhub.audit;

import com.classhub.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    public AdminAuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    public ApiResponse<List<AuditLogResponse>> list(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        AuditLogQueryService.ApiPage result =
                auditLogQueryService.list(action, actorUserId, entityType, page, size);
        return ApiResponse.of(result.data(), result.pagination());
    }
}
