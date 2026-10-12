package com.classhub.courseunit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseUnitRepository extends JpaRepository<CourseUnit, UUID> {

    boolean existsByAcademicClassIdAndNormalizedName(UUID academicClassId, String normalizedName);

    boolean existsByAcademicClassIdAndNormalizedNameAndIdNot(
            UUID academicClassId, String normalizedName, UUID id);

    Optional<CourseUnit> findByAcademicClassIdAndNormalizedName(UUID academicClassId, String normalizedName);

    List<CourseUnit> findAllByAcademicClassIdOrderByNameAsc(UUID academicClassId);

    List<CourseUnit> findByAcademicClassIdAndActiveOrderByNameAsc(UUID academicClassId, boolean active);

    long countByAcademicClassIdAndActive(UUID academicClassId, boolean active);

    long countByActive(boolean active);
}
