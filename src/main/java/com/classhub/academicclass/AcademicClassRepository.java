package com.classhub.academicclass;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicClassRepository extends JpaRepository<AcademicClass, UUID> {

    Optional<AcademicClass> findByJoinCodeIgnoreCase(String joinCode);

    boolean existsByJoinCodeIgnoreCase(String joinCode);

    List<AcademicClass> findAllByOrderByNameAsc();
}
