package com.ucms.base.repository;

import com.ucms.base.domain.CourseCategory;
import com.ucms.base.domain.ReferenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, UUID>, JpaSpecificationExecutor<CourseCategory> {
    Optional<CourseCategory> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByNameAndDeletedAtIsNull(String name);
    boolean existsByNameAndDeletedAtIsNullAndIdNot(String name, UUID id);
    long countByDeletedAtIsNull();
    long countByDeletedAtIsNullAndStatus(ReferenceStatus status);
}
