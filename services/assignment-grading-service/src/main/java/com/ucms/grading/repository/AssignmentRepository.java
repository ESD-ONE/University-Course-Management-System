package com.ucms.grading.repository;

import com.ucms.grading.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID>, JpaSpecificationExecutor<Assignment> {
    Optional<Assignment> findByIdAndDeletedAtIsNull(UUID id);
    /** Still used where every matching row is genuinely needed, not one page of them:
     * GradingService's bulk per-offering aggregation. */
    List<Assignment> findByCourseOfferingIdInAndDeletedAtIsNullOrderByDueDateDesc(Collection<UUID> offeringIds);
    List<Assignment> findByWeekIdAndDeletedAtIsNull(UUID weekId);
}
