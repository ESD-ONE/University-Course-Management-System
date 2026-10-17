package com.ucms.academic.repository;

import com.ucms.academic.domain.ProgramCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProgramCourseRepository extends JpaRepository<ProgramCourse, UUID> {
    boolean existsByDegreeProgramIdAndCatalogCourseId(UUID degreeProgramId, UUID catalogCourseId);
    boolean existsByDegreeProgramId(UUID degreeProgramId);
    List<ProgramCourse> findByDegreeProgramIdOrderByRecommendedYearAscRecommendedSemesterAsc(UUID degreeProgramId);
}
