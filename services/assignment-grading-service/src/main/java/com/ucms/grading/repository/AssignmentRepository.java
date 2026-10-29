package com.ucms.grading.repository;

import com.ucms.grading.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    Optional<Assignment> findByIdAndDeletedAtIsNull(UUID id);
    List<Assignment> findByDeletedAtIsNullOrderByDueDateDesc();
    List<Assignment> findByCourseOfferingIdInAndDeletedAtIsNullOrderByDueDateDesc(Collection<UUID> offeringIds);
}
