package com.ucms.grading.repository;

import com.ucms.grading.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    Optional<Submission> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
    List<Submission> findByAssignmentId(UUID assignmentId);
    List<Submission> findByAssignmentIdIn(Collection<UUID> assignmentIds);
    List<Submission> findByStudentId(UUID studentId);
    boolean existsByAssignmentId(UUID assignmentId);

    @Query("select s from Submission s join fetch s.assignment a where a.deletedAt is null")
    List<Submission> findAllLive();
}
