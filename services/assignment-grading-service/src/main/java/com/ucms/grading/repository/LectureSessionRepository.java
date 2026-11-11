package com.ucms.grading.repository;

import com.ucms.grading.domain.LectureSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LectureSessionRepository extends JpaRepository<LectureSession, UUID> {
    List<LectureSession> findByCourseOfferingIdAndDeletedAtIsNullOrderByStartsAtAsc(UUID offeringId);
    List<LectureSession> findByWeekIdAndDeletedAtIsNull(UUID weekId);
    Optional<LectureSession> findByIdAndDeletedAtIsNull(UUID id);
}
