package com.ucms.grading.repository;

import com.ucms.grading.domain.ContentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentItemRepository extends JpaRepository<ContentItem, UUID> {
    List<ContentItem> findByCourseOfferingIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID offeringId);
    List<ContentItem> findByWeekIdAndDeletedAtIsNull(UUID weekId);
    List<ContentItem> findBySessionIdAndDeletedAtIsNull(UUID sessionId);
    Optional<ContentItem> findByIdAndDeletedAtIsNull(UUID id);
}
