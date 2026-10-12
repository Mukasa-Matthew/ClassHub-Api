package com.classhub.coursework;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseworkAttachmentRepository extends JpaRepository<CourseworkAttachment, UUID> {

    List<CourseworkAttachment> findByCourseworkIdOrderByCreatedAtAsc(UUID courseworkId);

    Optional<CourseworkAttachment> findByIdAndCourseworkId(UUID id, UUID courseworkId);

    long countByCourseworkId(UUID courseworkId);

    @Query("""
            select a.coursework.id, count(a)
            from CourseworkAttachment a
            where a.coursework.id in :courseworkIds
            group by a.coursework.id
            """)
    List<Object[]> countGroupedByCourseworkId(@Param("courseworkIds") Collection<UUID> courseworkIds);
}
