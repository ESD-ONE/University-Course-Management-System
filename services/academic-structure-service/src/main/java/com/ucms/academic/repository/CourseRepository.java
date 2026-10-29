package com.ucms.academic.repository;

import com.ucms.academic.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findByDeletedAtIsNullOrderByCreatedAtDesc();
    List<Course> findByLecturerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID lecturerId);
    List<Course> findByDegreeProgramIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID degreeProgramId);
    List<Course> findByLecturerIdAndDegreeProgramIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID lecturerId, UUID degreeProgramId);
    List<Course> findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(List<UUID> ids);
    boolean existsByDegreeProgramIdAndDeletedAtIsNull(UUID degreeProgramId);
    boolean existsByCatalogCourseIdAndDeletedAtIsNull(UUID catalogCourseId);
}
