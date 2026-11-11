package com.ucms.grading.repository;

import com.ucms.grading.domain.GradeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GradeEventRepository extends JpaRepository<GradeEvent, UUID> {
    List<GradeEvent> findBySubmissionIdOrderByOccurredAtAsc(UUID submissionId);
}
