package com.classhub.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query(
            value = """
                    select a from AuditLog a
                    where (:action is null or a.action = :action)
                      and (:actorUserId is null or a.actorUserId = :actorUserId)
                      and (:entityType is null or a.entityType = :entityType)
                    order by a.createdAt desc
                    """,
            countQuery = """
                    select count(a) from AuditLog a
                    where (:action is null or a.action = :action)
                      and (:actorUserId is null or a.actorUserId = :actorUserId)
                      and (:entityType is null or a.entityType = :entityType)
                    """)
    Page<AuditLog> search(
            @Param("action") AuditAction action,
            @Param("actorUserId") UUID actorUserId,
            @Param("entityType") String entityType,
            Pageable pageable);
}
