package com.ucms.grading.repository;

import com.ucms.grading.domain.CourseWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseWeekRepository extends JpaRepository<CourseWeek, UUID> {
    List<CourseWeek> findByCourseOfferingIdAndDeletedAtIsNullOrderByWeekNumberAsc(UUID offeringId);
    Optional<CourseWeek> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByCourseOfferingIdAndWeekNumberAndDeletedAtIsNull(UUID offeringId, int weekNumber);
}
