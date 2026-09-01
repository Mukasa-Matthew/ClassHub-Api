package com.classhub.note;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LectureNoteAiOutputRepository extends JpaRepository<LectureNoteAiOutput, UUID> {

    List<LectureNoteAiOutput> findByLectureNoteIdOrderByCreatedAtDesc(UUID lectureNoteId);

    long countByLectureNoteId(UUID lectureNoteId);

    @Query("""
            select o.lectureNote.id, count(o)
            from LectureNoteAiOutput o
            where o.lectureNote.id in :noteIds
            group by o.lectureNote.id
            """)
    List<Object[]> countGroupedByNoteId(@Param("noteIds") java.util.Collection<UUID> noteIds);
}
