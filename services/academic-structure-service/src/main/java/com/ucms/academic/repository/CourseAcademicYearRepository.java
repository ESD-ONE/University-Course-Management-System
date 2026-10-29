package com.ucms.academic.repository;

import com.ucms.academic.domain.CourseAcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /** Every live offering with everything the cross-service offering view needs, in one
     * query — filtering happens in CourseOfferingQueryService, since the data set is a
     * single university's offerings, not something that needs DB-side paging. */
    @Query("select cay from CourseAcademicYear cay join fetch cay.course c join fetch c.catalogCourse "
            + "join fetch c.degreeProgram join fetch cay.academicYear left join fetch cay.semester "
            + "where cay.deletedAt is null and c.deletedAt is null")
    List<CourseAcademicYear> findAllLiveWithDetails();
}
