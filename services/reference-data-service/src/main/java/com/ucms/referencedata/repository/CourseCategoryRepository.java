package com.ucms.referencedata.repository;

import com.ucms.referencedata.domain.CourseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, UUID> {
    List<CourseCategory> findByDeletedAtIsNullOrderByNameAsc();
    Optional<CourseCategory> findByIdAndDeletedAtIsNull(UUID id);
}
