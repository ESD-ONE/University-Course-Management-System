package com.ucms.academic.repository;

import com.ucms.academic.domain.CourseAcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseAcademicYearRepository extends JpaRepository<CourseAcademicYear, UUID> {
    List<CourseAcademicYear> findByCourseIdAndDeletedAtIsNull(UUID courseId);
    List<CourseAcademicYear> findByAcademicYearIdAndDeletedAtIsNull(UUID academicYearId);
    /** The "Degree Programs" screen's per-row expand: every course of this degree
     * program that's assigned to this academic year, with its resolved lecturer. */
    List<CourseAcademicYear> findByAcademicYearIdAndCourseDegreeProgramIdAndDeletedAtIsNull(UUID academicYearId, UUID degreeProgramId);
    Optional<CourseAcademicYear> findByIdAndCourseIdAndDeletedAtIsNull(UUID id, UUID courseId);
    boolean existsByCourseIdAndAcademicYearIdAndDeletedAtIsNull(UUID courseId, UUID academicYearId);
    boolean existsBySemesterIdAndDeletedAtIsNull(UUID semesterId);
}
