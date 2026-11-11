package com.ucms.academic.repository;

import com.ucms.academic.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {
    List<Course> findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(List<UUID> ids);
    /** Used only to bulk-assign every course of a program to a newly-assigned academic
     * year (DegreeProgramService) — a real "every row" case, not a user-facing list. */
    List<Course> findByDegreeProgramIdAndDeletedAtIsNull(UUID degreeProgramId);
    boolean existsByDegreeProgramIdAndDeletedAtIsNull(UUID degreeProgramId);
    boolean existsByCatalogCourseIdAndDeletedAtIsNull(UUID catalogCourseId);
    boolean existsByDegreeProgramIdAndCatalogCourseIdAndDeletedAtIsNull(UUID degreeProgramId, UUID catalogCourseId);
}
