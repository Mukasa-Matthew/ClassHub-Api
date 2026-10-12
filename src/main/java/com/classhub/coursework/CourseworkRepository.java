package com.classhub.coursework;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseworkRepository extends JpaRepository<Coursework, UUID> {

    @Query("""
            select c from Coursework c
            join fetch c.courseUnit cu
            join fetch cu.academicClass
            join fetch c.createdBy
            where c.id = :id
            """)
    Optional<Coursework> findDetailedById(@Param("id") UUID id);

    @Query(
            value = """
                    select c from Coursework c
                    join fetch c.courseUnit cu
                    join fetch cu.academicClass
                    join fetch c.createdBy
                    where (:courseUnitId is null or cu.id = :courseUnitId)
                      and (:classId is null or cu.academicClass.id = :classId)
                      and (:status is null or c.status = :status)
                      and (:filterDueAfter = false or c.dueAt > :dueAfter)
                      and (:filterDueBefore = false or c.dueAt < :dueBefore)
                    order by c.dueAt asc, c.createdAt desc
                    """,
            countQuery = """
                    select count(c) from Coursework c
                    join c.courseUnit cu
                    where (:courseUnitId is null or cu.id = :courseUnitId)
                      and (:classId is null or cu.academicClass.id = :classId)
                      and (:status is null or c.status = :status)
                      and (:filterDueAfter = false or c.dueAt > :dueAfter)
                      and (:filterDueBefore = false or c.dueAt < :dueBefore)
                    """)
    Page<Coursework> search(
            @Param("courseUnitId") UUID courseUnitId,
            @Param("classId") UUID classId,
            @Param("status") CourseworkStatus status,
            @Param("filterDueAfter") boolean filterDueAfter,
            @Param("dueAfter") Instant dueAfter,
            @Param("filterDueBefore") boolean filterDueBefore,
            @Param("dueBefore") Instant dueBefore,
            Pageable pageable);

    long countByStatus(CourseworkStatus status);

    long countByStatusAndDueAtBefore(CourseworkStatus status, Instant dueAt);

    long countByStatusAndDueAtAfter(CourseworkStatus status, Instant dueAt);

    @Query("""
            select c from Coursework c
            join fetch c.courseUnit cu
            join fetch cu.academicClass
            where c.status = :status
            order by c.dueAt asc
            """)
    List<Coursework> findDetailedByStatusOrderByDueAtAsc(@Param("status") CourseworkStatus status);
}
