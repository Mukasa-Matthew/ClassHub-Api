package com.classhub.coursework;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseworkProgressRepository extends JpaRepository<CourseworkProgress, UUID> {

    Optional<CourseworkProgress> findByCourseworkIdAndStudentId(UUID courseworkId, UUID studentId);

    List<CourseworkProgress> findByStudentIdAndCourseworkIdIn(UUID studentId, Collection<UUID> courseworkIds);

    boolean existsByCourseworkIdAndStudentId(UUID courseworkId, UUID studentId);

    long countByCourseworkIdAndStudentId(UUID courseworkId, UUID studentId);
}
