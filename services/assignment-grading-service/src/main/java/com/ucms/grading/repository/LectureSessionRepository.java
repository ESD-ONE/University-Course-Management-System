package com.ucms.grading.repository;

import com.ucms.grading.domain.LectureSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LectureSessionRepository extends JpaRepository<LectureSession, UUID> {
    List<LectureSession> findByCourseOfferingIdAndDeletedAtIsNullOrderByStartsAtAsc(UUID offeringId);
    List<LectureSession> findByWeekIdAndDeletedAtIsNull(UUID weekId);
    Optional<LectureSession> findByIdAndDeletedAtIsNull(UUID id);
    /** UpcomingService's "what's starting soon" feed — same date-bounded-at-the-query
     * reasoning as AssignmentRepository's equivalent. */
    List<LectureSession> findByDeletedAtIsNullAndStartsAtBetween(OffsetDateTime from, OffsetDateTime to);
    List<LectureSession> findByCourseOfferingIdInAndDeletedAtIsNullAndStartsAtBetween(
            Collection<UUID> offeringIds, OffsetDateTime from, OffsetDateTime to);
}
