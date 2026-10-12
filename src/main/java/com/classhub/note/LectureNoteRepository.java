package com.classhub.note;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LectureNoteRepository extends JpaRepository<LectureNote, UUID> {

    @Query("""
            select n from LectureNote n
            join fetch n.courseUnit
            join fetch n.student
            where n.id = :id and n.student.id = :studentId
            """)
    Optional<LectureNote> findOwnedDetailedById(
            @Param("id") UUID id, @Param("studentId") UUID studentId);

    @Query(
            value = """
                    select n from LectureNote n
                    join fetch n.courseUnit
                    where n.student.id = :studentId
                      and (:courseUnitId is null or n.courseUnit.id = :courseUnitId)
                      and (:status is null or n.status = :status)
                    order by n.updatedAt desc, n.createdAt desc
                    """,
            countQuery = """
                    select count(n) from LectureNote n
                    where n.student.id = :studentId
                      and (:courseUnitId is null or n.courseUnit.id = :courseUnitId)
                      and (:status is null or n.status = :status)
                    """)
    Page<LectureNote> searchOwned(
            @Param("studentId") UUID studentId,
            @Param("courseUnitId") UUID courseUnitId,
            @Param("status") LectureNoteStatus status,
            Pageable pageable);
}
